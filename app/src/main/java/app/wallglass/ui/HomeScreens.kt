package app.wallglass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.wallglass.config.HeatingConfig
import app.wallglass.config.RoomConfig
import app.wallglass.ha.Entity

@Composable
fun HeatingScreen(vm: PanelActions, heating: HeatingConfig, states: Map<String, Entity>, onBack: () -> Unit) {
    val on = states[heating.switch]?.isOn == true
    val left = timerLeft(states[heating.timer ?: ""])
    SubPage("Piec", onBack) {
        Row(
            Modifier.fillMaxWidth().glass(22.dp).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                Modifier.size(84.dp).glass(42.dp, if (on) Color(0xFFF4511E) else Palette.glass),
                contentAlignment = Alignment.Center,
            ) { PanelIcon(PanelIcons.flame, 44.dp) }
            Column {
                Txt(if (on) "Grzeje" else "Wyłączony", 30, FontWeight.Light)
                Txt(if (on) (left?.let { "zostało $it" } ?: "bez timera") else "dotknij +1 godzina, aby włączyć", 14, color = Palette.soft)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            heating.addHour?.let { GlassButton("+1 godzina", Modifier.weight(1f)) { vm.run(it) } }
            GlassButton("Wyłącz", Modifier.weight(1f), fill = Color(0xBFD6453B)) {
                heating.stop?.let { vm.run(it) } ?: vm.service("switch", "turn_off", heating.switch)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            heating.schedules.forEach { s ->
                val active = states[s.entity]?.isOn == true
                Row(
                    Modifier.weight(1f).height(64.dp).glass(18.dp).tap { vm.toggle(s.entity) }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Txt(s.name, 16)
                        listOfNotNull(s.time, s.duration).joinToString(" · ").takeIf { it.isNotEmpty() }?.let { Txt(it, 12, color = Palette.soft) }
                    }
                    Switch(active)
                }
            }
        }
    }
}

@Composable
private fun Switch(on: Boolean) {
    Box(Modifier.size(46.dp, 26.dp).glass(13.dp, if (on) Palette.switchOn else Palette.track, Color.Transparent)) {
        Box(Modifier.padding(start = if (on) 23.dp else 3.dp, top = 3.dp).size(20.dp).glass(10.dp, Color.White, Color.Transparent))
    }
}

@Composable
fun RoomsScreen(rooms: List<RoomConfig>, states: Map<String, Entity>, history: Map<String, List<Float>>, onBack: () -> Unit) {
    val temps = rooms.mapNotNull { states[it.temperature]?.numericState }
    SubPage("Dom", onBack, trailing = {
        if (temps.isNotEmpty()) Txt("średnio ${formatTemp(temps.average())}°", 13, color = Palette.soft)
    }) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(rooms, key = { it.temperature }) { r -> RoomCard(r, states, history[r.temperature].orEmpty()) }
        }
    }
}

private fun tempColor(t: Double): Color = when {
    t < 16 -> Color(0xFF5AA9FF)
    t < 20 -> Color(0xFF6FD3C9)
    t < 22 -> Color(0xFF7EE08F)
    t < 24 -> Color(0xFFFFB347)
    else -> Color(0xFFFF6B5A)
}

@Composable
private fun RoomCard(room: RoomConfig, states: Map<String, Entity>, series: List<Float>) {
    val t = states[room.temperature]?.numericState
    val h = room.humidity?.let { states[it]?.numericState }
    val color = t?.let { tempColor(it) } ?: Palette.soft
    val fraction = (((t ?: 10.0) - 10.0) / 16.0).coerceIn(0.0, 1.0).toFloat()
    Column(Modifier.height(168.dp).glass(20.dp).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(room.name, 14, FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
            h?.let {
                PanelIcon(PanelIcons.drop, 12.dp, Color(0xFF9ED0FF))
                Txt(" ${it.toInt()}%", 12, color = Palette.soft)
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
            val track = Palette.track
            Canvas(Modifier.size(120.dp, 64.dp)) {
                val stroke = 9.dp.toPx()
                val arcSize = Size(size.width - stroke * 2, (size.width - stroke * 2))
                val topLeft = Offset(stroke, stroke)
                drawArc(track, 180f, 180f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(color, 180f, 180f * fraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Txt("${formatTemp(t)}°", 28, FontWeight.Light)
        }
        Sparkline(series, color, Modifier.fillMaxWidth().height(34.dp).padding(top = 6.dp))
    }
}

/** Min–max scaled line of the last 24 h with min/max labels. */
@Composable
private fun Sparkline(series: List<Float>, color: Color, modifier: Modifier) {
    if (series.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) { Txt("brak historii", 10, color = Palette.soft) }
        return
    }
    val lo = series.min()
    val hi = series.max()
    val range = (hi - lo).coerceAtLeast(0.4f)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            val path = androidx.compose.ui.graphics.Path()
            series.forEachIndexed { i, v ->
                val x = size.width * i / (series.size - 1)
                val y = size.height - (v - lo) / range * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(Modifier.padding(start = 4.dp), horizontalAlignment = Alignment.End) {
            Txt(formatTemp(hi.toDouble()), 9, color = Palette.soft)
            Txt(formatTemp(lo.toDouble()), 9, color = Palette.soft)
        }
    }
}
