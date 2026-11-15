package app.wallglass.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

enum class Swipe { UP, DOWN, LEFT, RIGHT }

/**
 * Detects a clear one-finger swipe without consuming events, so taps and scrolling
 * inside the page keep working. Needs ≥ 90 px travel, mostly along one axis.
 */
fun Modifier.swipes(onSwipe: (Swipe) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var last: Offset = down.position
        val start = System.currentTimeMillis()
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            last = change.position
            if (!change.pressed) break
        }
        val dx = last.x - down.position.x
        val dy = last.y - down.position.y
        if (System.currentTimeMillis() - start > 800) return@awaitEachGesture
        when {
            abs(dx) > 90 && abs(dx) > 2 * abs(dy) -> onSwipe(if (dx < 0) Swipe.LEFT else Swipe.RIGHT)
            abs(dy) > 90 && abs(dy) > 2 * abs(dx) -> onSwipe(if (dy < 0) Swipe.UP else Swipe.DOWN)
        }
    }
}
