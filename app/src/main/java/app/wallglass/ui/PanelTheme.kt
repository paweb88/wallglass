package app.wallglass.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** A colour style of the panel. `background == null` keeps the wallpaper (sky by hour, colour or photo). */
data class PanelTheme(
    val id: String,
    val label: String,
    val background: Brush?,
    val text: Color,
    val soft: Color,
    val card: Color,
    val cardBorder: Color,
    /** Fill of a selected chip and the text on it. */
    val selected: Color,
    val onSelected: Color,
    val ok: Color,
    val okBorder: Color,
    val okText: Color,
    val warn: Color,
    val warnBorder: Color,
    val heat: Color,
    val amber: Color,
    val wasteTint: Color,
    val iconTint: Color,
    val cloud: Color,
    val font: FontFamily = FontFamily.Default,
    /** Light backgrounds: never used at night or behind video. */
    val light: Boolean = false,
)

object PanelThemes {
    val sky = PanelTheme(
        id = "sky", label = "Niebo", background = null,
        text = Color.White, soft = Color(0xFFECE6F7), card = Color(0x24FFFFFF), cardBorder = Color(0x3DFFFFFF),
        selected = Color.White, onSelected = Color(0xFF3F2470),
        ok = Color(0x6B2E9D4F), okBorder = Color(0x998CE6AA), okText = Color(0xFF8EF0B0),
        warn = Color(0x8CE67828), warnBorder = Color(0xBFFFBE82), heat = Color(0x80FF8A50),
        amber = Color(0xFFFFC83A), wasteTint = Color(0xFFE0B27A), iconTint = Color.White, cloud = Color(0xFFF4F7FC),
    )
    val graphite = PanelTheme(
        id = "graphite", label = "Grafit", background = Brush.verticalGradient(listOf(Color(0xFF16181D), Color(0xFF1D2027))),
        text = Color(0xFFF1F3F6), soft = Color(0xFF8C93A1), card = Color(0xFF23262E), cardBorder = Color(0xFF2C3039),
        selected = Color(0xFF4FD1C5), onSelected = Color(0xFF16181D),
        ok = Color(0x1F4FD1C5), okBorder = Color(0x664FD1C5), okText = Color(0xFF4FD1C5),
        warn = Color(0x33F6AD55), warnBorder = Color(0x88F6AD55), heat = Color(0x40FF8A50),
        amber = Color(0xFFF6AD55), wasteTint = Color(0xFFF6AD55), iconTint = Color(0xFF4FD1C5), cloud = Color(0xFFE6E9F0),
    )
    val light = PanelTheme(
        id = "light", label = "Jasny", background = Brush.verticalGradient(listOf(Color(0xFFF2F1EE), Color(0xFFF2F1EE))),
        text = Color(0xFF1C1C1E), soft = Color(0xFF77767B), card = Color.White, cardBorder = Color(0xFFE6E3DD),
        selected = Color(0xFF2F6FED), onSelected = Color.White,
        ok = Color(0xFFE7F6EC), okBorder = Color(0xFFBFE5CB), okText = Color(0xFF1F9D55),
        warn = Color(0xFFFDEBD9), warnBorder = Color(0xFFF2C79B), heat = Color(0xFFFFE3D3),
        amber = Color(0xFFC26A1B), wasteTint = Color(0xFFC26A1B), iconTint = Color(0xFF2F6FED), cloud = Color(0xFFC3CAD8),
        light = true,
    )
    val sand = PanelTheme(
        id = "sand", label = "Piasek", background = Brush.linearGradient(listOf(Color(0xFFEDE4D6), Color(0xFFDCCBB4))),
        text = Color(0xFF3A2E24), soft = Color(0xFF7D6A58), card = Color(0x8CFFFFFF), cardBorder = Color(0xB3FFFFFF),
        selected = Color(0xFFC2603D), onSelected = Color.White,
        ok = Color(0x244E7D4A), okBorder = Color(0x664E7D4A), okText = Color(0xFF4E7D4A),
        warn = Color(0x33C2603D), warnBorder = Color(0x88C2603D), heat = Color(0x40C2603D),
        amber = Color(0xFFC2603D), wasteTint = Color(0xFFC2603D), iconTint = Color(0xFFC2603D), cloud = Color(0xFFC8C3BA),
        font = FontFamily.Serif, light = true,
    )
    val forest = PanelTheme(
        id = "forest", label = "Las", background = Brush.linearGradient(listOf(Color(0xFF0E211D), Color(0xFF14342D), Color(0xFF1B3F35))),
        text = Color(0xFFF3EFE3), soft = Color(0x99F3EFE3), card = Color(0x12FFFFFF), cardBorder = Color(0x1AFFFFFF),
        selected = Color(0xFFC8E68A), onSelected = Color(0xFF0E211D),
        ok = Color(0x1FC8E68A), okBorder = Color(0x66C8E68A), okText = Color(0xFFC8E68A),
        warn = Color(0x33E8B86A), warnBorder = Color(0x88E8B86A), heat = Color(0x40FF8A50),
        amber = Color(0xFFE8B86A), wasteTint = Color(0xFFE8B86A), iconTint = Color(0xFFC8E68A), cloud = Color(0xFFE6E9E0),
    )
    val black = PanelTheme(
        id = "black", label = "Czerń", background = Brush.verticalGradient(listOf(Color.Black, Color.Black)),
        text = Color.White, soft = Color(0xFF7A7A80), card = Color(0xFF0F0F10), cardBorder = Color(0xFF1F1F22),
        selected = Color(0xFFFF9F43), onSelected = Color.Black,
        ok = Color(0x1A5BE49B), okBorder = Color(0x555BE49B), okText = Color(0xFF5BE49B),
        warn = Color(0x33FF9F43), warnBorder = Color(0x88FF9F43), heat = Color(0x40FF9F43),
        amber = Color(0xFFFF9F43), wasteTint = Color(0xFFFF9F43), iconTint = Color(0xFFFF9F43), cloud = Color(0xFFE6E6EA),
    )

    val all = listOf(sky, graphite, light, sand, forest, black)

    fun byId(id: String?): PanelTheme = all.firstOrNull { it.id == id } ?: sky

    /**
     * The theme actually drawn: light themes switch to Czerń at night (no bright screen in a dark room)
     * and behind video or the assistant, which have their own dark background.
     */
    fun effective(chosen: PanelTheme, night: Boolean, darkPage: Boolean): PanelTheme =
        if (chosen.light && (night || darkPage)) black else chosen
}

/** The theme every [Palette] colour reads; a Compose state, so changing it redraws the panel. */
object ThemeState {
    var current: PanelTheme by mutableStateOf(PanelThemes.sky)
}
