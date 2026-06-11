package com.nmait.app.ui.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.nmait.app.ui.fragments.WebViewFragment

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val tabs = listOf(
        Tab("Home", "https://ai.nma-it.com/"),
        Tab("Services", "https://ai.nma-it.com/services"),
        Tab("Contact", "https://ai.nma-it.com/contact"),
        Tab("Blog", "https://ai.nma-it.com/blog")
    )

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        return WebViewFragment.newInstance(tabs[position].url)
    }

    fun getTitle(position: Int): String = tabs[position].title

    private data class Tab(val title: String, val url: String)
}
