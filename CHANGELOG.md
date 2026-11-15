# Changelog

## 0.2.0 – 2026-11-15

- Two APKs: full (with the “Hej Dom” wake word) and lite (without it, about half the size).
- `tools/piper-pl`: a Wyoming proxy that makes Piper read Polish dates, times and temperatures with correct declension.
- Step-by-step guide for training your own wake word.

- Talk to the door station from the gate page through go2rtc over WebRTC (any go2rtc backchannel, e.g. Hikvision `isapi://`), with echo cancellation. Switch it on with *Talk through go2rtc* in the integration options.

- Reminders: a to-do item with a due **time** shows as a card on the panel when it is due. *Done* completes the item, *Remind in 1 h* moves its due time. The to-do list shows the time with a bell.
- Quiet mode: a speaker button on the start screen, a switch in the panel settings and a *Quiet mode* switch entity in HA (works by voice when exposed to Assist). While on, the assistant answers in text only.
- Panel settings: screen brightness by day and at night.
- By day the panel uses full brightness (or the setting) instead of the system brightness.
- The wake word is ignored in a quiet room, which removes most false wakes.
- New start screen: bold clock, a Today card (closures, waste, next task), one-line forecast and tiles that show each module's state.
- Themes in the panel settings: Sky, Graphite, Light, Sand, Forest, Black. Light themes switch to Black at night.

## 0.1.0 – first public release

- Native launcher for Android wall panels (Android 8.0+, tested on the Sonoff NSPanel Pro).
- Home Assistant login over OAuth with an encrypted token store. The panel stays logged in through outages.
- Screens:
  - start
  - gate/doorbell
  - cameras (go2rtc RTSP or HLS)
  - security
  - covers
  - heating
  - rooms with 24 h charts
  - to-do
  - waste collection calendar
  - music
  - assistant
  - notifications
  - screensaver
  - settings
- `wallglass` integration: panel discovery, options flow, live config push, and the Connection, Screen and Page entities.
- Auto-discovery of entities and the optional `www/wallglass.json`.
- Night mode with limited brightness. The doorbell and notifications wake the panel.
- Voice assistant through an Assist pipeline, with the on-device “Hej Dom” wake word.
- Configurable swipe gestures.
