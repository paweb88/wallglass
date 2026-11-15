package app.wallglass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.wallglass.config.WallpaperConfig

/** Colours of the current [PanelTheme]; read at use time so a theme change redraws everything. */
object Palette {
    private val t get() = ThemeState.current
    /** Main text and icon colour (white on dark themes). */
    val white get() = t.text
    val soft get() = t.soft
    val glass get() = t.card
    /** Selected segment inside a card. */
    val glassStrong get() = if (t.light) t.text.copy(alpha = 0.10f) else t.card.copy(alpha = (t.card.alpha + 0.12f).coerceAtMost(1f))
    /** Empty part of bars, gauges and switches. */
    val track get() = t.text.copy(alpha = if (t.light) 0.14f else 0.22f)
    val glassBorder get() = t.cardBorder
    val selected get() = t.selected
    val onSelected get() = t.onSelected
    val ok get() = t.ok
    val okBorder get() = t.okBorder
    val warn get() = t.warn
    val warnBorder get() = t.warnBorder
    val heat get() = t.heat
    val cloud get() = t.cloud
    val font get() = t.font
    val danger = Color(0xFFD6453B)
    val action = Color(0xFF1F6FD1)
    val green = Color(0xFF2E9D4F)
    val switchOn = Color(0xFFFF8A50)
    val amber get() = t.amber
    val okText get() = t.okText
    val wasteTint get() = t.wasteTint
    val iconTint get() = t.iconTint
}

private data class Sky(val from: Int, val to: Int, val colors: List<Color>)

private val SKIES = listOf(
    Sky(21, 6, listOf(Color(0xFF070B1D), Color(0xFF121A3F), Color(0xFF1F2A5C))),
    Sky(6, 10, listOf(Color(0xFF27345F), Color(0xFF8A5A7A), Color(0xFFF2A65A))),
    Sky(10, 16, listOf(Color(0xFF0B4FB3), Color(0xFF1F86E0), Color(0xFF79C3F2))),
    Sky(16, 21, listOf(Color(0xFF150F36), Color(0xFF3F2470), Color(0xFFB24A72))),
)

private fun inRange(hour: Int, from: Int, to: Int) =
    if (from < to) hour in from until to else hour >= from || hour < to

/** Background brush for the configured wallpaper; images are drawn separately. */
fun wallpaperBrush(config: WallpaperConfig, hour: Int): Brush {
    if (config.mode == "dark") return Brush.linearGradient(listOf(Color(0xFF11131A), Color(0xFF1A1D27)))
    if (config.mode == "color") {
        val color = config.color?.let { parseColor(it) } ?: Color(0xFF1E2A3D)
        return Brush.linearGradient(listOf(color, color))
    }
    val sky = SKIES.firstOrNull { inRange(hour, it.from, it.to) } ?: SKIES[2]
    return Brush.linearGradient(sky.colors, start = Offset(0f, 0f), end = Offset(600f, 900f))
}

fun parseColor(hex: String): Color? = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrNull()

fun Modifier.glass(
    radius: Dp = 20.dp,
    fill: Color = Palette.glass,
    border: Color = Palette.glassBorder,
): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.clip(shape).background(fill, shape).border(1.dp, border, shape)
}
