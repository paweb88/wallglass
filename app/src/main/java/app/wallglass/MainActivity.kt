package app.wallglass

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wallglass.ha.VoiceState
import app.wallglass.ha.ConnectionState
import app.wallglass.ui.CamerasScreen
import app.wallglass.ui.CoversScreen
import app.wallglass.ui.GateScreen
import app.wallglass.ui.HeatingScreen
import app.wallglass.ui.RoomsScreen
import app.wallglass.ui.Screensaver
import app.wallglass.ui.SecurityScreen
import app.wallglass.ui.SetupScreen
import app.wallglass.ui.StartScreen
import app.wallglass.ui.NotificationCard
import app.wallglass.ui.TodoScreen
import app.wallglass.ui.WasteScreen
import app.wallglass.ui.Swipe
import app.wallglass.ui.swipes
import app.wallglass.ui.AssistantScreen
import app.wallglass.ui.MusicScreen
import app.wallglass.ui.wasteDays
import app.wallglass.ui.SettingsScreen
import app.wallglass.ui.SettingsActions
import app.wallglass.ui.allTiles
import app.wallglass.ui.WallpaperImage
import app.wallglass.ui.Txt
import app.wallglass.ui.glass
import app.wallglass.ui.tap
import app.wallglass.ui.wallpaperBrush
import app.wallglass.ui.PanelThemes
import app.wallglass.ui.ThemeState
import androidx.compose.runtime.SideEffect
import kotlinx.coroutines.delay
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {

    private val vm: PanelViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1)
        }
        hideSystemBars()
        setContent {
            var loggedIn by remember { mutableStateOf(!vm.needsLogin) }
            if (!loggedIn) {
                SetupScreen(vm.auth, vm.baseUrl) {
                    vm.connect()
                    loggedIn = true
                }
            } else {
                PanelRoot(vm, onAuthFailed = { vm.logout(); loggedIn = false }, setBrightness = ::setBrightness)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    /** null = follow the system setting. */
    private fun setBrightness(level: Float?) {
        val attrs = window.attributes
        attrs.screenBrightness = level ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = attrs
    }
}

@Composable
private fun PanelRoot(vm: PanelViewModel, onAuthFailed: () -> Unit, setBrightness: (Float?) -> Unit) {
    val states by vm.states.collectAsStateWithLifecycle()
    val panel by vm.panel.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    val forecasts by vm.forecasts.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val todo by vm.todo.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val voice by vm.voiceState.collectAsStateWithLifecycle()

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val cfg = panel.config
    val night = now.hour >= cfg.night.from || now.hour < cfg.night.to
    // start on the dark clock at night so a restart never lights up the room
    var sleeping by remember { mutableStateOf(night) }
    var manualBrightness by remember { mutableStateOf<Float?>(null) }

    val saverSeconds by rememberUpdatedState(cfg.screensaverSeconds)
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            vm.tick()
            val idle = System.currentTimeMillis() - lastTouch
            if (!sleeping && saverSeconds > 0 && idle > saverSeconds * 1000L) {
                sleeping = true
                vm.go(Page.START)
            }
            delay(5_000)
        }
    }
    LaunchedEffect(connection) { if (connection == ConnectionState.AUTH_FAILED) onAuthFailed() }
    val level: Float? = when {
        night && sleeping -> cfg.night.brightness
        night -> minOf(cfg.night.awakeBrightness, manualBrightness ?: 1f)
        // by day: full brightness unless HA dimmed the screen; never the (often very low) system setting
        else -> manualBrightness ?: settings.dayBrightness ?: 1f
    }
    LaunchedEffect(level) { setBrightness(level) }
    val theme = PanelThemes.effective(
        PanelThemes.byId(settings.theme), night,
        darkPage = page == Page.GATE || page == Page.CAMERAS || page == Page.ASSISTANT,
    )
    SideEffect { ThemeState.current = theme }
    val linkStatus by vm.linkStatus.collectAsStateWithLifecycle()
    LaunchedEffect(sleeping, page, level, linkStatus, settings.quiet) {
        vm.reportState(!sleeping, ((level ?: 1f) * 255).toInt(), page, settings.quiet)
    }
    LaunchedEffect(Unit) {
        vm.wakeRequests.collect {
            sleeping = false
            lastTouch = System.currentTimeMillis()
        }
    }
    LaunchedEffect(Unit) {
        vm.commands.collect { cmd ->
            when (cmd.command) {
                "screen" -> {
                    val on = cmd.data["on"]?.jsonPrimitive?.booleanOrNull ?: true
                    cmd.data["brightness"]?.jsonPrimitive?.intOrNull?.let { manualBrightness = (it / 255f).coerceIn(0.01f, 1f) }
                    sleeping = !on
                    if (on) lastTouch = System.currentTimeMillis()
                }
                "quiet" -> {
                    val on = cmd.data["on"]?.jsonPrimitive?.booleanOrNull ?: true
                    vm.localSettings.update { it.copy(quiet = on) }
                }
                "page" -> {
                    val target = cmd.data["page"]?.jsonPrimitive?.contentOrNull
                    Page.entries.firstOrNull { it.name.equals(target, ignoreCase = true) }?.let {
                        sleeping = false
                        lastTouch = System.currentTimeMillis()
                        vm.go(it)
                    }
                }
            }
        }
    }

    val ringing = panel.doorbell?.ringSensor?.let { states[it]?.isOn } == true
    val muted = panel.doorbell?.mute?.let { states[it]?.isOn } == true
    LaunchedEffect(ringing) {
        if (!ringing) return@LaunchedEffect
        // muted doorbell never wakes the panel; at night it wakes dimmed (see brightness above)
        if (muted && sleeping) return@LaunchedEffect
        sleeping = false
        lastTouch = System.currentTimeMillis()
        vm.go(Page.GATE)
    }
    // a new notification wakes the panel (but never at night)
    val newestId = notifications.firstOrNull()?.id
    LaunchedEffect(newestId) {
        if (newestId != null && !night) {
            sleeping = false
            lastTouch = System.currentTimeMillis()
        }
    }
    LaunchedEffect(message) { if (message != null) { delay(if (message?.ok == true) 2_000 else 5_000); vm.dismissMessage() } }
    BackHandler(enabled = page != Page.START) { vm.go(Page.START) }
    val missingModule = (page == Page.GATE && panel.doorbell == null) || (page == Page.HEATING && panel.heating == null)
    LaunchedEffect(missingModule) { if (missingModule) vm.go(Page.START) }

    Box(
        Modifier.fillMaxSize()
            .background(theme.background ?: wallpaperBrush(cfg.wallpaper, now.hour))
            .swipes { dir ->
                if (sleeping) return@swipes
                if (page != Page.START) {
                    if (dir == Swipe.RIGHT) vm.go(Page.START) // swipe right = back
                    return@swipes
                }
                val target = when (dir) {
                    Swipe.UP -> cfg.gestures.up
                    Swipe.DOWN -> cfg.gestures.down
                    Swipe.LEFT -> cfg.gestures.left
                    Swipe.RIGHT -> cfg.gestures.right
                }
                Page.entries.firstOrNull { it.name.equals(target, ignoreCase = true) }?.let(vm::go)
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastTouch = System.currentTimeMillis()
                    }
                }
            },
    ) {
        val wp = cfg.wallpaper
        if (theme.background == null && wp.mode == "image" && wp.image != null && vm.baseUrl != null) {
            WallpaperImage(vm.baseUrl!!, wp.image, vm.http, Modifier.fillMaxSize())
        }
        if (sleeping) {
            val outside = panel.weather?.let { states[it]?.attrDouble("temperature") }
            val inside = panel.rooms.firstOrNull()?.let { states[it.temperature]?.numericState }
            Screensaver(now, night, outside, inside) { sleeping = false; lastTouch = System.currentTimeMillis() }
            return@Box
        }
        val back = { vm.go(Page.START) }
        when (page) {
            Page.START -> StartScreen(
                now, panel, states, panel.weather?.let { forecasts[it] }.orEmpty(), settings.hiddenModules,
                todo = todo,
                onOpen = vm::go,
                quiet = settings.quiet,
                onToggleQuiet = { vm.localSettings.update { it.copy(quiet = !it.quiet) } },
            )
            Page.GATE -> panel.doorbell?.let { GateScreen(vm, it, states, back) }
            Page.CAMERAS -> CamerasScreen(vm, panel.cameras, states, back)
            Page.SECURITY -> SecurityScreen(vm, panel.alarms, panel.openings, states, back)
            Page.COVERS -> CoversScreen(vm, panel.covers, states, back)
            Page.HEATING -> panel.heating?.let { HeatingScreen(vm, it, states, back) }
            Page.ROOMS -> RoomsScreen(panel.rooms, states, history, back)
            Page.TODO -> TodoScreen(todo, vm::setTodoDone, back)
            Page.WASTE -> WasteScreen(wasteDays(panel.waste?.let { states[it] }), now.toLocalDate(), back)
            Page.MUSIC -> MusicScreen(vm, panel.mediaPlayers, states, back)
            Page.ASSISTANT -> {
                LaunchedEffect(Unit) { vm.startVoice() }
                androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.cancelVoice() } }
                // "OK" / "dzięki" or silence ends the conversation: back to the start screen and to sleep
                LaunchedEffect(voice) {
                    if (voice is VoiceState.Closed) {
                        vm.go(Page.START)
                        sleeping = true
                    }
                }
                AssistantScreen(
                    voice, vm.baseUrl, speak = !night && !settings.quiet,
                    onAnswerDone = vm::voiceAnswerDone,
                    onAgain = { vm.cancelVoice(); vm.startVoice() }, onClose = back,
                )
            }
            Page.SETTINGS -> SettingsScreen(
                modules = allTiles(panel, states),
                settings = settings,
                hasImage = panel.config.wallpaper.image != null,
                serverUrl = vm.baseUrl,
                actions = SettingsActions(
                    toggleModule = { name -> vm.localSettings.update { s -> s.copy(hiddenModules = if (name in s.hiddenModules) s.hiddenModules - name else s.hiddenModules + name) } },
                    setWallpaper = { w -> vm.localSettings.update { it.copy(wallpaper = w) } },
                    setScreensaver = { sec -> vm.localSettings.update { it.copy(screensaverSeconds = sec) } },
                    toggleWakeWord = { vm.localSettings.update { it.copy(wakeWord = !it.wakeWord) } },
                    toggleQuiet = { vm.localSettings.update { it.copy(quiet = !it.quiet) } },
                    setDayBrightness = { b -> vm.localSettings.update { it.copy(dayBrightness = b) } },
                    setNightBrightness = { b -> vm.localSettings.update { it.copy(nightBrightness = b) } },
                    setTheme = { id -> vm.localSettings.update { it.copy(theme = id) } },
                    logout = onAuthFailed,
                ),
                onBack = back,
            )
        }
        val shown = notifications.firstOrNull()
        if (page == Page.START && shown != null) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000))) {
                NotificationCard(
                    notification = shown,
                    more = notifications.size - 1,
                    onDone = { vm.completeNotification(shown) },
                    onSnooze = { vm.snoozeNotification(shown, 60) },
                )
            }
        }
        if (connection == ConnectionState.DISCONNECTED || connection == ConnectionState.CONNECTING) {
            Box(Modifier.align(Alignment.TopCenter).padding(8.dp).glass(12.dp, Color(0xCC8A2A1F)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Txt(if (connection == ConnectionState.CONNECTING) "Łączę z Home Assistantem…" else "Brak połączenia z Home Assistantem", 13)
            }
        }
        message?.let {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(14.dp)
                    .glass(16.dp, if (it.ok) Color(0xF02E9D4F) else Color(0xF0B3362D), Color(0x66FFFFFF))
                    .tap { vm.dismissMessage() }.padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Txt((if (it.ok) "✓  " else "✕  ") + it.text, 16, androidx.compose.ui.text.font.FontWeight.Medium)
            }
        }
    }
}
