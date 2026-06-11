package com.nmait.app.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.nmait.app.R

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.actionBookCall).setOnClickListener {
            val fragment = parentFragmentManager.primaryNavigationFragment
            val activity = activity
            // Open the contact form externally via the website modal trigger
            // or launch bottom sheet — for now open URL
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ai.nma-it.com/#contact"))
            startActivity(intent)
        }

        view.findViewById<View>(R.id.actionChat).setOnClickListener {
            // Open the chat bottom sheet
            val bottomSheet = com.nmait.app.ui.chat.ChatBottomSheetFragment()
            bottomSheet.show(parentFragmentManager, com.nmait.app.ui.chat.ChatBottomSheetFragment.TAG)
        }

        view.findViewById<View>(R.id.actionServices).setOnClickListener {
            // Switch to Services tab (index 1) via the parent activity
            val activity = activity as? com.nmait.app.MainActivity
            activity?.switchToTab(1)
        }

        view.findViewById<View>(R.id.latestBlog).setOnClickListener {
            // Open blog in browser or switch to blog tab (index 3)
            val activity = activity as? com.nmait.app.MainActivity
            activity?.switchToTab(3)
        }
    }
}
