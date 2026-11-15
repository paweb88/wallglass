package app.wallglass.voice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.exp

/**
 * openWakeWord-style detector: 80 ms chunks of 16 kHz audio → mel spectrogram (ONNX) →
 * Google speech embedding (ONNX) → small MLP head trained for one phrase ("Hej Dom").
 * Mirrors openwakeword.utils.AudioFeatures._streaming_features.
 */
class WakeWordDetector(context: Context, headAsset: String = "wakeword/hej_dom.bin") : Closeable {

    private val env = OrtEnvironment.getEnvironment()
    private val mel: OrtSession = env.createSession(context.assets.open("wakeword/melspectrogram.onnx").readBytes())
    private val embed: OrtSession = env.createSession(context.assets.open("wakeword/embedding_model.onnx").readBytes())
    private val head = Head.load(context.assets.open(headAsset).readBytes())
    val threshold: Float get() = head.threshold

    private val raw = FloatArray(CHUNK + CONTEXT)               // last chunk plus mel context
    private val melFrames = ArrayDeque<FloatArray>().apply { repeat(MEL_WINDOW) { addLast(FloatArray(MEL_BINS) { 1f }) } }
    private val features = ArrayDeque<FloatArray>()

    /** Feed exactly [CHUNK] samples; returns the wake word probability (0 until enough context). */
    fun process(chunk: ShortArray): Float {
        require(chunk.size == CHUNK) { "chunk must be $CHUNK samples" }
        System.arraycopy(raw, CHUNK, raw, 0, CONTEXT)
        for (i in 0 until CHUNK) raw[CONTEXT + i] = chunk[i].toFloat()

        addMelFrames(runMel(raw))
        features.addLast(runEmbedding())
        while (features.size > FEATURE_WINDOW) features.removeFirst()
        if (features.size < FEATURE_WINDOW) return 0f
        return head.predict(features)
    }

    fun reset() {
        raw.fill(0f)
        melFrames.clear(); repeat(MEL_WINDOW) { melFrames.addLast(FloatArray(MEL_BINS) { 1f }) }
        features.clear()
    }

    private fun runMel(samples: FloatArray): Array<FloatArray> {
        OnnxTensor.createTensor(env, FloatBuffer.wrap(samples), longArrayOf(1, samples.size.toLong())).use { input ->
            mel.run(mapOf(mel.inputNames.first() to input)).use { out ->
                val tensor = out[0] as OnnxTensor
                val buf = tensor.floatBuffer
                val frames = buf.remaining() / MEL_BINS
                return Array(frames) { FloatArray(MEL_BINS) { buf.get() / 10f + 2f } }
            }
        }
    }

    private fun addMelFrames(frames: Array<FloatArray>) {
        frames.forEach { melFrames.addLast(it) }
        while (melFrames.size > MEL_WINDOW) melFrames.removeFirst()
    }

    private fun runEmbedding(): FloatArray {
        val input = FloatBuffer.allocate(MEL_WINDOW * MEL_BINS)
        melFrames.forEach { input.put(it) }
        input.rewind()
        OnnxTensor.createTensor(env, input, longArrayOf(1, MEL_WINDOW.toLong(), MEL_BINS.toLong(), 1)).use { t ->
            embed.run(mapOf(embed.inputNames.first() to t)).use { out ->
                val buf = (out[0] as OnnxTensor).floatBuffer
                return FloatArray(EMBEDDING) { buf.get() }
            }
        }
    }

    override fun close() {
        mel.close(); embed.close()
    }

    /** Two-layer MLP (ReLU, sigmoid) exported from scikit-learn. */
    private class Head(val inputs: Int, val hidden: Int, val threshold: Float,
                       val w1: FloatArray, val b1: FloatArray, val w2: FloatArray, val b2: Float) {

        fun predict(frames: ArrayDeque<FloatArray>): Float {
            val x = FloatArray(inputs)
            var k = 0
            frames.forEach { f -> f.copyInto(x, k); k += f.size }
            var z = b2
            for (h in 0 until hidden) {
                var s = b1[h]
                val row = h * inputs
                for (i in 0 until inputs) s += w1[row + i] * x[i]
                if (s > 0f) z += w2[h] * s
            }
            return (1.0 / (1.0 + exp(-z.toDouble()))).toFloat()
        }

        companion object {
            fun load(bytes: ByteArray): Head {
                val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val magic = ByteArray(4).also { b.get(it) }
                require(String(magic) == "HDW1") { "unknown wake word head format" }
                val inputs = b.int; val hidden = b.int; val threshold = b.float
                fun floats(n: Int) = FloatArray(n) { b.float }
                return Head(inputs, hidden, threshold, floats(hidden * inputs), floats(hidden), floats(hidden), b.float)
            }
        }
    }

    companion object {
        const val CHUNK = 1280        // 80 ms @ 16 kHz
        const val CONTEXT = 480       // 3 hops of mel context, as in openWakeWord
        const val MEL_BINS = 32
        const val MEL_WINDOW = 76
        const val EMBEDDING = 96
        const val FEATURE_WINDOW = 16
    }
}
