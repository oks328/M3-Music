package io.github.oks328.m3music.ui

import org.junit.Assert.*
import org.junit.Test

class ArtworkSizeTest {
    @Test fun upgradesKnownHostsAndPreservesCropFlags() {
        listOf("lh3.googleusercontent.com", "yt3.googleusercontent.com").forEach {
            assertEquals("https://$it/cover=w720-h720-p-l90-rj", largeArtworkUrl("https://$it/cover=w60-h60-p-l90-rj"))
        }
    }
    @Test fun retainsLargerAndUnfamiliarSources() {
        listOf("https://lh3.googleusercontent.com/cover=w1000-h1000-l90-rj",
            "https://i.ytimg.com/vi/id/hqdefault.jpg", "https://example.com/cover=w60-h60",
            "https://yt3.googleusercontent.com.evil.example/cover=w60-h60", "https://yt3.googleusercontent.com/cover=w60-h60?signature=test",
            "https://user@yt3.googleusercontent.com/cover=w60-h60", "not a url").forEach { assertEquals(it, largeArtworkUrl(it)) }
        assertNull(largeArtworkUrl(null))
    }
}
