package io.github.oks328.m3music.account

import java.net.URI
import java.security.MessageDigest

internal object SignedAccountRequest {
    const val ORIGIN = "https://music.youtube.com"
    fun authorization(session: AccountSession, timestamp: Long): String {
        require(listOf(session.cookie, session.visitorData, session.dataSyncId, session.pageId, session.clientVersion)
            .none { '\r' in it || '\n' in it }) { "Invalid account session." }
        require(session.authUser.matches(Regex("[0-9]{1,3}"))) { "Invalid account selection." }
        val cookies = session.cookie.split(';').mapNotNull { part ->
            val name = part.substringBefore('=', "").trim()
            if (name.isBlank()) null else name to part.substringAfter('=').trim()
        }.toMap()
        val secret = listOf("SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID")
            .firstNotNullOfOrNull { cookies[it]?.takeIf(String::isNotBlank) }
            ?: throw IllegalArgumentException("Complete Google sign-in before connecting this account.")
        val hash = MessageDigest.getInstance("SHA-1").digest("$timestamp $secret $ORIGIN".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_$hash"
    }
}

internal fun isAllowedLoginUrl(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme.equals("https", true) && uri.userInfo == null && uri.port in listOf(-1, 443) &&
        uri.host?.lowercase() in setOf("accounts.google.com", "accounts.google.com.ph", "accounts.youtube.com", "music.youtube.com",
            "www.youtube.com", "consent.google.com", "consent.youtube.com", "myaccount.google.com", "www.google.com")
}.getOrDefault(false)
