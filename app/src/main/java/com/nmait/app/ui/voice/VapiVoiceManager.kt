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
 * Handles audio routing — forces speakerphone for louder output.
 */
class VapiVoiceManager(
    private val context: Context,
    private val publicKey: String,
    private val assistantId: String,
    private val volumeLevel: Float = 0.9f,
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

            // Configure Vapi with audio options for better volume
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
                            forceSpeakerphone()
                            boostVolume()
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

    /** Force audio through the loudspeaker for maximum volume */
    private fun forceSpeakerphone() {
        try {
            val am = audioManager ?: return

            // Request audio focus with music stream (gives us full volume control)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(true)
                    .build()
                audioFocusRequest = request
                am.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }

            // Route to speaker for louder playback
            am.isSpeakerphoneOn = true
            am.mode = AudioManager.MODE_NORMAL

            // Force wired headset/speaker routing
            am.setParameters("speaker_on=true")
            am.setParameters("bt_sco=false")
            am.setParameters("audio_instrument_enabled=true")

            Log.d(TAG, "Speakerphone forced ON for maximum volume")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to force speakerphone", e)
        }
    }

    /** Boost media stream volume near max */
    private fun boostVolume() {
        try {
            val am = audioManager ?: return
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVol = (maxVol * _volume.value).toInt().coerceIn(1, maxVol)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
            Log.d(TAG, "Volume boosted to $targetVol/$maxVol")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to boost volume", e)
        }
    }

    /** Set volume without changing device volume slider*/
    fun setVolume(level: Float) {
        _volume.value = level.coerceIn(0.0f, 1.0f)
        if (_isCallActive.value) {
            boostVolume()
        }
    }

    /** Restore audio settings when call ends */
    private fun restoreAudio() {
        try {
            val am = audioManager ?: return
            am.isSpeakerphoneOn = false
            am.setParameters("speaker_on=false")
            am.mode = AudioManager.MODE_NORMAL

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
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
