"""The panel screen as a light: on = awake, off = clock screensaver, brightness = backlight."""

from __future__ import annotations

from typing import Any

from homeassistant.components.light import ATTR_BRIGHTNESS, ColorMode, LightEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from . import send_command
from .const import DOMAIN
from .entity import PanelEntity


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback) -> None:
    async_add_entities([PanelScreen(hass.data[DOMAIN], entry, "screen")])


class PanelScreen(PanelEntity, LightEntity):
    _attr_color_mode = ColorMode.BRIGHTNESS
    _attr_supported_color_modes = {ColorMode.BRIGHTNESS}

    @property
    def available(self) -> bool:
        return bool(self.panel and self.panel.connected)

    @property
    def is_on(self) -> bool | None:
        return self.panel.state.screen_on if self.panel else None

    @property
    def brightness(self) -> int | None:
        return self.panel.state.brightness if self.panel else None

    async def async_turn_on(self, **kwargs: Any) -> None:
        data: dict[str, Any] = {"on": True}
        if ATTR_BRIGHTNESS in kwargs:
            data["brightness"] = int(kwargs[ATTR_BRIGHTNESS])
        send_command(self.hass, self.device_id, "screen", **data)

    async def async_turn_off(self, **kwargs: Any) -> None:
        send_command(self.hass, self.device_id, "screen", on=False)
