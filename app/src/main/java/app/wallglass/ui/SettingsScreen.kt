package app.wallglass.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.wallglass.config.PanelSettings

data class SettingsActions(
    val toggleModule: (String) -> Unit,
    val setWallpaper: (String?) -> Unit,
    val setScreensaver: (Int?) -> Unit,
    val logout: () -> Unit,
    val toggleWakeWord: () -> Unit = {},
    val toggleQuiet: () -> Unit = {},
    val setDayBrightness: (Float?) -> Unit = {},
    val setNightBrightness: (Float?) -> Unit = {},
    val setTheme: (String) -> Unit = {},
)

private val DAY_OPTIONS = listOf(0.25f to "25%", 0.5f to "50%", 0.75f to "75%", null to "100%")
private val NIGHT_OPTIONS = listOf(0.03f to "3%", 0.08f to "8%", null to "15%", 0.3f to "30%")

private val SAVER_OPTIONS = listOf(30 to "30 s", 60 to "1 min", 120 to "2 min", 300 to "5 min", 0 to "Nigdy")

@Composable
fun SettingsScreen(
    modules: List<TileModel>,
    settings: PanelSettings,
    hasImage: Boolean,
    serverUrl: String?,
    actions: SettingsActions,
    onBack: () -> Unit,
) {
    SubPage("Ustawienia panelu", onBack) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Section("Moduły na ekranie startowym") }
            items(modules.size) { i ->
                val m = modules[i]
                val visible = m.page.name !in settings.hiddenModules
                Row(
                    Modifier.fillMaxWidth().height(52.dp).glass(14.dp).tap { actions.toggleModule(m.page.name) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PanelIcon(m.icon, 22.dp)
                    Column(Modifier.weight(1f)) {
                        Txt(m.label, 15, FontWeight.Medium)
                        Txt(m.sub, 11, color = Palette.soft)
                    }
                    Toggle(visible)
                }
            }
            item { Section("Wygląd") }
            item {
                Chips(
                    options = PanelThemes.all.map { it.id to it.label },
                    selected = settings.theme,
                    onPick = actions.setTheme,
                )
            }
            if (PanelThemes.byId(settings.theme).light) {
                item { Txt("W nocy jasne motywy przechodzą na Czerń.", 11, color = Palette.soft, modifier = Modifier.padding(start = 4.dp)) }
            }
            if (PanelThemes.byId(settings.theme).background == null) item { Section("Tapeta") }
            if (PanelThemes.byId(settings.theme).background == null) item {
                Chips(
                    options = buildList {
                        add(null to "Jak w HA")
                        add("daytime" to "Pora dnia")
                        add("dark" to "Ciemna")
                        if (hasImage) add("image" to "Zdjęcie z HA")
                    },
                    selected = settings.wallpaper,
                    onPick = actions.setWallpaper,
                )
            }
            item { Section("Wygaszacz z zegarem po") }
            item {
                Chips(
                    options = SAVER_OPTIONS.map { (s, l) -> s as Int? to l },
                    selected = settings.screensaverSeconds ?: 60,
                    onPick = actions.setScreensaver,
                )
            }
            item { Section("Jasność w dzień") }
            item { Chips(DAY_OPTIONS, settings.dayBrightness, actions.setDayBrightness) }
            item { Section("Jasność w nocy (po dotknięciu)") }
            item { Chips(NIGHT_OPTIONS, settings.nightBrightness, actions.setNightBrightness) }
            item { Section("Dźwięk") }
            item {
                SwitchRow(PanelIcons.speakerOff, "Tryb cichy", "panel nic nie mówi; też: „Hej Dom, wycisz panel”", settings.quiet, actions.toggleQuiet)
            }
            if (app.wallglass.BuildConfig.WAKE_WORD) {
                item { Section("Asystent") }
                item {
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).glass(14.dp).tap(actions.toggleWakeWord).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PanelIcon(PanelIcons.mic, 22.dp)
                        Column(Modifier.weight(1f)) {
                            Txt("Słowo „Hej Dom”", 15, FontWeight.Medium)
                            Txt("nasłuch na panelu, do HA idzie dopiero pytanie", 11, color = Palette.soft)
                        }
                        Toggle(settings.wakeWord)
                    }
                }
            }
            item { Section("Połączenie") }
            item { LogoutRow(serverUrl, actions.logout) }
        }
    }
}

@Composable
private fun SwitchRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, on: Boolean, onTap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).glass(14.dp).tap(onTap).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PanelIcon(icon, 22.dp)
        Column(Modifier.weight(1f)) {
            Txt(title, 15, FontWeight.Medium)
            Txt(sub, 11, color = Palette.soft)
        }
        Toggle(on)
    }
}

@Composable
private fun Section(title: String) {
    Txt(title, 13, color = Palette.soft, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
}

@Composable
private fun <T> Chips(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                Modifier.height(40.dp).glass(20.dp, if (active) Palette.selected else Palette.glass)
                    .tap { onPick(value) }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Txt(label, 13, FontWeight.Medium, if (active) Palette.onSelected else Palette.white) }
        }
    }
}

@Composable
private fun Toggle(on: Boolean) {
    Box(Modifier.size(46.dp, 26.dp).glass(13.dp, if (on) Color(0xFF3EC46D) else Palette.track, Color.Transparent)) {
        Box(Modifier.padding(start = if (on) 23.dp else 3.dp, top = 3.dp).size(20.dp).glass(10.dp, Color.White, Color.Transparent))
    }
}

@Composable
private fun LogoutRow(serverUrl: String?, onLogout: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(56.dp).glass(14.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt("Home Assistant", 15, FontWeight.Medium)
            Txt(serverUrl ?: "–", 11, color = Palette.soft, maxLines = 1)
        }
        Box(
            Modifier.height(40.dp).glass(12.dp, if (armed) Palette.danger else Palette.glass)
                .tap { if (armed) onLogout() else armed = true }.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Txt(if (armed) "Na pewno?" else "Wyloguj", 13, FontWeight.Medium) }
    }
}
