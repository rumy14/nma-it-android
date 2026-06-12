package com.nmait.app.ui.vapi

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
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

    private fun setCallActiveUI(ctx: Context) {
        // Pulse animation starts
        startPulseAnimation()

        // Status
        statusDot.setBackgroundResource(R.drawable.vapi_dot_active)
        statusText.text = "Listening..."
        statusText.setTextColor(ContextCompat.getColor(ctx, R.color.voice_active))

        // Button — smooth scale + fade transition
        startStopIcon.text = "⏹️"
        startStopButton.animate()
            .scaleX(1.15f).scaleY(1.15f)
            .setDuration(150)
            .withEndAction {
                startStopButton.setBackgroundResource(R.drawable.vapi_main_btn_active)
                startStopButton.animate()
                    .scaleX(1.0f).scaleY(1.0f)
                    .setDuration(200)
                    .setInterpolator(OvershootInterpolator())
                    .start()

                // Animate icon in
                startStopIcon.animate()
                    .scaleX(1.0f).scaleY(1.0f)
                    .setDuration(200)
                    .start()
            }
            .start()

        // Avatar
        avatarIcon.text = "🤖"
        animateAvatarPulse(true)

        // Show panels with slide-up
        transcriptCard.apply {
            alpha = 0f; translationY = 30f; visibility = View.VISIBLE
            animate().alpha(1f).translationY(0f).setDuration(300).start()
        }
        transcriptText.text = "I'm listening..."

        volumeRow.apply {
            alpha = 0f; translationY = 20f; visibility = View.VISIBLE
            animate().alpha(1f).translationY(0f).setDuration(300).start()
        }

        controlRow.apply {
            alpha = 0f; translationY = 20f; visibility = View.VISIBLE
            animate().alpha(1f).translationY(0f).setDuration(300).start()
        }

        // Sync volume slider
        val volPercent = (vapiManager.volume.value * 100).toInt()
        if (!isVolumeDragging) volumeSlider.progress = volPercent
        volumePercent.text = "$volPercent%"
    }

    private fun setCallIdleUI(ctx: Context) {
        stopPulseAnimation()

        // Status
        statusDot.setBackgroundResource(R.drawable.vapi_dot_idle)
        statusText.text = "Tap to start"
        statusText.setTextColor(ContextCompat.getColor(ctx, R.color.bottom_nav_tint))

        // Button — smooth transition back
        startStopIcon.text = "🎤"
        startStopButton.animate()
            .scaleX(1.15f).scaleY(1.15f)
            .setDuration(150)
            .withEndAction {
                startStopButton.setBackgroundResource(R.drawable.vapi_main_btn)
                startStopButton.animate()
                    .scaleX(1.0f).scaleY(1.0f)
                    .setDuration(200)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            }
            .start()

        // Avatar
        avatarIcon.text = "🎤"
        animateAvatarPulse(false)

        // Hide panels with fade-out
        transcriptCard.animate().alpha(0f).setDuration(200).withEndAction {
            transcriptCard.visibility = View.GONE
        }.start()
        volumeRow.animate().alpha(0f).setDuration(200).withEndAction {
            volumeRow.visibility = View.GONE
        }.start()
        controlRow.animate().alpha(0f).setDuration(200).withEndAction {
            controlRow.visibility = View.GONE
        }.start()
    }

    /** Avatar breathing animation */
    private fun animateAvatarPulse(active: Boolean) {
        avatarIcon.clearAnimation()
        if (active) {
            avatarIcon.animate()
                .scaleX(1.15f).scaleY(1.15f)
                .setDuration(800)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    avatarIcon.animate()
                        .scaleX(1.0f).scaleY(1.0f)
                        .setDuration(800)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .withEndAction {
                            if (vapiManager.isCallActive.value) animateAvatarPulse(true)
                        }
                        .start()
                }
                .start()
        } else {
            avatarIcon.animate()
                .scaleX(1.0f).scaleY(1.0f)
                .setDuration(300)
                .start()
        }
    }

    /** Pulse ring animation around avatar */
    private fun startPulseAnimation() {
        pulseRingInner.visibility = View.VISIBLE
        pulseRingOuter.visibility = View.VISIBLE

        val innerPulse = createPulseAnimator(pulseRingInner, 1.0f, 1.4f, 1000)
        val outerPulse = createPulseAnimator(pulseRingOuter, 1.0f, 1.35f, 1000)

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
                target.alpha = (1.0f - (value - fromScale) / (toScale - fromScale)) * 0.5f
            }
            repeatMode = ValueAnimator.REVERSE
        }
    }

    private fun hapticClick() {
        try {
            val v = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val vm = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                }
                else -> requireContext().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION") v.vibrate(30)
                }
            }
        } catch (_: Exception) {}
    }

    private fun setupListeners() {
        startStopButton.setOnClickListener {
            hapticClick()

            // Press animation
            startStopButton.animate()
                .scaleX(0.85f).scaleY(0.85f)
                .setDuration(80)
                .withEndAction {
                    startStopButton.animate()
                        .scaleX(1.0f).scaleY(1.0f)
                        .setDuration(120)
                        .setInterpolator(OvershootInterpolator())
                        .start()
                }
                .start()

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

            // Animate mute icon
            muteIcon.animate()
                .scaleX(0.7f).scaleY(0.7f)
                .setDuration(80)
                .withEndAction {
                    muteIcon.imageTintList = if (!isMuted) {
                        ContextCompat.getColorStateList(requireContext(), R.color.voice_active)
                    } else {
                        ContextCompat.getColorStateList(requireContext(), R.color.bottom_nav_tint)
                    }
                    muteIcon.animate()
                        .scaleX(1.0f).scaleY(1.0f)
                        .setDuration(120)
                        .start()
                }
                .start()
        }

        view?.findViewById<View>(R.id.hangupButton)?.setOnClickListener {
            vapiManager.stopCall()
        }

        volumeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) volumePercent.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { isVolumeDragging = true }
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
