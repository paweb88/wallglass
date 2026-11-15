"""Config flow: panels announce themselves; options pick the entities each panel shows."""

from __future__ import annotations

from typing import Any

import probatio

from homeassistant.config_entries import ConfigEntry, ConfigFlow, ConfigFlowResult, OptionsFlow
from homeassistant.core import callback
from homeassistant.helpers import selector

from .const import (
    DOMAIN,
    PAGES,
    OPT_ALARMS,
    OPT_CAMERAS,
    OPT_CHIPS,
    OPT_COVERS,
    OPT_DOORBELL_CAMERA,
    OPT_DOORBELL_MUTE,
    OPT_DOORBELL_OPEN,
    OPT_DOORBELL_RING,
    OPT_GESTURES,
    OPT_GO2RTC,
    OPT_GO2RTC_API,
    OPT_MEDIA,
    OPT_HEATING_ADD_HOUR,
    OPT_HEATING_SCHEDULES,
    OPT_HEATING_STOP,
    OPT_HEATING_SWITCH,
    OPT_HEATING_TIMER,
    OPT_NIGHT_FROM,
    OPT_NIGHT_TO,
    OPT_OPENINGS,
    OPT_ROOMS,
    OPT_SCREENSAVER,
    OPT_TALK,
    LEGACY_OPTIONS,
    OPT_TODO,
    OPT_WALLPAPER,
    OPT_WALLPAPER_IMAGE,
    OPT_WASTE,
    OPT_WEATHER,
)

ACTION_DOMAINS = ["script", "button", "input_button", "switch", "lock", "scene"]


def _entity(domain: str | list[str], *, multiple: bool = False, device_class: str | list[str] | None = None):
    flt: dict[str, Any] = {"domain": domain}
    if device_class:
        flt["device_class"] = device_class
    return selector.EntitySelector(selector.EntitySelectorConfig(filter=[flt], multiple=multiple))


def _opt(key: str, options: dict[str, Any]) -> probatio.Optional:
    """Optional field pre-filled with the current value (so saving keeps it)."""
    if options.get(key) not in (None, "", []):
        return probatio.Optional(key, description={"suggested_value": options[key]})
    return probatio.Optional(key)


STEP_FIELDS: dict[str, dict[str, Any]] = {
    "general": {
        OPT_WEATHER: _entity("weather"),
        OPT_TODO: _entity("todo"),
        OPT_MEDIA: _entity("media_player", multiple=True),
        OPT_WASTE: _entity("sensor"),
        OPT_CHIPS: _entity(
            ["switch", "input_boolean", "binary_sensor", "alarm_control_panel", "lock", "cover", "climate"],
            multiple=True,
        ),
    },
    "doorbell": {
        OPT_DOORBELL_CAMERA: _entity("camera"),
        OPT_DOORBELL_RING: _entity(["binary_sensor", "input_boolean"]),
        OPT_DOORBELL_OPEN: _entity(ACTION_DOMAINS),
        OPT_DOORBELL_MUTE: _entity(["input_boolean", "switch"]),
        # talk-back through go2rtc (WebRTC to the stream named like the doorbell camera)
        OPT_TALK: selector.BooleanSelector(),
    },
    "cameras": {
        OPT_CAMERAS: _entity("camera", multiple=True),
        OPT_GO2RTC: selector.TextSelector(),
        OPT_GO2RTC_API: selector.TextSelector(),
    },
    "security": {
        OPT_ALARMS: _entity("alarm_control_panel", multiple=True),
        OPT_OPENINGS: _entity("binary_sensor", multiple=True, device_class=["door", "window", "garage_door", "opening"]),
        OPT_COVERS: _entity("cover", multiple=True),
    },
    "heating": {
        OPT_HEATING_SWITCH: _entity(["switch", "input_boolean", "climate"]),
        OPT_HEATING_TIMER: _entity("timer"),
        OPT_HEATING_ADD_HOUR: _entity(ACTION_DOMAINS),
        OPT_HEATING_STOP: _entity(ACTION_DOMAINS),
        OPT_HEATING_SCHEDULES: _entity(["input_boolean", "switch", "automation"], multiple=True),
    },
    "rooms": {
        OPT_ROOMS: _entity("sensor", multiple=True, device_class="temperature"),
    },
    "gestures": {
        key: selector.SelectSelector(selector.SelectSelectorConfig(options=PAGES, translation_key="page"))
        for key in OPT_GESTURES
    },
    "appearance": {
        OPT_WALLPAPER: selector.SelectSelector(
            selector.SelectSelectorConfig(options=["daytime", "dark", "image"], translation_key="wallpaper")
        ),
        OPT_WALLPAPER_IMAGE: selector.TextSelector(),
        OPT_SCREENSAVER: selector.NumberSelector(
            selector.NumberSelectorConfig(min=0, max=3600, step=10, unit_of_measurement="s", mode=selector.NumberSelectorMode.BOX)
        ),
        OPT_NIGHT_FROM: selector.NumberSelector(selector.NumberSelectorConfig(min=0, max=23, mode=selector.NumberSelectorMode.BOX)),
        OPT_NIGHT_TO: selector.NumberSelector(selector.NumberSelectorConfig(min=0, max=23, mode=selector.NumberSelectorMode.BOX)),
    },
}


class WallglassConfigFlow(ConfigFlow, domain=DOMAIN):
    """Panels are added when the app registers; the user only confirms."""

    VERSION = 1

    def __init__(self) -> None:
        self._info: dict[str, Any] = {}

    async def async_step_user(self, user_input: dict[str, Any] | None = None) -> ConfigFlowResult:
        """Adding the integration creates the hub entry; panels then register from the app."""
        if any(e.data.get("hub") for e in self._async_current_entries(include_ignore=False)):
            return self.async_abort(reason="use_app")
        if user_input is not None:
            return self.async_create_entry(title="Wallglass", data={"hub": True})
        return self.async_show_form(step_id="user")

    @classmethod
    @callback
    def async_supports_options_flow(cls, config_entry: ConfigEntry) -> bool:
        return not config_entry.data.get("hub")

    async def async_step_integration_discovery(self, discovery_info: dict[str, Any]) -> ConfigFlowResult:
        await self.async_set_unique_id(discovery_info["device_id"])
        self._abort_if_unique_id_configured()
        self._info = discovery_info
        self.context["title_placeholders"] = {"name": discovery_info["name"]}
        return await self.async_step_confirm()

    async def async_step_confirm(self, user_input: dict[str, Any] | None = None) -> ConfigFlowResult:
        if user_input is not None:
            return self.async_create_entry(title=self._info["name"], data=self._info)
        return self.async_show_form(
            step_id="confirm",
            description_placeholders={"name": self._info["name"], "model": self._info.get("model") or "?"},
        )

    @staticmethod
    @callback
    def async_get_options_flow(config_entry: ConfigEntry) -> OptionsFlow:
        return WallglassOptionsFlow()


class WallglassOptionsFlow(OptionsFlow):
    """Menu of sections; each section edits part of the options, 'save' stores them."""

    def __init__(self) -> None:
        self._options: dict[str, Any] | None = None

    @property
    def options(self) -> dict[str, Any]:
        if self._options is None:
            self._options = {k: v for k, v in self.config_entry.options.items() if k not in LEGACY_OPTIONS}
        return self._options

    async def async_step_init(self, user_input: dict[str, Any] | None = None) -> ConfigFlowResult:
        return self.async_show_menu(step_id="init", menu_options=[*STEP_FIELDS.keys(), "save"])

    async def async_step_save(self, user_input: dict[str, Any] | None = None) -> ConfigFlowResult:
        return self.async_create_entry(data=self.options)

    async def _section(self, step: str, user_input: dict[str, Any] | None) -> ConfigFlowResult:
        fields = STEP_FIELDS[step]
        if user_input is not None:
            updated = {k: v for k, v in self.options.items() if k not in fields}
            updated.update({k: v for k, v in user_input.items() if v not in (None, "", [], False)})
            self._options = updated
            return await self.async_step_init()
        schema = probatio.Schema({_opt(key, self.options): sel for key, sel in fields.items()})
        return self.async_show_form(step_id=step, data_schema=schema)

    async def async_step_general(self, user_input=None) -> ConfigFlowResult:
        return await self._section("general", user_input)

    async def async_step_doorbell(self, user_input=None) -> ConfigFlowResult:
        return await self._section("doorbell", user_input)

    async def async_step_cameras(self, user_input=None) -> ConfigFlowResult:
        return await self._section("cameras", user_input)

    async def async_step_security(self, user_input=None) -> ConfigFlowResult:
        return await self._section("security", user_input)

    async def async_step_heating(self, user_input=None) -> ConfigFlowResult:
        return await self._section("heating", user_input)

    async def async_step_rooms(self, user_input=None) -> ConfigFlowResult:
        return await self._section("rooms", user_input)

    async def async_step_gestures(self, user_input=None) -> ConfigFlowResult:
        return await self._section("gestures", user_input)

    async def async_step_appearance(self, user_input=None) -> ConfigFlowResult:
        return await self._section("appearance", user_input)
