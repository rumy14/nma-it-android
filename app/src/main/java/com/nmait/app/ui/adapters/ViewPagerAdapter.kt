package com.nmait.app.ui.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.nmait.app.ui.fragments.ContactFragment
import com.nmait.app.ui.fragments.HomeFragment
import com.nmait.app.ui.fragments.ServicesFragment
import com.nmait.app.ui.fragments.WebViewFragment

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val tabs = listOf(
        Tab("Home", HomeFragment::class.java, null),
        Tab("Services", ServicesFragment::class.java, null),
        Tab("Contact", ContactFragment::class.java, null),
        Tab("Blog", WebViewFragment::class.java, "https://ai.nma-it.com/blog/")
    )

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        val tab = tabs[position]
        return when (tab.fragmentClass) {
            HomeFragment::class.java -> HomeFragment()
            ServicesFragment::class.java -> ServicesFragment()
            ContactFragment::class.java -> ContactFragment()
            WebViewFragment::class.java -> WebViewFragment.newInstance(tab.url ?: "https://ai.nma-it.com/")
            else -> WebViewFragment.newInstance("https://ai.nma-it.com/")
        }
    }

    fun getTitle(position: Int): String = tabs[position].title

    private data class Tab(
        val title: String,
        val fragmentClass: Class<out Fragment>,
        val url: String?
    )
}
