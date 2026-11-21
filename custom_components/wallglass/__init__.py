"""Wallglass: configure and control wall panels running the Wallglass app."""

from __future__ import annotations

import logging
from typing import Any

import probatio

from homeassistant.components import websocket_api
from homeassistant.config_entries import SOURCE_INTEGRATION_DISCOVERY, ConfigEntry
from homeassistant.const import EVENT_HOMEASSISTANT_STARTED
from homeassistant.core import Event, HomeAssistant, callback
from homeassistant.helpers import config_validation as cv
from homeassistant.helpers.typing import ConfigType

from .const import DOMAIN, PAGES, PLATFORMS
from .hub import PanelHub, PanelRuntime
from .panel_config import build_panel_config

_LOGGER = logging.getLogger(__name__)

CONFIG_SCHEMA = cv.config_entry_only_config_schema(DOMAIN)


def _hub(hass: HomeAssistant) -> PanelHub:
    if DOMAIN not in hass.data:
        hass.data[DOMAIN] = PanelHub(hass)
    return hass.data[DOMAIN]


async def async_setup(hass: HomeAssistant, config: ConfigType) -> bool:
    _hub(hass)
    websocket_api.async_register_command(hass, ws_register)
    websocket_api.async_register_command(hass, ws_subscribe)
    websocket_api.async_register_command(hass, ws_state)
    return True


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    hub = _hub(hass)
    if entry.data.get("hub"):
        return True  # only keeps the integration (and its WebSocket commands) loaded
    device_id = entry.data["device_id"]
    hub.panels[device_id] = PanelRuntime(device_id=device_id, name=entry.title)
    entry.async_on_unload(entry.add_update_listener(_options_updated))
    if not hass.is_running:
        # A panel that connects while HA starts gets a config built before every entity has its
        # state; send it again once HA is up so names, humidity and icons are complete.
        @callback
        def _resend(_: Event) -> None:
            push_config(hass, entry)

        entry.async_on_unload(hass.bus.async_listen_once(EVENT_HOMEASSISTANT_STARTED, _resend))
    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
    return True


async def async_unload_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    if entry.data.get("hub"):
        return True
    unloaded = await hass.config_entries.async_unload_platforms(entry, PLATFORMS)
    if unloaded:
        _hub(hass).panels.pop(entry.data["device_id"], None)
    return unloaded


async def _options_updated(hass: HomeAssistant, entry: ConfigEntry) -> None:
    push_config(hass, entry)


@callback
def push_config(hass: HomeAssistant, entry: ConfigEntry) -> None:
    _hub(hass).send(
        entry.data["device_id"],
        {"type": "config", "config": build_panel_config(hass, dict(entry.options))},
    )


@callback
def send_command(hass: HomeAssistant, device_id: str, command: str, **data: Any) -> bool:
    return _hub(hass).send(device_id, {"type": "command", "command": command, **data})


def _entry_for(hass: HomeAssistant, device_id: str) -> ConfigEntry | None:
    for entry in hass.config_entries.async_entries(DOMAIN):
        if entry.data.get("device_id") == device_id:
            return entry
    return None


def _allowed(connection: websocket_api.ActiveConnection, entry: ConfigEntry) -> bool:
    """Only the HA user that registered the panel (or an admin) may act as it."""
    return connection.user.is_admin or entry.data.get("user_id") == connection.user.id


@websocket_api.websocket_command(
    {
        probatio.Required("type"): "wallglass/register",
        probatio.Required("device_id"): str,
        probatio.Required("name"): str,
        probatio.Optional("model"): str,
        probatio.Optional("version"): str,
    }
)
@websocket_api.async_response
async def ws_register(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict[str, Any]
) -> None:
    """Panel announces itself; a new one shows up in HA as a discovered device to confirm."""
    device_id = msg["device_id"]
    entry = _entry_for(hass, device_id)
    if entry is not None:
        connection.send_result(msg["id"], {"status": "configured" if _allowed(connection, entry) else "forbidden"})
        return
    in_progress = any(
        flow["context"].get("unique_id") == device_id
        for flow in hass.config_entries.flow.async_progress_by_handler(DOMAIN)
    )
    if not in_progress:
        await hass.config_entries.flow.async_init(
            DOMAIN,
            context={"source": SOURCE_INTEGRATION_DISCOVERY},
            data={
                "device_id": device_id,
                "name": msg["name"],
                "model": msg.get("model"),
                "version": msg.get("version"),
                "user_id": connection.user.id,
            },
        )
    connection.send_result(msg["id"], {"status": "pending"})


@websocket_api.websocket_command(
    {
        probatio.Required("type"): "wallglass/subscribe",
        probatio.Required("device_id"): str,
    }
)
@callback
def ws_subscribe(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict[str, Any]
) -> None:
    """Live channel to the panel: current config now, later config updates and commands."""
    device_id = msg["device_id"]
    entry = _entry_for(hass, device_id)
    hub = _hub(hass)
    if entry is None or device_id not in hub.panels:
        connection.send_error(msg["id"], "not_found", "Panel is not configured in HA yet")
        return
    if not _allowed(connection, entry):
        connection.send_error(msg["id"], "unauthorized", "This user did not register the panel")
        return

    msg_id = msg["id"]

    @callback
    def unsubscribe() -> None:
        hub.remove_subscriber(device_id, connection, msg_id)

    connection.subscriptions[msg_id] = unsubscribe
    connection.send_result(msg_id)
    hub.add_subscriber(device_id, connection, msg_id)
    connection.send_event(
        msg_id, {"type": "config", "config": build_panel_config(hass, dict(entry.options))}
    )


@websocket_api.websocket_command(
    {
        probatio.Required("type"): "wallglass/state",
        probatio.Required("device_id"): str,
        probatio.Optional("screen_on"): bool,
        probatio.Optional("brightness"): probatio.All(int, probatio.Range(min=0, max=255)),
        probatio.Optional("page"): probatio.In(PAGES),
        probatio.Optional("quiet"): bool,
    }
)
@callback
def ws_state(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict[str, Any]
) -> None:
    """Panel reports its screen and page so HA entities stay in sync."""
    entry = _entry_for(hass, msg["device_id"])
    if entry is None or not _allowed(connection, entry):
        connection.send_error(msg["id"], "not_found", "Unknown panel")
        return
    _hub(hass).update_state(
        msg["device_id"],
        screen_on=msg.get("screen_on"),
        brightness=msg.get("brightness"),
        page=msg.get("page"),
        quiet=msg.get("quiet"),
    )
    connection.send_result(msg["id"])
