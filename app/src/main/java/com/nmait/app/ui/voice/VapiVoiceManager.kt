package com.nmait.app.ui.voice

import ai.vapi.android.Vapi
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages Vapi voice AI sessions.
 * Uses aggressive repeated audio routing to override VAPI/Daily.co's internal
 * WebRTC audio management. Refreshes speakerphone every 200ms during calls.
 */
class VapiVoiceManager(
    private val context: Context,
    private val publicKey: String,
    private val assistantId: String,
    private val volumeLevel: Float = 1.0f,
    private val onCallStarted: () -> Unit = {},
    private val onCallEnded: () -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val onTranscript: (String) -> Unit = {}
) {

    companion object {
        private const val TAG = "VapiVoiceManager"
        private const val WATCHDOG_INTERVAL_MS = 200L // Very aggressive refresh rate
    }

    private var vapi: Vapi? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var watchdog: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isCallActive = MutableStateFlow(false)
    val isCallActive: StateFlow<Boolean> = _isCallActive.asStateFlow()
    private val _volume = MutableStateFlow(volumeLevel)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    fun init() {
        if (vapi != null) return
        try {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

            val configuration = Vapi.Configuration(
                publicKey = publicKey,
                host = "api.vapi.ai"
            )
            vapi = Vapi(context, configuration)

            scope.launch {
                vapi?.eventFlow?.collect { event ->
                    when (event) {
                        is Vapi.Event.CallDidStart -> {
                            Log.d(TAG, "Call started")
                            _isCallActive.value = true
                            forceSpeakerNow()
                            startWatchdog()
                            onCallStarted()
                        }
                        is Vapi.Event.CallDidEnd -> {
                            _isCallActive.value = false
                            stopWatchdog()
                            onCallEnded()
                        }
                        is Vapi.Event.Transcript -> {
                            onTranscript(event.text)
                        }
                        is Vapi.Event.Error -> {
                            Log.e(TAG, "Vapi error: ${event.error}")
                            _isCallActive.value = false
                            stopWatchdog()
                            onError(event.error)
                        }
                        is Vapi.Event.Hang -> {
                            _isCallActive.value = false
                            stopWatchdog()
                            onCallEnded()
                        }
                        else -> {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Init failed", e)
            onError("Failed to initialize: ${e.message}")
        }
    }

    /**
     * Aggressively force speakerphone + max volume.
     * This is called every WATCHDOG_INTERVAL_MS during an active call.
     */
    private fun forceSpeakerNow() {
        try {
            val am = audioManager ?: return

            // Audio focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (audioFocusRequest == null) {
                    audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAcceptsDelayedFocusGain(true)
                        .build()
                }
                am.requestAudioFocus(audioFocusRequest!!)
            }

            // VoIP mode (must be set BEFORE speakerphone on some devices)
            am.mode = AudioManager.MODE_IN_COMMUNICATION

            // Speaker ON
            am.isSpeakerphoneOn = true
            am.isMicrophoneMute = false

            // Vendor-specific audio boost params
            try { am.setParameters("speaker_on=true") } catch (_: Exception) {}
            try { am.setParameters("audio_volume_boost=on") } catch (_: Exception) {}
            try { am.setParameters("AVRCP_MODE=1") } catch (_: Exception) {}

            // Max volume on all relevant streams
            for (stream in intArrayOf(
                AudioManager.STREAM_MUSIC,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.STREAM_ALARM,
                AudioManager.STREAM_SYSTEM
            )) {
                val max = am.getStreamMaxVolume(stream)
                if (max > 0) am.setStreamVolume(stream, max, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "forceSpeakerNow failed", e)
        }
    }

    /** Starts a coroutine that re-applies speakerphone every 200ms */
    private fun startWatchdog() {
        watchdog?.cancel()
        watchdog = scope.launch {
            while (_isCallActive.value) {
                forceSpeakerNow()
                delay(WATCHDOG_INTERVAL_MS)
            }
        }
    }

    private fun stopWatchdog() {
        watchdog?.cancel()
        watchdog = null
    }

    fun setVolume(level: Float) {
        _volume.value = level.coerceIn(0.0f, 1.0f)
    }

    fun startCall() {
        // Apply audio BEFORE VAPI connects (head start)
        forceSpeakerNow()
        startWatchdog()

        val v = vapi ?: run {
            stopWatchdog()
            onError("Voice not initialized")
            return
        }
        scope.launch {
            v.start(assistantId = assistantId)
                .onSuccess { Log.d(TAG, "Call started") }
                .onFailure { e ->
                    Log.e(TAG, "Start failed", e)
                    _isCallActive.value = false
                    stopWatchdog()
                    onError("Failed to start: ${e.message}")
                }
        }
    }

    fun stopCall() {
        vapi?.stop()
        _isCallActive.value = false
        stopWatchdog()
        with(audioManager) {
            if (this != null) {
                isSpeakerphoneOn = false
                try { setParameters("speaker_on=false") } catch (_: Exception) {}
                try { setParameters("audio_volume_boost=off") } catch (_: Exception) {}
                mode = AudioManager.MODE_NORMAL
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest?.let { abandonAudioFocusRequest(it) }
                }
            }
        }
    }

    fun toggleMute() {
        scope.launch {
            vapi?.toggleMute()
                ?.onFailure { e -> Log.e(TAG, "Toggle mute failed", e) }
        }
    }

    /** Toggle speakerphone on/off */
    fun toggleSpeaker() {
        try {
            val am = audioManager ?: return
            val currentlyOn = am.isSpeakerphoneOn
            am.isSpeakerphoneOn = !currentlyOn
            Log.d(TAG, "Speaker toggled: ${!currentlyOn}")
        } catch (e: Exception) {
            Log.e(TAG, "Toggle speaker failed", e)
        }
    }

    /** Check current speaker state */
    fun isSpeakerOn(): Boolean {
        return try {
            audioManager?.isSpeakerphoneOn ?: false
        } catch (_: Exception) { false }
    }

    fun destroy() {
        try {
            vapi?.stop()
            stopWatchdog()
            with(audioManager) {
                if (this != null) {
                    isSpeakerphoneOn = false
                    mode = AudioManager.MODE_NORMAL
                }
            }
        } catch (_: Exception) {}
        vapi = null
    }
}
