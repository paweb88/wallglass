package app.wallglass.talk

import android.content.Context
import android.util.Log
import app.wallglass.config.TalkConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

sealed interface TalkState {
    data object Idle : TalkState
    data object Connecting : TalkState
    data object Talking : TalkState
    data class Failed(val reason: String) : TalkState
}

/**
 * One talk-back session at a time, through go2rtc over WebRTC. Ends on [stop], when the connection
 * drops, or after [MAX_TALK_MS], so a forgotten call never keeps the microphone open.
 */
class TalkBack(
    private val context: Context,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    /** Runs before the microphone is opened, e.g. to release it from the wake-word listener. */
    private val beforeStart: suspend () -> Unit,
) {
    private val _state = MutableStateFlow<TalkState>(TalkState.Idle)
    val state: StateFlow<TalkState> = _state.asStateFlow()
    private var job: Job? = null

    fun start(cfg: TalkConfig) {
        if (job?.isActive == true) return
        _state.value = TalkState.Connecting
        job = scope.launch(Dispatchers.Main) { session(cfg) }
    }

    fun stop() {
        job?.cancel()
    }

    private suspend fun session(cfg: TalkConfig) {
        val talk = Go2rtcTalk(context, http, cfg.url, cfg.stream)
        var failure: String? = null
        try {
            beforeStart()
            talk.open()
            val up = withTimeoutOrNull(CONNECT_MS) { talk.connected.filterNotNull().first() }
            if (up != true) throw java.io.IOException("WebRTC się nie połączył")
            _state.value = TalkState.Talking
            // talk until the connection drops; MAX_TALK_MS caps it
            val dropped = withTimeoutOrNull(MAX_TALK_MS) { talk.connected.first { it == false } }
            if (dropped != null) failure = "rozłączono"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Rozmowa przerwana", e)
            failure = if (e.message?.startsWith("go2rtc") == true) "go2rtc odrzucił" else "brak połączenia"
        } finally {
            withContext(NonCancellable) { talk.close() }
            _state.value = failure?.let { TalkState.Failed(it) } ?: TalkState.Idle
        }
    }

    private companion object {
        const val TAG = "TalkBack"
        const val CONNECT_MS = 8_000L
        const val MAX_TALK_MS = 3 * 60_000L
    }
}
