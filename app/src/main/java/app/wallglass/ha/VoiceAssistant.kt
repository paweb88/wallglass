package app.wallglass.ha

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** What the assistant is doing right now (drives the assistant screen). */
sealed interface VoiceState {
    data object Idle : VoiceState
    data object Listening : VoiceState
    data class Thinking(val heard: String?) : VoiceState
    data class Answer(val heard: String?, val answer: String, val ttsUrl: String?) : VoiceState
    data class Failed(val message: String) : VoiceState
}

/**
 * Push-to-talk Assist satellite: records 16 kHz mono PCM, streams it to HA's
 * `assist_pipeline/run` (stt → intent → tts) and reports the transcript and answer.
 * HA's own VAD ends the recording when the user stops speaking.
 */
class VoiceAssistant(private val client: HaClient, private val scope: CoroutineScope) {

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { runOnce() }
    }

    fun cancel() {
        job?.cancel()
        _state.value = VoiceState.Idle
    }

    @SuppressLint("MissingPermission") // checked by the caller (RECORD_AUDIO)
    private suspend fun runOnce() {
        _state.value = VoiceState.Listening
        val handlerId = CompletableDeferred<Int>()
        val sttDone = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        var heard: String? = null

        try {
            client.subscribeRaw(buildJsonObject {
                put("type", "assist_pipeline/run")
                put("start_stage", "stt")
                put("end_stage", "tts")
                putJsonObject("input") { put("sample_rate", SAMPLE_RATE) }
            }) { event -> onEvent(event, handlerId, sttDone, finished) { heard = it } }
        } catch (e: Exception) {
            _state.value = VoiceState.Failed("Asystent niedostępny: ${e.message}")
            return
        }

        val id = withTimeoutOrNull(5_000) { handlerId.await() }
        if (id == null) {
            _state.value = VoiceState.Failed("HA nie rozpoczął nasłuchu")
            return
        }
        streamMicrophone(id, sttDone)
        withTimeoutOrNull(60_000) { finished.await() }
        if (_state.value is VoiceState.Listening || _state.value is VoiceState.Thinking) {
            _state.value = VoiceState.Failed("Brak odpowiedzi")
        }
        Log.d(TAG, "Zakończono: $heard")
    }

    private fun onEvent(
        event: JsonObject,
        handlerId: CompletableDeferred<Int>,
        sttDone: CompletableDeferred<Unit>,
        finished: CompletableDeferred<Unit>,
        onHeard: (String) -> Unit,
    ) {
        val data = event["data"] as? JsonObject
        when (event["type"]?.jsonPrimitive?.contentOrNull) {
            "run-start" -> data?.get("runner_data")?.jsonObject?.get("stt_binary_handler_id")?.jsonPrimitive?.int
                ?.let { handlerId.complete(it) }
            "stt-vad-end" -> sttDone.complete(Unit)
            "stt-end" -> {
                sttDone.complete(Unit)
                val text = data?.get("stt_output")?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                text?.let(onHeard)
                _state.value = VoiceState.Thinking(text)
            }
            "intent-end" -> {
                val speech = data?.get("intent_output")?.jsonObject?.get("response")?.jsonObject
                    ?.get("speech")?.jsonObject?.get("plain")?.jsonObject?.get("speech")?.jsonPrimitive?.contentOrNull
                val heard = (_state.value as? VoiceState.Thinking)?.heard
                _state.value = VoiceState.Answer(heard, speech ?: "", null)
            }
            "tts-end" -> {
                val url = data?.get("tts_output")?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                (_state.value as? VoiceState.Answer)?.let { _state.value = it.copy(ttsUrl = url) }
            }
            "error" -> {
                sttDone.complete(Unit)
                val code = data?.get("code")?.jsonPrimitive?.contentOrNull
                _state.value = VoiceState.Failed(
                    if (code == "stt-no-text-recognized") "Nie usłyszałem – spróbuj jeszcze raz"
                    else data?.get("message")?.jsonPrimitive?.contentOrNull ?: "Błąd asystenta",
                )
                finished.complete(Unit)
            }
            "run-end" -> finished.complete(Unit)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun streamMicrophone(handlerId: Int, stop: CompletableDeferred<Unit>) =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val buffer = ShortArray(CHUNK / 2)
            val bytes = java.nio.ByteBuffer.allocate(CHUNK).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val started = System.currentTimeMillis()
            val mic = runCatching { app.wallglass.voice.MicInput() }.getOrNull()
            try {
                while (mic != null && isActive && !stop.isCompleted && System.currentTimeMillis() - started < MAX_RECORD_MS) {
                    if (!mic.read(buffer)) break
                    bytes.clear(); buffer.forEach { bytes.putShort(it) }
                    client.sendBinary(byteArrayOf(handlerId.toByte()) + bytes.array())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Mikrofon", e)
            } finally {
                mic?.close()
                client.sendBinary(byteArrayOf(handlerId.toByte())) // end of audio
            }
        }

    private companion object {
        const val TAG = "VoiceAssistant"
        const val SAMPLE_RATE = 16_000
        const val CHUNK = 2_048 // 64 ms of 16-bit mono audio
        const val MAX_RECORD_MS = 12_000L
    }
}
