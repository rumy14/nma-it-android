package com.nmait.app

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.nmait.app.ui.adapters.ViewPagerAdapter

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

        adapter = ViewPagerAdapter(this)
        viewPager.adapter = adapter

        // Disable swipe on ViewPager so tabs are nav-only (avoids accidental swipes)
        viewPager.isUserInputEnabled = false

        // Bottom nav item selection → switch ViewPager page
        bottomNav.setOnItemSelectedListener { item ->
            val position = when (item.itemId) {
                R.id.nav_home -> 0
                R.id.nav_services -> 1
                R.id.nav_contact -> 2
                R.id.nav_blog -> 3
                else -> return@setOnItemSelectedListener false
            }
            viewPager.setCurrentItem(position, false)
            true
        }

        // Handle back — let active WebView go back first
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val fragment = supportFragmentManager
                    .findFragmentByTag("f${viewPager.currentItem}")
                if (fragment is com.nmait.app.ui.fragments.WebViewFragment) {
                    if (!fragment.goBack()) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // ViewPager page change → update bottom nav selection
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val menuItemId = when (position) {
                    0 -> R.id.nav_home
                    1 -> R.id.nav_services
                    2 -> R.id.nav_contact
                    3 -> R.id.nav_blog
                    else -> R.id.nav_home
                }
                bottomNav.menu.findItem(menuItemId)?.isChecked = true
            }
        })
    }
}
