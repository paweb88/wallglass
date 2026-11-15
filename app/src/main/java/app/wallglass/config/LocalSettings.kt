package app.wallglass.config

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Settings changed on the panel itself; they override wallglass.json. */
data class PanelSettings(
    val hiddenModules: Set<String> = emptySet(),
    /** null = use wallglass.json; otherwise "daytime", "dark" or "image". */
    val wallpaper: String? = null,
    /** null = use wallglass.json; 0 = never. */
    val screensaverSeconds: Int? = null,
    /** Listen for the wake word ("Hej Dom") on the panel. */
    val wakeWord: Boolean = true,
    /** Quiet mode: the panel makes no sound of its own (no spoken answers). */
    val quiet: Boolean = false,
    /** Screen brightness in use by day (0–1); null = full. */
    val dayBrightness: Float? = null,
    /** Screen brightness in use at night (0–1); null = night.awake_brightness from HA. */
    val nightBrightness: Float? = null,
    /** Colour style id (see PanelThemes). */
    val theme: String = "sky",
)

class LocalSettings(context: Context) {

    private val prefs = context.getSharedPreferences("wallglass_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())

    /** Stable random id of this panel, used to register it in the Wallglass integration. */
    val deviceId: String = prefs.getString(KEY_DEVICE, null) ?: java.util.UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY_DEVICE, it).commit()
    }
    val settings: StateFlow<PanelSettings> = _settings.asStateFlow()

    private fun read() = PanelSettings(
        hiddenModules = prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet(),
        wallpaper = prefs.getString(KEY_WALLPAPER, null),
        screensaverSeconds = if (prefs.contains(KEY_SAVER)) prefs.getInt(KEY_SAVER, 60) else null,
        wakeWord = prefs.getBoolean(KEY_WAKE, true),
        quiet = prefs.getBoolean(KEY_QUIET, false),
        dayBrightness = if (prefs.contains(KEY_DAY)) prefs.getFloat(KEY_DAY, 1f) else null,
        nightBrightness = if (prefs.contains(KEY_NIGHT)) prefs.getFloat(KEY_NIGHT, 0.15f) else null,
        theme = prefs.getString(KEY_THEME, "sky") ?: "sky",
    )

    fun update(transform: (PanelSettings) -> PanelSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putStringSet(KEY_HIDDEN, next.hiddenModules)
            .apply { next.wallpaper?.let { putString(KEY_WALLPAPER, it) } ?: remove(KEY_WALLPAPER) }
            .apply { next.screensaverSeconds?.let { putInt(KEY_SAVER, it) } ?: remove(KEY_SAVER) }
            .putBoolean(KEY_WAKE, next.wakeWord)
            .putBoolean(KEY_QUIET, next.quiet)
            .putString(KEY_THEME, next.theme)
            .apply { next.dayBrightness?.let { putFloat(KEY_DAY, it) } ?: remove(KEY_DAY) }
            .apply { next.nightBrightness?.let { putFloat(KEY_NIGHT, it) } ?: remove(KEY_NIGHT) }
            .apply()
        _settings.value = next
    }

    private companion object {
        const val KEY_HIDDEN = "hidden_modules"
        const val KEY_WALLPAPER = "wallpaper"
        const val KEY_SAVER = "screensaver_seconds"
        const val KEY_DEVICE = "device_id"
        const val KEY_WAKE = "wake_word"
        const val KEY_QUIET = "quiet"
        const val KEY_THEME = "theme"
        const val KEY_DAY = "day_brightness"
        const val KEY_NIGHT = "night_brightness"
    }
}

/** Applies panel-side overrides on top of the HA-provided config. */
fun PanelConfig.withSettings(s: PanelSettings): PanelConfig = copy(
    wallpaper = when (s.wallpaper) {
        null -> wallpaper
        "dark" -> WallpaperConfig(mode = "color", color = "#11131A")
        else -> wallpaper.copy(mode = s.wallpaper)
    },
    screensaverSeconds = s.screensaverSeconds ?: screensaverSeconds,
    night = s.nightBrightness?.let { night.copy(awakeBrightness = it) } ?: night,
)
