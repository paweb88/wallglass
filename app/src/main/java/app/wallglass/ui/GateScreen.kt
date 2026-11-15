package app.wallglass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.wallglass.config.DoorbellConfig
import app.wallglass.ha.Entity
import app.wallglass.talk.TalkState
import kotlinx.coroutines.delay

private enum class GateStep { IDLE, ARMED, DONE }

private val OPEN_BLUE = Color(0xFF1F6FD1)
private val HANGUP_RED = Color(0xFFD6453B)

/** Doorbell view laid out like the "Furtka – rozmowa" mockup. */
@Composable
fun GateScreen(vm: PanelActions, doorbell: DoorbellConfig, states: Map<String, Entity>, onBack: () -> Unit) {
    var listening by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(listening) {
        seconds = 0
        while (listening) { delay(1_000); seconds++ }
    }
    val muted = doorbell.mute?.let { states[it]?.isOn } == true
    val talk by vm.talkState.collectAsState()
    val talking = talk == TalkState.Talking
    var talkSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(talking) {
        talkSeconds = 0
        while (talking) { delay(1_000); talkSeconds++ }
    }
    // never leave the microphone open after leaving the gate page
    DisposableEffect(Unit) { onDispose { vm.stopTalk() } }

    SubPage("Furtka", onBack, trailing = {
        if (talking) {
            Pill("Rozmowa · %d:%02d".format(talkSeconds / 60, talkSeconds % 60), Palette.ok, Palette.okBorder, Color(0xFF6EE08F))
        } else if (listening) {
            Pill("Słuchasz · %d:%02d".format(seconds / 60, seconds % 60), Palette.ok, Palette.okBorder, Color(0xFF6EE08F))
        } else if (doorbell.mute != null) {
            Box(Modifier.tap { vm.toggle(doorbell.mute) }) {
                Pill(
                    if (muted) "Dzwonek wyciszony" else "Dzwonek włączony",
                    if (muted) Palette.warn else Palette.glass,
                    if (muted) Palette.warnBorder else Palette.glassBorder,
                    if (muted) Color(0xFFFFC83A) else Color(0xFF6EE08F),
                )
            }
        }
    }) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            // while talking, the visitor is heard through WebRTC (with echo cancellation), not the video stream
            CameraLive(vm, doorbell.camera, doorbell.rtsp, Modifier.fillMaxSize(), volume = if (listening && !talking) 1f else 0f)
            if (listening || talking) SoundBars(Modifier.align(Alignment.BottomStart).padding(12.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            doorbell.talk?.let { cfg ->
                CallButton(
                    "Mikrofon", PanelIcons.mic,
                    when (talk) {
                        TalkState.Talking -> Palette.green
                        is TalkState.Failed -> HANGUP_RED.copy(alpha = 0.55f)
                        else -> Palette.glass
                    },
                    Modifier.weight(1f),
                    sub = when (val t = talk) {
                        TalkState.Connecting -> "łączę…"
                        TalkState.Talking -> "mówisz"
                        is TalkState.Failed -> t.reason
                        TalkState.Idle -> null
                    },
                ) {
                    when (talk) {
                        TalkState.Idle, is TalkState.Failed -> vm.startTalk(cfg)
                        else -> vm.stopTalk()
                    }
                }
            }
            CallButton("Głośność", PanelIcons.speaker, if (listening) Palette.green else Palette.glass, Modifier.weight(1f)) {
                listening = !listening
            }
            doorbell.open?.let { call -> OpenButton(Modifier.weight(1f)) { vm.run(call) } }
            CallButton("Rozłącz", PanelIcons.hangup, HANGUP_RED, Modifier.weight(1f)) {
                vm.stopTalk()
                listening = false
                onBack()
            }
        }
    }
}

@Composable
private fun Pill(text: String, fill: Color, border: Color, dot: Color) {
    Row(
        Modifier.height(34.dp).glass(17.dp, fill, border).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Txt(text, 13, FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun CallButton(
    label: String,
    icon: ImageVector,
    fill: Color,
    modifier: Modifier,
    sub: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.45f
    Column(
        modifier.height(72.dp).glass(20.dp, fill, Palette.glassBorder).let { if (enabled) it.tap(onClick) else it },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PanelIcon(icon, 26.dp, Color.White.copy(alpha = alpha))
        Txt(label, 12, FontWeight.Medium, Color.White.copy(alpha = alpha))
        sub?.let { Txt(it, 9, color = Palette.soft.copy(alpha = alpha)) }
    }
}

/** Two-step open: first tap arms it for 4 s, the second one opens. */
@Composable
private fun OpenButton(modifier: Modifier, onOpen: () -> Unit) {
    var step by remember { mutableStateOf(GateStep.IDLE) }
    LaunchedEffect(step) {
        when (step) {
            GateStep.ARMED -> { delay(4_000); step = GateStep.IDLE }
            GateStep.DONE -> { delay(3_000); step = GateStep.IDLE }
            GateStep.IDLE -> Unit
        }
    }
    CallButton(
        label = when (step) {
            GateStep.IDLE -> "Otwórz"
            GateStep.ARMED -> "Jeszcze raz"
            GateStep.DONE -> "Otwarte"
        },
        icon = PanelIcons.unlock,
        fill = when (step) {
            GateStep.IDLE -> OPEN_BLUE
            GateStep.ARMED -> Color(0xFFE08A00)
            GateStep.DONE -> Palette.green
        },
        modifier = modifier,
    ) {
        step = when (step) {
            GateStep.IDLE -> GateStep.ARMED
            GateStep.ARMED -> { onOpen(); GateStep.DONE }
            GateStep.DONE -> GateStep.DONE
        }
    }
}

/** Little animated level bars shown while listening. */
@Composable
private fun SoundBars(modifier: Modifier) {
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(180); phase++ } }
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x73000000)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(8, 16, 11, 19, 7).forEachIndexed { i, h ->
            val height = 4 + (h + (phase * (i + 3)) % 9) % 18
            Box(Modifier.size(4.dp, height.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF6EE08F)))
        }
    }
}

/** Live view of a camera: RTSP if configured, else HLS from HA, with snapshot underneath. */
@Composable
fun CameraLive(vm: PanelActions, entityId: String, rtsp: String?, modifier: Modifier, volume: Float = 0f) {
    var url by remember(entityId) { mutableStateOf(rtsp) }
    var rtspFailed by remember(entityId) { mutableStateOf(false) }
    LaunchedEffect(entityId, rtspFailed) {
        // no RTSP (or it failed): ask HA for its HLS stream instead
        if (url == null || rtspFailed) url = vm.streamUrl(entityId)
    }
    val base = vm.baseUrl ?: return
    Box(modifier.clip(RoundedCornerShape(20.dp))) {
        LiveVideo(url, Modifier.fillMaxSize(), volume = volume, onError = { if (url?.startsWith("rtsp") == true) rtspFailed = true }) {
            CameraSnapshot(base, entityId, vm.http, vm::accessToken, 2_000, Modifier.fillMaxSize())
        }
    }
}
