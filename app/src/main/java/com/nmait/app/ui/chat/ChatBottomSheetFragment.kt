package com.nmait.app.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.core.content.ContextCompat
import com.nmait.app.R
import com.nmait.app.ui.voice.VoiceChatManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ChatBottomSheetFragment : BottomSheetDialogFragment() {

    private lateinit var adapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var chatInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var micButton: ImageButton
    private lateinit var voiceToggle: ImageButton

    private var isVoiceMode = false
    private var messageIdCounter = 0L
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var voiceManager: VoiceChatManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_NMAIT_BottomSheet)

        // Initialize voice manager — must be in onCreate for launcher registration
        voiceManager = VoiceChatManager(
            fragment = this,
            onSpeechResult = { text ->
                if (text.isNotBlank()) {
                    chatInput.setText(text)
                    sendMessage(text)
                    chatInput.text.clear()
                }
            },
            onSpeechError = { error -> /* optional: show a toast */ }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
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
        setupListeners()
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        adapter.addMessage(ChatMessage(
            id = messageIdCounter++,
            content = "Hi! 👋 I'm the NMA IT sales assistant. I can help you find the right AI automation solution for your business. What are you looking to automate?",
            isUser = false
        ))
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

        // Mic button → voice input
        micButton.setOnClickListener {
            voiceManager.requestAndListen()
        }

        // Voice toggle → enables/disables TTS for responses
        voiceToggle.setOnClickListener {
            isVoiceMode = !isVoiceMode
            if (isVoiceMode) {
                voiceToggle.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.primary)
                voiceManager.speak("Voice mode activated. I'll read responses aloud.")
            } else {
                voiceToggle.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.bottom_nav_tint)
                voiceManager.stopSpeaking()
            }
        }
    }

    private fun sendMessage(text: String) {
        // Add user message
        adapter.addMessage(ChatMessage(
            id = messageIdCounter++, content = text, isUser = true
        ))
        scrollToBottom()

        if (isVoiceMode) {
            voiceManager.speak("You said: $text")
        }

        // Call API
        scope.launch {
            val reply = callChatApi(text)
            val responseText = reply ?: "Sorry, I had trouble responding. Please try again."

            adapter.addMessage(ChatMessage(
                id = messageIdCounter++, content = responseText, isUser = false
            ))
            scrollToBottom()

            if (isVoiceMode) {
                voiceManager.speak(responseText)
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

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()); it.flush() }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(response).optString("reply")
            } else null
        } catch (e: Exception) {
            e.printStackTrace(); null
        }
    }

    override fun onDestroy() {
        voiceManager.destroy()
        super.onDestroy()
    }

    companion object {
        const val TAG = "ChatBottomSheet"
    }
}
