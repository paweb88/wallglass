package app.wallglass

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.wallglass.auth.AuthApi
import app.wallglass.auth.TokenStore
import app.wallglass.config.ConfigLoader
import app.wallglass.config.Discovery
import app.wallglass.config.LocalSettings
import app.wallglass.config.PanelConfig
import app.wallglass.config.PanelSettings
import app.wallglass.config.withSettings
import app.wallglass.config.ResolvedPanel
import app.wallglass.config.ServiceCall
import app.wallglass.ha.ConnectionState
import app.wallglass.ha.Entity
import app.wallglass.ha.ForecastDay
import app.wallglass.ha.HaClient
import app.wallglass.ha.LinkStatus
import app.wallglass.ha.PanelCommand
import app.wallglass.ha.PanelLink
import app.wallglass.ha.VoiceAssistant
import app.wallglass.ha.VoiceState
import app.wallglass.voice.WakeWordListener
import app.wallglass.talk.TalkBack
import app.wallglass.talk.TalkState
import app.wallglass.config.TalkConfig
import app.wallglass.ui.PanelActions
import app.wallglass.ha.HistoryApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import app.wallglass.ha.PanelNotification
import app.wallglass.ha.TodoItem
import app.wallglass.ha.dueReminders
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Short feedback shown after an action. */
data class Toast(val text: String, val ok: Boolean)

fun serviceLabel(service: String): String = when (service) {
    "turn_on" -> "uruchomiono"
    "turn_off" -> "wyłączono"
    "toggle" -> "przełączono"
    "open_cover" -> "otwieram"
    "close_cover" -> "zamykam"
    "stop_cover" -> "zatrzymano"
    "alarm_disarm" -> "rozbrojono"
    "alarm_arm_home" -> "uzbrojono (w domu)"
    "alarm_arm_away" -> "uzbrojono (wyjście)"
    else -> "wykonano"
}

enum class Page { START, GATE, CAMERAS, SECURITY, COVERS, HEATING, ROOMS, TODO, SETTINGS, WASTE, ASSISTANT, MUSIC }

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PanelViewModel(app: Application) : AndroidViewModel(app), PanelActions {

    override val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    val tokenStore = TokenStore(app)
    val auth = AuthApi(http, tokenStore)
    private val configLoader = ConfigLoader(http)

    private val _client = MutableStateFlow<HaClient?>(null)
    val client: StateFlow<HaClient?> = _client.asStateFlow()

    private val _config = MutableStateFlow(PanelConfig())
    private val _page = MutableStateFlow(Page.START)
    val page: StateFlow<Page> = _page.asStateFlow()

    private val _message = MutableStateFlow<Toast?>(null)
    val message: StateFlow<Toast?> = _message.asStateFlow()

    val needsLogin: Boolean get() = tokenStore.baseUrl == null || tokenStore.load() == null
    override val baseUrl: String? get() = tokenStore.baseUrl

    override suspend fun accessToken(): String = auth.validAccessToken()

    val states: StateFlow<Map<String, Entity>> = _client
        .flatMapLatest { it?.states ?: flowOf(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val connection: StateFlow<ConnectionState> = _client
        .flatMapLatest { it?.connection ?: flowOf(ConnectionState.DISCONNECTED) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionState.CONNECTING)

    val forecasts: StateFlow<Map<String, List<ForecastDay>>> = _client
        .flatMapLatest { it?.forecasts ?: flowOf(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val localSettings = LocalSettings(app)
    val settings: StateFlow<PanelSettings> = localSettings.settings

    /** Config pushed by the Wallglass integration; wins over wallglass.json when present. */
    private val _linkConfig = MutableStateFlow<PanelConfig?>(null)
    private val _link = MutableStateFlow<PanelLink?>(null)
    val linkStatus: StateFlow<LinkStatus> = _link
        .flatMapLatest { it?.status ?: flowOf(LinkStatus.NO_INTEGRATION) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LinkStatus.NO_INTEGRATION)
    val commands: kotlinx.coroutines.flow.Flow<PanelCommand> = _link.flatMapLatest { it?.commands ?: kotlinx.coroutines.flow.emptyFlow() }

    val panel: StateFlow<ResolvedPanel> = combine(_config, _linkConfig, localSettings.settings, states) { file, linked, local, st ->
        Discovery.resolve((linked ?: file).withSettings(local), st)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Discovery.resolve(PanelConfig(), emptyMap()))


    private var session: kotlinx.coroutines.CoroutineScope? = null

    private val _voice = MutableStateFlow<VoiceAssistant?>(null)
    val voiceState: StateFlow<VoiceState> = _voice
        .flatMapLatest { it?.state ?: flowOf(VoiceState.Idle) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VoiceState.Idle)

    fun startVoice() = _voice.value?.start()

    private val _wakeRequests = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits when "Hej Dom" was heard – the UI wakes the screen. */
    val wakeRequests: kotlinx.coroutines.flow.SharedFlow<Unit> = _wakeRequests

    private val wakeListener = WakeWordListener(app, viewModelScope) {
        _wakeRequests.tryEmit(Unit)
        go(Page.ASSISTANT)
    }

    private val talkBack = TalkBack(app, http, viewModelScope) { wakeListener.stopAndWait() }
    override val talkState: StateFlow<TalkState> = talkBack.state
    override fun startTalk(cfg: TalkConfig) = talkBack.start(cfg)
    override fun stopTalk() = talkBack.stop()

    init {
        // listen for the wake word only while connected, enabled, and the microphone is not in use
        viewModelScope.launch {
            combine(page, localSettings.settings, connection, talkBack.state) { p, s, c, t ->
                p != Page.ASSISTANT && s.wakeWord && c == ConnectionState.CONNECTED &&
                    (t is TalkState.Idle || t is TalkState.Failed)
            }.distinctUntilChanged().collect { on -> if (on) wakeListener.start() else wakeListener.stop() }
        }
    }

    fun cancelVoice() = _voice.value?.cancel()

    /** The answer was spoken: listen for a follow-up question. */
    fun voiceAnswerDone() = _voice.value?.answerDone()

    fun connect() {
        val url = tokenStore.baseUrl ?: return
        _client.value?.stop()
        session?.cancel()
        val scope = kotlinx.coroutines.CoroutineScope(viewModelScope.coroutineContext + kotlinx.coroutines.SupervisorJob(viewModelScope.coroutineContext[kotlinx.coroutines.Job]))
        session = scope
        val ha = HaClient(http, auth, url, scope)
        _client.value = ha
        ha.start()
        val link = PanelLink(ha, localSettings.deviceId, deviceName(), android.os.Build.MODEL, app.wallglass.BuildConfig.VERSION_NAME)
        _link.value = link
        _voice.value = VoiceAssistant(ha, scope) { wakeListener.stopAndWait() }
        scope.launch { ha.connectedEvents.collect { n -> if (n > 0) link.onConnected(scope) } }
        scope.launch {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
            link.config.collect { obj ->
                _linkConfig.value = obj?.let {
                    runCatching { json.decodeFromJsonElement(PanelConfig.serializer(), it) }
                        .onFailure { e -> Log.e("PanelViewModel", "Błędna konfiguracja z integracji", e) }
                        .getOrNull()
                }
            }
        }
        scope.launch {
            val cfg = configLoader.load(url)
            _config.value = cfg
            Discovery.resolve(cfg, ha.states.value).weather?.let { ha.watchForecast(it) }
        }
        scope.launch {
            panel.collect { p -> p.weather?.let { ha.watchForecast(it) } }
        }
        scope.launch {
            val api = HistoryApi(http, auth, url)
            combine(panel.map { p -> p.rooms.map { it.temperature } }.distinctUntilChanged(), ha.connection) { ids, c -> ids to c }
                .collectLatest { (ids, c) ->
                    if (ids.isEmpty() || c != ConnectionState.CONNECTED) return@collectLatest
                    while (true) {
                        api.series(ids).takeIf { it.isNotEmpty() }?.let { _history.value = it }
                        delay(if (_history.value.keys.containsAll(ids)) 600_000 else 30_000)
                    }
                }
        }
        scope.launch {
            // the to-do entity's state is its open-item count; refetch items whenever it changes
            combine(panel, ha.states) { p, st -> p.todo?.let { st[it]?.state } to p.todo }
                .distinctUntilChanged()
                .collect { (_, entity) -> if (entity != null) refreshTodo() }
        }
        scope.launch {
            // a changed due time does not change the open-item count, so also refetch now and then
            while (true) {
                delay(5 * 60_000L)
                refreshTodo()
            }
        }
    }

    fun logout() {
        _client.value?.stop()
        _client.value = null
        session?.cancel()
        session = null
        _link.value = null
        _linkConfig.value = null
        _history.value = emptyMap()
        _todo.value = emptyList()
        tokenStore.clear()
    }

    fun go(page: Page) {
        _page.value = page
    }

    fun dismissMessage() {
        _message.value = null
    }

    override fun toggle(entityId: String) = service(entityId.substringBefore('.'), "toggle", entityId)

    override fun run(call: ServiceCall) {
        val (domain, service) = call.service.split('.', limit = 2).let { it[0] to it.getOrElse(1) { "turn_on" } }
        service(domain, service, call.entityId)
    }

    override fun service(domain: String, service: String, entityId: String) = serviceWith(domain, service, entityId, null)

    override fun serviceWith(domain: String, service: String, entityId: String, extra: JsonObject?) {
        val ha = _client.value ?: return
        viewModelScope.launch {
            try {
                val data = buildJsonObject {
                    put("entity_id", entityId)
                    extra?.forEach { (k, v) -> put(k, v) }
                }
                ha.callService(domain, service, data)
                val name = states.value[entityId]?.name ?: entityId
                _message.value = Toast("$name: ${serviceLabel(service)}", ok = true)
            } catch (e: Exception) {
                Log.e("PanelViewModel", "Usługa $domain.$service dla $entityId nie powiodła się", e)
                _message.value = Toast("Nie udało się: ${e.message}", ok = false)
            }
        }
    }

    private val snoozedUntil = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val clock = MutableStateFlow(System.currentTimeMillis())

    private val _todo = MutableStateFlow<List<TodoItem>>(emptyList())
    val todo: StateFlow<List<TodoItem>> = _todo.asStateFlow()

    /** Notifications to show now: HA ones matching the prefix and not snoozed, plus due to-do reminders. */
    val notifications: StateFlow<List<PanelNotification>> = combine(
        _client.flatMapLatest { it?.notifications ?: flowOf(emptyMap()) },
        _config, snoozedUntil, clock, _todo,
    ) { all, cfg, snoozed, now, todoItems ->
        val fromHa = all.values
            .filter { cfg.notificationPrefix == "*" || it.id.startsWith(cfg.notificationPrefix) }
            .filter { (snoozed[it.id] ?: 0L) <= now }
        val reminders = dueReminders(todoItems, LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault()))
        (fromHa + reminders).sortedByDescending { it.createdAt }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun tick() {
        clock.value = System.currentTimeMillis()
    }

    /** "Done": a reminder completes its to-do item, anything else is dismissed in HA. */
    fun completeNotification(notification: PanelNotification) {
        val uid = notification.todoUid
        if (uid == null) {
            callRaw("persistent_notification", "dismiss", buildJsonObject { put("notification_id", notification.id) })
            return
        }
        _todo.value.firstOrNull { it.uid == uid }?.let { setTodoDone(it, true) }
    }

    /** "Remind later": a reminder moves its due time in HA (all panels), anything else hides here only. */
    fun snoozeNotification(notification: PanelNotification, minutes: Long) {
        val uid = notification.todoUid
        if (uid == null) {
            snoozedUntil.value = snoozedUntil.value + (notification.id to System.currentTimeMillis() + minutes * 60_000)
            return
        }
        val entity = panel.value.todo ?: return
        val later = LocalDateTime.now().plusMinutes(minutes).withSecond(0).withNano(0)
        val offset = later.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString()
        _todo.value = _todo.value.map { if (it.uid == uid) it.copy(due = offset) else it }
        callRaw("todo", "update_item", buildJsonObject {
            put("entity_id", entity)
            put("item", uid)
            put("due_datetime", later.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
        })
    }

    private val _history = MutableStateFlow<Map<String, List<Float>>>(emptyMap())
    /** 24 h temperature series per room sensor, refreshed every 10 minutes. */
    val history: StateFlow<Map<String, List<Float>>> = _history.asStateFlow()

    fun refreshTodo() {
        val ha = _client.value ?: return
        val entity = panel.value.todo ?: return
        viewModelScope.launch {
            try {
                _todo.value = ha.todoItems(entity)
            } catch (e: Exception) {
                Log.w("PanelViewModel", "Lista zadań $entity: ${e.message}")
            }
        }
    }

    fun setTodoDone(item: TodoItem, done: Boolean) {
        val entity = panel.value.todo ?: return
        _todo.value = _todo.value.map { if (it.uid == item.uid) it.copy(done = done) else it }
        callRaw("todo", "update_item", buildJsonObject {
            put("entity_id", entity)
            put("item", item.uid)
            put("status", if (done) "completed" else "needs_action")
        })
    }

    private fun callRaw(domain: String, service: String, data: JsonObject) {
        val ha = _client.value ?: return
        viewModelScope.launch {
            try {
                ha.callService(domain, service, data)
            } catch (e: Exception) {
                Log.e("PanelViewModel", "Usługa $domain.$service nie powiodła się", e)
                _message.value = Toast("Nie udało się: ${e.message}", ok = false)
            }
        }
    }

    fun reportState(screenOn: Boolean, brightness: Int, page: Page, quiet: Boolean) {
        val link = _link.value ?: return
        session?.launch { link.reportState(screenOn, brightness, page.name.lowercase(), quiet) }
    }

    private fun deviceName(): String =
        "Panel ${android.os.Build.MODEL}".takeIf { android.os.Build.MODEL.isNotBlank() } ?: "Panel"

    override suspend fun streamUrl(entityId: String): String? = try {
        _client.value?.cameraStreamUrl(entityId)
    } catch (e: Exception) {
        Log.w("PanelViewModel", "Strumień $entityId: ${e.message}")
        null
    }

    // last in the class body: connect() needs every property above to be initialised
    init {
        if (!needsLogin) connect()
    }

    override fun onCleared() {
        _client.value?.stop()
    }
}
