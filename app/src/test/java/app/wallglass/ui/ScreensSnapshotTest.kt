package app.wallglass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.wallglass.config.ChipConfig
import app.wallglass.config.Discovery
import app.wallglass.config.DoorbellConfig
import app.wallglass.config.TalkConfig
import app.wallglass.config.HeatingConfig
import app.wallglass.config.PanelConfig
import app.wallglass.config.PanelSettings
import app.wallglass.config.RoomConfig
import app.wallglass.config.ScheduleConfig
import app.wallglass.config.ServiceCall
import app.wallglass.config.WallpaperConfig
import app.wallglass.ha.Entity
import app.wallglass.ha.ForecastDay
import app.wallglass.ha.PanelNotification
import app.wallglass.ha.TodoItem
import com.android.resources.Density
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Renders every screen at 480×480 with sample data (no device needed). Run: ./gradlew recordPaparazziDebug */
class ScreensSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig(screenWidth = 480, screenHeight = 480, xdpi = 160, ydpi = 160, density = Density.MEDIUM),
        maxPercentDifference = 0.5,
    )

    private val actions = object : PanelActions {
        override val baseUrl: String? = null
        override val http = OkHttpClient()
        override suspend fun accessToken() = ""
        override suspend fun streamUrl(entityId: String): String? = null
        override fun service(domain: String, service: String, entityId: String) = Unit
        override fun serviceWith(domain: String, service: String, entityId: String, extra: JsonObject?) = Unit
        override fun run(call: ServiceCall) = Unit
        override fun toggle(entityId: String) = Unit
    }

    private fun e(id: String, state: String, vararg attrs: Pair<String, Any>) = id to Entity(
        id, state,
        JsonObject(attrs.associate { (k, v) -> k to if (v is Number) JsonPrimitive(v) else JsonPrimitive(v.toString()) }),
    )

    private val states = mapOf(
        e("weather.home", "partlycloudy", "temperature" to 5.4),
        e("alarm_control_panel.dol", "disarmed", "friendly_name" to "Dół"),
        e("alarm_control_panel.gora", "armed_home", "friendly_name" to "Góra"),
        e("binary_sensor.wejscie", "off", "device_class" to "door", "friendly_name" to "Wejście główne"),
        e("binary_sensor.kuchnia", "on", "device_class" to "window", "friendly_name" to "Kuchnia"),
        e("binary_sensor.salon", "off", "device_class" to "window", "friendly_name" to "Salon"),
        e("binary_sensor.garaz", "off", "device_class" to "garage_door", "friendly_name" to "Brama garażowa"),
        e("cover.rolety_salon", "open", "friendly_name" to "Rolety Salon", "current_position" to 100),
        e("cover.rolety_gabinet", "open", "friendly_name" to "Rolety Gabinet", "current_position" to 40),
        e("cover.rolety_sypialnia", "closed", "friendly_name" to "Rolety Sypialnia", "current_position" to 0),
        e("switch.piec", "on", "friendly_name" to "Piec"),
        e("timer.piec", "active", "finishes_at" to LocalDateTime.now().plusMinutes(42).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime().toString()),
        e("input_boolean.rano", "on"),
        e("input_boolean.wieczor", "off"),
        e("input_boolean.wycisz", "on"),
        e("sensor.salon_t", "20.8"), e("sensor.salon_h", "47"),
        e("sensor.bedroom_t", "19.6"), e("sensor.bedroom_h", "53"),
        e("sensor.kids_t", "21.5"), e("sensor.kids_h", "50"),
        e("sensor.garaz_t", "13.7"), e("sensor.garaz_h", "61"),
        e("sensor.wywoz", "Plastik i metal, Papier", "daysTo" to 1),
        e("todo.dom", "3"),
        e("camera.ogrod", "idle", "friendly_name" to "Ogród"),
        e("camera.garaz", "idle", "friendly_name" to "Garaż"),
    )

    private val config = PanelConfig(
        weather = "weather.home",
        doorbell = DoorbellConfig(camera = "camera.domofon", mute = "input_boolean.wycisz",
            open = ServiceCall("script.turn_on", "script.furtka"),
            talk = TalkConfig(url = "http://192.168.1.10:1984", stream = "domofon")),
        heating = HeatingConfig(
            switch = "switch.piec", timer = "timer.piec",
            addHour = ServiceCall("script.turn_on", "script.piec_1h"),
            schedules = listOf(ScheduleConfig("Rano", "input_boolean.rano", "05:00", "90 min"), ScheduleConfig("Wieczorem", "input_boolean.wieczor", "18:00", "60 min")),
        ),
        rooms = listOf(
            RoomConfig("Salon", "sensor.salon_t", "sensor.salon_h"),
            RoomConfig("Sypialnia", "sensor.bedroom_t", "sensor.bedroom_h"),
            RoomConfig("Pokój dziecka", "sensor.kids_t", "sensor.kids_h"),
            RoomConfig("Garaż", "sensor.garaz_t", "sensor.garaz_h"),
        ),
        todo = "todo.dom",
    )
    private val panel = Discovery.resolve(config, states)
    private val evening = LocalDateTime.of(2026, 11, 14, 19, 12)
    private val forecast = (0..4).map { i ->
        ForecastDay(LocalDate.of(2026, 11, 14 + i).atStartOfDay().atOffset(java.time.ZoneOffset.UTC).toString(),
            listOf("partlycloudy", "cloudy", "rainy", "sunny", "snowy")[i], 6.0 - i, 2.0 - i / 2.0)
    }

    private val todoItems = listOf(
        TodoItem("2", "Zapłacić za prąd", false, "2026-11-20T18:00:00+01:00", null),
        TodoItem("3", "Kupić filtr do rekuperatora", false, null, null),
    )

    private fun shot(hour: Int = 19, content: @Composable () -> Unit) = paparazzi.snapshot {
        Box(Modifier.fillMaxSize().background(wallpaperBrush(WallpaperConfig(), hour))) { content() }
    }

    @Test fun start_evening() = shot { StartScreen(evening, panel, states, forecast, emptySet(), todoItems) {} }

    @Test fun start_day() = shot(12) { StartScreen(evening.withHour(12).withMinute(40), panel, states, forecast, emptySet(), todoItems, quiet = true) {} }

    @Test fun notification() = shot {
        StartScreen(evening, panel, states, forecast, emptySet(), todoItems) {}
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x99000000))) {
            NotificationCard(PanelNotification("wallglass_smieci", "Jutro wywóz odpadów", "Plastik i metal, Papier – wystaw kubły przed bramę", null), 1, {}, {})
        }
    }

    @Test fun gate() = shot { GateScreen(actions, panel.doorbell!!, states) {} }

    @Test fun security() = shot { SecurityScreen(actions, panel.alarms, panel.openings, states) {} }

    @Test fun covers() = shot { CoversScreen(actions, panel.covers, states) {} }

    @Test fun heating() = shot { HeatingScreen(actions, panel.heating!!, states) {} }

    @Test fun rooms() = shot {
        val wave = (0 until 48).map { 20f + kotlin.math.sin(it / 6.0).toFloat() * 0.6f }
        RoomsScreen(panel.rooms, states, panel.rooms.associate { it.temperature to wave }) {}
    }

    @Test fun todo() = shot {
        TodoScreen(
            listOf(
                TodoItem("1", "Wystawić kubły – plastik i papier", false, LocalDate.of(2026, 11, 14).toString(), null),
                TodoItem("2", "Zapłacić za prąd", false, "2026-11-20T18:00:00+01:00", null),
                TodoItem("3", "Kupić filtr do rekuperatora", false, null, null),
                TodoItem("4", "Wymienić baterię w czujniku", true, null, null),
            ),
            { _, _ -> },
        ) {}
    }

    @Test fun settings() = shot {
        SettingsScreen(allTiles(panel, states), PanelSettings(hiddenModules = setOf("ROOMS")), true, "http://homeassistant.local:8123",
            SettingsActions({}, {}, {}, {})) {}
    }

    @Test fun waste() = shot {
        val today = LocalDate.of(2026, 11, 14)
        val days = listOf(19 to "Bio", 23 to "Plastik i metal", 24 to "Papier", 25 to "Szkło", 26 to "Bio", 27 to "Zmieszane")
            .map { (d, f) -> WasteDay(LocalDate.of(2026, 11, d), listOf(f)) } + WasteDay(LocalDate.of(2026, 12, 3), listOf("Bio", "Papier"))
        WasteScreen(days, today) {}
    }

    @Test fun music() = shot(19) {
        val st = states + e("media_player.spotify", "playing", "friendly_name" to "Spotify", "media_title" to "Tytuł utworu",
            "media_artist" to "Wykonawca", "media_duration" to 221, "media_position" to 84, "volume_level" to 0.55, "source" to "Salon")
        MusicScreen(actions, listOf("media_player.spotify"), st) {}
    }

    @Test fun assistant() = paparazzi.snapshot {
        AssistantScreen(
            app.wallglass.ha.VoiceState.Answer("Jaka jest temperatura w salonie?", "W salonie jest 20,8 stopnia.", null),
            null, false, {}, {},
        )
    }

    @Test fun screensaver_night() = paparazzi.snapshot { Screensaver(evening.withHour(23).withMinute(47), true, 3.0, 20.4) {} }

    @Test fun chips_config() = shot {
        val withChips = Discovery.resolve(config.copy(chips = listOf(ChipConfig("switch.piec", "fire", "#FF8A50"), ChipConfig("input_boolean.wycisz", "bell_off", "#FFC83A"))), states)
        StartScreen(evening, withChips, states, forecast, emptySet()) {}
    }

    @Test fun start_minimal() = shot(12) {
        val minimal = PanelConfig(weather = "weather.home", cameras = emptyList(), alarms = emptyList(), openings = emptyList(),
            covers = emptyList(), rooms = emptyList(), mediaPlayers = emptyList(), waste = "", todo = null)
        StartScreen(evening.withHour(12), Discovery.resolve(minimal, mapOf(e("weather.home", "sunny", "temperature" to 21.0))), mapOf(e("weather.home", "sunny", "temperature" to 21.0)), emptyList(), emptySet()) {}
    }

    @Test fun start_long_values() = shot(12) {
        val busy = states + mapOf(
            e("media_player.salon", "playing", "media_title" to "Bardzo długi tytuł utworu z remasterem"),
            e("binary_sensor.kuchnia", "on", "device_class" to "window", "friendly_name" to "Okno w kuchni od strony ogrodu"),
        )
        StartScreen(evening.withHour(12), Discovery.resolve(config.copy(mediaPlayers = listOf("media_player.salon")), busy), busy, forecast, emptySet(), todoItems) {}
    }

    // ---- themes: the start screen in every theme, every sub-page in a light and a dark one
    private fun themed(theme: PanelTheme, content: @Composable () -> Unit) {
        ThemeState.current = theme
        try {
            paparazzi.snapshot {
                Box(Modifier.fillMaxSize().background(theme.background ?: wallpaperBrush(WallpaperConfig(), 12))) { content() }
            }
        } finally {
            ThemeState.current = PanelThemes.sky
        }
    }

    @Composable
    private fun settingsScreen(s: PanelSettings) =
        SettingsScreen(allTiles(panel, states), s, true, "http://homeassistant.local:8123", SettingsActions({}, {}, {}, {})) {}

    private val noon = evening.withHour(12).withMinute(40)

    @Test fun theme_start_graphite() = themed(PanelThemes.graphite) { StartScreen(noon, panel, states, forecast, emptySet(), todoItems) {} }
    @Test fun theme_start_light() = themed(PanelThemes.light) { StartScreen(noon, panel, states, forecast, emptySet(), todoItems) {} }
    @Test fun theme_start_sand() = themed(PanelThemes.sand) { StartScreen(noon, panel, states, forecast, emptySet(), todoItems) {} }
    @Test fun theme_start_forest() = themed(PanelThemes.forest) { StartScreen(noon, panel, states, forecast, emptySet(), todoItems) {} }
    @Test fun theme_start_black() = themed(PanelThemes.black) { StartScreen(noon, panel, states, forecast, emptySet(), todoItems) {} }

    @Test fun theme_light_security() = themed(PanelThemes.light) { SecurityScreen(actions, panel.alarms, panel.openings, states) {} }
    @Test fun theme_light_covers() = themed(PanelThemes.light) { CoversScreen(actions, panel.covers, states) {} }
    @Test fun theme_light_heating() = themed(PanelThemes.light) { HeatingScreen(actions, panel.heating!!, states) {} }
    @Test fun theme_light_todo() = themed(PanelThemes.light) { TodoScreen(todoItems, { _, _ -> }) {} }
    @Test fun theme_light_rooms() = themed(PanelThemes.light) { RoomsScreen(panel.rooms, states, emptyMap()) {} }
    @Test fun theme_light_waste() = themed(PanelThemes.light) {
        WasteScreen(listOf(WasteDay(LocalDate.of(2026, 11, 19), listOf("Bio")), WasteDay(LocalDate.of(2026, 11, 23), listOf("Plastik i metal"))), LocalDate.of(2026, 11, 14)) {}
    }
    @Test fun theme_light_settings() = themed(PanelThemes.light) { settingsScreen(PanelSettings(theme = "light")) }
    @Test fun theme_graphite_security() = themed(PanelThemes.graphite) { SecurityScreen(actions, panel.alarms, panel.openings, states) {} }
    @Test fun theme_graphite_heating() = themed(PanelThemes.graphite) { HeatingScreen(actions, panel.heating!!, states) {} }
    @Test fun theme_graphite_settings() = themed(PanelThemes.graphite) { settingsScreen(PanelSettings(theme = "graphite")) }
    @Test fun theme_sand_todo() = themed(PanelThemes.sand) { TodoScreen(todoItems, { _, _ -> }) {} }
}
