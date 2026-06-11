package com.nmait.app.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.nmait.app.R
import com.nmait.app.ui.chat.ChatBottomSheetFragment
import com.nmait.app.ui.voice.VoiceChatManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ContactFragment : Fragment() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var voiceManager: VoiceChatManager
    private lateinit var messageInput: EditText
    private lateinit var formStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceManager = VoiceChatManager(
            fragment = this,
            onSpeechResult = { text ->
                messageInput.setText(messageInput.text.toString() + text + " ")
                messageInput.setSelection(messageInput.length())
            },
            onSpeechError = {
                formStatus.text = it
                formStatus.setTextColor(0xFFEF4444.toInt())
                formStatus.visibility = View.VISIBLE
            }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_contact, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val nameInput = view.findViewById<EditText>(R.id.nameInput)
        val emailInput = view.findViewById<EditText>(R.id.emailInput)
        val whatsappInput = view.findViewById<EditText>(R.id.whatsappInput)
        messageInput = view.findViewById(R.id.messageInput)
        val sendButton = view.findViewById<Button>(R.id.sendButton)
        formStatus = view.findViewById(R.id.formStatus)

        // Direct call
        view.findViewById<View>(R.id.actionCall).setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+1234567890")))
        }

        // Direct email
        view.findViewById<View>(R.id.actionEmail).setOnClickListener {
            startActivity(Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:rumy103040@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "NMA IT Automation Inquiry")
            })
        }

        // Voice chat — opens the chat bottom sheet in voice mode + mic button
        view.findViewById<View>(R.id.actionVoiceChat).setOnClickListener {
            val bottomSheet = ChatBottomSheetFragment()
            bottomSheet.show(parentFragmentManager, ChatBottomSheetFragment.TAG)
        }

        // Mic button on message field — voice dictation
        // Actually we handle it via the Voice Chat button above, which gives full chat

        // Send form
        sendButton.setOnClickListener {
            val name = nameInput.text?.toString()?.trim() ?: ""
            val email = emailInput.text?.toString()?.trim() ?: ""
            val whatsapp = whatsappInput.text?.toString()?.trim() ?: ""
            val message = messageInput.text?.toString()?.trim() ?: ""

            if (name.isEmpty() || email.isEmpty() || message.isEmpty()) {
                formStatus.text = "Please fill in name, email, and message."
                formStatus.setTextColor(0xFFEF4444.toInt())
                formStatus.visibility = View.VISIBLE
                return@setOnClickListener
            }

            sendButton.isEnabled = false
            sendButton.text = "Sending..."

            scope.launch {
                val result = submitContact(name, email, whatsapp, message)
                sendButton.isEnabled = true
                sendButton.text = "Send Message"

                if (result) {
                    formStatus.text = "Message sent! We'll get back to you soon."
                    formStatus.setTextColor(0xFF22C55E.toInt())
                    formStatus.visibility = View.VISIBLE
                    nameInput.text?.clear()
                    emailInput.text?.clear()
                    whatsappInput.text?.clear()
                    messageInput.text?.clear()
                } else {
                    formStatus.text = "Failed to send. Please check your connection and try again."
                    formStatus.setTextColor(0xFFEF4444.toInt())
                    formStatus.visibility = View.VISIBLE
                }
            }
        }
    }

    private suspend fun submitContact(
        name: String, email: String, whatsapp: String, message: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://ai.nma-it.com/api/contact")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val body = JSONObject().apply {
                put("name", name)
                put("email", email)
                put("whatsapp", whatsapp)
                put("interest", "General Inquiry")
                put("message", message)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()); it.flush() }
            conn.responseCode == 200
        } catch (e: Exception) {
            e.printStackTrace(); false
        }
    }

    override fun onDestroy() {
        voiceManager.destroy()
        super.onDestroy()
    }
}
