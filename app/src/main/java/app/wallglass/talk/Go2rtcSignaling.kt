package app.wallglass.talk

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/** go2rtc's WebRTC signaling: POST the offer to `/api/webrtc?src=<stream>`, the answer comes back in the body. */
object Go2rtcSignaling {
    fun url(api: String, stream: String): String =
        api.trimEnd('/') + "/api/webrtc?src=" + URLEncoder.encode(stream, "UTF-8").replace("+", "%20")

    fun offer(sdp: String): String = buildJsonObject {
        put("type", JsonPrimitive("offer"))
        put("sdp", JsonPrimitive(sdp))
    }.toString()

    /** SDP of the answer (JSON or raw), or null when the body is an error message. */
    fun answer(body: String): String? {
        val text = body.trim()
        if (text.startsWith("{")) {
            return runCatching { Json.parseToJsonElement(text).jsonObject["sdp"]?.jsonPrimitive?.content }.getOrNull()
        }
        return if (text.startsWith("v=")) body else null
    }
}
