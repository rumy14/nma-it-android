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
 *
 * Audio routing: uses MODE_IN_COMMUNICATION for VoIP. A watchdog coroutine
 * periodically re-applies the audio mode and volume, but respects manual
 * speakerphone toggles from the user.
 */
class VapiVoiceManager(
    private val context: Context,
    private val publicKey: String,
    private val assistantId: String,
    private val volumeLevel: Float = 1.0f,
    private val onConnecting: () -> Unit = {},
    private val onCallStarted: () -> Unit = {},
    private val onCallEnded: () -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val onTranscript: (String) -> Unit = {}
) {

    companion object {
        private const val TAG = "VapiVoiceManager"
        private const val WATCHDOG_MS = 400L
    }

    private var vapi: Vapi? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var watchdog: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    // ─── State flows ───
    private val _isCallActive = MutableStateFlow(false)
    val isCallActive: StateFlow<Boolean> = _isCallActive.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _volume = MutableStateFlow(volumeLevel)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    // Speaker override: true = watchdog keeps forcing speaker,
    // false = user has toggled it off, watchdog respects that
    private var forceSpeakerEnabled = true

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
                            _isConnecting.value = false
                            _isCallActive.value = true
                            forceSpeakerEnabled = true
                            applyAudioSettings()
                            startWatchdog()
                            onCallStarted()
                        }
                        is Vapi.Event.CallDidEnd -> {
                            _isConnecting.value = false
                            _isCallActive.value = false
                            stopWatchdog()
                            onCallEnded()
                        }
                        is Vapi.Event.Transcript -> onTranscript(event.text)
                        is Vapi.Event.Error -> {
                            Log.e(TAG, "Vapi error: ${event.error}")
                            _isConnecting.value = false
                            _isCallActive.value = false
                            stopWatchdog()
                            onError(event.error)
                        }
                        is Vapi.Event.Hang -> {
                            _isConnecting.value = false
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

    /** Apply audio mode + volume. Respects forceSpeakerEnabled flag. */
    private fun applyAudioSettings() {
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

            // VoIP mode
            am.mode = AudioManager.MODE_IN_COMMUNICATION

            // Speaker — only force ON if user hasn't manually toggled it off
            if (forceSpeakerEnabled) {
                am.isSpeakerphoneOn = true
            }

            am.isMicrophoneMute = false

            // Vendor-specific
            try { am.setParameters("speaker_on=true") } catch (_: Exception) {}
            try { am.setParameters("audio_volume_boost=on") } catch (_: Exception) {}

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
            Log.e(TAG, "applyAudioSettings failed", e)
        }
    }

    /** Watchdog — re-applies audio mode + volume periodically */
    private fun startWatchdog() {
        watchdog?.cancel()
        watchdog = scope.launch {
            while (_isCallActive.value) {
                applyAudioSettings()
                delay(WATCHDOG_MS)
            }
        }
    }

    private fun stopWatchdog() {
        watchdog?.cancel()
        watchdog = null
    }

    fun setVolume(level: Float) {
        _volume.value = level.coerceIn(0.0f, 1.0f)
        if (_isCallActive.value) {
            try {
                val am = audioManager
                val max = am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: return
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * level).toInt().coerceIn(1, max), 0)
            } catch (_: Exception) {}
        }
    }

    fun startCall() {
        forceSpeakerEnabled = true
        _isConnecting.value = true
        onConnecting()

        // Pre-set audio
        applyAudioSettings()

        val v = vapi ?: run {
            _isConnecting.value = false
            onError("Voice not initialised")
            return
        }
        scope.launch {
            v.start(assistantId = assistantId)
                .onSuccess { Log.d(TAG, "Call started") }
                .onFailure { e ->
                    Log.e(TAG, "Start failed", e)
                    _isConnecting.value = false
                    _isCallActive.value = false
                    stopWatchdog()
                    onError("Failed to start: ${e.message}")
                }
        }
        // Start watchdog early so it takes effect even before CallDidStart
        startWatchdog()
    }

    fun stopCall() {
        vapi?.stop()
        _isConnecting.value = false
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
                ?.onFailure { e -> Log.e(TAG, "Mute failed", e) }
        }
    }

    /** Toggle speaker — watchdog will NOT override this choice */
    fun toggleSpeaker() {
        try {
            val am = audioManager ?: return
            forceSpeakerEnabled = !forceSpeakerEnabled
            am.isSpeakerphoneOn = forceSpeakerEnabled
            Log.d(TAG, "Speaker toggled: $forceSpeakerEnabled")
        } catch (e: Exception) {
            Log.e(TAG, "Toggle speaker failed", e)
        }
    }

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
