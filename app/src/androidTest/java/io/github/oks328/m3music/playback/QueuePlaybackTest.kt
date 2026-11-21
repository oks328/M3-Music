@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package io.github.oks328.m3music.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.platform.app.InstrumentationRegistry
import io.github.oks328.m3music.music.AudioSource
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class QueuePlaybackTest {
    @Test fun playlistAdvancesAutomaticallyThroughLazilyResolvedSongs() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val wave = silentWave()
        val finished = CountDownLatch(1)
        val failure = AtomicReference<PlaybackException?>()
        val transitions = mutableListOf<String>()
        val resolved = java.util.Collections.synchronizedList(mutableListOf<String>())
        var player: ExoPlayer? = null
        try {
            instrumentation.runOnMainSync {
                val factory = TrackDataSourceFactory(DataSource.Factory { ByteArrayDataSource(wave) }) { id ->
                    resolved.add(id); AudioSource("memory:///$id", "audio/wav", 0)
                }
                player = ExoPlayer.Builder(instrumentation.targetContext)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(factory)).build().apply {
                        volume = 0f
                        addListener(object : Player.Listener {
                            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                                mediaItem?.let { transitions += it.mediaId }
                            }
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_ENDED) finished.countDown()
                            }
                            override fun onPlayerError(error: PlaybackException) { failure.set(error); finished.countDown() }
                        })
                        setMediaItems(listOf("first", "second").map { id ->
                            MediaItem.Builder().setMediaId(id).setUri("track:///$id").build()
                        })
                        prepare(); play()
                    }
            }
            assertTrue("Playlist should finish both short audio tracks", finished.await(15, TimeUnit.SECONDS))
            assertNull("Queue playback should decode successfully", failure.get())
            assertEquals(listOf("first", "second"), transitions)
            assertEquals(setOf("first", "second"), resolved.toSet())
        } finally { instrumentation.runOnMainSync { player?.release() } }
    }

    private fun silentWave(): ByteArray {
        val samples = 2400
        return ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples * 2); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(8000); putInt(16000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(samples * 2)
            repeat(samples) { putShort(0) }
        }.array()
    }
}
