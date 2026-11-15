"""Runtime state of connected panels and the push channel to them."""

from __future__ import annotations

from dataclasses import dataclass, field
import logging
from typing import Any

from homeassistant.components.websocket_api import ActiveConnection
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_send

from .const import SIGNAL_PANEL_UPDATED

_LOGGER = logging.getLogger(__name__)


@dataclass
class PanelState:
    """What the panel last reported about itself."""

    screen_on: bool | None = None
    brightness: int | None = None  # 0-255
    page: str | None = None
    quiet: bool | None = None


@dataclass
class PanelRuntime:
    """One configured panel: its subscribers and last reported state."""

    device_id: str
    name: str
    state: PanelState = field(default_factory=PanelState)
    subscribers: dict[tuple[int, int], tuple[ActiveConnection, int]] = field(default_factory=dict)

    @property
    def connected(self) -> bool:
        return bool(self.subscribers)


class PanelHub:
    """Registry of panels keyed by the app's device id."""

    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self.panels: dict[str, PanelRuntime] = {}

    @callback
    def add_subscriber(self, device_id: str, connection: ActiveConnection, msg_id: int) -> None:
        panel = self.panels[device_id]
        panel.subscribers[(id(connection), msg_id)] = (connection, msg_id)
        self.notify(device_id)

    @callback
    def remove_subscriber(self, device_id: str, connection: ActiveConnection, msg_id: int) -> None:
        panel = self.panels.get(device_id)
        if panel is None:
            return
        panel.subscribers.pop((id(connection), msg_id), None)
        self.notify(device_id)

    @callback
    def send(self, device_id: str, payload: dict[str, Any]) -> bool:
        """Push a message to every live connection of the panel. Returns False if offline."""
        panel = self.panels.get(device_id)
        if panel is None or not panel.subscribers:
            _LOGGER.debug("Panel %s offline, dropping %s", device_id, payload.get("type"))
            return False
        for connection, msg_id in list(panel.subscribers.values()):
            connection.send_event(msg_id, payload)
        return True

    @callback
    def update_state(self, device_id: str, **values: Any) -> None:
        panel = self.panels.get(device_id)
        if panel is None:
            return
        for key, value in values.items():
            if value is not None:
                setattr(panel.state, key, value)
        self.notify(device_id)

    @callback
    def notify(self, device_id: str) -> None:
        async_dispatcher_send(self.hass, SIGNAL_PANEL_UPDATED.format(device_id))
