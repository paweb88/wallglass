# Automations

## Entities created for each panel

Each confirmed panel becomes a device in HA with these entities:

| Entity | Type | What it does |
|---|---|---|
| Connection | `binary_sensor` (connectivity, diagnostic) | `on` while the app is connected |
| Screen | `light` | On/off and brightness of the panel's screen. Turning it on wakes the panel from the screensaver |
| Page | `select` | The page currently shown. Selecting an option switches the panel to that page |
| Quiet mode | `switch` | While on, the panel makes no sound of its own (no spoken assistant answers). Expose it to Assist to say “mute the panel” |

Entity ids follow the device name, e.g. `light.kitchen_panel_screen`, `select.kitchen_panel_page`.

## Examples

**Show the gate when someone rings.** The panel already does this if a *ring sensor* is configured.
This example is for other triggers:

```yaml
automation:
  - alias: Panel – show gate on motion at the gate
    triggers:
      - trigger: state
        entity_id: binary_sensor.gate_motion
        to: "on"
    actions:
      - action: select.select_option
        target: { entity_id: select.kitchen_panel_page }
        data: { option: gate }
```

**Turn the screen off when everybody leaves:**

```yaml
      - action: light.turn_off
        target: { entity_id: light.kitchen_panel_screen }
```

**Dim the screen in the evening:**

```yaml
      - action: light.turn_on
        target: { entity_id: light.kitchen_panel_screen }
        data: { brightness_pct: 30 }
```

## Notifications

The panel shows Home Assistant **persistent notifications** whose `notification_id` starts with `wallglass`.
You can change the prefix with `notification_prefix`, and `*` shows all of them.
The notification appears as a card over the current screen and wakes the panel, except at night.

- **Done** dismisses the notification in HA, so it disappears from every panel.
- **Remind in 1 h** hides it on this panel for an hour.

```yaml
automation:
  - alias: Waste reminder on the panel
    triggers:
      - trigger: time
        at: "18:00:00"
    conditions:
      - condition: numeric_state
        entity_id: sensor.waste_next
        attribute: daysTo
        below: 2
    actions:
      - action: persistent_notification.create
        data:
          notification_id: wallglass_waste
          title: Waste collection tomorrow
          message: "{{ states('sensor.waste_next') }} – put the bins out"
```

## Reminders

Reminders are to-do items with a due **time** on the panel's to-do list. When that time comes, the panel shows
a card (and wakes up, except at night). **Done** completes the item; **Remind in 1 h** moves its due time an hour
later, so every panel sees the change. Items with only a date stay ordinary tasks.

The built-in Assist intent for lists cannot set a due time. To set reminders by voice
(“Hej Dom, remind me tomorrow at 6 pm to pay the nursery”), add this script and expose it to Assist
(*Settings → Voice assistants → Expose*):

```yaml
script:
  add_reminder:
    alias: Add reminder
    description: >-
      Sets a reminder for a specific date and time. Use it when someone asks to be reminded
      of something at a given time. Without a time, add a plain item to the list instead.
    mode: parallel
    fields:
      text:
        description: What to remind about, short.
        required: true
        selector: { text: {} }
      when:
        description: Date and time as YYYY-MM-DD HH:MM, counted from the current date and time.
        required: true
        selector: { datetime: {} }
    sequence:
      - action: todo.add_item
        target: { entity_id: todo.home }
        data: { item: "{{ text }}", due_datetime: "{{ when }}" }
```

The list must support due times (the *Local To-do* integration does).

## Doorbell

When the configured **ring sensor** turns `on`, the panel:
- wakes up (at night only to `awake_brightness`)
- opens the gate page with the live stream
- shows **Open** (two taps), **Listen**, **Microphone** (when talk-back is configured) and a mute toggle

If the **mute** entity is on, a ring does not wake the panel from the screensaver.
Talking back goes through go2rtc (WebRTC); see [configuration](configuration.md#talking-to-the-door-station).

## WebSocket API (for developers)

The integration registers three commands. Each one is only accepted from the HA user that registered the panel.

| Command | Direction | Purpose |
|---|---|---|
| `wallglass/register` | panel → HA | Announce the panel (`device_id`, name, model, version). Starts discovery |
| `wallglass/subscribe` | panel → HA | Live channel: `{"type":"config"}` now and after option changes; `{"type":"command","command":"screen"/"page",…}` |
| `wallglass/state` | panel → HA | Report screen on/off, brightness and the current page |
