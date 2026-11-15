package app.wallglass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.wallglass.ha.Entity
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val PL = Locale("pl", "PL")
private val DATE_KEY = Regex("""\d{4}-\d{2}-\d{2}""")

/** One collection day: date and fractions ("Bio", "Plastik i metal" …). */
data class WasteDay(val date: LocalDate, val fractions: List<String>)

/**
 * Upcoming collections from a Waste Collection Schedule sensor, whose attributes are
 * "YYYY-MM-DD": "Fraction, Fraction".
 */
fun wasteDays(sensor: Entity?): List<WasteDay> = sensor?.attributes.orEmpty()
    .filterKeys { DATE_KEY.matches(it) }
    .mapNotNull { (key, value) ->
        val text = (value as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        runCatching { WasteDay(LocalDate.parse(key), text.split(',').map { it.trim() }.filter { it.isNotEmpty() }) }.getOrNull()
    }
    .sortedBy { it.date }

@Composable
fun WasteScreen(days: List<WasteDay>, today: LocalDate, onBack: () -> Unit) {
    val upcoming = days.filter { !it.date.isBefore(today) }
    SubPage("Wywóz odpadów", onBack) {
        if (upcoming.isEmpty()) {
            Txt("Brak terminów – sprawdź czujnik wywozu w HA", 15, color = Palette.soft)
            return@SubPage
        }
        NextCard(upcoming.first(), today)
        TwoWeeks(upcoming.associateBy { it.date }, today)
        Txt("Kolejne terminy", 13, color = Palette.soft, modifier = Modifier.padding(start = 4.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(upcoming.drop(1).flatMap { d -> d.fractions.map { d.date to it } }) { (date, fraction) ->
                Row(
                    Modifier.fillMaxWidth().height(40.dp).glass(12.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Dot(WasteColors.colorFor(fraction), 14)
                    Txt(fraction, 15, FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
                    Txt(shortDate(date), 13, color = Palette.soft)
                    Txt(relative(date, today), 12, color = Palette.soft, modifier = Modifier.width(66.dp))
                }
            }
        }
    }
}

@Composable
private fun NextCard(next: WasteDay, today: LocalDate) {
    val color = WasteColors.colorFor(next.fractions.firstOrNull().orEmpty())
    Row(
        Modifier.fillMaxWidth().glass(18.dp, Palette.glassStrong).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(color).border(2.dp, Color.White, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) { PanelIcon(PanelIcons.trash, 24.dp) }
        Column {
            Txt("NAJBLIŻSZY · ${relative(next.date, today).uppercase(PL)}", 12, color = Palette.soft)
            Txt(
                "${next.fractions.joinToString(", ")} · ${next.date.dayOfWeek.getDisplayName(TextStyle.FULL, PL)} ${shortDate(next.date, withDay = false)}",
                17, FontWeight.SemiBold, maxLines = 2,
            )
        }
    }
}

/** 14 days from today, each cell with dots in the colours of that day's fractions. */
@Composable
private fun TwoWeeks(byDate: Map<LocalDate, WasteDay>, today: LocalDate) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        (0 until 14).chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                week.forEach { offset ->
                    val date = today.plusDays(offset.toLong())
                    val fractions = byDate[date]?.fractions.orEmpty()
                    Column(
                        Modifier.weight(1f).height(56.dp)
                            .glass(12.dp, if (fractions.isEmpty()) Color(0x12FFFFFF) else Palette.glassStrong,
                                if (offset == 0) Color.White else Palette.glassBorder),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Txt(if (offset == 0) "dziś" else date.dayOfWeek.getDisplayName(TextStyle.SHORT, PL).trimEnd('.'), 10, color = Palette.soft)
                        Txt("${date.dayOfMonth}", 15, FontWeight.SemiBold)
                        Row(Modifier.height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            fractions.forEach { Dot(WasteColors.colorFor(it), 9) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dot(color: Color, sizeDp: Int) {
    Box(Modifier.size(sizeDp.dp).clip(CircleShape).background(color).border(1.dp, Color.White, CircleShape))
}

private fun shortDate(d: LocalDate, withDay: Boolean = true): String {
    val dm = d.format(DateTimeFormatter.ofPattern("d.MM"))
    return if (withDay) "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, PL).trimEnd('.')} $dm" else dm
}

private fun relative(d: LocalDate, today: LocalDate): String =
    daysLabel(ChronoUnit.DAYS.between(today, d).toInt())

@Suppress("unused")
private val unusedAlign = TextAlign.End
