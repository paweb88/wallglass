package app.wallglass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope

private val SUN = Brush.radialGradient(listOf(Color(0xFFFFF6B0), Color(0xFFFFC83A), Color(0xFFFF9420)))
private val CLOUD get() = Palette.cloud
private val CLOUD_DARK = Color(0xFFB8C3D6)
private val MOON = Color(0xFFF3EDD3)
private val RAIN = Color(0xFF7CC4FF)
private val BOLT = Color(0xFFFFD54F)

/** Draws a weather condition icon (HA condition names) in a square canvas. */
@Composable
fun WeatherIcon(condition: String, night: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 100f
        when (condition) {
            "sunny" -> sun(50f, 50f, 26f, u)
            "clear-night" -> moon(50f, 48f, 26f, u)
            "partlycloudy" -> {
                if (night) moon(60f, 34f, 18f, u) else sun(60f, 36f, 20f, u)
                cloud(8f, 34f, 1.1f, CLOUD, u)
            }
            "cloudy" -> {
                cloud(28f, 16f, 0.85f, CLOUD_DARK, u)
                cloud(6f, 32f, 1.1f, CLOUD, u)
            }
            "rainy", "pouring" -> {
                cloud(6f, 20f, 1.1f, CLOUD_DARK, u)
                drops(if (condition == "pouring") 4 else 3, u)
            }
            "snowy", "snowy-rainy", "hail" -> {
                cloud(6f, 20f, 1.1f, CLOUD, u)
                for (i in 0 until 4) drawCircle(Color.White, 4f * u, Offset((28f + i * 15f) * u, (80f + (i % 2) * 7f) * u))
            }
            "lightning", "lightning-rainy" -> {
                cloud(6f, 18f, 1.1f, CLOUD_DARK, u)
                drawLine(BOLT, Offset(52f * u, 64f * u), Offset(42f * u, 82f * u), 7f * u, StrokeCap.Round)
                drawLine(BOLT, Offset(42f * u, 82f * u), Offset(56f * u, 80f * u), 7f * u, StrokeCap.Round)
                drawLine(BOLT, Offset(56f * u, 80f * u), Offset(48f * u, 96f * u), 7f * u, StrokeCap.Round)
            }
            "fog" -> {
                cloud(6f, 14f, 1.1f, CLOUD, u)
                drawLine(CLOUD, Offset(18f * u, 78f * u), Offset(82f * u, 78f * u), 6f * u, StrokeCap.Round)
                drawLine(CLOUD, Offset(26f * u, 90f * u), Offset(74f * u, 90f * u), 6f * u, StrokeCap.Round)
            }
            else -> cloud(6f, 30f, 1.1f, CLOUD, u)
        }
    }
}

private fun DrawScope.sun(x: Float, y: Float, r: Float, u: Float) {
    drawCircle(SUN, r * u, Offset(x * u, y * u))
}

private fun DrawScope.moon(x: Float, y: Float, r: Float, u: Float) {
    drawCircle(MOON, r * u, Offset(x * u, y * u))
    drawCircle(Color.Black.copy(alpha = 0f), r * u, Offset(x * u, y * u))
    // crescent: overlay a shifted circle in background-free way using a darker tone
    drawCircle(Color(0x33000000), r * 0.82f * u, Offset((x + r * 0.45f) * u, (y - r * 0.3f) * u))
}

/** Cloud of three puffs; base path designed in a 68×40 box scaled by [s]. */
private fun DrawScope.cloud(x: Float, y: Float, s: Float, color: Color, u: Float) {
    fun p(px: Float, py: Float) = Offset((x + px * s) * u, (y + py * s) * u)
    drawCircle(color, 14f * s * u, p(20f, 26f))
    drawCircle(color, 19f * s * u, p(38f, 20f))
    drawCircle(color, 13f * s * u, p(55f, 28f))
    drawRect(color, topLeft = p(20f, 26f), size = Size(35f * s * u, 14f * s * u))
}

private fun DrawScope.drops(count: Int, u: Float) {
    for (i in 0 until count) {
        val x = 30f + i * 15f
        drawLine(RAIN, Offset(x * u, 72f * u), Offset((x - 5f) * u, 86f * u), 6f * u, StrokeCap.Round)
    }
}
