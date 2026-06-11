package com.nmait.app.ui.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.nmait.app.ui.fragments.ContactFragment
import com.nmait.app.ui.fragments.HomeFragment
import com.nmait.app.ui.fragments.ServicesFragment
import com.nmait.app.ui.vapi.VapiVoiceFragment

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val tabs = listOf(
        Tab("Home", HomeFragment::class.java),
        Tab("Services", ServicesFragment::class.java),
        Tab("Contact", ContactFragment::class.java),
        Tab("Voice", VapiVoiceFragment::class.java)
    )

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        return when (tabs[position].fragmentClass) {
            HomeFragment::class.java -> HomeFragment()
            ServicesFragment::class.java -> ServicesFragment()
            ContactFragment::class.java -> ContactFragment()
            VapiVoiceFragment::class.java -> VapiVoiceFragment()
            else -> HomeFragment()
        }
    }

    fun getTitle(position: Int): String = tabs[position].title

    private data class Tab(
        val title: String,
        val fragmentClass: Class<out Fragment>
    )
}
