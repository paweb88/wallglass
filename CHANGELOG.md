# Changelog

## Unreleased

- Start screen tiles: a big icon on top with the name and state below it, readable from across the room. The
  “Dom” tile is now “Temp.” with a thermometer icon.
- Blinds: a stop button between up and down, to leave a blind half-way (shown unless the cover cannot stop).
- Music: when Spotify's session has gone stale, HA only lets it pick a device and play failed with a validation error.
  The music screen now shows “Na: Speaker” (tap to change the device) and play moves playback there, then resumes
  as soon as HA reports the new session.
- Larger waste fraction dots, centred on the waste tile.
- Waste collection is a start-screen tile: coloured dots for the fractions, the date (“jutro”, “czw, 19.11”), warm when
  the bins have to go out; tapping it opens the schedule. The Today card no longer repeats closures and waste, which
  have their own tiles, and the tiles grow to use the freed space.
- Start screen tiles: the name (Furtka, Kamery…) and its icon are larger, as readable from a distance as the state below.
- The screen dims by the room, not only by the clock: the panel's light sensor (or, until it reports, the sun below
  the horizon) puts a dark room on the night brightness, so the clock no longer shines at full brightness in the evening.
- The night brightness chosen in the panel settings is now actually used.
- Sounds are no longer taken for questions: a transcript made only of annotations such as “*śmiech*” or “[muzyka]”
  (a crying baby, the TV) counts as silence, so it ends a follow-up instead of keeping the conversation going.

## 0.2.1 – 2026-11-15

- Follow-up questions: after an answer the assistant listens again in the same conversation (the model keeps the context).
  Say “OK”, “dzięki”, “to wszystko” or similar, or stay silent for 6 s, to end it. The panel then returns to the start
  screen and goes to sleep.
- A short beep marks when the panel starts listening.
- If nothing was heard right after the wake word, the panel listens once more instead of giving up.
- The wake-word listener releases the microphone before the assistant starts recording.

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
