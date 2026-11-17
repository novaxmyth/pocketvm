package com.antidaze.pocketvm.guest.apps

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.core.content.ContextCompat
import com.antidaze.pocketvm.R

class VirtualBrowserController(
    private val context: Context
) {
    @SuppressLint("SetJavaScriptEnabled")
    fun createView(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.background))
        }

        // Top Navigation Bar
        val navBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_card_cyber)
            setPadding(12, 8, 12, 8)
        }

        var webView: WebView? = null

        val btnBack = ImageButton(context).apply {
            setImageResource(R.drawable.ic_back)
            setColorFilter(Color.WHITE)
            setBackgroundResource(R.drawable.bg_chip)
            layoutParams = LinearLayout.LayoutParams(36, 36).apply { rightMargin = 8 }
            setOnClickListener {
                if (webView?.canGoBack() == true) webView?.goBack()
            }
        }
        navBar.addView(btnBack)

        val editUrl = EditText(context).apply {
            hint = "Search or type URL…"
            setText("https://duckduckgo.com")
            setTextColor(Color.WHITE)
            textSize = 13f
            setSingleLine()
            setBackgroundResource(R.drawable.bg_chip)
            setPadding(16, 8, 16, 8)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        navBar.addView(editUrl)

        val btnGo = ImageButton(context).apply {
            setImageResource(R.drawable.ic_play)
            setColorFilter(ContextCompat.getColor(context, R.color.primary))
            setBackgroundResource(R.drawable.bg_chip)
            layoutParams = LinearLayout.LayoutParams(36, 36).apply { leftMargin = 8 }
            setOnClickListener {
                var url = editUrl.text.toString().trim()
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = if (url.contains(".")) "https://$url" else "https://duckduckgo.com/?q=${android.net.Uri.encode(url)}"
                }
                webView?.loadUrl(url)
            }
        }
        navBar.addView(btnGo)
        root.addView(navBar)

        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                6
            )
            visibility = View.GONE
        }
        root.addView(progressBar)

        val wv = WebView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    url?.let { editUrl.setText(it) }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress < 100) {
                        progressBar.visibility = View.VISIBLE
                        progressBar.progress = newProgress
                    } else {
                        progressBar.visibility = View.GONE
                    }
                }
            }
        }
        webView = wv
        root.addView(wv)

        wv.loadUrl("https://duckduckgo.com")
        return root
    }
}
