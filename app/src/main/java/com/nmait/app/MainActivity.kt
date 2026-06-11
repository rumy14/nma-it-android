package com.nmait.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.nmait.app.ui.adapters.ViewPagerAdapter
import com.nmait.app.ui.chat.ChatBottomSheetFragment

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var adapter: ViewPagerAdapter

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
                R.id.nav_voice -> 3
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
                    3 -> R.id.nav_voice
                    else -> R.id.nav_home
                }
                bottomNav.menu.findItem(id)?.isChecked = true
            }
        })

        // ─── Back button ───
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })

        // ─── Chat FAB ───
        findViewById<FloatingActionButton>(R.id.chatFab).setOnClickListener {
            ChatBottomSheetFragment().show(supportFragmentManager, ChatBottomSheetFragment.TAG)
        }
    }

    fun switchToTab(position: Int) {
        if (position in 0..3) {
            viewPager.setCurrentItem(position, false)
        }
    }
}
