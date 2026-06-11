package com.nmait.app.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nmait.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import android.speech.SpeechRecognizer

class ChatBottomSheetFragment : BottomSheetDialogFragment() {

    private lateinit var adapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var chatInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var micButton: ImageButton
    private lateinit var voiceToggle: ImageButton

    private var tts: TextToSpeech? = null
    private var isVoiceMode = false
    private var messageIdCounter = 0L
    private val scope = CoroutineScope(Dispatchers.Main)

    // Modern Activity Result API
    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val spokenText = results[0]
                chatInput.setText(spokenText)
                sendMessage(spokenText)
                chatInput.text.clear()
            }
        }
    }

    private val audioPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecognition()
        } else {
            Toast.makeText(requireContext(), "Microphone permission required for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_NMAIT_BottomSheet)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_chat_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.chatMessages)
        chatInput = view.findViewById(R.id.chatInput)
        sendButton = view.findViewById(R.id.sendButton)
        micButton = view.findViewById(R.id.micButton)
        voiceToggle = view.findViewById(R.id.voiceToggle)

        setupRecyclerView()
        setupTts()
        setupListeners()
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // Add welcome message
        adapter.addMessage(ChatMessage(
            id = messageIdCounter++,
            content = "Hi! 👋 I'm the NMA IT sales assistant. I can help you find the right AI automation solution for your business. What are you looking to automate?",
            isUser = false
        ))
    }

    private fun setupTts() {
        tts = TextToSpeech(requireContext()) { status ->
            if (status != TextToSpeech.ERROR) {
                tts?.language = Locale.US
            }
        }
    }

    private fun setupListeners() {
        sendButton.setOnClickListener {
            val text = chatInput.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                chatInput.text.clear()
            }
        }

        chatInput.setOnEditorActionListener { _, _, _ ->
            val text = chatInput.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                chatInput.text.clear()
            }
            true
        }

        micButton.setOnClickListener {
            if (checkPermission()) {
                startVoiceRecognition()
            }
        }

        voiceToggle.setOnClickListener {
            isVoiceMode = !isVoiceMode
            val tintColor = if (isVoiceMode) {
                ContextCompat.getColorStateList(requireContext(), R.color.primary)
            } else {
                ContextCompat.getColorStateList(requireContext(), R.color.bottom_nav_tint)
            }
            voiceToggle.imageTintList = tintColor
        }
    }

    private fun sendMessage(text: String) {
        // Add user message
        val userMsg = ChatMessage(
            id = messageIdCounter++,
            content = text,
            isUser = true
        )
        adapter.addMessage(userMsg)
        scrollToBottom()

        // Read user message aloud in voice mode
        if (isVoiceMode) {
            tts?.speak("You said: $text", TextToSpeech.QUEUE_FLUSH, null, null)
        }

        // Call API
        scope.launch {
            val reply = callChatApi(text)
            if (reply != null) {
                val botMsg = ChatMessage(
                    id = messageIdCounter++,
                    content = reply,
                    isUser = false
                )
                adapter.addMessage(botMsg)
                scrollToBottom()

                // Read bot response aloud in voice mode
                if (isVoiceMode) {
                    tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            } else {
                adapter.addMessage(ChatMessage(
                    id = messageIdCounter++,
                    content = "Sorry, I had trouble responding. Please try again.",
                    isUser = false
                ))
                scrollToBottom()
            }
        }
    }

    private fun scrollToBottom() {
        recyclerView.post {
            if (adapter.itemCount > 0) {
                recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    private suspend fun callChatApi(message: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://ai.nma-it.com/api/chatbot")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val body = JSONObject().apply {
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", message)
                    })
                })
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(body.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                json.optString("reply")
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun startVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            Toast.makeText(requireContext(), "Voice recognition not available", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
        }

        speechLauncher.launch(intent)
    }

    private fun checkPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return false
            }
        }
        return true
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        const val TAG = "ChatBottomSheet"
    }
}
