package app.wallglass.talk

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Two-way audio with a go2rtc stream over WebRTC: the panel microphone goes to the stream's
 * backchannel (e.g. a Hikvision ISAPI source) and the stream's audio plays on the panel speaker.
 * WebRTC brings echo cancellation, noise suppression and gain control.
 */
class Go2rtcTalk(
    private val context: Context,
    private val http: OkHttpClient,
    private val api: String,
    private val stream: String,
) : AutoCloseable {
    private var pc: PeerConnection? = null
    private var source: AudioSource? = null
    private var track: AudioTrack? = null
    private var savedMode: Int? = null
    private var savedSpeaker = false

    private val _connected = MutableStateFlow<Boolean?>(null)
    /** null while connecting, true when audio flows, false when the connection failed or dropped. */
    val connected: StateFlow<Boolean?> = _connected

    /** Negotiates the call; returns when go2rtc has answered (audio starts once ICE connects). */
    suspend fun open() {
        val factory = Factory.get(context)
        val gathered = CompletableDeferred<Unit>()
        val config = PeerConnection.RTCConfiguration(emptyList()).apply { // LAN only: host candidates
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val peer = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                if (state == PeerConnection.IceGatheringState.COMPLETE) gathered.complete(Unit)
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.i(TAG, "ICE: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> _connected.value = true
                    PeerConnection.IceConnectionState.FAILED, PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.CLOSED -> if (_connected.value != false) _connected.value = false
                    else -> Unit
                }
            }
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceCandidate(candidate: IceCandidate) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddStream(stream: MediaStream) = Unit
            override fun onRemoveStream(stream: MediaStream) = Unit
            override fun onDataChannel(channel: DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
        }) ?: throw IOException("Nie udało się utworzyć połączenia WebRTC")
        pc = peer

        startCommunicationAudio()
        val src = factory.createAudioSource(MediaConstraints().apply {
            listOf("googEchoCancellation", "googAutoGainControl", "googNoiseSuppression", "googHighpassFilter")
                .forEach { mandatory += MediaConstraints.KeyValuePair(it, "true") }
        })
        source = src
        val mic = factory.createAudioTrack("mic", src).also { track = it }
        peer.addTransceiver(mic, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV))

        val offer = peer.awaitSdp { obs -> peer.createOffer(obs, MediaConstraints()) }
        peer.awaitSet { obs -> peer.setLocalDescription(obs, offer) }
        withTimeoutOrNull(GATHER_MS) { gathered.await() } // all host candidates go in the offer

        val local = peer.localDescription?.description ?: throw IOException("Brak oferty SDP")
        val answer = withContext(Dispatchers.IO) { post(local) }
        peer.awaitSet { obs -> peer.setRemoteDescription(obs, SessionDescription(SessionDescription.Type.ANSWER, answer)) }
        Log.i(TAG, "go2rtc odpowiedział, strumień $stream")
    }

    private fun post(sdp: String): String {
        val request = Request.Builder()
            .url(Go2rtcSignaling.url(api, stream))
            .post(Go2rtcSignaling.offer(sdp).toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IOException("go2rtc: HTTP ${res.code} ${body.take(120)}")
            return Go2rtcSignaling.answer(body) ?: throw IOException("go2rtc: ${body.take(120)}")
        }
    }

    /** Voice-call audio mode: enables the platform echo canceller and keeps playback on the speaker. */
    private fun startCommunicationAudio() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        savedMode = am.mode
        @Suppress("DEPRECATION")
        savedSpeaker = am.isSpeakerphoneOn
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = true
    }

    override fun close() {
        pc?.let { runCatching { it.close() }; it.dispose() }
        pc = null
        track?.dispose(); track = null
        source?.dispose(); source = null
        savedMode?.let { mode ->
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode = mode
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = savedSpeaker
        }
        savedMode = null
    }

    private suspend fun PeerConnection.awaitSdp(call: (SdpObserver) -> Unit): SessionDescription = suspendCoroutine { cont ->
        call(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) = cont.resume(sdp)
            override fun onCreateFailure(error: String?) = cont.resumeWithException(IOException("SDP: $error"))
            override fun onSetSuccess() = Unit
            override fun onSetFailure(error: String?) = Unit
        })
    }

    private suspend fun PeerConnection.awaitSet(call: (SdpObserver) -> Unit): Unit = suspendCoroutine { cont ->
        call(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) = Unit
            override fun onCreateFailure(error: String?) = Unit
            override fun onSetSuccess() = cont.resume(Unit)
            override fun onSetFailure(error: String?) = cont.resumeWithException(IOException("SDP: $error"))
        })
    }

    /** One factory and audio device module for the app's lifetime (creating them is slow). */
    private object Factory {
        private var factory: PeerConnectionFactory? = null

        @Synchronized
        fun get(context: Context): PeerConnectionFactory = factory ?: run {
            val app = context.applicationContext
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(app).createInitializationOptions())
            val adm = JavaAudioDeviceModule.builder(app)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule()
            PeerConnectionFactory.builder().setAudioDeviceModule(adm).createPeerConnectionFactory().also { factory = it }
        }
    }

    private companion object {
        const val TAG = "Go2rtcTalk"
        const val GATHER_MS = 2_000L
    }
}
