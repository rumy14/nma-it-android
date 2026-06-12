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
 * Manages Vapi voice AI sessions with aggressive speakerphone routing.
 * Continuously forces MODE_IN_COMMUNICATION + speakerphone during calls
 * to override any internal VAPI SDK audio routing.
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
        private const val SPEAKER_REFRESH_MS = 800L // Re-apply speaker mode every 800ms
        private const val VOLUME_TARGET = 1.0f // 100% volume
    }

    private var vapi: Vapi? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var previousAudioMode = AudioManager.MODE_NORMAL
    private var previousSpeakerphone = false
    private var previousMicMute = false
    private var speakerWatchdog: Job? = null
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

            // Store previous audio settings BEFORE VAPI touches them
            val am = audioManager
            if (am != null) {
                previousAudioMode = am.mode
                previousSpeakerphone = am.isSpeakerphoneOn
                previousMicMute = am.isMicrophoneMute
            }

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
                            // Move audio setup BEFORE UI callback so speaker
                            // is already active when user hears first word
                            forceSpeakerLoudest()
                            startSpeakerWatchdog()
                            onCallStarted()
                        }
                        is Vapi.Event.CallDidEnd -> {
                            Log.d(TAG, "Call ended")
                            _isCallActive.value = false
                            stopSpeakerWatchdog()
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
                            stopSpeakerWatchdog()
                            restoreAudio()
                            onError(event.error)
                        }
                        is Vapi.Event.SpeechUpdate -> {
                            Log.d(TAG, "Speech update")
                        }
                        is Vapi.Event.Hang -> {
                            Log.d(TAG, "Hang detected")
                            _isCallActive.value = false
                            stopSpeakerWatchdog()
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

    /** Call BEFORE vapi.start() to establish audio routing early */
    private fun preCallAudioSetup() {
        try {
            val am = audioManager ?: return
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = true
            am.isMicrophoneMute = false
            Log.d(TAG, "Pre-call audio: MODE_IN_COMMUNICATION + speakerphone")
        } catch (e: Exception) {
            Log.e(TAG, "Pre-call audio setup failed", e)
        }
    }

    /** Aggressively force speakerphone and max volume */
    private fun forceSpeakerLoudest() {
        try {
            val am = audioManager ?: return

            // ─── Audio Focus (HIGHEST PRIORITY) ───
            requestAudioFocus()

            // ─── VoIP Mode (enables full-duplex speaker audio) ───
            am.mode = AudioManager.MODE_IN_COMMUNICATION

            // ─── Speakerphone ON ───
            am.isSpeakerphoneOn = true

            // ─── Mic unmuted ───
            am.isMicrophoneMute = false

            // ─── Speaker route handled by MODE_IN_COMMUNICATION + setSpeakerphoneOn(true) ───

            // ─── Volume: max on ALL relevant streams ───
            // TARGET_VOLUME_STREAMS.forEach { stream ->
            //     val max = am.getStreamMaxVolume(stream)
            //     if (max > 0) am.setStreamVolume(stream, (max * VOLUME_TARGET).toInt().coerceIn(1, max), 0)
            // }

            val musicMax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (musicMax > 0) {
                val target = (musicMax * VOLUME_TARGET).toInt().coerceIn(1, musicMax)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            }

            val voiceMax = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            if (voiceMax > 0) {
                am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, voiceMax, 0)
            }

            // System/alarm stream as backup
            val alarmMax = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (alarmMax > 0) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, alarmMax, 0)
            }

            Log.d(TAG, "Speaker forced LOUDEST: mode=COMMUNICATION, speaker=true, speakerDevice=true")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to force speaker", e)
        }
    }

    /** Periodic watchdog — re-apply speaker settings in case VAPI overrides them */
    private fun startSpeakerWatchdog() {
        speakerWatchdog?.cancel()
        speakerWatchdog = scope.launch {
            while (true) {
                delay(SPEAKER_REFRESH_MS)
                if (!_isCallActive.value) break
                try {
                    val am = audioManager ?: continue
                    if (am.mode != AudioManager.MODE_IN_COMMUNICATION) {
                        am.mode = AudioManager.MODE_IN_COMMUNICATION
                        Log.d(TAG, "Watchdog restored MODE_IN_COMMUNICATION")
                    }
                    if (!am.isSpeakerphoneOn) {
                        am.isSpeakerphoneOn = true
                        Log.d(TAG, "Watchdog restored speakerphone")
                    }
                    if (am.isMicrophoneMute) {
                        am.isMicrophoneMute = false
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun stopSpeakerWatchdog() {
        speakerWatchdog?.cancel()
        speakerWatchdog = null
    }

    private fun requestAudioFocus() {
        try {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { }
                    .build()
                audioFocusRequest = request
                am.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        } catch (_: Exception) {}
    }

    fun setVolume(level: Float) {
        _volume.value = level.coerceIn(0.0f, 1.0f)
        if (_isCallActive.value) {
            try {
                val am = audioManager ?: return
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val target = (max * _volume.value).toInt().coerceIn(1, max)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            } catch (_: Exception) {}
        }
    }

    /** Restore audio settings when call ends */
    private fun restoreAudio() {
        try {
            val am = audioManager ?: return
            stopSpeakerWatchdog()

            // Restore previous state
            am.mode = previousAudioMode
            am.isSpeakerphoneOn = previousSpeakerphone
            am.isMicrophoneMute = previousMicMute



            // Abandon audio focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }

            Log.d(TAG, "Audio restored")
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
        // Set audio BEFORE VAPI starts — gives us a head start
        preCallAudioSetup()
        scope.launch {
            v.start(assistantId = assistantId)
                .onSuccess { Log.d(TAG, "Call started successfully") }
                .onFailure { e ->
                    Log.e(TAG, "Call start failed", e)
                    _isCallActive.value = false
                    stopSpeakerWatchdog()
                    restoreAudio()
                    onError("Failed to start: ${e.message}")
                }
        }
    }

    /** End the current voice conversation */
    fun stopCall() {
        vapi?.stop()
        _isCallActive.value = false
        stopSpeakerWatchdog()
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
            stopSpeakerWatchdog()
            restoreAudio()
        } catch (_: Exception) {}
        vapi = null
    }
}
