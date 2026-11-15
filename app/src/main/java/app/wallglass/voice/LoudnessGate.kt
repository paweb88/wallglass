package app.wallglass.voice

/**
 * Remembers the loudness (RMS) of the last few audio chunks. A wake word detected in a quiet room
 * is almost always a false positive (the model reacts to amplified background hiss), so a detection
 * only counts when something at speech level was heard just before it.
 */
class LoudnessGate(private val window: Int = 15, private val minRms: Double = MIN_SPEECH_RMS) {
    private val recent = ArrayDeque<Double>(window)

    fun push(rms: Double) {
        if (recent.size == window) recent.removeFirst()
        recent.addLast(rms)
    }

    /** Loudest chunk in the window (about 1.2 s at 80 ms chunks). */
    val loudest: Double get() = recent.maxOrNull() ?: 0.0

    val isLoudEnough: Boolean get() = loudest >= minRms

    companion object {
        /** Quiet room after the ×8 mic gain: ~400–2500. Speech near the panel: 4000–25000. */
        const val MIN_SPEECH_RMS = 3000.0

        fun rms(chunk: ShortArray): Double {
            if (chunk.isEmpty()) return 0.0
            var sq = 0.0
            for (v in chunk) sq += v.toDouble() * v
            return kotlin.math.sqrt(sq / chunk.size)
        }
    }
}
