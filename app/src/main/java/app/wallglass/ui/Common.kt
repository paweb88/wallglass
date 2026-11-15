package app.wallglass.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

val TextWhite: TextStyle get() = TextStyle(color = Palette.white, fontSize = 15.sp, fontFamily = Palette.font)

/** Click with visible feedback: the element shrinks and brightens while pressed. */
fun Modifier.tap(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "tapScale")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .drawWithContent {
            drawContent()
            if (pressed) drawRect(Color(0x33FFFFFF))
        }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

@Composable
fun Txt(
    text: String,
    size: Int = 15,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Palette.white,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = size.sp, fontWeight = weight, fontFamily = Palette.font),
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) androidx.compose.ui.text.style.TextOverflow.Clip else androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}

@Composable
fun PanelIcon(icon: ImageVector, size: Dp = 26.dp, tint: Color = Palette.white) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

/** Sub-page frame: back button, title, optional right-side content. */
@Composable
fun SubPage(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(46.dp).glass(15.dp).tap(onBack),
                contentAlignment = Alignment.Center,
            ) { PanelIcon(PanelIcons.back) }
            Txt(title, size = 22, weight = FontWeight.Light, modifier = Modifier.weight(1f))
            trailing()
        }
        content()
    }
}

/** Large glass button with icon and label. */
@Composable
fun GlassButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    fill: Color = Palette.glass,
    border: Color = Palette.glassBorder,
    height: Dp = 64.dp,
    onClick: () -> Unit,
) {
    Row(
        modifier.height(height).glass(18.dp, fill, border).tap(onClick).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { PanelIcon(it) }
        Txt(label, size = 16, weight = FontWeight.Medium)
    }
}

fun formatTemp(value: Double?, decimals: Int = 1): String =
    value?.let { String.format(Locale("pl", "PL"), "%.${decimals}f", it) } ?: "–"

@Composable
fun rememberNoRipple() = remember { MutableInteractionSource() }
