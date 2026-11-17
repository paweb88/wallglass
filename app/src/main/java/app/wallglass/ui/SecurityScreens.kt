package app.wallglass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.wallglass.config.NamedEntity
import app.wallglass.ha.Entity

private val ALARM_ACTIONS = listOf(
    "alarm_disarm" to "Wył.",
    "alarm_arm_home" to "Dom",
    "alarm_arm_away" to "Wyjście",
)

@Composable
fun SecurityScreen(
    vm: PanelActions,
    alarms: List<NamedEntity>,
    openings: List<NamedEntity>,
    states: Map<String, Entity>,
    onBack: () -> Unit,
) {
    val open = openings.count { states[it.entity]?.isOn == true }
    SubPage("Ochrona", onBack, trailing = {
        Txt(if (open == 0) "wszystko zamknięte" else "otwarte: $open", 13, color = Palette.soft)
    }) {
        if (alarms.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                alarms.take(2).forEach { a -> AlarmCard(vm, a, states[a.entity], Modifier.weight(1f)) }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(openings, key = { it.entity }) { o ->
                val e = states[o.entity]
                val isOpen = e?.isOn == true
                Row(
                    Modifier.height(42.dp).glass(12.dp, if (isOpen) Palette.warn else Palette.glass, if (isOpen) Palette.warnBorder else Palette.glassBorder)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PanelIcon(
                        when (e?.deviceClass) { "door" -> PanelIcons.door; "garage_door" -> PanelIcons.garage; else -> PanelIcons.window },
                        18.dp,
                    )
                    Txt(o.name ?: e?.name ?: o.entity, 13, maxLines = 1, modifier = Modifier.weight(1f))
                    Txt(if (isOpen) "OTWARTE" else "zamk.", 11, FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AlarmCard(vm: PanelActions, alarm: NamedEntity, entity: Entity?, modifier: Modifier) {
    Column(modifier.glass(18.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(alarm.name ?: entity?.name ?: alarm.entity, 16, FontWeight.Medium, modifier = Modifier.weight(1f))
            Txt(alarmLabel(entity?.state), 12, color = Palette.soft)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ALARM_ACTIONS.forEach { (service, label) ->
                val active = when (service) {
                    "alarm_disarm" -> entity?.state == "disarmed"
                    "alarm_arm_home" -> entity?.state == "armed_home"
                    else -> entity?.state == "armed_away"
                }
                Box(
                    Modifier.weight(1f).height(40.dp)
                        .glass(12.dp, if (active) Palette.glassStrong else Color.Transparent)
                        .tap { vm.service("alarm_control_panel", service, alarm.entity) },
                    contentAlignment = Alignment.Center,
                ) { Txt(label, 12, if (active) FontWeight.SemiBold else FontWeight.Normal) }
            }
        }
    }
}

private fun alarmLabel(state: String?): String = when (state) {
    "disarmed" -> "wyłączony"
    "armed_home" -> "w domu"
    "armed_away" -> "uzbrojony"
    "armed_night" -> "noc"
    "arming" -> "uzbrajanie…"
    "pending" -> "oczekuje…"
    "triggered" -> "ALARM!"
    else -> state ?: "–"
}

@Composable
fun CoversScreen(vm: PanelActions, covers: List<NamedEntity>, states: Map<String, Entity>, onBack: () -> Unit) {
    SubPage("Rolety", onBack, trailing = {
        Box(Modifier.height(46.dp).glass(15.dp).tap { covers.forEach { vm.service("cover", "open_cover", it.entity) } }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Txt("Wszystkie w górę", 13)
        }
        Box(Modifier.height(46.dp).glass(15.dp).tap { covers.forEach { vm.service("cover", "close_cover", it.entity) } }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Txt("w dół", 13)
        }
    }) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(covers, key = { it.entity }) { c -> CoverCard(vm, c, states[c.entity]) }
        }
    }
}

@Composable
private fun CoverCard(vm: PanelActions, cover: NamedEntity, e: Entity?) {
    val position = e?.attrDouble("current_position")
    val closedFraction = when {
        position != null -> (1.0 - position / 100.0).toFloat()
        e?.state == "closed" -> 1f
        else -> 0f
    }
    Row(Modifier.height(if (canStop(e)) 182.dp else 150.dp).glass(20.dp).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.width(54.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                .border(2.dp, Color(0x8CFFFFFF), RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF7CC4F5), Color(0xFFF2C27A)))),
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(closedFraction).background(Color(0xFFE2DDEC)))
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Txt(cover.name ?: e?.name ?: cover.entity, 15, FontWeight.Medium, maxLines = 2)
                Txt(coverLabel(e, position), 12, color = Palette.soft)
            }
            Box(Modifier.fillMaxWidth().height(38.dp).glass(12.dp).tap { vm.service("cover", "open_cover", cover.entity) }, contentAlignment = Alignment.Center) {
                PanelIcon(PanelIcons.up, 20.dp)
            }
            // stop leaves the blind wherever it is, e.g. half-way down
            if (canStop(e)) {
                Box(Modifier.fillMaxWidth().height(34.dp).glass(12.dp).tap { vm.service("cover", "stop_cover", cover.entity) }, contentAlignment = Alignment.Center) {
                    PanelIcon(PanelIcons.stop, 18.dp)
                }
            }
            Box(Modifier.fillMaxWidth().height(38.dp).glass(12.dp).tap { vm.service("cover", "close_cover", cover.entity) }, contentAlignment = Alignment.Center) {
                PanelIcon(PanelIcons.down, 20.dp)
            }
        }
    }
}

private const val COVER_STOP = 8L

/** Stop is offered unless the cover says it cannot (no supported_features means "assume it can"). */
fun canStop(e: Entity?): Boolean {
    val features = e?.attrDouble("supported_features")?.toLong() ?: return true
    return features and COVER_STOP != 0L
}

private fun coverLabel(e: Entity?, position: Double?): String = when {
    e == null -> "–"
    e.state == "opening" -> "otwiera się…"
    e.state == "closing" -> "zamyka się…"
    position != null && position > 0 && position < 100 -> "otwarta w ${position.toInt()}%"
    e.state == "open" -> "otwarta"
    e.state == "closed" -> "zamknięta"
    else -> e.state
}
