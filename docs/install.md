# Installation

You need three things:
1. The **app** on the panel.
2. The **`wallglass` integration** in Home Assistant. It is optional but recommended, because it is how you configure the panel.
3. A **Home Assistant user** for the panel to log in as.

## 1. Home Assistant user

Create a dedicated user (*Settings → People → Add person → Allow login*) and leave it as a non-admin.
The panel needs no admin rights: all of its WebSocket commands work for normal users.

## 2. The integration

**HACS (recommended)**
1. HACS → ⋮ → *Custom repositories* → add `https://github.com/paweb88/wallglass` with type **Integration**.
2. Install **Wallglass**, then restart Home Assistant.

**Manual**
Copy `custom_components/wallglass` to `<config>/custom_components/wallglass` and restart Home Assistant.

Then go to *Settings → Devices & services → Add integration → **Wallglass*** to create the hub entry.
Panels add themselves later (step 4).

## 3. The app

Download the APKs from the [releases page](https://github.com/paweb88/wallglass/releases/latest). Check them against `SHA256SUMS` attached to the release.
They support `arm64-v8a` and `armeabi-v7a` on Android 8.0 or newer.

| File | What |
|---|---|
| `wallglass-0.2.0.apk` | Full: with the on-device “Hej Dom” wake word (~63 MB) |
| `wallglass-0.2.0-lite.apk` | Lite: without the wake word, smaller (~29 MB). The assistant still works from the mic button |

Both are signed with the same key, so you can switch later by installing the other over it.

### Sonoff NSPanel Pro

The NSPanel Pro runs Android 8.1, but its launcher is locked down. You reach it over ADB:

1. Install the **eWeLink** phone app and pair the panel (scan the QR code shown on the panel).
2. In eWeLink, open the panel → **Settings**. Scroll to **Device ID** and tap it 5–10 times until the
   developer-mode warning appears. Accept it.
   > Sonoff states that enabling developer mode/ADB may affect your warranty.
3. Turn on **ADB** in the new developer section. Note the panel's IP address (you can see it in eWeLink or on your router).
4. From a computer with [Android platform-tools](https://developer.android.com/tools/releases/platform-tools):

```bash
adb connect 192.168.1.50:5555          # your panel's IP
adb install -r wallglass-0.2.0.apk          # or wallglass-0.2.0-lite.apk
adb shell cmd package set-home-activity app.wallglass/.MainActivity
adb shell pm grant app.wallglass android.permission.RECORD_AUDIO   # assistant and talking to the gate
adb shell am start -n app.wallglass/.MainActivity
```

Wallglass is now the home screen: it starts on boot and the Home gesture returns to it. After that you no longer need eWeLink.

Optional extras:
- **Keep ADB over Wi-Fi after a reboot.** This needs root, which the NSPanel Pro has: `adb shell su 0 setprop persist.adb.tcp.port 5555`.
- **Fix the time zone** if the clock is off. Stock panels often ship with `Asia/Shanghai`:
  `adb shell su 0 setprop persist.sys.timezone Europe/Warsaw` (use your own zone), then reboot.

### Other Android tablets

Install the APK the normal way: enable *Install unknown apps* and open the file, or use `adb install`.
When Android asks which home app to use, choose **Wallglass → Always**.
The layout targets square 480×480 screens, but it scales to other sizes.

## 4. First start

1. Enter your Home Assistant URL, e.g. `http://homeassistant.local:8123` or `http://192.168.1.10:8123`.
2. The normal HA login page opens. Log in as the panel user.
3. The panel registers itself with the integration. In HA, *Settings → Devices & services* shows it under
   **Discovered**. Click **Add**.
4. Open the panel's **Configure** menu to choose what it shows. See [configuration.md](configuration.md).

The panel works before step 3 as well: it then uses auto-discovery and, optionally, `www/wallglass.json`.

## Local settings on the panel

**Long-press the clock** on the start screen to open the panel's own settings:
- show or hide tiles
- wallpaper override
- screensaver delay
- “Hej Dom” on or off
- the server address
- log out

## Updating

- **App:** `adb install -r wallglass-<version>.apk`. Your login and settings are kept.
  Moving from a self-built **debug** APK to the signed release APK needs `adb uninstall app.wallglass` first,
  because the signatures differ. You will have to log in again after that.
- **Integration:** update it through HACS (or replace the folder), then restart Home Assistant.

## Uninstalling

```bash
adb uninstall app.wallglass
```
Then remove the panel device and the Wallglass integration in Home Assistant.
