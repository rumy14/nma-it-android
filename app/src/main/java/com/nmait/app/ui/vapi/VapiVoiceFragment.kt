package com.nmait.app.ui.vapi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.nmait.app.R
import com.nmait.app.ui.voice.VapiVoiceManager

class VapiVoiceFragment : Fragment() {

    private lateinit var vapiManager: VapiVoiceManager
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var transcriptText: TextView
    private lateinit var transcriptCard: CardView
    private lateinit var startStopButton: View
    private lateinit var startStopIcon: TextView
    private lateinit var controlRow: View
    private lateinit var muteButton: View
    private lateinit var muteIcon: ImageView

    companion object {
        const val TAG = "VapiVoice"
        private const val VAPI_PUBLIC_KEY = "903ffbab-e2a4-43de-9db6-772c9d2933f5"
        private const val VAPI_ASSISTANT_ID = "93aa1fbb-6ea6-4e35-9af9-fa8bf61af796"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_vapi_voice, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusDot = view.findViewById(R.id.statusDot)
        statusText = view.findViewById(R.id.statusText)
        transcriptText = view.findViewById(R.id.transcriptText)
        transcriptCard = view.findViewById(R.id.transcriptCard)
        startStopButton = view.findViewById(R.id.startStopButton)
        startStopIcon = view.findViewById(R.id.startStopIcon)
        controlRow = view.findViewById(R.id.controlRow)
        muteButton = view.findViewById(R.id.muteButton)
        muteIcon = muteButton.findViewById<ImageView>(R.id.micIcon)

        setupVapiManager()
        setupListeners()
    }

    private fun setupVapiManager() {
        val rootView = view
        vapiManager = VapiVoiceManager(
            context = requireContext(),
            publicKey = VAPI_PUBLIC_KEY,
            assistantId = VAPI_ASSISTANT_ID,
            onCallStarted = {
                val ctx = context
                rootView?.post {
                    if (ctx == null) return@post
                    statusDot.setBackgroundResource(R.drawable.vapi_dot_active)
                    statusText.text = "Listening..."
                    statusText.setTextColor(ContextCompat.getColor(ctx, R.color.voice_active))
                    startStopIcon.text = "🎤"
                    startStopButton.setBackgroundResource(R.drawable.vapi_main_btn_active)
                    transcriptCard.visibility = View.VISIBLE
                    transcriptText.text = "I'm listening..."
                    controlRow.visibility = View.VISIBLE
                }
            },
            onCallEnded = {
                val ctx = context
                rootView?.post {
                    if (ctx == null) return@post
                    statusDot.setBackgroundResource(R.drawable.vapi_dot_idle)
                    statusText.text = "Tap to start"
                    statusText.setTextColor(ContextCompat.getColor(ctx, R.color.bottom_nav_tint))
                    startStopIcon.text = "🎤"
                    startStopButton.setBackgroundResource(R.drawable.vapi_main_btn)
                    transcriptCard.visibility = View.GONE
                    controlRow.visibility = View.GONE
                }
            },
            onError = { error ->
                val ctx = context
                rootView?.post {
                    if (ctx == null) return@post
                    Toast.makeText(ctx, error, Toast.LENGTH_SHORT).show()
                    statusDot.setBackgroundResource(R.drawable.vapi_dot_idle)
                    statusText.text = "Try again"
                    startStopIcon.text = "🎤"
                    startStopButton.setBackgroundResource(R.drawable.vapi_main_btn)
                }
            },
            onTranscript = { text ->
                rootView?.post {
                    if (isAdded && text.isNotBlank()) {
                        transcriptText.text = text
                        transcriptCard.visibility = View.VISIBLE
                        statusText.text = "Speaking..."
                        statusDot.setBackgroundResource(R.drawable.vapi_dot_active)
                    }
                }
            }
        )
        vapiManager.init()
    }

    private fun setupListeners() {
        startStopButton.setOnClickListener {
            if (vapiManager.isCallActive.value) {
                vapiManager.stopCall()
            } else {
                vapiManager.startCall()
            }
        }

        muteButton.setOnClickListener {
            vapiManager.toggleMute()
            val isMuted = muteIcon.isSelected
            muteIcon.isSelected = !isMuted
            muteIcon.imageTintList = if (!isMuted) {
                ContextCompat.getColorStateList(requireContext(), R.color.voice_active)
            } else {
                ContextCompat.getColorStateList(requireContext(), R.color.bottom_nav_tint)
            }
        }

        view?.findViewById<View>(R.id.hangupButton)?.setOnClickListener {
            vapiManager.stopCall()
        }
    }

    override fun onDestroy() {
        vapiManager.destroy()
        super.onDestroy()
    }
}
