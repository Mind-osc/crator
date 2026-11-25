package com.crator.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import java.net.URLEncoder

/** Το claude.ai μέσα στο Crator, με τον λογαριασμό του χρήστη (χωρίς API key). */
object ClaudeWeb {
    private const val HOME = "https://claude.ai/new"
    private var view: WebView? = null
    val loading = mutableStateOf(true)

    private val INTERNAL = listOf("claude.ai", "anthropic.com", "accounts.google.com", "appleid.apple.com", "challenges.cloudflare.com")

    @SuppressLint("SetJavaScriptEnabled")
    fun get(activity: MainActivity): WebView {
        view?.let { return it }
        val wv = WebView(activity)
        wv.setBackgroundColor(AColor.TRANSPARENT)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            // Χωρίς το «; wv» για να το βλέπουν οι σελίδες σύνδεσης σαν κανονικό Chrome
            userAgentString = userAgentString.replace("; wv", "")
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            runCatching { WebSettingsCompat.setAlgorithmicDarkening(wv.settings, true) }
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest): Boolean {
                val host = req.url.host ?: return false
                if (INTERNAL.any { host == it || host.endsWith(".$it") }) return false
                Actions.start(activity, Intent(Intent.ACTION_VIEW, req.url))
                return true
            }
            override fun onPageStarted(v: WebView?, url: String?, favicon: android.graphics.Bitmap?) { loading.value = true }
            override fun onPageFinished(v: WebView?, url: String?) {
                loading.value = false
                CookieManager.getInstance().flush()
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(v: WebView?, cb: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                if (cb == null || params == null) return false
                return activity.pickFiles(params.createIntent()) { code, data ->
                    cb.onReceiveValue(FileChooserParams.parseResult(code, data))
                }
            }
        }
        wv.loadUrl(HOME)
        view = wv
        return wv
    }

    fun currentUrl(): String? = view?.url

    fun newChat(text: String?) {
        val v = view ?: return
        val url = if (text.isNullOrBlank()) HOME else HOME + "?q=" + URLEncoder.encode(text, "UTF-8")
        v.loadUrl(url)
    }

    fun back(): Boolean {
        val v = view ?: return false
        if (v.canGoBack()) { v.goBack(); return true }
        return false
    }

    fun signOut(ctx: Context) {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        view?.clearCache(true)
        view?.loadUrl(HOME)
        Toast.makeText(ctx, "Αποσυνδέθηκες από το claude.ai", Toast.LENGTH_SHORT).show()
    }

    fun release() {
        view?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }
        view = null
    }
}

@Composable
fun ClaudeWebView(seed: String, modifier: Modifier = Modifier) {
    val activity = LocalContext.current as MainActivity
    val loading by ClaudeWeb.loading
    LaunchedEffect(seed) { if (seed.isNotBlank()) ClaudeWeb.newChat(seed) }
    Box(modifier) {
        AndroidView(
            factory = { ClaudeWeb.get(activity).also { (it.parent as? ViewGroup)?.removeView(it) } },
            modifier = Modifier.fillMaxSize()
        )
        AnimatedVisibility(loading, modifier = Modifier.align(Alignment.Center), exit = fadeOut(tween(300))) {
            CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
        }
    }
}

/** Παράδοση στην επίσημη εφαρμογή Claude */
object ClaudeApp {
    const val PKG = "com.anthropic.claude"

    fun installed(ctx: Context) = ctx.packageManager.getLaunchIntentForPackage(PKG) != null

    fun send(ctx: Context, text: String): Boolean {
        if (!installed(ctx)) { missing(ctx); return false }
        val t = text.trim()
        if (t.isEmpty()) { open(ctx); return true }
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t).setPackage(PKG)
        if (Actions.start(ctx, i)) return true
        // αν η εφαρμογή δεν δέχεται κοινοποίηση, ανοίγει και το κείμενο μένει στο πρόχειρο
        ctx.getSystemService(android.content.ClipboardManager::class.java)
            ?.setPrimaryClip(android.content.ClipData.newPlainText("claude", t))
        Toast.makeText(ctx, "Το μήνυμα αντιγράφηκε· επικόλλησέ το στον Claude", Toast.LENGTH_LONG).show()
        open(ctx)
        return true
    }

    fun open(ctx: Context) {
        val i = ctx.packageManager.getLaunchIntentForPackage(PKG)
        if (i == null || !Actions.start(ctx, i)) missing(ctx)
    }

    private fun missing(ctx: Context) {
        Toast.makeText(ctx, "Δεν βρέθηκε η εφαρμογή Claude", Toast.LENGTH_SHORT).show()
        Actions.openUrl(ctx, "market://details?id=$PKG")
    }
}
