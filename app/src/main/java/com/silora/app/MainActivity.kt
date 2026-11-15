package com.silora.app

import android.app.Activity
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
import androidx.webkit.WebViewAssetLoader

class MainActivity : Activity() {
    private lateinit var web: WebView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        createWebView()
    }
    private fun createWebView() {
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web = WebView(this)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return loader.shouldInterceptRequest(request.url)
                    ?: WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), java.io.ByteArrayInputStream(byteArrayOf()))
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                view.destroy()
                createWebView()
                return true
            }
        }
        setContentView(web)
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }
    @Deprecated("Activity back callback for initial target SDK")
    override fun onBackPressed() { web.evaluateJavascript("window.goBack()") { handled -> if (handled != "true") finish() } }
    override fun onDestroy() { web.destroy(); super.onDestroy() }
}
