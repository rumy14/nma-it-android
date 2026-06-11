package com.nmait.app.ui.vapi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nmait.app.R
import com.nmait.app.ui.voice.VapiVoiceManager

class VapiBottomSheetFragment : BottomSheetDialogFragment() {

    private lateinit var vapiManager: VapiVoiceManager
    private lateinit var statusText: TextView
    private lateinit var transcriptText: TextView
    private lateinit var transcriptCard: CardView
    private lateinit var startStopButton: View
    private lateinit var startStopIcon: TextView
    private lateinit var muteButton: ImageButton
    private lateinit var closeButton: ImageButton

    companion object {
        const val TAG = "VapiBottomSheet"
        private const val VAPI_PUBLIC_KEY = "903ffbab-e2a4-43de-9db6-772c9d2933f5"
        private const val VAPI_ASSISTANT_ID = "93aa1fbb-6ea6-4e35-9af9-fa8bf61af796"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_NMAIT_BottomSheet)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_vapi_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusText = view.findViewById(R.id.statusText)
        transcriptText = view.findViewById(R.id.transcriptText)
        transcriptCard = view.findViewById(R.id.transcriptCard)
        startStopButton = view.findViewById(R.id.startStopButton)
        startStopIcon = view.findViewById(R.id.startStopIcon)
        muteButton = view.findViewById(R.id.muteButton)
        closeButton = view.findViewById(R.id.closeButton)

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
                    statusText.text = "Listening..."
                    statusText.setTextColor(ContextCompat.getColor(ctx, R.color.voice_active))
                    startStopIcon.text = "⏹️"
                    startStopButton.setBackgroundResource(R.drawable.vapi_start_bg_active)
                    transcriptCard.visibility = View.VISIBLE
                    transcriptText.text = "Speak now..."
                    muteButton.visibility = View.VISIBLE
                    closeButton.visibility = View.VISIBLE
                }
            },
            onCallEnded = {
                val ctx = context
                rootView?.post {
                    if (ctx == null) return@post
                    statusText.text = "Tap to start a conversation"
                    statusText.setTextColor(ContextCompat.getColor(ctx, R.color.bottom_nav_tint))
                    startStopIcon.text = "🎤"
                    startStopButton.setBackgroundResource(R.drawable.vapi_start_bg)
                    transcriptText.text = ""
                    transcriptCard.visibility = View.GONE
                    muteButton.visibility = View.GONE
                    closeButton.visibility = View.GONE
                }
            },
            onError = { error ->
                val ctx = context
                rootView?.post {
                    if (ctx == null) return@post
                    Toast.makeText(ctx, error, Toast.LENGTH_SHORT).show()
                    statusText.text = "Try again"
                    statusText.setTextColor(ContextCompat.getColor(ctx, R.color.voice_active))
                    startStopIcon.text = "🎤"
                    startStopButton.setBackgroundResource(R.drawable.vapi_start_bg)
                }
            },
            onTranscript = { text ->
                rootView?.post {
                    if (isAdded && text.isNotBlank()) {
                        transcriptText.text = text
                        transcriptCard.visibility = View.VISIBLE
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
            val ctx = context ?: return@setOnClickListener
            vapiManager.toggleMute()
            muteButton.isSelected = !muteButton.isSelected
            muteButton.imageTintList = if (muteButton.isSelected) {
                ContextCompat.getColorStateList(ctx, R.color.voice_active)
            } else {
                ContextCompat.getColorStateList(ctx, R.color.bottom_nav_tint)
            }
        }

        closeButton.setOnClickListener {
            vapiManager.stopCall()
            dismiss()
        }
    }

    override fun onDestroy() {
        vapiManager.destroy()
        super.onDestroy()
    }
}
