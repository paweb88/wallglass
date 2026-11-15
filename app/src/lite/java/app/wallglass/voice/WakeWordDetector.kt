package app.wallglass.voice

import android.content.Context
import java.io.Closeable

/**
 * The "lite" build has no on-device wake word (no ONNX Runtime, no models), which makes the APK smaller.
 * The assistant still works from the tile, the mic button or a swipe. Creating a detector always fails,
 * and the listener is never started (BuildConfig.WAKE_WORD is false).
 */
class WakeWordDetector(@Suppress("UNUSED_PARAMETER") context: Context) : Closeable {
    init {
        throw UnsupportedOperationException("This build has no wake word")
    }

    val threshold: Float get() = 1f

    fun process(@Suppress("UNUSED_PARAMETER") chunk: ShortArray): Float = 0f

    fun reset() = Unit

    override fun close() = Unit

    companion object {
        const val CHUNK = 1280
    }
}
