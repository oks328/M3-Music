package io.github.oks328.m3music.playback

import io.github.oks328.m3music.music.AudioSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PlaybackResolverTest {
    @Test fun completedDownloadPlaysWithoutNetworkAccess() = runTest {
        val saved = AudioSource("offline:///abcdefghijk", "audio/webm", 128)
        val resolver = PlaybackResolver({ saved }, { error("Network must never be used for a saved song") })
        assertEquals(saved, resolver.resolve("abcdefghijk"))
    }
    @Test fun unsavedSongResolvesFromNetwork() = runTest {
        val online = AudioSource("https://example.com/audio", "audio/webm", 128)
        val resolver = PlaybackResolver({ null }, { online })
        assertEquals(online, resolver.resolve("abcdefghijk"))
    }
}
