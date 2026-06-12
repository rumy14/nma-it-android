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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages Vapi voice AI sessions.
 * Wraps the Vapi SDK and exposes simple start/stop controls.
 * Uses MODE_IN_COMMUNICATION for optimal VoIP audio routing
 * (loud speaker output + sensitive mic input).
 */
class VapiVoiceManager(
    private val context: Context,
    private val publicKey: String,
    private val assistantId: String,
    private val volumeLevel: Float = 0.95f,
    private val onCallStarted: () -> Unit = {},
    private val onCallEnded: () -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val onTranscript: (String) -> Unit = {}
) {

    companion object {
        private const val TAG = "VapiVoiceManager"
    }

    private var vapi: Vapi? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var previousAudioMode = AudioManager.MODE_NORMAL
    private var previousSpeakerphone = false
    private var previousMicMute = false
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isCallActive = MutableStateFlow(false)
    val isCallActive: StateFlow<Boolean> = _isCallActive.asStateFlow()

    private val _volume = MutableStateFlow(volumeLevel)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    /** Initialize Vapi SDK, audio manager, and start listening for events */
    fun init() {
        if (vapi != null) return

        try {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

            val configuration = Vapi.Configuration(
                publicKey = publicKey,
                host = "api.vapi.ai"
            )

            vapi = Vapi(context, configuration)

            // Listen for events
            scope.launch {
                vapi?.eventFlow?.collect { event ->
                    when (event) {
                        is Vapi.Event.CallDidStart -> {
                            Log.d(TAG, "Call started")
                            _isCallActive.value = true
                            setupVoipAudio()
                            onCallStarted()
                        }
                        is Vapi.Event.CallDidEnd -> {
                            Log.d(TAG, "Call ended")
                            _isCallActive.value = false
                            restoreAudio()
                            onCallEnded()
                        }
                        is Vapi.Event.Transcript -> {
                            Log.d(TAG, "Transcript: ${event.text}")
                            onTranscript(event.text)
                        }
                        is Vapi.Event.Error -> {
                            Log.e(TAG, "Error: ${event.error}")
                            _isCallActive.value = false
                            restoreAudio()
                            onError(event.error)
                        }
                        is Vapi.Event.SpeechUpdate -> {
                            Log.d(TAG, "Speech update")
                        }
                        is Vapi.Event.Hang -> {
                            Log.d(TAG, "Hang detected")
                            _isCallActive.value = false
                            restoreAudio()
                            onCallEnded()
                        }
                        else -> { /* ignore */ }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Init failed", e)
            onError("Failed to initialize: ${e.message}")
        }
    }

    /**
     * Set up VoIP-style audio routing:
     * - MODE_IN_COMMUNICATION (VoIP mode — max speaker + sensitive mic)
     * - Speakerphone ON
     * - Full audio focus on music stream
     * - Volume boosted near max
     */
    private fun setupVoipAudio() {
        try {
            val am = audioManager ?: return

            // Save previous state
            previousAudioMode = am.mode
            previousSpeakerphone = am.isSpeakerphoneOn
            previousMicMute = am.isMicrophoneMute

            // ─── Audio Focus (ensure we get the audio path) ───
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(true)
                    .build()
                audioFocusRequest = request
                am.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN)
            }

            // ─── VoIP Mode ───
            // MODE_IN_COMMUNICATION routes through the speaker at full volume
            // and optimizes mic sensitivity for voice pickup
            am.mode = AudioManager.MODE_IN_COMMUNICATION

            // ─── Force speakerphone ───
            am.isSpeakerphoneOn = true

            // ─── Ensure mic is unmuted ───
            am.isMicrophoneMute = false

            // ─── Route audio directly to speaker ───
            // MODE_IN_COMMUNICATION + speakerphone=true already handles this
            // on all modern Android versions

            // ─── Boost volume to near max ───
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVol = (maxVol * _volume.value).toInt().coerceIn(1, maxVol)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)

            // Also boost voice call stream
            val maxVoiceVol = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            if (maxVoiceVol > 0) {
                am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVoiceVol, 0)
            }

            Log.d(TAG, "VoIP audio: mode=COMMUNICATION, speaker=true, vol=$targetVol/$maxVol")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set up VoIP audio", e)
        }
    }

    /** Update volume level without touching the device slider */
    fun setVolume(level: Float) {
        _volume.value = level.coerceIn(0.0f, 1.0f)
        if (_isCallActive.value) {
            try {
                val am = audioManager ?: return
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val target = (maxVol * _volume.value).toInt().coerceIn(1, maxVol)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set volume", e)
            }
        }
    }

    /** Restore audio settings when call ends */
    private fun restoreAudio() {
        try {
            val am = audioManager ?: return

            // Restore mode
            am.mode = previousAudioMode

            // Restore speakerphone
            am.isSpeakerphoneOn = previousSpeakerphone

            // Restore mic
            am.isMicrophoneMute = previousMicMute



            // Abandon audio focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }

            Log.d(TAG, "Audio restored to previous state")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore audio", e)
        }
    }

    /** Start a voice conversation */
    fun startCall() {
        val v = vapi ?: run {
            onError("Voice not initialized")
            return
        }
        scope.launch {
            v.start(assistantId = assistantId)
                .onSuccess { Log.d(TAG, "Call started successfully") }
                .onFailure { e ->
                    Log.e(TAG, "Call start failed", e)
                    _isCallActive.value = false
                    restoreAudio()
                    onError("Failed to start: ${e.message}")
                }
        }
    }

    /** End the current voice conversation */
    fun stopCall() {
        vapi?.stop()
        _isCallActive.value = false
        restoreAudio()
    }

    /** Toggle microphone mute */
    fun toggleMute() {
        scope.launch {
            vapi?.toggleMute()
                ?.onSuccess { Log.d(TAG, "Mute toggled") }
                ?.onFailure { e -> Log.e(TAG, "Toggle mute failed", e) }
        }
    }

    /** Cleanup */
    fun destroy() {
        try {
            vapi?.stop()
            restoreAudio()
        } catch (_: Exception) {}
        vapi = null
    }
}
