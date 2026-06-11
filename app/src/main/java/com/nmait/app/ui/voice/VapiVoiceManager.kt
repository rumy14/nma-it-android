package com.nmait.app.ui.voice

import ai.vapi.android.Vapi
import android.content.Context
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
 */
class VapiVoiceManager(
    private val context: Context,
    private val publicKey: String,
    private val assistantId: String,
    private val onCallStarted: () -> Unit = {},
    private val onCallEnded: () -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val onTranscript: (String) -> Unit = {}
) {

    companion object {
        private const val TAG = "VapiVoiceManager"
    }

    private var vapi: Vapi? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isCallActive = MutableStateFlow(false)
    val isCallActive: StateFlow<Boolean> = _isCallActive.asStateFlow()

    /** Initialize Vapi SDK and start listening for events */
    fun init() {
        if (vapi != null) return

        try {
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
                            onCallStarted()
                        }
                        is Vapi.Event.CallDidEnd -> {
                            Log.d(TAG, "Call ended")
                            _isCallActive.value = false
                            onCallEnded()
                        }
                        is Vapi.Event.Transcript -> {
                            Log.d(TAG, "Transcript: ${event.text}")
                            onTranscript(event.text)
                        }
                        is Vapi.Event.Error -> {
                            Log.e(TAG, "Error: ${event.error}")
                            _isCallActive.value = false
                            onError(event.error)
                        }
                        is Vapi.Event.SpeechUpdate -> {
                            Log.d(TAG, "Speech update")
                        }
                        is Vapi.Event.Hang -> {
                            Log.d(TAG, "Hang detected")
                            _isCallActive.value = false
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
                    onError("Failed to start: ${e.message}")
                }
        }
    }

    /** End the current voice conversation */
    fun stopCall() {
        vapi?.stop()
        _isCallActive.value = false
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
        try { vapi?.stop() } catch (_: Exception) {}
        vapi = null
    }
}
