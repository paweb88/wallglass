# Development

## Requirements

- JDK 17
- Android SDK, platform 35. Put its path in `local.properties` as `sdk.dir=…`
- Python 3.12, only if you want to train the wake word

## Build

```bash
./gradlew assembleFullDebug            # app/build/outputs/apk/full/debug/app-full-debug.apk
./gradlew assembleLiteDebug            # without the wake word
./gradlew testFullDebugUnitTest        # unit and snapshot tests
./gradlew recordPaparazziFullDebug     # re-render screenshots into app/src/test/snapshots
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
```

### Release build

The release build is signed with the key described in a `keystore.properties` file.
That file is never committed. Its path comes from `$WALLGLASS_KEYSTORE_PROPERTIES`, or the file can sit in the project root:

```properties
storeFile=/absolute/path/release.keystore
storePassword=…
keyAlias=wallglass
keyPassword=…
```

```bash
WALLGLASS_KEYSTORE_PROPERTIES=~/.config/wallglass/keystore.properties ./gradlew assembleFullRelease assembleLiteRelease
mkdir -p dist
cp app/build/outputs/apk/full/release/app-full-release.apk dist/wallglass-<version>.apk
cp app/build/outputs/apk/lite/release/app-lite-release.apk dist/wallglass-<version>-lite.apk
(cd dist && shasum -a 256 wallglass-*.apk > SHA256SUMS)
```

Without the properties file the release build falls back to the debug key.
The official APKs are signed with this certificate:
SHA-256 `17:ff:58:aa:e7:11:a0:60:24:1e:62:5f:a5:8e:f4:71:e7:44:b9:04:67:d7:54:38:33:8c:ba:22:ee:dc:91:c8`.

## Layout

```
app/src/main/java/app/wallglass/
  MainActivity.kt          root composable: pages, brightness, night, doorbell, notifications, gestures
  PanelViewModel.kt        state, HA session, history, voice, wake word
  auth/                    OAuth with HA (client_id https://paweb88.github.io/wallglass/), encrypted token store
  ha/                      WebSocket client, entities, history API, panel link to the integration, Assist
  config/                  PanelConfig (JSON schema), auto-discovery, local settings
  ui/                      one file per screen + shared components, icons, themes (PanelTheme.kt)
  talk/                    talking to the door station: go2rtc WebRTC signalling and session
  voice/                   microphone input, openWakeWord pipeline, wake-word listener
app/src/full/               wake-word detector (ONNX Runtime) and models: assets/wakeword/
app/src/lite/               stub detector for the build without a wake word
app/src/test/              unit tests and Paparazzi screenshots (480×480)
custom_components/wallglass/ Home Assistant integration (HACS layout)
tools/wakeword/            wake-word training scripts
tools/piper-pl/            Wyoming proxy that makes Piper read Polish numbers and dates correctly
```

## How the pieces talk

1. The app logs in through `/auth/authorize` in a WebView and exchanges the code at `/auth/token`.
   Refresh tokens are renewed automatically. Network errors and HA restarts don't log the panel out;
   only a 400, 401 or 403 from `/auth/token` does.
2. The WebSocket client runs `get_states`, subscribes to `state_changed`, persistent notifications and the
   weather forecast, and reconnects automatically.
3. `PanelLink` registers with the integration (`wallglass/register`) and subscribes to config and commands.
   It reports screen and page state back.
4. Config from the integration overrides `wallglass.json`. Missing parts are filled in by `Discovery`.

## Contributing

Pull requests are welcome. The most wanted are:
- English and other UI translations (strings are still inline in Polish)
- two-way doorbell audio
- an `assist_satellite` entity so HA can make announcements through the panel
- more screen sizes

Please run `./gradlew testFullDebugUnitTest` before you open a PR.
If you change the UI, run `recordPaparazziFullDebug` and include the screenshots.
