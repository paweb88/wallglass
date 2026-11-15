"""Base entity for a panel device."""

from __future__ import annotations

from homeassistant.config_entries import ConfigEntry
from homeassistant.helpers.device_registry import DeviceInfo
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity import Entity

from .const import DOMAIN, SIGNAL_PANEL_UPDATED
from .hub import PanelHub, PanelRuntime


class PanelEntity(Entity):
    """Entity tied to one panel; refreshes when the panel reports or (dis)connects."""

    _attr_has_entity_name = True
    _attr_should_poll = False

    def __init__(self, hub: PanelHub, entry: ConfigEntry, key: str) -> None:
        self.hub = hub
        self.device_id: str = entry.data["device_id"]
        self._attr_unique_id = f"{self.device_id}_{key}"
        self._attr_translation_key = key
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, self.device_id)},
            name=entry.title,
            manufacturer="Wallglass",
            model=entry.data.get("model") or "Panel",
            sw_version=entry.data.get("version"),
        )

    @property
    def panel(self) -> PanelRuntime | None:
        return self.hub.panels.get(self.device_id)

    async def async_added_to_hass(self) -> None:
        self.async_on_remove(
            async_dispatcher_connect(self.hass, SIGNAL_PANEL_UPDATED.format(self.device_id), self.async_write_ha_state)
        )
