package app.wallglass.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Listens on the microphone and calls [onWake] when the wake word is heard.
 * Nothing leaves the device while listening; the microphone is released before [onWake]
 * so the assistant can record the actual question.
 */
class WakeWordListener(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onWake: () -> Unit,
) {
    private var job: Job? = null
    private var detector: WakeWordDetector? = null

    private val _level = MutableStateFlow(0f)
    /** Last probability – handy for tuning the threshold. */
    val level: StateFlow<Float> = _level.asStateFlow()

    val running: Boolean get() = job?.isActive == true

    fun start() {
        if (!app.wallglass.BuildConfig.WAKE_WORD || running) return
        val previous = job // may still be closing its recorder; two recorders at once break capture
        job = scope.launch(Dispatchers.Default) {
            previous?.join()
            listen()
        }
    }

    fun stop() {
        job?.cancel() // the reference stays, so stopAndWait() can still wait for the recorder to close
    }

    /** Stops listening and waits until the microphone is released (another recorder can then open it). */
    suspend fun stopAndWait() {
        job?.cancelAndJoin()
    }

    @SuppressLint("MissingPermission") // RECORD_AUDIO is requested at startup
    private suspend fun listen() {
        val det = detector ?: runCatching { WakeWordDetector(context) }
            .onFailure { Log.e(TAG, "Nie udało się wczytać modelu słowa wybudzającego", it) }
            .getOrNull()?.also { detector = it } ?: return
        det.reset()
        val chunk = ShortArray(WakeWordDetector.CHUNK)
        var hits = 0
        var woke = false
        var peakP = 0f
        var peakRms = 0.0
        var n = 0
        val gate = LoudnessGate()
        val mic = runCatching { MicInput() }.onFailure { Log.e(TAG, "Mikrofon niedostępny", it) }.getOrNull() ?: return
        try {
            while (kotlin.coroutines.coroutineContext.isActive && mic.read(chunk)) {
                val p = det.process(chunk)
                _level.value = p
                peakP = maxOf(peakP, p)
                val rms = LoudnessGate.rms(chunk)
                gate.push(rms)
                peakRms = maxOf(peakRms, rms)
                n += chunk.size
                if (n >= RATE * 2) { // every 2 s: loudest 80 ms window and best score, for tuning
                    Log.d(TAG, "peakRms=${"%.0f".format(peakRms)} maxP=${"%.3f".format(peakP)}")
                    peakP = 0f; peakRms = 0.0; n = 0
                }
                hits = if (p >= det.threshold) hits + 1 else 0
                if (hits >= REQUIRED_HITS) {
                    val loudest = "%.0f".format(gate.loudest)
                    if (!gate.isLoudEnough) {
                        // quiet room: the model reacted to background hiss, not to someone speaking
                        Log.i(TAG, "Odrzucono wybudzenie – za cicho (p=$p, rms=$loudest)")
                        hits = 0
                        continue
                    }
                    Log.i(TAG, "Wykryto słowo wybudzające (p=$p, rms=$loudest)")
                    woke = true
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Nasłuch przerwany", e)
        } finally {
            mic.close()
        }
        if (woke) withContext(Dispatchers.Main) { onWake() }
    }

    private companion object {
        const val TAG = "WakeWord"
        const val RATE = 16_000
        const val REQUIRED_HITS = 2 // two consecutive 80 ms windows above threshold
    }
}
