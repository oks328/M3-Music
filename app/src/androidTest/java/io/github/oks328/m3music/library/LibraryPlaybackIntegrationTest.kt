@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package io.github.oks328.m3music.library

import android.content.ComponentName
import androidx.lifecycle.ViewModelProvider
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.oks328.m3music.MainActivity
import io.github.oks328.m3music.MusicViewModel
import io.github.oks328.m3music.downloads.DownloadStatus
import io.github.oks328.m3music.playback.PlaybackService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class LibraryPlaybackIntegrationTest {
    @Test fun savedPlaylistUsesThePlaybackServiceAndRecordsHistory() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifySavedLibrary") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            lateinit var model: MusicViewModel
            lateinit var future: com.google.common.util.concurrent.ListenableFuture<MediaController>
            activity.onActivity {
                model = ViewModelProvider(it)[MusicViewModel::class.java]
                future = MediaController.Builder(it, SessionToken(it, ComponentName(it, PlaybackService::class.java))).buildAsync()
            }
            val controller = future.get(15, TimeUnit.SECONDS)
            var originalVolume = 1f
            var originalItems = emptyList<androidx.media3.common.MediaItem>()
            var originalIndex = 0
            var originalPosition = 0L
            var originalPlaying = false
            var captured = false
            try {
                val saved = withTimeout(15_000) { model.downloads.first { !it.loading } }.items
                    .firstOrNull { it.status == DownloadStatus.COMPLETED }
                assumeTrue("Requires an existing saved song; test does not download or remove songs", saved != null)
                val song = requireNotNull(saved).track
                withTimeout(15_000) { model.playback.first { it.connected } }
                activity.onActivity {
                    originalVolume = controller.volume
                    originalItems = (0 until controller.mediaItemCount).map(controller::getMediaItemAt)
                    originalIndex = controller.currentMediaItemIndex.coerceAtLeast(0)
                    originalPosition = controller.currentPosition.coerceAtLeast(0)
                    originalPlaying = controller.playWhenReady
                    captured = true
                    controller.volume = 0f
                    model.playPlaylist(listOf(song, song), 0)
                }
                val playing = withTimeout(20_000) { model.playback.first { it.playing && it.positionMs > 500 } }
                assertTrue("Saved playlist audio must use local storage", playing.offline)
                assertEquals(2, playing.queueSize)
                assertEquals(listOf(song.id, song.id), playing.queue.map { it.id })
                assertTrue(playing.canNext)
                activity.onActivity { model.playQueueItem(1) }
                val next = withTimeout(20_000) { model.playback.first { it.queueIndex == 1 && it.playing } }
                assertTrue(next.canPrevious)
                activity.onActivity { model.seek(3000) }
                val sought = withTimeout(10_000) { model.playback.first { it.queueIndex == 1 && it.positionMs in 2500..10000 && it.playing } }
                assertTrue(sought.offline)
                val history = withTimeout(10_000) { model.library.first { it.history.any { track -> track.id == song.id } } }
                assertEquals(1, history.history.count { it.id == song.id })
            } finally {
                instrumentation.runOnMainSync {
                    if (captured) {
                        controller.stop()
                        if (originalItems.isEmpty()) controller.clearMediaItems() else {
                            controller.setMediaItems(originalItems, originalIndex, originalPosition)
                            controller.prepare()
                            controller.playWhenReady = originalPlaying
                        }
                        controller.volume = originalVolume
                    }
                    MediaController.releaseFuture(future)
                }
            }
        }
    }
}
