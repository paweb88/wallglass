package app.wallglass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

private val PL = Locale("pl", "PL")

/** Black clock screen; dimmer digits at night. Any tap wakes the panel. */
@Composable
fun Screensaver(now: LocalDateTime, night: Boolean, outside: Double?, inside: Double?, onWake: () -> Unit) {
    val color = if (night) Color(0xFF5A5A5A) else Color(0xFFBDBDBD)
    Column(
        Modifier.fillMaxSize().background(Color.Black).tap(onWake),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Txt("%02d:%02d".format(now.hour, now.minute), 150, FontWeight.Thin, color)
        Txt(
            "${now.dayOfWeek.getDisplayName(TextStyle.FULL, PL)}, ${now.dayOfMonth} ${now.month.getDisplayName(TextStyle.FULL, PL)}",
            22, FontWeight.Light, color,
        )
        Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            outside?.let { Txt("Na zewnątrz ${formatTemp(it, 0)}°", 17, color = color) }
            inside?.let { Txt("W domu ${formatTemp(it)}°", 17, color = color) }
        }
    }
}
