package io.github.oks328.m3music.account

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class SignedAccountRequestTest {
    @Test fun signsTheSelectedCookieAtTheMusicOrigin() {
        val session = AccountSession("PREF=not-a-secret; SAPISID=dummy=value; OTHER=ignored")
        val hash = MessageDigest.getInstance("SHA-1").digest("1700000000 dummy=value https://music.youtube.com".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals("SAPISIDHASH 1700000000_$hash", SignedAccountRequest.authorization(session, 1700000000))
    }
    @Test fun secureCookieCanAuthenticateWhenSapisidIsAbsent() {
        assertTrue(SignedAccountRequest.authorization(AccountSession("__Secure-3PAPISID=dummy"), 10).startsWith("SAPISIDHASH 10_"))
    }
    @Test fun anonymousOrInjectedCookieHeadersAreRejected() {
        listOf("PREF=anonymous", "SAPISID=", "SAPISID=one\r\nX-Injected: yes").forEach {
            assertThrows(IllegalArgumentException::class.java) { SignedAccountRequest.authorization(AccountSession(it), 10) }
        }
    }
    @Test fun sessionDiagnosticsDoNotExposeCredentials() {
        assertFalse(AccountSession("SAPISID=private-test-value").toString().contains("private-test-value"))
    }
    @Test fun loginNavigationRequiresAnExactTrustedHttpsHost() {
        listOf("https://accounts.google.com/ServiceLogin", "https://music.youtube.com/", "https://consent.google.com/").forEach {
            assertTrue(it, isAllowedLoginUrl(it))
        }
        listOf("http://music.youtube.com/", "https://music.youtube.com.evil.example/", "https://evil.example/music.youtube.com",
            "https://accounts.google.com@evil.example/", "javascript:alert(1)", "file:///sdcard/login.html").forEach {
            assertFalse(it, isAllowedLoginUrl(it))
        }
    }
    @Test fun deviceVerificationRequiresTheExactGoogleHttpsHost() {
        assertTrue(isAllowedLoginUrl("https://gds.google.com/"))
        listOf("http://gds.google.com/", "https://gds.google.com.evil.example/",
            "https://gds.google.com@evil.example/", "https://gds.google.com:8443/").forEach {
            assertFalse(it, isAllowedLoginUrl(it))
        }
    }
    @Test fun philippinesVerificationRedirectRequiresTheExactHttpsHost() {
        assertTrue(isAllowedLoginUrl("https://accounts.google.com.ph/accounts/SetSID"))
        listOf("http://accounts.google.com.ph/", "https://accounts.google.com.ph.evil.example/",
            "https://accounts.google.com.ph@evil.example/", "https://accounts.google.com.ph:8443/",
            "https://untrusted.google.com.ph/").forEach { assertFalse(it, isAllowedLoginUrl(it)) }
    }
}
