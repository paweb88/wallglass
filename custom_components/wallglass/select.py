"""Which screen the panel shows; selecting one switches the panel to it."""

from __future__ import annotations

from homeassistant.components.select import SelectEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from . import send_command
from .const import DOMAIN, PAGES
from .entity import PanelEntity


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback) -> None:
    async_add_entities([PanelPage(hass.data[DOMAIN], entry, "page")])


class PanelPage(PanelEntity, SelectEntity):
    _attr_options = PAGES

    @property
    def available(self) -> bool:
        return bool(self.panel and self.panel.connected)

    @property
    def current_option(self) -> str | None:
        return self.panel.state.page if self.panel else None

    async def async_select_option(self, option: str) -> None:
        send_command(self.hass, self.device_id, "page", page=option)
