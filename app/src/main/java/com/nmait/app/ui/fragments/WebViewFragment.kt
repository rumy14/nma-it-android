package com.nmait.app.ui.fragments

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

class WebViewFragment : Fragment() {

    private var webView: WebView? = null
    private var progressBar: ProgressBar? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(
            com.nmait.app.R.layout.fragment_webview,
            container,
            false
        )
        webView = root.findViewById(com.nmait.app.R.id.webView)
        progressBar = root.findViewById(com.nmait.app.R.id.progressBar)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val url = arguments?.getString("url") ?: "https://ai.nma-it.com"

        webView?.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                allowFileAccess = false
                allowContentAccess = false
                if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                    WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_ON)
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    progressBar?.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar?.visibility = View.GONE
                    // Prevent horizontal scrolling
                    view?.evaluateJavascript(
                        """
                        (function() {
                            document.body.style.overflowX = 'hidden';
                            document.documentElement.style.overflowX = 'hidden';
                            var meta = document.querySelector('meta[name="viewport"]');
                            if (!meta) {
                                meta = document.createElement('meta');
                                meta.name = 'viewport';
                                document.head.appendChild(meta);
                            }
                            meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes';
                        })();
                        """.trimIndent(),
                        null
                    )
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean = false
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar?.progress = newProgress
                }
            }

            loadUrl(url)
        }
    }

    override fun onDestroyView() {
        webView?.destroy()
        webView = null
        progressBar = null
        super.onDestroyView()
    }

    /**
     * Navigate back in the WebView history.
     * @return true if the WebView handled the back (had history), false otherwise.
     */
    fun goBack(): Boolean {
        return webView?.let {
            if (it.canGoBack()) {
                it.goBack()
                true
            } else {
                false
            }
        } ?: false
    }

    companion object {
        fun newInstance(url: String): WebViewFragment {
            val fragment = WebViewFragment()
            fragment.arguments = Bundle().apply {
                putString("url", url)
            }
            return fragment
        }
    }
}
