package app.wallglass.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.wallglass.Page
import app.wallglass.config.Discovery
import app.wallglass.config.ResolvedPanel
import app.wallglass.ha.Entity
import app.wallglass.ha.ForecastDay
import app.wallglass.ha.TodoDue
import app.wallglass.ha.TodoItem
import app.wallglass.ha.dueLabel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

private val PL = Locale("pl", "PL")

data class TileModel(
    val label: String,
    val sub: String,
    val icon: ImageVector,
    val page: Page,
    val active: Boolean = false,
    /** Good news worth a green tile, e.g. everything closed. */
    val ok: Boolean = false,
)

/**
 * Start screen: clock and weather, a "Today" card (closures, waste, next task), a one-line forecast and
 * tiles that show each module's state. Rows and tiles only appear for what is configured.
 */
@Composable
fun StartScreen(
    now: LocalDateTime,
    panel: ResolvedPanel,
    states: Map<String, Entity>,
    forecast: List<ForecastDay>,
    hidden: Set<String>,
    todo: List<TodoItem> = emptyList(),
    quiet: Boolean = false,
    onToggleQuiet: () -> Unit = {},
    onOpen: (Page) -> Unit,
) {
    val night = now.hour >= 17 || now.hour < 7
    val today = todayRows(panel, states, todo, now)
    // the Today card already gives the to-do list a way in; the assistant has the mic button
    val tiles = allTiles(panel, states)
        .filter { it.page.name !in hidden }
        .filterNot { it.page == Page.ASSISTANT || (it.page == Page.TODO && today.any { r -> r.page == Page.TODO }) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            // long-press on the clock opens the panel settings
            Column(Modifier.weight(1f).pointerInput(Unit) { detectTapGestures(onLongPress = { onOpen(Page.SETTINGS) }) }) {
                Txt("%02d:%02d".format(now.hour, now.minute), size = 80, weight = FontWeight.SemiBold, maxLines = 1)
                Txt(dateLabel(now.toLocalDate()), size = 14, color = Palette.soft, modifier = Modifier.padding(start = 3.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                panel.weather?.let { states[it] }?.let { w -> CurrentWeather(w, night) }
                StatusChips(panel, states, quiet, onToggleQuiet) { onOpen(Page.ASSISTANT) }
            }
        }
        if (today.isNotEmpty()) TodayCard(today, onOpen)
        if (forecast.isNotEmpty()) ForecastStrip(forecast)
        TileGrid(tiles, onOpen, Modifier.weight(1f))
    }
}

@Composable
private fun CurrentWeather(w: Entity, night: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val cond = if (w.state == "sunny" && night) "clear-night" else w.state
        WeatherIcon(cond, night, Modifier.size(54.dp))
        Txt("${formatTemp(w.attrDouble("temperature"), 0)}°", size = 38, weight = FontWeight.Light)
    }
    Txt(conditionLabel(w.state), size = 11, color = Palette.soft)
}

@Composable
private fun StatusChips(panel: ResolvedPanel, states: Map<String, Entity>, quiet: Boolean, onToggleQuiet: () -> Unit, onAssistant: () -> Unit) {
    val active = panel.chips.filter { c -> states[c.entity]?.let { Discovery.isActive(it) } == true }
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        active.take(4).forEach { c ->
            val tint = c.color?.let { parseColor(it) } ?: Palette.white
            RoundChip(chipIcon(c.icon), tint, on = true)
        }
        // quiet mode: amber while the panel is silent
        RoundChip(if (quiet) PanelIcons.speakerOff else PanelIcons.speaker, Palette.amber, on = quiet, onTap = onToggleQuiet)
        RoundChip(PanelIcons.mic, Palette.white, on = false, onTap = onAssistant)
    }
}

@Composable
private fun RoundChip(icon: ImageVector, tint: Color, on: Boolean, onTap: (() -> Unit)? = null) {
    val base = Modifier.size(32.dp).glass(16.dp, tint.copy(alpha = if (on) 0.26f else 0.10f), tint.copy(alpha = if (on) 0.7f else 0.3f))
    Box(if (onTap != null) base.tap(onTap) else base, contentAlignment = Alignment.Center) {
        PanelIcon(icon, 17.dp, if (on) tint else Palette.white)
    }
}

private fun chipIcon(name: String?) = when (name?.removePrefix("mdi:")?.replace('-', '_')) {
    "flame", "fire", "radiator" -> PanelIcons.flame
    "shield", "shield_home", "shield_lock" -> PanelIcons.shield
    "bell_off" -> PanelIcons.bellOff
    "garage", "garage_open" -> PanelIcons.garage
    "door", "door_open" -> PanelIcons.door
    "window", "window_open" -> PanelIcons.window
    "unlock", "lock_open" -> PanelIcons.unlock
    "blinds", "blinds_open" -> PanelIcons.blinds
    "camera", "cctv" -> PanelIcons.camera
    "trash", "trash_can" -> PanelIcons.trash
    else -> PanelIcons.home
}

/** One line of the Today card. */
data class TodayRow(val icon: ImageVector, val tint: Color, val text: String, val detail: String, val page: Page)

private fun todayRows(panel: ResolvedPanel, states: Map<String, Entity>, todo: List<TodoItem>, now: LocalDateTime): List<TodayRow> = buildList {
    if (panel.openings.isNotEmpty() || panel.alarms.isNotEmpty()) {
        val open = panel.openings.mapNotNull { states[it.entity] }.filter { it.isOn }
        val armed = panel.alarms.mapNotNull { states[it.entity] }.count { it.state.startsWith("armed") }
        val covers = panel.covers.count { states[it.entity]?.state == "open" }
        val detail = listOfNotNull(
            if (armed > 0) "alarm: $armed" else null,
            if (covers > 0) "rolety: $covers otw." else null,
        ).joinToString(" · ")
        add(
            if (open.isEmpty()) TodayRow(PanelIcons.lock, Palette.okText, "Wszystko zamknięte", detail, Page.SECURITY)
            else TodayRow(PanelIcons.unlock, Palette.amber, "Otwarte: ${open.size}", open.first().name, Page.SECURITY),
        )
    }
    panel.waste?.let { states[it] }?.takeIf { it.isAvailable }?.let { w ->
        val days = w.attrDouble("daysTo")?.toInt()
        add(TodayRow(PanelIcons.trash, if (days != null && days <= 1) Palette.amber else Palette.wasteTint, w.state, daysLabel(days), Page.WASTE))
    }
    if (panel.todo != null) {
        val next = todo.filterNot { it.done }
            .sortedBy { TodoDue.parse(it.due)?.let { d -> d.at ?: d.date.atTime(23, 59) } ?: LocalDateTime.MAX }
            .firstOrNull()
        val more = todo.count { !it.done } - 1
        add(
            if (next == null) TodayRow(PanelIcons.check, Palette.soft, "Brak zadań", "", Page.TODO)
            else TodayRow(
                if (TodoDue.parse(next.due)?.time != null) PanelIcons.bell else PanelIcons.check, Palette.amber,
                next.summary,
                listOfNotNull(dueLabel(TodoDue.parse(next.due), now), if (more > 0) "+$more" else null).joinToString(" · "),
                Page.TODO,
            ),
        )
    }
}

@Composable
private fun TodayCard(rows: List<TodayRow>, onOpen: (Page) -> Unit) {
    Column(Modifier.fillMaxWidth().glass(18.dp).padding(vertical = 4.dp)) {
        rows.forEach { r ->
            Row(
                Modifier.fillMaxWidth().height(30.dp).tap { onOpen(r.page) }.padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PanelIcon(r.icon, 18.dp, r.tint)
                Txt(r.text, 14, maxLines = 1, modifier = Modifier.weight(1f))
                if (r.detail.isNotEmpty()) Txt(r.detail, 12, color = Palette.soft, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ForecastStrip(days: List<ForecastDay>) {
    Row(Modifier.fillMaxWidth().height(38.dp).glass(16.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        days.take(5).forEach { d ->
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Txt(dayShort(d.datetime), size = 11, color = Palette.soft)
                WeatherIcon(d.condition, false, Modifier.size(24.dp))
                Txt("${formatTemp(d.tempHigh, 0)}°", size = 12, weight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun TileGrid(tiles: List<TileModel>, onOpen: (Page) -> Unit, modifier: Modifier) {
    if (tiles.isEmpty()) return
    val perRow = if (tiles.size <= 3) tiles.size else 4
    val rows = tiles.chunked(perRow)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.Bottom)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 54.dp, max = 86.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { t -> StateTile(t, Modifier.weight(1f).fillMaxHeight()) { onOpen(t.page) } }
                repeat(perRow - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StateTile(t: TileModel, modifier: Modifier, onTap: () -> Unit) {
    val fill = when {
        t.active -> Palette.heat
        t.ok -> Palette.ok
        else -> Palette.glass
    }
    val border = if (t.ok && !t.active) Palette.okBorder else Palette.glassBorder
    Column(
        modifier.glass(16.dp, fill, border).tap(onTap).padding(horizontal = 9.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // the name has to be readable from across the room, not only the state below it
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelIcon(t.icon, 20.dp, if (t.ok) Palette.okText else Palette.iconTint)
            Txt(t.label, 15, FontWeight.Medium, Palette.white.copy(alpha = 0.85f), maxLines = 1)
        }
        Txt(t.sub.replaceFirstChar { it.uppercase() }, 15, FontWeight.SemiBold, if (t.ok) Palette.okText else Palette.white, maxLines = 1)
    }
}

fun allTiles(panel: ResolvedPanel, states: Map<String, Entity>): List<TileModel> = buildList {
    panel.doorbell?.let { add(TileModel("Furtka", "podgląd", PanelIcons.doorbell, Page.GATE)) }
    if (panel.cameras.isNotEmpty()) add(TileModel("Kamery", "${panel.cameras.size} ${plural(panel.cameras.size, "kamera", "kamery", "kamer")}", PanelIcons.camera, Page.CAMERAS))
    if (panel.alarms.isNotEmpty() || panel.openings.isNotEmpty()) {
        val open = panel.openings.count { states[it.entity]?.isOn == true }
        add(TileModel("Ochrona", if (open == 0) "zamknięte" else "otwarte: $open", PanelIcons.shield, Page.SECURITY, ok = open == 0))
    }
    if (panel.covers.isNotEmpty()) {
        val open = panel.covers.count { states[it.entity]?.state == "open" }
        add(TileModel("Rolety", if (open == 0) "zamknięte" else "otwarte: $open", PanelIcons.blinds, Page.COVERS))
    }
    panel.heating?.let { h ->
        val on = states[h.switch]?.isOn == true
        add(TileModel("Piec", if (on) timerLeft(states[h.timer ?: ""]) ?: "grzeje" else "wyłączony", PanelIcons.flame, Page.HEATING, on))
    }
    panel.todo?.let { id ->
        val open = states[id]?.state?.toIntOrNull()
        add(TileModel("Zadania", if (open == null || open == 0) "brak" else "do zrobienia: $open", PanelIcons.check, Page.TODO))
    }
    if (panel.mediaPlayers.isNotEmpty()) {
        val playing = panel.mediaPlayers.mapNotNull { states[it] }.firstOrNull { it.state == "playing" }
        add(TileModel("Muzyka", playing?.attrString("media_title") ?: "nic nie gra", PanelIcons.music, Page.MUSIC, playing != null))
    }
    add(TileModel("Asystent", "zapytaj", PanelIcons.mic, Page.ASSISTANT))
    if (panel.rooms.isNotEmpty()) {
        val temps = panel.rooms.mapNotNull { states[it.temperature]?.numericState }
        val avg = if (temps.isEmpty()) null else temps.average()
        add(TileModel("Dom", "${formatTemp(avg)}°", PanelIcons.home, Page.ROOMS))
    }
}

private fun plural(n: Int, one: String, few: String, many: String) = when {
    n == 1 -> one
    n % 10 in 2..4 && n % 100 !in 12..14 -> few
    else -> many
}

fun daysLabel(days: Int?): String = when (days) {
    null -> "–"
    0 -> "dziś"
    1 -> "jutro"
    else -> "za $days dni"
}

fun timerLeft(timer: Entity?): String? {
    if (timer?.state != "active") return null
    val finishes = timer.attrString("finishes_at") ?: return null
    val end = runCatching { java.time.OffsetDateTime.parse(finishes).toInstant() }.getOrNull() ?: return null
    val min = ((end.toEpochMilli() - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
    return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
}

private fun dateLabel(d: LocalDate): String =
    "${d.dayOfWeek.getDisplayName(TextStyle.FULL, PL)}, ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL, PL)}"

private fun dayShort(iso: String): String = runCatching {
    java.time.OffsetDateTime.parse(iso).dayOfWeek.getDisplayName(TextStyle.SHORT, PL).trimEnd('.')
}.getOrDefault("")

fun conditionLabel(state: String): String = when (state) {
    "clear-night" -> "Bezchmurnie"
    "sunny" -> "Słonecznie"
    "partlycloudy" -> "Częściowe zachmurzenie"
    "cloudy" -> "Pochmurno"
    "rainy" -> "Deszcz"
    "pouring" -> "Ulewa"
    "snowy" -> "Śnieg"
    "snowy-rainy" -> "Deszcz ze śniegiem"
    "fog" -> "Mgła"
    "hail" -> "Grad"
    "lightning" -> "Burza"
    "lightning-rainy" -> "Burza z deszczem"
    "windy", "windy-variant" -> "Wietrznie"
    else -> state
}
