package com.nmait.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.nmait.app.ui.adapters.ViewPagerAdapter
import com.nmait.app.ui.chat.ChatBottomSheetFragment
import com.nmait.app.ui.voice.VapiVoiceManager

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var adapter: ViewPagerAdapter
    private lateinit var vapiManager: VapiVoiceManager

    // Vapi credentials (from website widget)
    private val vapiPublicKey = "903ffbab-e2a4-43de-9db6-772c9d2933f5"
    private val vapiAssistantId = "93aa1fbb-6ea6-4e35-9af9-fa8bf61af796"

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        bottomNav = findViewById(R.id.bottomNavigation)

        // ─── ViewPager ───
        adapter = ViewPagerAdapter(this)
        viewPager.adapter = adapter
        viewPager.isUserInputEnabled = false

        // ─── Bottom Nav ───
        bottomNav.setOnItemSelectedListener { item ->
            val position = when (item.itemId) {
                R.id.nav_home -> 0
                R.id.nav_services -> 1
                R.id.nav_contact -> 2
                else -> return@setOnItemSelectedListener false
            }
            viewPager.setCurrentItem(position, false)
            true
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val id = when (position) {
                    0 -> R.id.nav_home
                    1 -> R.id.nav_services
                    2 -> R.id.nav_contact
                    else -> R.id.nav_home
                }
                bottomNav.menu.findItem(id)?.isChecked = true
            }
        })

        // ─── Back button ───
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // No WebViews to check anymore — just exit
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })

        // ─── Text Chat FAB ───
        findViewById<FloatingActionButton>(R.id.chatFab).setOnClickListener {
            ChatBottomSheetFragment().show(supportFragmentManager, ChatBottomSheetFragment.TAG)
        }

        // ─── Vapi Voice ───
        vapiManager = VapiVoiceManager(
            context = this,
            publicKey = vapiPublicKey,
            assistantId = vapiAssistantId,
            onCallStarted = {
                runOnUiThread {
                    findViewById<FloatingActionButton>(R.id.vapiFab)
                        .setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.voice_active))
                }
            },
            onCallEnded = {
                runOnUiThread {
                    findViewById<FloatingActionButton>(R.id.vapiFab)
                        .setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary))
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                }
            },
            onTranscript = { text ->
                // Optional: use for live captions
            }
        )
        vapiManager.init()

        findViewById<FloatingActionButton>(R.id.vapiFab).setOnClickListener {
            if (!checkVapiPermissions()) return@setOnClickListener

            if (vapiManager.isCallActive.value) {
                vapiManager.stopCall()
            } else {
                vapiManager.startCall()
            }
        }
    }

    private fun checkVapiPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
                return false
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return false
        }
        return true
    }

    override fun onDestroy() {
        vapiManager.destroy()
        super.onDestroy()
    }

    fun switchToTab(position: Int) {
        if (position in 0..2) {
            viewPager.setCurrentItem(position, false)
        }
    }
}
