package com.nmait.app.ui.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.util.Locale

/**
 * Reusable voice module handling speech-to-text (STT) and text-to-speech (TTS).
 * Works with any Fragment via the launcher callbacks.
 */
class VoiceChatManager(
    private val fragment: Fragment,
    private val onSpeechResult: (String) -> Unit,
    private val onSpeechError: (String) -> Unit = {},
    private val onTtsStart: () -> Unit = {},
    private val onTtsDone: () -> Unit = {}
) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Speech recognition launcher (modern API)
    val speechLauncher: ActivityResultLauncher<Intent> =
        fragment.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val data = result.data
                val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (!results.isNullOrEmpty()) {
                    onSpeechResult(results[0])
                } else {
                    onSpeechError("No speech detected")
                }
            } else {
                onSpeechError("Speech recognition cancelled")
            }
        }

    // Audio permission launcher
    val audioPermLauncher: ActivityResultLauncher<String> =
        fragment.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) startListening()
            else onSpeechError("Microphone permission denied")
        }

    init {
        initTts(fragment.requireContext())
    }

    private fun initTts(context: Context) {
        tts = TextToSpeech(context) { status ->
            ttsReady = (status != TextToSpeech.ERROR)
            if (ttsReady) {
                tts?.language = Locale.US
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = onTtsStart()
                    override fun onDone(utteranceId: String?) = onTtsDone()
                    override fun onError(utteranceId: String?) = onSpeechError("TTS error")
                })
            }
        }
    }

    /** Check mic permission, request if needed, then start listening */
    fun requestAndListen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    fragment.requireContext(), Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return
            }
        }
        startListening()
    }

    /** Start listening for speech input */
    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(fragment.requireContext())) {
            onSpeechError("Voice recognition not available")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
        }
        speechLauncher.launch(intent)
    }

    /** Speak text aloud */
    fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance")
        }
    }

    /** Stop speaking */
    fun stopSpeaking() {
        tts?.stop()
    }

    /** Cleanup — call in Fragment.onDestroy() */
    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
