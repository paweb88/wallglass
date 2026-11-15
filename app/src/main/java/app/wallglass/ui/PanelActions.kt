package app.wallglass.ui

import app.wallglass.config.ServiceCall
import app.wallglass.config.TalkConfig
import app.wallglass.talk.TalkState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient

private val NO_TALK = MutableStateFlow<TalkState>(TalkState.Idle)

/** What screens may do with Home Assistant; implemented by PanelViewModel, faked in screenshot tests. */
interface PanelActions {
    val baseUrl: String?
    val http: OkHttpClient
    suspend fun accessToken(): String
    suspend fun streamUrl(entityId: String): String?
    fun service(domain: String, service: String, entityId: String)
    fun serviceWith(domain: String, service: String, entityId: String, extra: kotlinx.serialization.json.JsonObject?)
    fun run(call: ServiceCall)
    fun toggle(entityId: String)

    /** Talk-back to the door station (see [app.wallglass.talk.TalkBack]). */
    val talkState: StateFlow<TalkState> get() = NO_TALK
    fun startTalk(cfg: TalkConfig) {}
    fun stopTalk() {}
}
