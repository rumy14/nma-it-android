package com.nmait.app.ui.vapi

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
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
    private lateinit var volumeSlider: SeekBar
    private lateinit var volumeRow: View
    private lateinit var volumePercent: TextView
    private lateinit var pulseRingInner: ImageView
    private lateinit var pulseRingOuter: ImageView
    private lateinit var avatarContainer: View

    private var pulseAnimator: AnimatorSet? = null

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
        volumeSlider = view.findViewById(R.id.volumeSlider)
        volumeRow = view.findViewById(R.id.volumeRow)
        volumePercent = view.findViewById(R.id.volumePercent)
        pulseRingInner = view.findViewById(R.id.pulseRingInner)
        pulseRingOuter = view.findViewById(R.id.pulseRingOuter)
        avatarContainer = view.findViewById(R.id.avatarContainer)

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
                    volumeRow.visibility = View.VISIBLE
                    startPulseAnimation()
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
                    volumeRow.visibility = View.GONE
                    stopPulseAnimation()
                }
            },
            onError = { error ->
                val ctx = context
                rootView?.post {
                    if (ctx == null) return@post
                    Toast.makeText(ctx, error, Toast.LENGTH_SHORT).show()
                    statusText.text = "Try again"
                    stopPulseAnimation()
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

    private fun startPulseAnimation() {
        pulseRingInner?.visibility = View.VISIBLE
        pulseRingOuter?.visibility = View.VISIBLE

        val innerPulse = createPulseAnimator(pulseRingInner, 1.0f, 1.3f, 1200).apply {
            repeatCount = ValueAnimator.INFINITE
        }
        val outerPulse = createPulseAnimator(pulseRingOuter, 1.0f, 1.25f, 1200).apply {
            repeatCount = ValueAnimator.INFINITE
        }

        pulseAnimator = AnimatorSet().apply {
            playTogether(innerPulse, outerPulse)
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        pulseRingInner?.let {
            it.visibility = View.GONE
            it.scaleX = 1.0f; it.scaleY = 1.0f
        }
        pulseRingOuter?.let {
            it.visibility = View.GONE
            it.scaleX = 1.0f; it.scaleY = 1.0f
        }
    }

    private fun createPulseAnimator(target: View, from: Float, to: Float, dur: Long): ValueAnimator {
        return ValueAnimator.ofFloat(from, to).apply {
            this.duration = dur
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                target.scaleX = v
                target.scaleY = v
                target.alpha = (1.0f - (v - from) / (to - from)) * 0.6f
            }
            repeatMode = ValueAnimator.REVERSE
        }
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

        volumeSlider?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) volumePercent?.text = "$p%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                val level = (sb?.progress ?: 90) / 100f
                vapiManager.setVolume(level)
                volumePercent?.text = "${sb?.progress ?: 90}%"
            }
        })
    }

    override fun onDestroy() {
        stopPulseAnimation()
        vapiManager.destroy()
        super.onDestroy()
    }
}
