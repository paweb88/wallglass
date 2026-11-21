"""Turns the options chosen in HA into the JSON config the panel app understands."""

from __future__ import annotations

import re
from urllib.parse import urlsplit
from typing import Any

from homeassistant.core import HomeAssistant
from homeassistant.helpers import area_registry as ar
from homeassistant.helpers import device_registry as dr
from homeassistant.helpers import entity_registry as er

from .const import (
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
    OPT_TODO,
    OPT_WALLPAPER,
    OPT_WALLPAPER_IMAGE,
    OPT_WASTE,
    OPT_WEATHER,
)

CONFIG_SCHEMA_VERSION = 1

_ACTION_SERVICES = {
    "script": "script.turn_on",
    "button": "button.press",
    "input_button": "input_button.press",
    "switch": "switch.turn_on",
    "input_boolean": "input_boolean.turn_on",
    "lock": "lock.open",
    "cover": "cover.open_cover",
    "scene": "scene.turn_on",
}

_CHIP_ICONS = {
    "garage_door": "garage",
    "door": "door",
    "window": "window",
    "opening": "window",
    "lock": "unlock",
}


def action_for(entity_id: str | None) -> dict[str, str] | None:
    """Service call that 'activates' an entity (script, button, switch …)."""
    if not entity_id:
        return None
    domain = entity_id.split(".", 1)[0]
    return {"service": _ACTION_SERVICES.get(domain, f"{domain}.turn_on"), "entity_id": entity_id}


def _area_name(hass: HomeAssistant, entity_id: str) -> str | None:
    ent = er.async_get(hass).async_get(entity_id)
    if ent is None:
        return None
    area_id = ent.area_id
    if area_id is None and ent.device_id:
        device = dr.async_get(hass).async_get(ent.device_id)
        area_id = device.area_id if device else None
    area = ar.async_get(hass).async_get_area(area_id) if area_id else None
    return area.name if area else None


def _humidity_for(hass: HomeAssistant, temperature_id: str) -> str | None:
    """A humidity sensor on the same device as the temperature sensor.

    The registry is checked as well as the state, so the sensor is found while HA is still
    starting and the integration that owns it (MQTT, Zigbee…) has not restored its state yet.
    """
    registry = er.async_get(hass)
    ent = registry.async_get(temperature_id)
    if ent is None or ent.device_id is None:
        return None
    for other in er.async_entries_for_device(registry, ent.device_id):
        if other.domain != "sensor":
            continue
        state = hass.states.get(other.entity_id)
        state_class = state.attributes.get("device_class") if state else None
        if "humidity" in (state_class, other.device_class, other.original_device_class):
            return other.entity_id
    return None


def entity_name(hass: HomeAssistant, entity_id: str) -> str:
    """Friendly name, also before the entity has a state (HA still starting)."""
    if state := hass.states.get(entity_id):
        return state.name
    ent = er.async_get(hass).async_get(entity_id)
    if ent is None:
        return entity_id
    name = ent.name or ent.original_name
    if ent.has_entity_name and ent.device_id:
        device = dr.async_get(hass).async_get(ent.device_id)
        device_name = (device.name_by_user or device.name) if device else None
        name = " ".join(part for part in (device_name, name) if part)
    return name or entity_id


_NAME_NOISE = re.compile(r"(?i)\b(temperature|temperatura|termometr|humidity|wilgotność)\b")


def _clean(name: str) -> str:
    cleaned = re.sub(r"\s+", " ", _NAME_NOISE.sub("", name)).strip()
    return cleaned or name


def _room(hass: HomeAssistant, temperature_id: str) -> dict[str, Any]:
    name = _area_name(hass, temperature_id) or _clean(entity_name(hass, temperature_id))
    room: dict[str, Any] = {"name": name, "temperature": temperature_id}
    if humidity := _humidity_for(hass, temperature_id):
        room["humidity"] = humidity
    return room


def _chip(hass: HomeAssistant, entity_id: str) -> dict[str, Any]:
    state = hass.states.get(entity_id)
    domain = entity_id.split(".", 1)[0]
    icon = None
    if state is not None:
        icon = state.attributes.get("icon") or _CHIP_ICONS.get(state.attributes.get("device_class", ""))
    if icon is None and domain == "alarm_control_panel":
        icon = "shield"
    return {"entity": entity_id, "icon": icon} if icon else {"entity": entity_id}


def _entities(options: dict[str, Any], key: str) -> list[dict[str, str]] | None:
    """Explicit list or None, so the app keeps auto-discovering what was left empty."""
    values = options.get(key) or []
    return [{"entity": e} for e in values] if values else None


def go2rtc_api(options: dict[str, Any]) -> str | None:
    """go2rtc API address: the explicit option, else the RTSP host on go2rtc's default API port 1984."""
    if api := (options.get(OPT_GO2RTC_API) or "").strip().rstrip("/"):
        return api
    rtsp = urlsplit((options.get(OPT_GO2RTC) or "").strip())
    return f"http://{rtsp.hostname}:1984" if rtsp.scheme == "rtsp" and rtsp.hostname else None


def build_panel_config(hass: HomeAssistant, options: dict[str, Any]) -> dict[str, Any]:
    """Config pushed to the panel; keys left out fall back to auto-discovery in the app."""
    cfg: dict[str, Any] = {"schema": CONFIG_SCHEMA_VERSION}

    for key, out in ((OPT_WEATHER, "weather"), (OPT_TODO, "todo"), (OPT_WASTE, "waste")):
        if options.get(key):
            cfg[out] = options[key]

    if camera := options.get(OPT_DOORBELL_CAMERA):
        doorbell: dict[str, Any] = {"camera": camera}
        if options.get(OPT_DOORBELL_RING):
            doorbell["ring_sensor"] = options[OPT_DOORBELL_RING]
        if open_action := action_for(options.get(OPT_DOORBELL_OPEN)):
            doorbell["open"] = open_action
        if options.get(OPT_DOORBELL_MUTE):
            doorbell["mute"] = options[OPT_DOORBELL_MUTE]
        if options.get(OPT_TALK) and (api := go2rtc_api(options)):
            doorbell["talk"] = {"type": "go2rtc", "url": api, "stream": camera.split(".", 1)[1]}
        cfg["doorbell"] = doorbell

    go2rtc = (options.get(OPT_GO2RTC) or "").rstrip("/")
    if cameras := options.get(OPT_CAMERAS):
        # go2rtc streams named like the camera's object id get a low-latency RTSP url
        cfg["cameras"] = [
            {"entity": e, **({"rtsp": f"{go2rtc}/{e.split('.', 1)[1]}"} if go2rtc else {})} for e in cameras
        ]
    if go2rtc and "doorbell" in cfg:
        cfg["doorbell"]["rtsp"] = f"{go2rtc}/{cfg['doorbell']['camera'].split('.', 1)[1]}"

    for key in (OPT_ALARMS, OPT_OPENINGS, OPT_COVERS):
        if (items := _entities(options, key)) is not None:
            cfg[key] = items

    if switch := options.get(OPT_HEATING_SWITCH):
        heating: dict[str, Any] = {"switch": switch}
        if options.get(OPT_HEATING_TIMER):
            heating["timer"] = options[OPT_HEATING_TIMER]
        if add := action_for(options.get(OPT_HEATING_ADD_HOUR)):
            heating["add_hour"] = add
        if stop := action_for(options.get(OPT_HEATING_STOP)):
            heating["stop"] = stop
        heating["schedules"] = [
            {"name": entity_name(hass, e), "entity": e}
            for e in options.get(OPT_HEATING_SCHEDULES) or []
        ]
        cfg["heating"] = heating

    if media := options.get(OPT_MEDIA):
        cfg["media_players"] = list(media)
    if rooms := options.get(OPT_ROOMS):
        cfg["rooms"] = [_room(hass, e) for e in rooms]
    if chips := options.get(OPT_CHIPS):
        cfg["chips"] = [_chip(hass, e) for e in chips]

    wallpaper = options.get(OPT_WALLPAPER)
    if wallpaper:
        cfg["wallpaper"] = {"mode": wallpaper}
        if wallpaper == "image" and options.get(OPT_WALLPAPER_IMAGE):
            cfg["wallpaper"]["image"] = options[OPT_WALLPAPER_IMAGE]
    if options.get(OPT_SCREENSAVER) is not None:
        cfg["screensaver_seconds"] = int(options[OPT_SCREENSAVER])
    if options.get(OPT_NIGHT_FROM) is not None or options.get(OPT_NIGHT_TO) is not None:
        cfg["night"] = {
            "from": int(options.get(OPT_NIGHT_FROM, 21)),
            "to": int(options.get(OPT_NIGHT_TO, 6)),
        }
    gestures = {k.removeprefix("gesture_"): options[k] for k in OPT_GESTURES if options.get(k)}
    if gestures:
        cfg["gestures"] = gestures
    return cfg
