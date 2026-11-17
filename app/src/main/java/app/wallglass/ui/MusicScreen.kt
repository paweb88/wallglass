package app.wallglass.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.wallglass.ha.Entity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Request

private const val START_TIMEOUT_MS = 30_000L

private val MUSIC_BG = Brush.linearGradient(listOf(Color(0xFF2A1A12), Color(0xFF6B3A1F), Color(0xFFC2703A)))

/** Media player view: art, title, progress, transport controls, volume, player picker. */
@Composable
fun MusicScreen(vm: PanelActions, players: List<String>, states: Map<String, Entity>, onBack: () -> Unit) {
    if (players.isEmpty()) {
        SubPage("Muzyka", onBack) { Txt("Brak odtwarzaczy w HA", 15, color = Palette.soft) }
        return
    }
    // prefer whatever is playing
    var selected by remember {
        mutableStateOf(players.firstOrNull { states[it]?.state == "playing" } ?: players.first())
    }
    val p = states[selected]
    // stale Spotify: play has to start on a device first
    val needsDevice = PlayerStart.needsDevice(p)
    val others = players.filter { it != selected }.mapNotNull { states[it] }
    var device by remember(selected) { mutableStateOf<String?>(null) }
    val target = device?.takeIf { it in PlayerStart.sources(p) } ?: PlayerStart.defaultDevice(p, others)
    // after moving playback, resume as soon as HA reports a session that can play (Spotify takes a few seconds)
    var startRequested by remember(selected) { mutableLongStateOf(0L) }
    LaunchedEffect(startRequested, needsDevice) {
        if (startRequested == 0L || needsDevice) return@LaunchedEffect
        if (System.currentTimeMillis() - startRequested < START_TIMEOUT_MS) vm.service("media_player", "media_play", selected)
        startRequested = 0L
    }
    Box(Modifier.fillMaxSize().background(MUSIC_BG)) {
        SubPage("Muzyka", onBack, trailing = {
            Box(
                Modifier.height(46.dp).glass(15.dp).tap {
                    selected = players[(players.indexOf(selected) + 1) % players.size]
                }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val source = p?.attrString("source")?.let { " · $it" }.orEmpty()
                Txt((p?.name ?: selected) + source, 13, maxLines = 1)
            }
        }) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(vm, p?.attrString("entity_picture"), Modifier.size(150.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Txt(if (p?.state == "playing") "TERAZ GRA" else stateLabel(p?.state), 12, color = Color(0xFFFFE2C8))
                    Txt(p?.attrString("media_title") ?: "—", 21, FontWeight.SemiBold, maxLines = 2)
                    Txt(p?.attrString("media_artist") ?: "", 15, color = Color(0xFFFFE2C8), maxLines = 1)
                    p?.attrString("media_album_name")?.let { Txt(it, 12, color = Color(0xFFFFE2C8), maxLines = 1) }
                    if (needsDevice && target != null) {
                        val sources = PlayerStart.sources(p)
                        Box(
                            Modifier.padding(top = 6.dp).height(40.dp).glass(14.dp).tap {
                                device = sources[(sources.indexOf(target) + 1) % sources.size]
                            }.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Txt("Na: $target", 15, FontWeight.Medium, maxLines = 1) }
                    }
                }
            }
            Progress(p)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val shuffle = p?.attributes?.get("shuffle")?.toString() == "true"
                Round(PanelIcons.shuffle, 46, if (shuffle) Color(0x55FFFFFF) else Color.Transparent) {
                    vm.serviceWith("media_player", "shuffle_set", selected, buildJsonObject { put("shuffle", !shuffle) })
                }
                Round(PanelIcons.prev, 56, Palette.glass) { vm.service("media_player", "media_previous_track", selected) }
                Round(if (p?.state == "playing") PanelIcons.pause else PanelIcons.play, 74, Color.White, Color(0xFF6B3A1F)) {
                    if (needsDevice && target != null) {
                        // move playback to the device, then resume once HA has picked up the new session
                        vm.serviceWith("media_player", "select_source", selected, buildJsonObject { put("source", target) })
                        startRequested = System.currentTimeMillis()
                    } else {
                        vm.service("media_player", "media_play_pause", selected)
                    }
                }
                Round(PanelIcons.next, 56, Palette.glass) { vm.service("media_player", "media_next_track", selected) }
                val repeat = p?.attrString("repeat")
                Round(PanelIcons.repeat, 46, if (repeat != null && repeat != "off") Color(0x55FFFFFF) else Color.Transparent) {
                    val next = when (repeat) { "off", null -> "all"; "all" -> "one"; else -> "off" }
                    vm.serviceWith("media_player", "repeat_set", selected, buildJsonObject { put("repeat", next) })
                }
            }
            Volume(p?.attrDouble("volume_level")?.toFloat()) { level ->
                vm.serviceWith("media_player", "volume_set", selected, buildJsonObject { put("volume_level", level.toDouble()) })
            }
        }
    }
}

private fun stateLabel(state: String?) = when (state) {
    "paused" -> "PAUZA"
    "idle", "on" -> "GOTOWY"
    "off" -> "WYŁĄCZONY"
    "unavailable", null -> "NIEDOSTĘPNY"
    else -> state.uppercase()
}

@Composable
private fun Cover(vm: PanelActions, path: String?, modifier: Modifier) {
    var image by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        val base = vm.baseUrl ?: return@LaunchedEffect
        if (path == null) { image = null; return@LaunchedEffect }
        image = withContext(Dispatchers.IO) {
            runCatching {
                val url = if (path.startsWith("http")) path else base.trimEnd('/') + path
                vm.http.newCall(Request.Builder().url(url).header("Authorization", "Bearer ${vm.accessToken()}").build()).execute().use { r ->
                    r.body?.bytes()?.let { decodeScaled(it, 300)?.asImageBitmap() }
                }
            }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Color(0xFFF4B56A), Color(0xFFD0573A), Color(0xFF5A2340)))),
        contentAlignment = Alignment.Center) {
        image?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            ?: PanelIcon(PanelIcons.music, 48.dp)
    }
}

@Composable
private fun Progress(p: Entity?) {
    val duration = p?.attrDouble("media_duration") ?: return
    val position = p.attrDouble("media_position") ?: 0.0
    val updated = p.attrString("media_position_updated_at")
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(p.state) { while (p.state == "playing") { delay(1000); now = System.currentTimeMillis() } }
    val elapsed = if (p.state == "playing" && updated != null) {
        runCatching { (now - java.time.OffsetDateTime.parse(updated).toInstant().toEpochMilli()) / 1000.0 }.getOrDefault(0.0)
    } else 0.0
    val pos = (position + elapsed).coerceIn(0.0, duration)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.track)) {
            Box(Modifier.fillMaxWidth((pos / duration).toFloat()).fillMaxHeight().background(Palette.white))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt(mmss(pos), 12, color = Color(0xFFFFE2C8)); Txt(mmss(duration), 12, color = Color(0xFFFFE2C8))
        }
    }
}

private fun mmss(s: Double) = "%d:%02d".format(s.toInt() / 60, s.toInt() % 60)

@Composable
private fun Round(icon: androidx.compose.ui.graphics.vector.ImageVector, size: Int, fill: Color, tint: Color = Color.White, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(fill).tap(onClick), contentAlignment = Alignment.Center) {
        PanelIcon(icon, (size * 0.42f).dp, tint)
    }
}

/** Volume bar: tap or drag to set. */
@Composable
private fun Volume(level: Float?, onSet: (Float) -> Unit) {
    var local by remember(level) { mutableStateOf(level ?: 0f) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 6.dp)) {
        PanelIcon(PanelIcons.speaker, 22.dp)
        Box(
            Modifier.weight(1f).height(28.dp)
                .pointerInput(Unit) {
                    detectTapGestures { o -> local = (o.x / size.width).coerceIn(0f, 1f); onSet(local) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragEnd = { onSet(local) }) { change, _ ->
                        local = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Palette.track)) {
                Box(Modifier.fillMaxWidth(local).fillMaxHeight().background(Palette.white))
            }
        }
        Txt("${(local * 100).toInt()}", 13)
    }
}

@Suppress("unused")
private val unused = BitmapFactory.Options()
