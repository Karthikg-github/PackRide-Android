package com.karthik.packride.voice

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.firebase.functions.FirebaseFunctions
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.IRtcEngineEventHandler.AudioVolumeInfo
import io.agora.rtc2.RtcEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Group-ride voice intercom — Kotlin port of iOS VoiceChatManager.swift.
 * Wraps Agora's RTC engine for one simple job: join the voice channel for
 * the current group ride (channel name = the ride code, which already
 * works as a shared invite secret the same way it does everywhere else in
 * the app), let riders mute/unmute, and show who's currently talking.
 *
 * Security note: this class never holds the Agora App Certificate. It only
 * holds the App ID (not secret — required just to initialize the SDK) and
 * asks a Firebase Cloud Function (generateAgoraToken, see
 * packride-functions/functions/index.js — the SAME function iOS calls,
 * already deployed, no backend change needed here) for a short-lived join
 * token each time it connects. The certificate itself lives only in that
 * function's server-side .env file. Embedding the certificate here instead
 * would let anyone pull it out of the compiled app and join/eavesdrop on
 * any channel.
 *
 * App-wide singleton (init/get — same pattern as SharedLocationManager /
 * CrashDetectionManager, see those files) rather than something scoped to
 * GroupRideScreen's composition: the Agora engine and any live channel
 * membership need to survive navigating away from the Group Ride tab while
 * a ride (and its voice channel) is still active, the same way iOS's
 * VoiceChatManager instance lives for the app's whole lifetime rather than
 * being torn down when MapView disappears.
 */
class VoiceChatManager private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val _speakingUids = MutableStateFlow<Set<Int>>(emptySet())
    val speakingUids: StateFlow<Set<Int>> = _speakingUids.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _audioRoute = MutableStateFlow("Automatic")
    val audioRoute: StateFlow<String> = _audioRoute.asStateFlow()

    private val _speakerEnabled = MutableStateFlow(false)
    val speakerEnabled: StateFlow<Boolean> = _speakerEnabled.asStateFlow()

    // SAME Agora App ID as iOS (PackRide/VoiceChatManager.swift) — one
    // Agora project, cross-platform. Not secret (unlike the App
    // Certificate), safe to ship in the client.
    private val appId = "e863d891fe1f4bbf8f02fef6c6802501"

    private var engine: RtcEngine? = null
    private var currentChannel: String? = null

    // Agora's callbacks below arrive on Agora's own worker thread, not the
    // main thread — unlike iOS, which has to hop back to DispatchQueue.main
    // for @Published to be safe, MutableStateFlow.value is safe to set from
    // any thread, so no dispatcher hop is needed here.
    private val eventHandler = object : IRtcEngineEventHandler() {
        override fun onError(err: Int) {
            _connectionError.value = "Voice chat error (code $err)."
        }

        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            currentChannel = channel
            _isConnecting.value = false
            _isConnected.value = true
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) {
            when (state) {
                Constants.CONNECTION_STATE_CONNECTING,
                Constants.CONNECTION_STATE_RECONNECTING -> _isConnecting.value = true
                Constants.CONNECTION_STATE_CONNECTED -> {
                    _isConnecting.value = false
                    _isConnected.value = true
                }
                Constants.CONNECTION_STATE_DISCONNECTED -> {
                    _isConnecting.value = false
                    _isConnected.value = false
                }
                Constants.CONNECTION_STATE_FAILED -> {
                    _isConnecting.value = false
                    _isConnected.value = false
                    _connectionError.value = "Voice chat disconnected (reason $reason). Tap the microphone to retry."
                }
            }
        }

        override fun onAudioRouteChanged(routing: Int) {
            _audioRoute.value = when (routing) {
                Constants.AUDIO_ROUTE_BLUETOOTH_DEVICE_HFP -> "Bluetooth headset"
                Constants.AUDIO_ROUTE_BLUETOOTH_DEVICE_A2DP -> "Bluetooth audio"
                Constants.AUDIO_ROUTE_HEADSET -> "Wired headset"
                Constants.AUDIO_ROUTE_HEADSETNOMIC -> "Wired headphones"
                Constants.AUDIO_ROUTE_EARPIECE -> "Phone earpiece"
                Constants.AUDIO_ROUTE_SPEAKERPHONE,
                Constants.AUDIO_ROUTE_LOUDSPEAKER -> "Phone speaker"
                Constants.AUDIO_ROUTE_USBDEVICE,
                Constants.AUDIO_ROUTE_USB_HEADSET -> "USB headset"
                Constants.AUDIO_ROUTE_HDMI -> "HDMI"
                else -> "Automatic"
            }
            _speakerEnabled.value = routing == Constants.AUDIO_ROUTE_SPEAKERPHONE ||
                routing == Constants.AUDIO_ROUTE_LOUDSPEAKER
        }

        override fun onTokenPrivilegeWillExpire(token: String?) {
            val channel = currentChannel ?: return
            scope.launch {
                try {
                    val result = FirebaseFunctions.getInstance()
                        .getHttpsCallable("generateAgoraToken")
                        .call(mapOf("channelName" to channel))
                        .await()
                    val freshToken = (result.data as? Map<*, *>)?.get("token") as? String
                    if (freshToken == null || engine?.renewToken(freshToken) != 0) {
                        _connectionError.value = "Voice authorization couldn't be renewed. Tap the microphone to reconnect."
                    }
                } catch (e: Exception) {
                    _connectionError.value = "Voice authorization couldn't be renewed: ${e.message}"
                }
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            _speakingUids.value = _speakingUids.value - uid
        }

        override fun onAudioVolumeIndication(speakers: Array<AudioVolumeInfo>, totalVolume: Int) {
            // A little above zero, so normal background hiss/road noise
            // doesn't constantly light up the "speaking" indicator for
            // everyone — matches iOS's > 5 threshold exactly.
            _speakingUids.value = speakers.filter { it.volume > 5 }.map { it.uid }.toSet()
        }
    }

    // MARK: - Join / Leave
    fun join(channelName: String) {
        if (channelName.isEmpty() || currentChannel == channelName || _isConnecting.value) return
        _connectionError.value = null
        _isConnecting.value = true

        if (engine == null) {
            engine = try {
                RtcEngine.create(appContext, appId, eventHandler).also {
                    it.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
                    it.enableAudio()
                    // Agora AINS aggressive mode is tuned for demanding
                    // outdoor noise. Helmet mic placement still matters, but
                    // this is the strongest real-time suppression mode.
                    it.setAINSMode(true, 1)
                    it.enableAudioVolumeIndication(200, 3, true)
                    // Prefer a connected Bluetooth/wired headset. If none is
                    // present Agora falls back to the earpiece; the rider can
                    // explicitly switch to speaker from the group screen.
                    it.setDefaultAudioRoutetoSpeakerphone(false)
                }
            } catch (e: Exception) {
                _isConnecting.value = false
                _connectionError.value = "Couldn't start the voice engine: ${e.message}"
                return
            }
        }

        scope.launch {
            try {
                val result = FirebaseFunctions.getInstance()
                    .getHttpsCallable("generateAgoraToken")
                    .call(mapOf("channelName" to channelName))
                    .await()
                val data = result.data as? Map<*, *>
                val token = data?.get("token") as? String
                if (token == null) {
                    _isConnecting.value = false
                    _connectionError.value = "The voice server returned an unexpected response."
                    return@launch
                }

                val options = ChannelMediaOptions().apply {
                    channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                    clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                    publishMicrophoneTrack = true
                    autoSubscribeAudio = true
                }
                val joinResult = engine?.joinChannel(token, channelName, 0, options) ?: -1
                if (joinResult == 0) {
                    currentChannel = channelName
                    ContextCompat.startForegroundService(
                        appContext,
                        Intent(appContext, VoiceCommsService::class.java)
                    )
                } else {
                    _isConnecting.value = false
                    _connectionError.value = "Couldn't join voice chat (code $joinResult)."
                }
            } catch (e: Exception) {
                _isConnecting.value = false
                _connectionError.value = "Couldn't reach the voice server: ${e.message}"
            }
        }
    }

    fun leave() {
        engine?.leaveChannel()
        currentChannel = null
        _isConnected.value = false
        _isMuted.value = false
        _speakingUids.value = emptySet()
        _audioRoute.value = "Automatic"
        _speakerEnabled.value = false
        appContext.stopService(Intent(appContext, VoiceCommsService::class.java))
    }

    fun toggleMute() {
        val muted = !_isMuted.value
        _isMuted.value = muted
        engine?.muteLocalAudioStream(muted)
    }

    fun toggleSpeaker() {
        val enabled = !_speakerEnabled.value
        val result = engine?.setEnableSpeakerphone(enabled) ?: -1
        if (result != 0) {
            _connectionError.value = "Couldn't change the voice audio output (code $result)."
        }
    }

    /** Kotlin StateFlow equivalent of iOS setting `connectionError = nil` from the alert's OK button. */
    fun clearError() {
        _connectionError.value = null
    }

    companion object {
        @Volatile
        private var instance: VoiceChatManager? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = VoiceChatManager(context.applicationContext)
                    }
                }
            }
        }

        fun get(): VoiceChatManager =
            instance ?: error("Call VoiceChatManager.init(Application) first")
    }
}
