package io.github.oks328.m3music.music

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class YouTubeLiveTest {
    @Test fun searchResultLoadsActualAudio() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runLiveYouTube") == "true")
        val tracks = YouTubeMusicRepository.search("Daft Punk Get Lucky")
        assertTrue("Live song search should return results", tracks.isNotEmpty())
        val source = YouTubeMusicRepository.resolve(tracks.first().id)
        assertTrue(source.url.startsWith("https://"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val finished = CountDownLatch(1)
        val failure = AtomicReference<PlaybackException?>()
        var player: ExoPlayer? = null
        try {
            instrumentation.runOnMainSync {
                player = ExoPlayer.Builder(instrumentation.targetContext).build().apply {
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY) finished.countDown()
                        }
                        override fun onPlayerError(error: PlaybackException) {
                            failure.set(error)
                            finished.countDown()
                        }
                    })
                    setMediaItem(MediaItem.Builder().setUri(source.url).setMimeType(source.mimeType).build())
                    prepare() // Fetch actual bytes without playing audio aloud.
                }
            }
            assertTrue("Audio should become ready within 40 seconds", finished.await(40, TimeUnit.SECONDS))
            assertNull("Audio stream should load successfully", failure.get())
        } finally {
            instrumentation.runOnMainSync { player?.release() }
        }
    }
}
