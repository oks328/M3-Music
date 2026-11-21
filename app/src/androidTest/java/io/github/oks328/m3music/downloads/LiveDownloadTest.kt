@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package io.github.oks328.m3music.downloads

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.oks328.m3music.MainActivity
import io.github.oks328.m3music.music.AudioSource
import io.github.oks328.m3music.music.YouTubeMusicRepository
import io.github.oks328.m3music.playback.PlaybackResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LiveDownloadTest {
    @Test fun downloadSongThenPlayWithNetworkForbidden() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runLiveDownloads") == "true")
        val track = YouTubeMusicRepository.search("Daft Punk Get Lucky").first()
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            lateinit var downloads: OfflineDownloads
            activity.onActivity { downloads = OfflineDownloads.get(it); downloads.download(track) }
            val completed = withTimeout(150_000) {
                downloads.state.first { state -> state.items.any {
                    it.track.id == track.id && it.status in listOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED)
                } }
            }.items.first { it.track.id == track.id }
            assertEquals("A real song should download", DownloadStatus.COMPLETED, completed.status)
            assertTrue("Download must contain audio bytes", completed.bytes > 0)
            val source = PlaybackResolver(downloads::completedSource) {
                error("Completed songs must not contact YouTube to start playback")
            }.resolve(track.id)
            verifyLocalAudio(downloads, track.id, source)
        }
    }

    @Test fun savedSongPlaysAfterProcessRestart() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyPersistedDownload") == "true")
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            lateinit var downloads: OfflineDownloads
            activity.onActivity { downloads = OfflineDownloads.get(it) }
            val saved = withTimeout(15_000) {
                downloads.state.first { !it.loading }
            }.items.first { it.status == DownloadStatus.COMPLETED }
            val source = PlaybackResolver(downloads::completedSource) {
                error("Restarted offline playback must not resolve a network URL")
            }.resolve(saved.track.id)
            verifyLocalAudio(downloads, saved.track.id, source)
        }
    }

    private suspend fun verifyLocalAudio(downloads: OfflineDownloads, id: String, source: AudioSource) {
        assertTrue(source.url.startsWith("offline:"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ready = CountDownLatch(1)
        val failure = AtomicReference<PlaybackException?>()
        var player: ExoPlayer? = null
        try {
            instrumentation.runOnMainSync {
                player = ExoPlayer.Builder(instrumentation.targetContext)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(OfflineDataSourceFactory(downloads.cache,
                        DataSource.Factory { error("Offline playback must never create an HTTP source") })))
                    .build().apply {
                        volume = 0f // Test actual progress without playing sound aloud.
                        addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(state: Int) {
                                if (state == Player.STATE_READY) ready.countDown()
                            }
                            override fun onPlayerError(error: PlaybackException) {
                                failure.set(error)
                                ready.countDown()
                            }
                        })
                        setMediaItem(MediaItem.Builder().setMediaId(id).setUri(source.url)
                            .setMimeType(source.mimeType).setCustomCacheKey(id).build())
                        prepare()
                        play()
                    }
            }
            assertTrue("Downloaded audio should become ready", ready.await(20, TimeUnit.SECONDS))
            assertNull("Downloaded audio should decode without networking", failure.get())
            delay(1500)
            instrumentation.runOnMainSync {
                assertTrue("Local playback position should advance", requireNotNull(player).currentPosition > 500)
            }
        } finally { instrumentation.runOnMainSync { player?.release() } }
    }
}
