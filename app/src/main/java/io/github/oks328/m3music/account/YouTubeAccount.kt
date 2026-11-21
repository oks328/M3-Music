package io.github.oks328.m3music.account

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal class YouTubeAccount private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val controller = AccountController(scope, AccountSessionVault(context.applicationContext), YouTubeAccountClient())
    val state = controller.state

    fun disconnect() {
        controller.disconnect()
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
    }

    companion object {
        @Volatile private var instance: YouTubeAccount? = null
        fun get(context: Context): YouTubeAccount = instance ?: synchronized(this) {
            instance ?: YouTubeAccount(context.applicationContext).also { instance = it }
        }
    }
}
