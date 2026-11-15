package app.wallglass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import app.wallglass.ha.PanelNotification
import app.wallglass.ha.TodoItem
import app.wallglass.ha.TodoDue
import app.wallglass.ha.dueLabel
import java.time.LocalDateTime

private enum class TodoFilter(val label: String) { OPEN("Do zrobienia"), ALL("Wszystkie") }

@Composable
fun TodoScreen(items: List<TodoItem>, onToggle: (TodoItem, Boolean) -> Unit, onBack: () -> Unit) {
    var filter by remember { mutableStateOf(TodoFilter.OPEN) }
    val open = items.count { !it.done }
    val shown = items
        .filter { filter == TodoFilter.ALL || !it.done }
        .sortedWith(compareBy<TodoItem> { it.done }.thenBy { TodoDue.parse(it.due)?.let { d -> d.at ?: d.date.atTime(23, 59) } ?: LocalDateTime.MAX })
    SubPage("Zadania domowe", onBack, trailing = { Txt("$open do zrobienia", 13, color = Palette.soft) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TodoFilter.entries.forEach { f ->
                val active = f == filter
                Box(
                    Modifier.height(36.dp).glass(18.dp, if (active) Palette.selected else Palette.glass).tap { filter = f }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Txt(f.label, 13, FontWeight.Medium, if (active) Palette.onSelected else Palette.white) }
            }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Txt(if (items.isEmpty()) "Lista jest pusta" else "Wszystko zrobione", 18, color = Palette.soft)
            }
            return@SubPage
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.uid }) { item -> TodoRow(item) { onToggle(item, !item.done) } }
        }
    }
}

@Composable
private fun TodoRow(item: TodoItem, onToggle: () -> Unit) {
    val now = LocalDateTime.now()
    val due = TodoDue.parse(item.due)
    val urgent = !item.done && due?.isUrgent(now) == true
    Row(
        Modifier.fillMaxWidth().heightIn(min = 62.dp)
            .glass(16.dp, if (item.done) Color(0x0DFFFFFF) else if (urgent) Palette.warn else Palette.glass, if (urgent) Palette.warnBorder else Palette.glassBorder)
            .tap(onToggle).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(9.dp))
                .background(if (item.done) Color(0xFF3EC46D) else Color.Transparent)
                .border(2.dp, if (item.done) Color(0xFF3EC46D) else Palette.soft, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) { if (item.done) PanelIcon(PanelIcons.check, 18.dp) }
        Column(Modifier.weight(1f)) {
            Text(
                item.summary,
                style = TextStyle(
                    color = if (item.done) Palette.soft else Palette.white,
                    fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = Palette.font,
                    textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                ),
            )
            dueLabel(due, now)?.let { label ->
                val color = if (urgent) Color(0xFFFFD2B0) else Palette.soft
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // a time makes the item a reminder: the panel shows a card when it is due
                    if (due?.time != null) PanelIcon(PanelIcons.bell, 13.dp, color)
                    Txt(label, 12, color = color)
                }
            }
        }
    }
}

/** Card shown over the start screen for each pending notification. */
@Composable
fun NotificationCard(
    notification: PanelNotification,
    more: Int,
    onDone: () -> Unit,
    onSnooze: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xF5FFFFFF)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            notification.title?.let { Txt(it.uppercase(), 12, FontWeight.SemiBold, Color(0xFF8A5A00)) }
            Txt(notification.message, 19, FontWeight.SemiBold, Color(0xFF1C1A29))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF3F2F7)).tap(onSnooze),
                    contentAlignment = Alignment.Center,
                ) { Txt("Przypomnij za godzinę", 15, color = Color(0xFF1C1A29)) }
                Box(
                    Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(14.dp)).background(Palette.green).tap(onDone),
                    contentAlignment = Alignment.Center,
                ) { Txt("Zrobione", 15, FontWeight.SemiBold) }
            }
        }
        if (more > 0) {
            Box(Modifier.glass(16.dp, Color(0xD1141228)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Txt("+$more inne powiadomienia", 14)
            }
        }
    }
}
