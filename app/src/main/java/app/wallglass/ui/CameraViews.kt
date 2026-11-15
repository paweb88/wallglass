package app.wallglass.ui

import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Periodically refreshed camera snapshot from HA's camera proxy. */
@Composable
fun CameraSnapshot(
    baseUrl: String,
    entityId: String,
    http: OkHttpClient,
    token: suspend () -> String,
    refreshMillis: Long,
    modifier: Modifier = Modifier,
) {
    var image by remember(entityId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(entityId, baseUrl) {
        while (true) {
            fetchSnapshot(baseUrl, entityId, http, token)?.let { image = it }
            delay(refreshMillis)
        }
    }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        image?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } ?: Txt("Ładuję obraz…", size = 13, color = Palette.soft)
    }
}

private suspend fun fetchSnapshot(baseUrl: String, entityId: String, http: OkHttpClient, token: suspend () -> String): ImageBitmap? =
    withContext(Dispatchers.IO) {
        try {
            val bearer = token()
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/camera_proxy/$entityId")
                .header("Authorization", "Bearer $bearer")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bytes = response.body?.bytes() ?: return@withContext null
                decodeScaled(bytes, 640)?.asImageBitmap()
            }
        } catch (e: Exception) {
            Log.w("CameraSnapshot", "$entityId: ${e.message}")
            null
        }
    }

/** Decodes with inSampleSize so the longer edge is at most about [maxEdge] px (avoids OOM on the panel). */
fun decodeScaled(bytes: ByteArray, maxEdge: Int): android.graphics.Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
} catch (e: OutOfMemoryError) {
    Log.e("CameraViews", "Za mało pamięci na obraz", e)
    null
}

/**
 * Live video. Uses the RTSP URL when given (low latency, e.g. go2rtc),
 * otherwise the HLS URL provided by Home Assistant.
 */
@OptIn(UnstableApi::class)
@Composable
fun LiveVideo(
    url: String?,
    modifier: Modifier = Modifier,
    volume: Float = 0f,
    onError: () -> Unit = {},
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current
    if (url == null) {
        Box(modifier) { fallback() }
        return
    }
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            val source = if (url.startsWith("rtsp")) {
                RtspMediaSource.Factory().setForceUseRtpTcp(true).createMediaSource(MediaItem.fromUri(url))
            } else {
                HlsMediaSource.Factory(DefaultHttpDataSource.Factory()).createMediaSource(MediaItem.fromUri(url))
            }
            setMediaSource(source)
            this.volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    LaunchedEffect(player, volume) { player.volume = volume }
    var hasFrame by remember(url) { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onRenderedFirstFrame() {
                hasFrame = true
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.w("LiveVideo", "Odtwarzanie $url nieudane: ${error.errorCodeName}")
                onError()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    Box(modifier.background(Color.Black)) {
        fallback()
        AndroidView(
            factory = { ctx ->
                // TextureView-based so the snapshot underneath stays visible until the first frame
                (android.view.LayoutInflater.from(ctx).inflate(app.wallglass.R.layout.player_texture, null) as PlayerView).apply {
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setKeepContentOnPlayerReset(true)
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (hasFrame) 1f else 0f },
        )
    }
}

/** Wallpaper photo from HA (e.g. /local/tapeta.jpg), darkened for legibility. */
@Composable
fun WallpaperImage(baseUrl: String, path: String, http: OkHttpClient, modifier: Modifier = Modifier) {
    var image by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        image = withContext(Dispatchers.IO) {
            val url = if (path.startsWith("http")) path else baseUrl.trimEnd('/') + "/" + path.trimStart('/')
            try {
                http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    val bytes = r.body?.bytes()
                    if (r.isSuccessful && bytes != null) decodeScaled(bytes, 640)?.asImageBitmap() else null
                }
            } catch (e: Exception) {
                Log.w("WallpaperImage", "Tapeta $url: ${e.message}")
                null
            }
        }
    }
    image?.let {
        Box(modifier) {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color(0x66000000)))
        }
    }
}
