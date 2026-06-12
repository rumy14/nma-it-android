package com.nmait.app.ui.vapi

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.SeekBar
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
    private lateinit var volumeRow: View
    private lateinit var volumeSlider: SeekBar
    private lateinit var volumePercent: TextView
    private lateinit var muteButton: View
    private lateinit var muteIcon: ImageView
    private lateinit var avatarIcon: TextView
    private lateinit var pulseRingInner: ImageView
    private lateinit var pulseRingOuter: ImageView

    private var pulseAnimator: AnimatorSet? = null
    private var isVolumeDragging = false

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
        volumeRow = view.findViewById(R.id.volumeRow)
        volumeSlider = view.findViewById(R.id.volumeSlider)
        volumePercent = view.findViewById(R.id.volumePercent)
        muteButton = view.findViewById(R.id.muteButton)
        muteIcon = muteButton.findViewById<ImageView>(R.id.micIcon)
        avatarIcon = view.findViewById(R.id.avatarIcon)
        pulseRingInner = view.findViewById(R.id.pulseRingInner)
        pulseRingOuter = view.findViewById(R.id.pulseRingOuter)

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
                    setCallActiveUI(ctx)
                }
            },
            onCallEnded = {
                val ctx = context
                rootView?.post {
                    if (ctx == null) return@post
                    setCallIdleUI(ctx)
                }
            },
            onError = { error ->
                val ctx = context
                rootView?.post {
                    if (ctx == null) return@post
                    Toast.makeText(ctx, error, Toast.LENGTH_SHORT).show()
                    setCallIdleUI(ctx)
                    statusText.text = "Try again"
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

    private fun setCallActiveUI(ctx: android.content.Context) {
        // Pulse animation
        startPulseAnimation()

        // Status
        statusDot.setBackgroundResource(R.drawable.vapi_dot_active)
        statusText.text = "Listening..."
        statusText.setTextColor(ContextCompat.getColor(ctx, R.color.voice_active))

        // Button
        startStopIcon.text = "⏹️"
        startStopButton.setBackgroundResource(R.drawable.vapi_main_btn_active)

        // Avatar
        avatarIcon.text = "🤖"
        animateAvatarActive()

        // Show panels
        transcriptCard.visibility = View.VISIBLE
        transcriptText.text = "I'm listening..."
        volumeRow.visibility = View.VISIBLE
        controlRow.visibility = View.VISIBLE

        // Sync volume slider with manager
        val volPercent = (vapiManager.volume.value * 100).toInt()
        if (!isVolumeDragging) {
            volumeSlider.progress = volPercent
        }
        volumePercent.text = "$volPercent%"
    }

    private fun setCallIdleUI(ctx: android.content.Context) {
        // Stop pulse
        stopPulseAnimation()

        // Status
        statusDot.setBackgroundResource(R.drawable.vapi_dot_idle)
        statusText.text = "Tap to start"
        statusText.setTextColor(ContextCompat.getColor(ctx, R.color.bottom_nav_tint))

        // Button
        startStopIcon.text = "🎤"
        startStopButton.setBackgroundResource(R.drawable.vapi_main_btn)

        // Avatar
        avatarIcon.text = "🎤"
        animateAvatarIdle()

        // Hide panels
        transcriptCard.visibility = View.GONE
        volumeRow.visibility = View.GONE
        controlRow.visibility = View.GONE
    }

    /** Animate the avatar with a subtle breathing pulse during active call */
    private fun animateAvatarActive() {
        val scaleUp = ObjectAnimator.ofFloat(avatarIcon, "scaleX", 1.0f, 1.15f).apply {
            duration = 800
            interpolator = AccelerateDecelerateInterpolator()
        }
        val scaleDown = ObjectAnimator.ofFloat(avatarIcon, "scaleY", 1.0f, 1.15f).apply {
            duration = 800
            interpolator = AccelerateDecelerateInterpolator()
        }
        avatarIcon.animate()
            .scaleX(1.15f)
            .scaleY(1.15f)
            .setDuration(800)
            .withEndAction {
                avatarIcon.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(800)
                    .start()
            }
            .start()
    }

    private fun animateAvatarIdle() {
        avatarIcon.clearAnimation()
        avatarIcon.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(300)
            .start()
    }

    /** Pulse ring animation */
    private fun startPulseAnimation() {
        pulseRingInner.visibility = View.VISIBLE
        pulseRingOuter.visibility = View.VISIBLE

        val innerPulse = createPulseAnimator(pulseRingInner, 1.0f, 1.3f, 1200)
        val outerPulse = createPulseAnimator(pulseRingOuter, 1.0f, 1.25f, 1200)

        innerPulse.repeatCount = ValueAnimator.INFINITE
        outerPulse.repeatCount = ValueAnimator.INFINITE

        pulseAnimator = AnimatorSet().apply {
            playTogether(innerPulse, outerPulse)
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        pulseRingInner.visibility = View.GONE
        pulseRingOuter.visibility = View.GONE
        pulseRingInner.scaleX = 1.0f
        pulseRingInner.scaleY = 1.0f
        pulseRingOuter.scaleX = 1.0f
        pulseRingOuter.scaleY = 1.0f
    }

    private fun createPulseAnimator(
        target: View, fromScale: Float, toScale: Float, durationMs: Long
    ): ValueAnimator {
        return ValueAnimator.ofFloat(fromScale, toScale).apply {
            this.duration = durationMs
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                target.scaleX = value
                target.scaleY = value
                target.alpha = (1.0f - (value - fromScale) / (toScale - fromScale)) * 0.6f
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

        // Volume slider
        volumeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    volumePercent.text = "$progress%"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isVolumeDragging = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isVolumeDragging = false
                val level = (seekBar?.progress ?: 90) / 100f
                vapiManager.setVolume(level)
                volumePercent.text = "${seekBar?.progress ?: 90}%"
            }
        })
    }

    override fun onDestroy() {
        stopPulseAnimation()
        vapiManager.destroy()
        super.onDestroy()
    }
}
