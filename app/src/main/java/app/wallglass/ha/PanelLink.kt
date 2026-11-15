package app.wallglass.ha

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Status of this panel in the Wallglass integration. */
enum class LinkStatus { NO_INTEGRATION, PENDING, LINKED, FORBIDDEN }

/** A command pushed by HA (screen on/off, brightness, page). */
data class PanelCommand(val command: String, val data: JsonObject)

/**
 * Talks to the optional `wallglass` custom integration: registers the panel,
 * receives its config and commands, reports panel state back.
 */
class PanelLink(
    private val client: HaClient,
    private val deviceId: String,
    private val deviceName: String,
    private val model: String,
    private val version: String,
) {
    private val _status = MutableStateFlow(LinkStatus.NO_INTEGRATION)
    val status: StateFlow<LinkStatus> = _status.asStateFlow()

    private val _config = MutableStateFlow<JsonObject?>(null)
    /** Config chosen in HA (null = integration not used; app falls back to wallglass.json / discovery). */
    val config: StateFlow<JsonObject?> = _config.asStateFlow()

    private val _commands = MutableSharedFlow<PanelCommand>(extraBufferCapacity = 8)
    val commands: SharedFlow<PanelCommand> = _commands.asSharedFlow()

    private var job: Job? = null

    /** Call after every (re)connect; keeps re-registering until HA confirms the panel. */
    fun onConnected(scope: CoroutineScope) {
        job?.cancel()
        // reset so LINKED is a fresh transition after every reconnect (the app then re-reports its state)
        _status.value = LinkStatus.PENDING
        job = scope.launch {
            while (true) {
                val status = register()
                _status.value = status
                if (status == LinkStatus.LINKED) {
                    subscribe()
                    return@launch
                }
                if (status == LinkStatus.NO_INTEGRATION || status == LinkStatus.FORBIDDEN) return@launch
                delay(30_000) // waiting for the user to confirm the discovered panel in HA
            }
        }
    }

    private suspend fun register(): LinkStatus = try {
        val result = client.command(buildJsonObject {
            put("type", "wallglass/register")
            put("device_id", deviceId)
            put("name", deviceName)
            put("model", model)
            put("version", version)
        }) as? JsonObject
        when (result?.get("status")?.jsonPrimitive?.contentOrNull) {
            "configured" -> LinkStatus.LINKED
            "forbidden" -> LinkStatus.FORBIDDEN
            else -> LinkStatus.PENDING
        }
    } catch (e: HaCommandException) {
        Log.i(TAG, "Integracja Wallglass niedostępna: ${e.message}")
        LinkStatus.NO_INTEGRATION
    }

    private suspend fun subscribe() {
        try {
            client.subscribeRaw(buildJsonObject {
                put("type", "wallglass/subscribe")
                put("device_id", deviceId)
            }) { event ->
                when (event["type"]?.jsonPrimitive?.contentOrNull) {
                    "config" -> _config.value = event["config"] as? JsonObject
                    "command" -> event["command"]?.jsonPrimitive?.contentOrNull?.let {
                        _commands.tryEmit(PanelCommand(it, event))
                    }
                }
            }
        } catch (e: HaCommandException) {
            Log.w(TAG, "Subskrypcja panelu odrzucona: ${e.message}")
            _status.value = LinkStatus.PENDING
        }
    }

    /** Report screen/page state so the HA entities stay in sync (ignored when not linked). */
    suspend fun reportState(screenOn: Boolean, brightness: Int, page: String, quiet: Boolean) {
        if (_status.value != LinkStatus.LINKED) return
        try {
            client.command(buildJsonObject {
                put("type", "wallglass/state")
                put("device_id", deviceId)
                put("screen_on", screenOn)
                put("brightness", brightness.coerceIn(0, 255))
                put("page", page)
                put("quiet", quiet)
            })
        } catch (e: Exception) {
            Log.w(TAG, "Raport stanu nieudany: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "PanelLink"
    }
}
