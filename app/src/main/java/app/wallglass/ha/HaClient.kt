package app.wallglass.ha

import android.util.Log
import app.wallglass.auth.AuthApi
import app.wallglass.auth.AuthException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

enum class ConnectionState { CONNECTING, CONNECTED, DISCONNECTED, AUTH_FAILED }

class HaCommandException(message: String) : Exception(message)

/** Live WebSocket connection to Home Assistant with automatic reconnect. */
class HaClient(
    private val http: OkHttpClient,
    private val auth: AuthApi,
    private val baseUrl: String,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonElement?>>()
    private val subscriptions = ConcurrentHashMap<Int, (JsonObject) -> Unit>()
    private var socket: WebSocket? = null
    private var loopJob: Job? = null
    private var authenticated = CompletableDeferred<Unit>()
    private val forecastTargets = mutableSetOf<String>()

    private val _states = MutableStateFlow<Map<String, Entity>>(emptyMap())
    val states: StateFlow<Map<String, Entity>> = _states.asStateFlow()

    private val _connection = MutableStateFlow(ConnectionState.CONNECTING)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _notifications = MutableStateFlow<Map<String, PanelNotification>>(emptyMap())
    /** HA persistent notifications, keyed by notification_id. */
    val notifications: StateFlow<Map<String, PanelNotification>> = _notifications.asStateFlow()

    private val _forecasts = MutableStateFlow<Map<String, List<ForecastDay>>>(emptyMap())
    val forecasts: StateFlow<Map<String, List<ForecastDay>>> = _forecasts.asStateFlow()

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch { connectLoop() }
    }

    fun stop() {
        loopJob?.cancel()
        socket?.close(1000, "stop")
    }

    private suspend fun connectLoop() {
        var backoff = 1_000L
        while (true) {
            val closed = CompletableDeferred<Unit>()
            try {
                _connection.value = ConnectionState.CONNECTING
                val token = auth.validAccessToken()
                authenticated = CompletableDeferred()
                socket = http.newWebSocket(
                    Request.Builder().url(wsUrl()).build(),
                    Listener(token, closed),
                )
                withTimeout(15_000) { authenticated.await() }
                _connection.value = ConnectionState.CONNECTED
                backoff = 1_000L
                afterConnect()
                closed.await()
            } catch (e: AuthException) {
                if (e.permanent) {
                    Log.e(TAG, "Logowanie odrzucone przez HA", e)
                    _connection.value = ConnectionState.AUTH_FAILED
                    return
                }
                Log.w(TAG, "Odświeżenie tokenu nieudane (spróbuję ponownie): ${e.message}")
                socket?.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Połączenie z HA przerwane: ${e.message}")
                socket?.cancel()
            }
            _connection.value = ConnectionState.DISCONNECTED
            failPending("Rozłączono")
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(30_000L)
        }
    }

    private suspend fun afterConnect() {
        val states = command(buildJsonObject { put("type", "get_states") })
        val map = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            (states as? JsonArray).orEmpty()
                .mapNotNull { Entity.fromJson(it.jsonObject) }
                .associateBy { it.entityId }
        }
        _states.value = map
        subscribe(buildJsonObject {
            put("type", "subscribe_events")
            put("event_type", "state_changed")
        }) { event -> onStateChanged(event) }
        try {
            subscribe(buildJsonObject { put("type", "persistent_notification/subscribe") }) { onNotifications(it) }
        } catch (e: HaCommandException) {
            Log.w(TAG, "Powiadomienia niedostępne: ${e.message}")
        }
        forecastTargets.toList().forEach { subscribeForecast(it) }
        _connectedEvents.value += 1
    }

    private fun onNotifications(event: JsonObject) {
        val type = event["type"]?.jsonPrimitive?.contentOrNull
        val items = event["notifications"]?.jsonObject.orEmpty().mapNotNull { (id, value) ->
            val o = value as? JsonObject ?: return@mapNotNull null
            id to PanelNotification(
                id = id,
                title = o["title"]?.jsonPrimitive?.contentOrNull,
                message = o["message"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull,
            )
        }.toMap()
        _notifications.update { current ->
            when (type) {
                "current" -> items
                "removed" -> current - items.keys
                else -> current + items
            }
        }
    }

    /** Lists items of a to-do entity. */
    suspend fun todoItems(entityId: String): List<TodoItem> {
        val result = command(buildJsonObject {
            put("type", "todo/item/list")
            put("entity_id", entityId)
        }) as? JsonObject ?: return emptyList()
        return result["items"]?.jsonArray.orEmpty().mapNotNull { el ->
            val o = el.jsonObject
            val uid = o["uid"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            TodoItem(
                uid = uid,
                summary = o["summary"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                done = o["status"]?.jsonPrimitive?.contentOrNull == "completed",
                due = o["due"]?.jsonPrimitive?.contentOrNull,
                description = o["description"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }

    private fun onStateChanged(event: JsonObject) {
        val data = event["data"]?.jsonObject ?: return
        val id = data["entity_id"]?.jsonPrimitive?.contentOrNull ?: return
        val newState = data["new_state"] as? JsonObject
        _states.update { current ->
            if (newState == null) current - id
            else Entity.fromJson(newState)?.let { current + (id to it) } ?: current
        }
    }

    /** Subscribes to the daily forecast of a weather entity (kept across reconnects). */
    suspend fun watchForecast(entityId: String) {
        if (!forecastTargets.add(entityId)) return
        if (_connection.value == ConnectionState.CONNECTED) subscribeForecast(entityId)
    }

    private suspend fun subscribeForecast(entityId: String) {
        try {
            subscribe(buildJsonObject {
                put("type", "weather/subscribe_forecast")
                put("entity_id", entityId)
                put("forecast_type", "daily")
            }) { event ->
                val days = event["forecast"]?.jsonArray.orEmpty().map { item ->
                    val o = item.jsonObject
                    ForecastDay(
                        datetime = o["datetime"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        condition = o["condition"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        tempHigh = o["temperature"]?.jsonPrimitive?.doubleOrNull,
                        tempLow = o["templow"]?.jsonPrimitive?.doubleOrNull,
                    )
                }
                _forecasts.update { it + (entityId to days) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Prognoza $entityId niedostępna: ${e.message}")
        }
    }

    suspend fun callService(domain: String, service: String, data: JsonObject) {
        command(buildJsonObject {
            put("type", "call_service")
            put("domain", domain)
            put("service", service)
            put("service_data", data)
        })
    }

    /** Asks HA for an HLS stream URL of a camera; returns an absolute URL. */
    suspend fun cameraStreamUrl(entityId: String): String {
        val result = command(buildJsonObject {
            put("type", "camera/stream")
            put("entity_id", entityId)
        }) as? JsonObject ?: throw HaCommandException("Brak strumienia")
        val url = result["url"]?.jsonPrimitive?.contentOrNull ?: throw HaCommandException("Brak adresu strumienia")
        return if (url.startsWith("http")) url else baseUrl.trimEnd('/') + url
    }

    suspend fun command(message: JsonObject): JsonElement? {
        authenticated.await()
        val id = nextId.getAndIncrement()
        val deferred = CompletableDeferred<JsonElement?>()
        pending[id] = deferred
        send(JsonObject(message + ("id" to JsonPrimitive(id))))
        return withTimeout(20_000) { deferred.await() }
    }

    /** Subscription for other components (e.g. the Wallglass integration channel). */
    suspend fun subscribeRaw(message: JsonObject, onEvent: (JsonObject) -> Unit) = subscribe(message, onEvent)

    private val _connectedEvents = MutableStateFlow(0)
    /** Increments after every successful (re)connect, once states are loaded. */
    val connectedEvents: StateFlow<Int> = _connectedEvents.asStateFlow()

    private suspend fun subscribe(message: JsonObject, onEvent: (JsonObject) -> Unit) {
        authenticated.await()
        val id = nextId.getAndIncrement()
        val deferred = CompletableDeferred<JsonElement?>()
        pending[id] = deferred
        subscriptions[id] = onEvent
        send(JsonObject(message + ("id" to JsonPrimitive(id))))
        withTimeout(20_000) { deferred.await() }
    }

    /** Binary frame (e.g. audio for an Assist pipeline). */
    fun sendBinary(bytes: ByteArray) {
        socket?.send(okio.ByteString.of(*bytes))
    }

    private fun send(obj: JsonObject) {
        val ok = socket?.send(obj.toString()) ?: false
        if (!ok) throw HaCommandException("Brak połączenia z Home Assistantem")
    }

    private fun failPending(reason: String) {
        pending.values.forEach { it.completeExceptionally(HaCommandException(reason)) }
        pending.clear()
        subscriptions.clear()
    }

    private fun wsUrl(): String {
        val base = baseUrl.trimEnd('/')
        return base.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/api/websocket"
    }

    private inner class Listener(
        private val token: String,
        private val closed: CompletableDeferred<Unit>,
    ) : WebSocketListener() {

        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            when (msg["type"]?.jsonPrimitive?.contentOrNull) {
                "auth_required" -> webSocket.send(buildJsonObject {
                    put("type", "auth")
                    put("access_token", token)
                }.toString())
                "auth_ok" -> authenticated.complete(Unit)
                "auth_invalid" -> authenticated.completeExceptionally(AuthException("Token odrzucony", permanent = true))
                "result" -> onResult(msg)
                "event" -> {
                    val id = msg["id"]?.jsonPrimitive?.int ?: return
                    val event = msg["event"] as? JsonObject ?: return
                    subscriptions[id]?.invoke(event)
                }
            }
        }

        private fun onResult(msg: JsonObject) {
            val id = msg["id"]?.jsonPrimitive?.int ?: return
            val deferred = pending.remove(id) ?: return
            val success = msg["success"]?.jsonPrimitive?.contentOrNull == "true"
            if (success) {
                deferred.complete(msg["result"])
            } else {
                subscriptions.remove(id)
                val error = msg["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                deferred.completeExceptionally(HaCommandException(error ?: "Polecenie odrzucone"))
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
            closed.complete(Unit)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            closed.complete(Unit)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "WebSocket: ${t.message}")
            authenticated.completeExceptionally(t)
            closed.complete(Unit)
        }
    }

    private companion object {
        const val TAG = "HaClient"
    }
}
