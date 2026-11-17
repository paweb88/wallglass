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
    /** [turn] counts the turns of one conversation (0 = first question after waking). */
    data class Listening(val turn: Int = 0) : VoiceState
    data class Thinking(val heard: String?) : VoiceState
    data class Answer(val heard: String?, val answer: String, val ttsUrl: String?) : VoiceState
    data class Failed(val message: String) : VoiceState
    /** The conversation is over ("OK", "dzięki" or silence) – the screen can go back to sleep. */
    data object Closed : VoiceState
}

/**
 * Assist satellite with follow-up questions: records 16 kHz mono PCM, streams it to HA's
 * `assist_pipeline/run` (stt → intent → tts) and reports the transcript and answer.
 * HA's own VAD ends each recording when the user stops speaking. After an answer has been spoken the
 * UI calls [answerDone] and the assistant listens again in the same conversation, until the user says
 * a closing phrase ([ConversationEnd]) or stays silent.
 *
 * @param releaseMic frees the microphone held by the wake-word listener before recording starts.
 */
class VoiceAssistant(
    private val client: HaClient,
    private val scope: CoroutineScope,
    private val releaseMic: suspend () -> Unit = {},
) {

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var job: Job? = null
    private var conversationId: String? = null
    private var turn = 0

    /** Starts a new conversation. */
    fun start() {
        if (job?.isActive == true) return
        conversationId = null
        turn = 0
        job = scope.launch { runTurn() }
    }

    /** The answer has been spoken (or shown, when silent): listen for a follow-up. */
    fun answerDone() {
        if (job?.isActive == true || _state.value !is VoiceState.Answer) return
        if (turn + 1 >= MAX_TURNS) {
            _state.value = VoiceState.Closed
            return
        }
        turn += 1
        job = scope.launch { runTurn() }
    }

    fun cancel() {
        job?.cancel()
        _state.value = VoiceState.Idle
    }

    @SuppressLint("MissingPermission") // checked by the caller (RECORD_AUDIO)
    private suspend fun runTurn(retry: Boolean = false) {
        val followUp = turn > 0
        _state.value = VoiceState.Listening(turn)
        releaseMic()
        val run = Run(followUp)

        try {
            client.subscribeRaw(buildJsonObject {
                put("type", "assist_pipeline/run")
                put("start_stage", "stt")
                put("end_stage", "tts")
                putJsonObject("input") { put("sample_rate", SAMPLE_RATE) }
                conversationId?.let { put("conversation_id", it) }
            }) { event -> onEvent(event, run) }
        } catch (e: Exception) {
            _state.value = VoiceState.Failed("Asystent niedostępny: ${e.message}")
            return
        }

        val id = withTimeoutOrNull(5_000) { run.handlerId.await() }
        if (id == null) {
            _state.value = VoiceState.Failed("HA nie rozpoczął nasłuchu")
            return
        }
        streamMicrophone(id, run)
        withTimeoutOrNull(60_000) { run.finished.await() }
        Log.d(TAG, "Tura $turn zakończona: ${run.heard}")
        when {
            run.closed || (followUp && run.silent) -> _state.value = VoiceState.Closed
            // nothing understood right after waking: give the user one more chance
            run.silent && !retry -> runTurn(retry = true)
            run.silent -> _state.value = VoiceState.Failed("Nie usłyszałem – spróbuj jeszcze raz")
            _state.value is VoiceState.Listening || _state.value is VoiceState.Thinking ->
                _state.value = VoiceState.Failed("Brak odpowiedzi")
        }
    }

    /** Everything one pipeline run reports back. */
    private class Run(val followUp: Boolean) {
        val handlerId = CompletableDeferred<Int>()
        val speechStarted = CompletableDeferred<Unit>()
        val sttDone = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        var heard: String? = null
        /** The user said a closing phrase; later pipeline events are ignored. */
        var closed = false
        /** Nothing was recognised. */
        var silent = false
        /** Only sounds were recognised; later pipeline events are ignored. */
        var ignored = false
    }

    private fun onEvent(event: JsonObject, run: Run) {
        val data = event["data"] as? JsonObject
        if (run.closed || run.ignored) {
            if (event["type"]?.jsonPrimitive?.contentOrNull in END_EVENTS) run.finished.complete(Unit)
            return
        }
        when (event["type"]?.jsonPrimitive?.contentOrNull) {
            "run-start" -> data?.get("runner_data")?.jsonObject?.get("stt_binary_handler_id")?.jsonPrimitive?.int
                ?.let { run.handlerId.complete(it) }
            "stt-vad-start" -> run.speechStarted.complete(Unit)
            "stt-vad-end" -> run.sttDone.complete(Unit)
            "stt-end" -> {
                run.sttDone.complete(Unit)
                val text = data?.get("stt_output")?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                run.heard = text
                when {
                    text.isNullOrBlank() -> run.silent = true
                    // "*płacz*", "[muzyka]": a sound, not someone talking – drop the answer HA will still produce
                    NonSpeech.isNonSpeech(text) -> {
                        run.silent = true
                        run.ignored = true
                    }
                    // closing phrases only count as an answer to the assistant, never right after waking
                    run.followUp && ConversationEnd.isClosing(text) -> {
                        run.closed = true
                        _state.value = VoiceState.Closed
                    }
                    else -> _state.value = VoiceState.Thinking(text)
                }
            }
            "intent-end" -> {
                val output = data?.get("intent_output")?.jsonObject
                output?.get("conversation_id")?.jsonPrimitive?.contentOrNull?.let { conversationId = it }
                val speech = output?.get("response")?.jsonObject
                    ?.get("speech")?.jsonObject?.get("plain")?.jsonObject?.get("speech")?.jsonPrimitive?.contentOrNull
                val heard = (_state.value as? VoiceState.Thinking)?.heard
                _state.value = VoiceState.Answer(heard, speech ?: "", null)
            }
            "tts-end" -> {
                val url = data?.get("tts_output")?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                (_state.value as? VoiceState.Answer)?.let { _state.value = it.copy(ttsUrl = url) }
            }
            "error" -> {
                run.sttDone.complete(Unit)
                val code = data?.get("code")?.jsonPrimitive?.contentOrNull
                if (code == "stt-no-text-recognized") run.silent = true
                else _state.value = VoiceState.Failed(data?.get("message")?.jsonPrimitive?.contentOrNull ?: "Błąd asystenta")
                run.finished.complete(Unit)
            }
            "run-end" -> run.finished.complete(Unit)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun streamMicrophone(handlerId: Int, run: Run) =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val buffer = ShortArray(CHUNK / 2)
            val bytes = java.nio.ByteBuffer.allocate(CHUNK).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val started = System.currentTimeMillis()
            val mic = runCatching { app.wallglass.voice.MicInput() }.getOrNull()
            try {
                while (mic != null && isActive && !run.sttDone.isCompleted && System.currentTimeMillis() - started < MAX_RECORD_MS) {
                    // a follow-up nobody answers: stop early instead of recording silence
                    if (run.followUp && !run.speechStarted.isCompleted &&
                        System.currentTimeMillis() - started > FOLLOW_UP_WAIT_MS
                    ) {
                        run.silent = true
                        run.closed = true // whatever STT makes of the silence is ignored
                        _state.value = VoiceState.Closed
                        break
                    }
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
        /** How long a follow-up waits for the user to start speaking. */
        const val FOLLOW_UP_WAIT_MS = 6_000L
        /** Questions in one conversation before the assistant closes on its own. */
        const val MAX_TURNS = 6
        val END_EVENTS = setOf("run-end", "error")
    }
}
