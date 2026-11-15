package app.wallglass.ui

import androidx.annotation.OptIn
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.wallglass.ha.VoiceState

private val ORB = Brush.sweepGradient(listOf(Color(0xFF7B6CFF), Color(0xFF3FC6FF), Color(0xFFB56CFF), Color(0xFF7B6CFF)))
private val BG = Brush.radialGradient(listOf(Color(0xFF2B2266), Color(0xFF120F2E), Color(0xFF07060F)))

/** Push-to-talk assistant: listens, shows what was heard and the answer, speaks it (not at night). */
@Composable
fun AssistantScreen(
    state: VoiceState,
    baseUrl: String?,
    speak: Boolean,
    onAgain: () -> Unit,
    onClose: () -> Unit,
    onAnswerDone: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().background(BG).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Orb(active = state is VoiceState.Listening || state is VoiceState.Thinking)
        if (state is VoiceState.Listening && speak) ListeningCue(state.turn)
        val (label, main) = when (state) {
            VoiceState.Idle -> "Słucham…" to "Powiedz, co mam zrobić"
            is VoiceState.Listening ->
                if (state.turn == 0) "Słucham…" to "Powiedz, co mam zrobić"
                else "Słucham dalej…" to "Powiedz „OK”, żeby zakończyć"
            VoiceState.Closed -> "Do usłyszenia" to "…"
            is VoiceState.Thinking -> "Powiedziałeś" to quote(state.heard)
            is VoiceState.Answer -> "Powiedziałeś" to quote(state.heard)
            is VoiceState.Failed -> "Coś poszło nie tak" to state.message
        }
        Txt(label, 13, color = Color(0xFFB9B2EA))
        Text(main, style = TextStyle(color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center), maxLines = 3)
        if (state is VoiceState.Thinking) Txt("Myślę…", 15, color = Color(0xFFD4CFF5))
        if (state is VoiceState.Answer) {
            Box(Modifier.fillMaxWidth().glass(20.dp, Color(0x1AFFFFFF)).padding(16.dp)) {
                Text(state.answer, style = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium), maxLines = 6)
            }
            if (speak && baseUrl != null && state.ttsUrl != null) {
                SpeakOnce(baseUrl + state.ttsUrl, onAnswerDone)
            } else if (!speak) {
                // silent (night / quiet mode): give a moment to read, then listen again
                LaunchedEffect(state) {
                    kotlinx.coroutines.delay(SILENT_READ_MS)
                    onAnswerDone()
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Zapytaj znowu", Modifier.weight(1f), icon = PanelIcons.mic, height = 52.dp, onClick = onAgain)
            GlassButton("Gotowe", Modifier.weight(1f), fill = Palette.action, height = 52.dp, onClick = onClose)
        }
    }
}

private fun quote(text: String?) = if (text.isNullOrBlank()) "…" else "„$text”"

@Composable
private fun Orb(active: Boolean) {
    val pulse by rememberInfiniteTransition(label = "orb").animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse",
    )
    Box(Modifier.padding(top = 8.dp).size(120.dp).scale(if (active) pulse else 1f), contentAlignment = Alignment.Center) {
        Box(Modifier.size(120.dp).clip(CircleShape).background(ORB))
        Box(Modifier.size(100.dp).clip(CircleShape).background(Color(0xFF1B1550)), contentAlignment = Alignment.Center) {
            PanelIcon(PanelIcons.mic, 40.dp)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun SpeakOnce(url: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val done by androidx.compose.runtime.rememberUpdatedState(onDone)
    DisposableEffect(url) {
        val player = ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) done()
                }

                override fun onPlayerError(error: PlaybackException) = done()
            })
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            play()
        }
        onDispose { player.release() }
    }
}

/** Short beep when the panel starts listening, so the user knows when to speak. */
@Composable
private fun ListeningCue(turn: Int) {
    DisposableEffect(turn) {
        val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, CUE_VOLUME) }.getOrNull()
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, CUE_MS)
        onDispose { tone?.release() }
    }
}

private const val CUE_VOLUME = 60
private const val CUE_MS = 120
private const val SILENT_READ_MS = 2_500L

@Composable
fun rememberAssistantStart(onStart: () -> Unit) {
    LaunchedEffect(Unit) { onStart() }
}

@Suppress("unused")
private val unusedHeight = Modifier.height(0.dp)
