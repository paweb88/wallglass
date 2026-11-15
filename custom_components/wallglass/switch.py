"""Quiet mode: while on, the panel makes no sound of its own (no spoken assistant answers)."""

from __future__ import annotations

from typing import Any

from homeassistant.components.switch import SwitchEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from . import send_command
from .const import DOMAIN
from .entity import PanelEntity


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback) -> None:
    async_add_entities([PanelQuiet(hass.data[DOMAIN], entry, "quiet")])


class PanelQuiet(PanelEntity, SwitchEntity):
    _attr_icon = "mdi:volume-off"

    @property
    def available(self) -> bool:
        return bool(self.panel and self.panel.connected)

    @property
    def is_on(self) -> bool | None:
        return self.panel.state.quiet if self.panel else None

    async def async_turn_on(self, **kwargs: Any) -> None:
        send_command(self.hass, self.device_id, "quiet", on=True)

    async def async_turn_off(self, **kwargs: Any) -> None:
        send_command(self.hass, self.device_id, "quiet", on=False)
