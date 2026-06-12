package com.nmait.app.ui.vapi

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
    private lateinit var speakerButton: View
    private lateinit var speakerIcon: ImageView
    private lateinit var avatarIcon: TextView
    private lateinit var pulseRingInner: ImageView
    private lateinit var pulseRingOuter: ImageView
    private var connectingText: TextView? = null

    private var pulseAnimator: AnimatorSet? = null
    private var isVolumeDragging = false
    private var isInCallFlow = false

    // Permission launcher
    private val micPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) doStartCall()
        else Toast.makeText(requireContext(), "Microphone permission is needed for voice calls", Toast.LENGTH_LONG).show()
    }

    companion object {
        const val TAG = "VapiVoice"
        private const val VAPI_PUBLIC_KEY = "903ffb…33f5"
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
        speakerButton = view.findViewById(R.id.speakerButton)
        speakerIcon = speakerButton.findViewById<ImageView>(R.id.speakerIcon)
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
            onConnecting = {
                rootView?.post {
                    if (!isAdded) return@post
                    setConnectingUI()
                }
            },
            onCallStarted = {
                val ctx = context
                rootView?.post {
                    if (ctx == null) return@post
                    isInCallFlow = false
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

    /** "Connecting..." state — shown immediately after tapping the button */
    private fun setConnectingUI() {
        val ctx = context ?: return
        statusDot.setBackgroundResource(R.drawable.vapi_dot_active)
        statusText.text = "Connecting..."
        statusText.setTextColor(ContextCompat.getColor(ctx, R.color.primary))
        startStopIcon.text = "⏳"
        startStopButton.setBackgroundResource(R.drawable.vapi_main_btn_active)
        avatarIcon.text = "📞"

        // Subtle rotate on the icon to show loading
        val rotate = RotateAnimation(0f, 360f,
            RotateAnimation.RELATIVE_TO_SELF, 0.5f,
            RotateAnimation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 1200
            repeatCount = RotateAnimation.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        startStopIcon.startAnimation(rotate)
    }

    private fun setCallActiveUI(ctx: Context) {
        // Stop connecting animation
        startStopIcon.clearAnimation()

        startPulseAnimation()

        // Status
        statusDot.setBackgroundResource(R.drawable.vapi_dot_active)
        statusText.text = "Listening..."
        statusText.setTextColor(ContextCompat.getColor(ctx, R.color.voice_active))

        // Button
        startStopIcon.text = "⏹️"
        animateButtonTo(startStopButton, R.drawable.vapi_main_btn_active, 1.0f)

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

        // Speaker icon = green (ON) by default
        speakerIcon.isSelected = true
        speakerIcon.imageTintList = ContextCompat.getColorStateList(ctx, R.color.voice_active)

        // Sync volume slider
        val volPercent = (vapiManager.volume.value * 100).toInt()
        if (!isVolumeDragging) volumeSlider.progress = volPercent
        volumePercent.text = "$volPercent%"
    }

    private fun setCallIdleUI(ctx: Context) {
        startStopIcon.clearAnimation()
        stopPulseAnimation()

        statusDot.setBackgroundResource(R.drawable.vapi_dot_idle)
        statusText.text = "Tap to start"
        statusText.setTextColor(ContextCompat.getColor(ctx, R.color.bottom_nav_tint))

        startStopIcon.text = "🎤"
        animateButtonTo(startStopButton, R.drawable.vapi_main_btn, 1.0f)

        avatarIcon.text = "🎤"
        animateAvatarPulse(false)

        // Hide panels
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

    private fun animateButtonTo(btn: View, bgRes: Int, targetScale: Float) {
        btn.animate()
            .scaleX(1.15f).scaleY(1.15f)
            .setDuration(120)
            .withEndAction {
                btn.setBackgroundResource(bgRes)
                btn.animate()
                    .scaleX(targetScale).scaleY(targetScale)
                    .setDuration(180)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            }
            .start()
    }

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
            avatarIcon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start()
        }
    }

    private fun startPulseAnimation() {
        pulseRingInner.visibility = View.VISIBLE
        pulseRingOuter.visibility = View.VISIBLE

        val innerPulse = ValueAnimator.ofFloat(1.0f, 1.4f).apply {
            duration = 1000; interpolator = AccelerateDecelerateInterpolator()
            repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            addUpdateListener { a ->
                val v = a.animatedValue as Float
                pulseRingInner.scaleX = v; pulseRingInner.scaleY = v
                pulseRingInner.alpha = (1.0f - (v - 1.0f) / 0.4f) * 0.5f
            }
        }
        val outerPulse = ValueAnimator.ofFloat(1.0f, 1.35f).apply {
            duration = 1000; interpolator = AccelerateDecelerateInterpolator()
            repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            addUpdateListener { a ->
                val v = a.animatedValue as Float
                pulseRingOuter.scaleX = v; pulseRingOuter.scaleY = v
                pulseRingOuter.alpha = (1.0f - (v - 1.0f) / 0.35f) * 0.5f
            }
        }
        pulseAnimator = AnimatorSet().apply { playTogether(innerPulse, outerPulse); start() }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel(); pulseAnimator = null
        pulseRingInner.visibility = View.GONE; pulseRingOuter.visibility = View.GONE
        pulseRingInner.scaleX = 1.0f; pulseRingInner.scaleY = 1.0f
        pulseRingOuter.scaleX = 1.0f; pulseRingOuter.scaleY = 1.0f
    }

    /** Show a user-friendly permission explainer before requesting mic */
    private fun requestMicPermissionFriendly() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.RECORD_AUDIO)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                doStartCall()
                return
            }
        } else {
            doStartCall()
            return
        }

        // Show explainer dialog first
        AlertDialog.Builder(requireContext(), R.style.Theme_NMAIT_BottomSheet)
            .setTitle("🎙️ Microphone Access")
            .setMessage("To talk to the Sales Agent, I need access to your microphone.\n\nYour voice is only heard during the call and nothing is recorded.")
            .setPositiveButton("Allow") { _: DialogInterface, _: Int ->
                micPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun doStartCall() {
        hapticClick()
        vapiManager.startCall()
    }

    private fun hapticClick() {
        try {
            val v = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    (requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                }
                else -> requireContext().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                } else { @Suppress("DEPRECATION") v.vibrate(30) }
            }
        } catch (_: Exception) {}
    }

    private fun setupListeners() {
        startStopButton.setOnClickListener {
            if (vapiManager.isCallActive.value) {
                vapiManager.stopCall()
            } else if (!vapiManager.isConnecting.value) {
                // Press animation
                startStopButton.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction {
                    startStopButton.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120)
                        .setInterpolator(OvershootInterpolator()).start()
                }.start()
                // Check permission first
                requestMicPermissionFriendly()
            }
        }

        muteButton.setOnClickListener {
            vapiManager.toggleMute()
            val isMuted = muteIcon.isSelected
            muteIcon.isSelected = !isMuted
            muteIcon.animate().scaleX(0.7f).scaleY(0.7f).setDuration(80).withEndAction {
                muteIcon.imageTintList = if (!isMuted) {
                    ContextCompat.getColorStateList(requireContext(), R.color.voice_active)
                } else {
                    ContextCompat.getColorStateList(requireContext(), R.color.bottom_nav_tint)
                }
                muteIcon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
            }.start()
        }

        view?.findViewById<View>(R.id.hangupButton)?.setOnClickListener { vapiManager.stopCall() }

        speakerButton.setOnClickListener {
            vapiManager.toggleSpeaker()
            val on = vapiManager.isSpeakerOn()
            speakerIcon.isSelected = on
            speakerIcon.imageTintList = ContextCompat.getColorStateList(
                requireContext(), if (on) R.color.voice_active else R.color.bottom_nav_tint
            )
            Toast.makeText(requireContext(), if (on) "Speaker ON" else "Speaker OFF", Toast.LENGTH_SHORT).show()
        }

        volumeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) volumePercent.text = "$p%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { isVolumeDragging = true }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                isVolumeDragging = false
                vapiManager.setVolume((sb?.progress ?: 90) / 100f)
                volumePercent.text = "${sb?.progress ?: 90}%"
            }
        })
    }

    override fun onDestroy() {
        stopPulseAnimation()
        vapiManager.destroy()
        super.onDestroy()
    }
}
