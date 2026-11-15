package app.wallglass.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Panel configuration. Loaded from `<HA>/local/wallglass.json` when present;
 * every missing part is filled in by [Discovery] from the entities in Home Assistant.
 */
@Serializable
data class PanelConfig(
    val weather: String? = null,
    val doorbell: DoorbellConfig? = null,
    val cameras: List<CameraConfig>? = null,
    val alarms: List<NamedEntity>? = null,
    val openings: List<NamedEntity>? = null,
    val covers: List<NamedEntity>? = null,
    val heating: HeatingConfig? = null,
    val rooms: List<RoomConfig>? = null,
    val todo: String? = null,
    @SerialName("media_players") val mediaPlayers: List<String>? = null,
    /** Status icons next to the weather, shown only while active. Auto-detected when null. */
    val chips: List<ChipConfig>? = null,
    /** Sensor with the next waste collection (Waste Collection Schedule, attribute daysTo). */
    val waste: String? = null,
    /** Show HA persistent notifications whose id starts with this prefix; "*" shows all. */
    @SerialName("notification_prefix") val notificationPrefix: String = "wallglass",
    val exclude: List<String> = emptyList(),
    val wallpaper: WallpaperConfig = WallpaperConfig(),
    @SerialName("screensaver_seconds") val screensaverSeconds: Int = 60,
    val night: NightConfig = NightConfig(),
    val gestures: GestureConfig = GestureConfig(),
)

/** Page opened by a swipe on the start screen (page names as in the HA page select). */
@Serializable
data class GestureConfig(
    val up: String? = "assistant",
    val down: String? = "security",
    val left: String? = "cameras",
    val right: String? = "rooms",
)

@Serializable
data class ChipConfig(
    val entity: String,
    /** flame, shield, bell_off, garage, door, window, unlock, blinds, camera, home */
    val icon: String? = null,
    val color: String? = null,
)

@Serializable
data class NamedEntity(
    val entity: String,
    val name: String? = null,
)

@Serializable
data class CameraConfig(
    val entity: String,
    val name: String? = null,
    /** Optional low-latency stream (e.g. go2rtc RTSP); otherwise HA's HLS stream is used. */
    val rtsp: String? = null,
)

@Serializable
data class DoorbellConfig(
    val camera: String,
    val rtsp: String? = null,
    @SerialName("ring_sensor") val ringSensor: String? = null,
    val open: ServiceCall? = null,
    val mute: String? = null,
    /** Talking back through the panel's microphone; null hides the microphone button. */
    val talk: TalkConfig? = null,
)

/** Two-way audio through go2rtc (WebRTC); the stream needs a backchannel source, e.g. Hikvision `isapi://`. */
@Serializable
data class TalkConfig(
    val type: String = "go2rtc",
    /** go2rtc API, e.g. `http://192.168.1.10:1984`. */
    val url: String,
    /** go2rtc stream name. */
    val stream: String,
)

@Serializable
data class ServiceCall(
    val service: String,
    @SerialName("entity_id") val entityId: String,
)

@Serializable
data class HeatingConfig(
    val switch: String,
    val timer: String? = null,
    @SerialName("add_hour") val addHour: ServiceCall? = null,
    val stop: ServiceCall? = null,
    val schedules: List<ScheduleConfig> = emptyList(),
)

@Serializable
data class ScheduleConfig(
    val name: String,
    val entity: String,
    val time: String? = null,
    val duration: String? = null,
)

@Serializable
data class RoomConfig(
    val name: String,
    val temperature: String,
    val humidity: String? = null,
)

@Serializable
data class WallpaperConfig(
    /** "daytime" (gradient by time of day), "color" or "image". */
    val mode: String = "daytime",
    val color: String? = null,
    val image: String? = null,
)

@Serializable
data class NightConfig(
    val from: Int = 21,
    val to: Int = 6,
    /** Screen brightness of the clock screensaver at night. */
    val brightness: Float = 0.02f,
    /** Brightness while in use at night (touch, doorbell) – never full brightness. */
    @SerialName("awake_brightness") val awakeBrightness: Float = 0.15f,
)
