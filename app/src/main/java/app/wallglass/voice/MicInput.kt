package app.wallglass.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log

/**
 * 16 kHz mono microphone with the platform AGC/noise suppressor when available and a
 * software gain on top (some wall panels have very quiet PDM microphones).
 */
class MicInput(
    private val gain: Float = DEFAULT_GAIN,
    rate: Int = RATE,
    source: Int = MediaRecorder.AudioSource.VOICE_RECOGNITION,
) : AutoCloseable {

    @SuppressLint("MissingPermission") // RECORD_AUDIO is requested at startup
    private val record = AudioRecord(
        source, rate,
        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        maxOf(AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 1280 * 8),
    )
    private val agc = if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(record.audioSessionId)?.apply { enabled = true } else null
    private val ns = if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true } else null

    init {
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            agc?.release(); ns?.release(); record.release()
            throw IllegalStateException("Mikrofon zajęty lub niedostępny")
        }
        Log.i(TAG, "Mikrofon: ${rate} Hz AGC=${agc != null} NS=${ns != null} wzmocnienie=$gain")
        record.startRecording()
    }

    /** Fills [buffer] completely (blocking); returns false if the microphone stopped. */
    fun read(buffer: ShortArray): Boolean {
        var filled = 0
        while (filled < buffer.size) {
            val n = record.read(buffer, filled, buffer.size - filled)
            if (n <= 0) {
                Log.w(TAG, "Odczyt mikrofonu zwrócił $n (stan ${record.recordingState})")
                return false
            }
            filled += n
        }
        if (gain != 1f) {
            for (i in buffer.indices) buffer[i] = (buffer[i] * gain).toInt().coerceIn(-32768, 32767).toShort()
        }
        return true
    }

    override fun close() {
        runCatching { record.stop() }
        agc?.release(); ns?.release()
        record.release()
    }

    companion object {
        const val RATE = 16_000
        const val DEFAULT_GAIN = 8f
        private const val TAG = "MicInput"
    }
}
