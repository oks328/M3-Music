package io.github.oks328.m3music.account

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.oks328.m3music.ui.theme.M3MusicTheme
import kotlinx.coroutines.launch
import org.json.JSONArray

/** Google's page owns the sign-in form. No JavaScript bridge or form-field inspection is installed. */
class YouTubeLoginActivity : ComponentActivity() {
    private var web: WebView? = null
    private val account by lazy { YouTubeAccount.get(this) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(false)
        setContent {
            val state by account.state.collectAsStateWithLifecycle()
            var host by remember { mutableStateOf("accounts.google.com") }
            var ready by remember { mutableStateOf(false) }
            var progress by remember { mutableStateOf(0) }
            var error by remember { mutableStateOf<String?>(null) }
            BackHandler {
                if (web?.canGoBack() == true && !state.connecting) web?.goBack() else finish()
            }
            M3MusicTheme {
                Scaffold { insets ->
                    Column(Modifier.fillMaxSize().padding(insets)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Connect YouTube Music", style = MaterialTheme.typography.titleLarge)
                            Text(host, style = MaterialTheme.typography.bodyMedium)
                            Text("Sign in on Google's page, choose your YouTube identity, then tap Connect account.", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { finish() }) { Text("Close") }
                                Button(enabled = ready && !state.connecting && !state.initializing, onClick = {
                                    val view = web ?: return@Button
                                    if (Uri.parse(view.url).host != "music.youtube.com") return@Button
                                    view.evaluateJavascript(CONFIG_SCRIPT) { result ->
                                        if (Uri.parse(view.url).host != "music.youtube.com") return@evaluateJavascript
                                        val values = runCatching { JSONArray(result) }.getOrNull() ?: JSONArray()
                                        val candidate = AccountSession(
                                            cookie = CookieManager.getInstance().getCookie(MUSIC_URL).orEmpty(),
                                            visitorData = values.optString(0), dataSyncId = values.optString(1).substringBefore("||"),
                                            pageId = values.optString(2), authUser = values.optString(3, "0"),
                                            clientVersion = values.optString(4).ifBlank { "1.20260114.03.00" })
                                        lifecycleScope.launch {
                                            if (account.controller.connect(candidate)) finish()
                                            else error = account.state.value.error
                                        }
                                    }
                                }) { Text(if (state.connecting) "Verifying…" else "Connect account") }
                            }
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        }
                        if (state.connecting || progress in 1..99) LinearProgressIndicator(Modifier.fillMaxWidth())
                        AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context ->
                            WebView(context).apply {
                                web = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                settings.javaScriptCanOpenWindowsAutomatically = false
                                settings.safeBrowsingEnabled = true
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                        val allowed = !request.isForMainFrame || isAllowedLoginUrl(request.url.toString())
                                        if (!allowed) error = "This link can't be opened in the sign-in window. Return to Google's sign-in page."
                                        return !allowed
                                    }
                                    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                                        host = Uri.parse(url.orEmpty()).host.orEmpty()
                                        ready = false
                                    }
                                    override fun onPageFinished(view: WebView, url: String?) {
                                        host = Uri.parse(url.orEmpty()).host.orEmpty()
                                        ready = host == "music.youtube.com" && runCatching {
                                            SignedAccountRequest.authorization(AccountSession(CookieManager.getInstance().getCookie(MUSIC_URL).orEmpty()), 1)
                                        }.isSuccess
                                    }
                                }
                                // A new connection starts fresh; the validated stored session remains separate.
                                CookieManager.getInstance().removeAllCookies { loadUrl(SIGN_IN_URL) }
                            }
                        })
                        Text("The verified session is stored encrypted on this device. Disconnect removes this app's saved session.",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        web?.apply { stopLoading(); clearFocus(); clearHistory(); destroy() }
        web = null
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
        WebStorage.getInstance().deleteAllData()
        super.onDestroy()
    }

    private companion object {
        const val MUSIC_URL = "https://music.youtube.com"
        const val SIGN_IN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
        const val CONFIG_SCRIPT = "(function(){try{var c=(window.yt&&window.yt.config_)||(window.ytcfg&&window.ytcfg.data_)||{};var cl=(c.INNERTUBE_CONTEXT||{}).client||{};return [c.VISITOR_DATA||cl.visitorData||'',c.DATASYNC_ID||'',c.DELEGATED_SESSION_ID||'',String(c.SESSION_INDEX||0),c.INNERTUBE_CLIENT_VERSION||cl.clientVersion||''];}catch(e){return []}})()"
    }
}
