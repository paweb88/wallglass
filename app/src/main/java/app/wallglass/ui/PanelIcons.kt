package app.wallglass.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Line icons on a 24×24 grid, matching the design. */
object PanelIcons {
    val doorbell = stroke("M8.5 2.5h7a2.5 2.5 0 0 1 2.5 2.5v14a2.5 2.5 0 0 1-2.5 2.5h-7A2.5 2.5 0 0 1 6 19V5a2.5 2.5 0 0 1 2.5-2.5zM12 6.5a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5zM12 15.3a1.2 1.2 0 1 1 0 2.4 1.2 1.2 0 0 1 0-2.4z")
    val camera = stroke("M3 8.5l12-4 3 7-12 4zM18 11.5l3-1M7 15.5v4H3")
    val shield = stroke("M12 3l7 3v5c0 4.5-3 8.2-7 10-4-1.8-7-5.5-7-10V6zM9 12l2 2 4-4")
    val blinds = stroke("M5.5 3h13a1.5 1.5 0 0 1 1.5 1.5v15a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 19.5v-15A1.5 1.5 0 0 1 5.5 3zM4 7h16M4 11h16M4 15h16")
    val flame = stroke("M12 21c-4 0-6.5-2.6-6.5-6.2 0-3.4 2.6-5.6 3.6-9.3 2.2 1.4 3.3 3.4 3.4 5.3 1-0.8 1.6-2 1.7-3.3 2.4 2 3.8 4.6 3.8 7.3 0 3.6-2.5 6.2-6 6.2z")
    val home = stroke("M3.5 11L12 4l8.5 7M5.5 9.5V20h13V9.5M12 13v3")
    val back = stroke("M15 5l-7 7 7 7")
    val close = stroke("M6 6l12 12M18 6L6 18")
    val lock = stroke("M7 10.5h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2zM8 10.5V7.5a4 4 0 0 1 8 0v3")
    val unlock = stroke("M7 10h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2zM8 10V7a4 4 0 0 1 7.5-2")
    val bell = stroke("M6 17h12l-1.5-2V11a4.5 4.5 0 0 0-9 0v4L6 17M10 20h4")
    val bellOff = stroke("M6 17h12l-1.5-2V11a4.5 4.5 0 0 0-7-3.7M6.5 11v4L5 17M10 20h4M4 4l16 16")
    val up = stroke("M6 15l6-6 6 6")
    val down = stroke("M6 9l6 6 6-6")
    val door = stroke("M6 21V4h12v17M3 21h18M14.5 12.5v.1")
    val window = stroke("M5.5 3.5h13a1.5 1.5 0 0 1 1.5 1.5v14a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 19V5a1.5 1.5 0 0 1 1.5-1.5zM12 3.5v17M4 12h16")
    val garage = stroke("M3 21V9l9-5 9 5v12M6 21v-9h12v9M6 15h12M6 18h12")
    val drop = stroke("M12 3c3.5 4.5 6 7.8 6 11a6 6 0 0 1-12 0c0-3.2 2.5-6.5 6-11z")
    val speakerOff = stroke("M4 9.5h4l5-4v13l-5-4H4zM17 9.5l5 5M22 9.5l-5 5")
    val speaker = stroke("M4 9.5h4l5-4v13l-5-4H4zM16.5 9a4 4 0 0 1 0 6M19 6.5a7.5 7.5 0 0 1 0 11")
    val mic = stroke("M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3zM5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21")
    val hangup = stroke("M3 14.5c5-4.5 13-4.5 18 0l-2.5 2.5-3-1.5v-2.2a10 10 0 0 0-7 0v2.2l-3 1.5z")
    val music = stroke("M9 18V5l11-2v13M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0zM20 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z")
    val play = stroke("M7 4.5v15l12-7.5z")
    val pause = stroke("M8 5v14M16 5v14")
    val prev = stroke("M7 5v14M19 5L9 12l10 7z")
    val next = stroke("M17 5v14M5 5l10 7-10 7z")
    val shuffle = stroke("M4 7h3c4 0 6 10 10 10h3M4 17h3c1.6 0 2.8-1.5 3.8-3.3M14 8.5C15 7.6 16 7 17 7h3M18 4l3 3-3 3M18 14l3 3-3 3")
    val repeat = stroke("M4 11V9a3 3 0 0 1 3-3h13M17 3l3 3-3 3M20 13v2a3 3 0 0 1-3 3H4M7 21l-3-3 3-3")
    val trash = stroke("M4 7h16M9 7V4.5h6V7M6 7l1 13h10l1-13M10 11v6M14 11v6")
    val check = stroke("M5 12.5l4.5 4.5L19 7.5")

    private fun stroke(d: String): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
}
