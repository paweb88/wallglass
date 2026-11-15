# Configuration

The panel builds its configuration in layers:

1. **Base config.** This is either the **integration options** (recommended; set them on the panel's device in HA under
   *Configure*, and they apply live) or, when the panel is not linked to the integration, **`www/wallglass.json`**
   (your HA config folder, served at `/local/wallglass.json`). When the integration is linked, `wallglass.json` is ignored.
2. **Auto-discovery.** Every section left empty in the base config is filled in from your entities.
3. **Local settings on the panel.** Long-press the clock (or open *Settings* from the page select). They apply on top, but only to that one panel:
   hidden tiles, a wallpaper override, the screensaver delay, brightness by day and at night, and quiet mode.

## Integration options

Open *Settings → Devices & services → Wallglass → (your panel) → Configure*. The menu has these sections.
Click **Save** at the end.

| Section | Option | Entity types | Empty means |
|---|---|---|---|
| General | Weather | `weather` | first weather entity |
| | To-do list | `todo` | first list other than `todo.shopping_list` |
| | Waste collection sensor | `sensor` | first sensor with a `daysTo` attribute |
| | Status icons | any (several) | auto: heating on, armed alarms, muted doorbell, open garage doors |
| | Media players | `media_player` (several) | all available players |
| Doorbell / gate | Doorbell camera | `camera` | no gate screen |
| | Ring sensor | `binary_sensor`, `input_boolean` (rings while `on`) | no auto-open on ring |
| | Open gate | `script`, `button`, `input_button`, `switch`, `lock`, `cover`, `scene`, … | no “Open” button |
| | Doorbell mute | `input_boolean`, `switch` | no mute pill |
| | Talk through go2rtc | on/off | no microphone button |
| Cameras | Cameras on the panel | `camera` (several) | all available cameras, except the doorbell camera |
| | go2rtc RTSP address | text, e.g. `rtsp://192.168.1.10:8554` | HA HLS stream |
| | go2rtc API address | text, e.g. `http://192.168.1.10:1984` | RTSP host, port 1984 |
| Security and covers | Alarm zones | `alarm_control_panel` (several) | all |
| | Windows and doors | `binary_sensor`, door/window/garage_door/opening (several) | all of those classes |
| | Covers | `cover` (several) | all |
| Heating | Heating switch | `switch`, `input_boolean`, `climate` | no heating tile |
| | Timer | `timer` | no countdown |
| | “+1 hour” and “Off” actions | script, button, … | buttons hidden |
| | Schedules | `input_boolean`, `switch`, `automation` (several) | none |
| Rooms | Temperature sensors | temperature `sensor` (several) | all temperature sensors (except CPU/battery and similar) |
| Gestures | Up / Down / Left / Right | page name | `assistant` / `security` / `cameras` / `rooms` |
| Look and night | Wallpaper | `daytime`, `dark`, `image` | `daytime` |
| | Wallpaper photo | path, e.g. `/local/wallpaper.jpg` | – |
| | Screensaver after | seconds, `0` = never | 60 |
| | Night from / until hour | 0–23 | 21 → 6 |

How some options are interpreted:
- **Room names** come from the sensor's HA *area*, so name your areas. **Humidity** is taken from a humidity
  sensor on the same device.
- **Open gate / “+1 hour” / “Off”** call the matching service for the entity's domain:
  `script.turn_on`, `button.press`, `lock.open`, `cover.open_cover` and so on.
- **go2rtc.** Each camera gets `<address>/<object_id>`. For `camera.garden` that is `rtsp://…:8554/garden`,
  so name the go2rtc streams after the camera's object id. RTSP has much lower latency than HLS.
  If a stream fails, the panel falls back to HLS.
- **Status icons** show only while the entity is active: `on`, `open`, `home`, `unlocked`, armed alarms, a
  running `climate`. The icon comes from the entity's `icon` attribute or its device class.

## `wallglass.json`

The same settings as a file, for setups without the integration. Save it as `<config>/www/wallglass.json`.
Every key is optional; a key that is missing is auto-discovered.

> Files in `www/` are served **without authentication**. Don't put anything secret in this file,
> such as RTSP URLs with passwords. Use the integration for those.

```json
{
  "weather": "weather.home",
  "doorbell": {
    "camera": "camera.gate",
    "rtsp": "rtsp://192.168.1.10:8554/gate",
    "ring_sensor": "binary_sensor.doorbell_ring",
    "open": { "service": "script.turn_on", "entity_id": "script.open_gate" },
    "mute": "input_boolean.doorbell_mute",
    "talk": { "url": "http://192.168.1.10:1984", "stream": "gate" }
  },
  "cameras": [
    { "entity": "camera.garden", "name": "Garden", "rtsp": "rtsp://192.168.1.10:8554/garden" },
    { "entity": "camera.driveway" }
  ],
  "alarms": [{ "entity": "alarm_control_panel.ground_floor", "name": "Ground floor" }],
  "openings": [{ "entity": "binary_sensor.front_door" }, { "entity": "binary_sensor.garage_door" }],
  "covers": [{ "entity": "cover.living_room" }],
  "heating": {
    "switch": "switch.boiler",
    "timer": "timer.boiler",
    "add_hour": { "service": "script.turn_on", "entity_id": "script.boiler_plus_hour" },
    "stop": { "service": "script.turn_on", "entity_id": "script.boiler_off" },
    "schedules": [{ "name": "Morning", "entity": "input_boolean.boiler_morning", "time": "05:00", "duration": "90 min" }]
  },
  "rooms": [{ "name": "Living room", "temperature": "sensor.living_t", "humidity": "sensor.living_h" }],
  "todo": "todo.home",
  "media_players": ["media_player.spotify"],
  "waste": "sensor.waste_next",
  "chips": [
    { "entity": "switch.boiler", "icon": "flame", "color": "#FF8A50" },
    { "entity": "input_boolean.doorbell_mute", "icon": "bell_off", "color": "#FFC83A" }
  ],
  "notification_prefix": "wallglass",
  "exclude": ["camera.test"],
  "wallpaper": { "mode": "daytime" },
  "screensaver_seconds": 60,
  "night": { "from": 21, "to": 6, "brightness": 0.02, "awake_brightness": 0.15 },
  "gestures": { "up": "assistant", "down": "security", "left": "cameras", "right": "rooms" }
}
```

### Key reference

| Key | Type | Default | Description |
|---|---|---|---|
| `weather` | entity id | auto | Weather on the start screen (5-day forecast via `weather/subscribe_forecast`) |
| `doorbell` | object | – | `camera` (required), `rtsp`, `ring_sensor`, `open` (service call), `mute`, `talk` (`url` of the go2rtc API, `stream`) |
| `cameras` | list | auto | `entity`, optional `name`, optional `rtsp` |
| `alarms`, `openings`, `covers` | list | auto | `entity`, optional `name` |
| `heating` | object | – | `switch` (required), `timer`, `add_hour`, `stop`, `schedules[]` (`name`, `entity`, `time`, `duration`) |
| `rooms` | list | auto | `name`, `temperature`, optional `humidity` |
| `todo` | entity id | auto | To-do list |
| `media_players` | list of ids | auto | Players on the music screen |
| `waste` | entity id | auto | Waste Collection Schedule sensor (see below) |
| `chips` | list | auto | `entity`, `icon`, `color` (hex) |
| `notification_prefix` | string | `wallglass` | Show only persistent notifications whose id starts with this. `*` shows all |
| `exclude` | list of ids | `[]` | Entities that auto-discovery skips |
| `wallpaper` | object | `daytime` | `mode`: `daytime` (sky gradient by hour), `dark`, `color` (+ `color`), `image` (+ `image`, a path under HA such as `/local/x.jpg`) |
| `screensaver_seconds` | int | 60 | Idle time before the clock screensaver, `0` = never |
| `night` | object | 21 → 6 | `from`, `to` (hours); `brightness` = screensaver brightness at night (0–1); `awake_brightness` = brightness when touched or ringing at night |
| `gestures` | object | see above | Page for each swipe on the start screen. `null` turns a direction off |

**Status icon names:** `flame`, `shield`, `bell_off`, `garage`, `door`, `window`, `unlock`, `blinds`, `camera`,
`trash`, `home`. Matching `mdi:` names also work, e.g. `mdi:fire`, `mdi:shield-home`, `mdi:garage-open`, `mdi:cctv`.

**Page names** (gestures, the *Page* select): `start`, `gate`, `cameras`, `security`, `covers`, `heating`,
`rooms`, `todo`, `waste`, `assistant`, `music`, `settings`.

## Talking to the door station

The **Microphone** button on the gate page talks to the door station **through go2rtc over WebRTC**.
The panel sends its microphone to go2rtc, and go2rtc passes it to the station's two-way audio channel
(the *backchannel*). The visitor's voice comes back over the same connection. WebRTC adds echo cancellation,
noise suppression and automatic gain.

1. In go2rtc, give the doorbell stream a source with a backchannel and name the stream after the doorbell camera's
   object id (`camera.gate` → `gate`). For a Hikvision door station:
   ```yaml
   streams:
     gate:
       - rtsp://user:pass@192.168.1.20:554/Streaming/channels/101
       - isapi://user:pass@192.168.1.20:80/
   ```
   Other backchannels go2rtc supports (ONVIF/RTSP backchannel, Tapo, …) work the same way.
2. In the integration options, under *Doorbell / gate*, switch on **Talk through go2rtc**.
3. The panel finds go2rtc's API at the host of the **go2rtc RTSP address** on port `1984`. If yours is elsewhere,
   set **go2rtc API address** under *Cameras*.

What to know:
- The panel needs to reach go2rtc's API (port 1984) and its WebRTC port (8555 by default) on your LAN.
- Door station passwords stay in go2rtc. The panel never sees them.
- A call ends when you tap the button again, leave the page, the connection drops, or after 3 minutes.
- While you talk, the video stays muted and you hear the visitor through WebRTC. The wake word is paused.

## Waste collection

Install [Waste Collection Schedule](https://github.com/mampfes/hacs_waste_collection_schedule) with your
source, then add a sensor like this one (in `configuration.yaml` under `sensor:`):

```yaml
sensor:
  - platform: waste_collection_schedule
    name: Waste collection
    types: [Mixed, Bio, Plastic and metal, Paper, Glass]   # optional filter, after customize/alias
    add_days_to: true                                     # adds daysTo – the panel finds the sensor by it
    value_template: "{{ value.types | join(', ') }}"
```

With the default details format, the sensor's attributes list the upcoming collections as
`YYYY-MM-DD: "Fraction, Fraction"`. The panel builds its calendar from those attributes.
Use `customize` → `alias` in the source config to give fractions short names.
The panel colours fractions by name: plastic/metal yellow, paper blue, glass green, bio brown,
mixed grey, and recognises both Polish and English names.

## Night mode and screensaver

- After `screensaver_seconds` without a touch, the panel shows a large clock with the outside and inside temperature.
- Between `night.from` and `night.to` the screensaver is dimmed to `night.brightness`. A touch, the doorbell or a
  notification wakes the panel only to `awake_brightness`, never to full brightness.
- At night the assistant answers in text only (no TTS sound).
- A muted doorbell (the `mute` entity is on) does not wake the panel from the screensaver. It still opens the gate page if the panel is in use.

## Gestures

Swipe on the start screen to open pages: by default up = assistant, down = security,
left = cameras, right = rooms. On any sub-page, swiping right goes back.
