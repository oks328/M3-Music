@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package io.github.oks328.m3music.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.github.oks328.m3music.downloads.OfflineDataSourceFactory
import io.github.oks328.m3music.downloads.OfflineDownloads
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.github.oks328.m3music.MainActivity
import io.github.oks328.m3music.library.LocalLibrary
import io.github.oks328.m3music.music.Track
import io.github.oks328.m3music.music.YouTubeMusicRepository
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import kotlinx.coroutines.runBlocking

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val sources = OfflineDataSourceFactory(OfflineDownloads.get(this).cache, DefaultDataSource.Factory(this))
        val resolver = PlaybackResolver(OfflineDownloads.get(this)::completedSource, YouTubeMusicRepository::resolve)
        val queuedSources = TrackDataSourceFactory(sources) { id -> runBlocking { resolver.resolve(id) } }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(queuedSources))
            .setWakeMode(C.WAKE_MODE_NETWORK).build().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
        }
        player.addListener(object : Player.Listener {
            private var recordedId: String? = null
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem?.mediaId != recordedId) recordedId = null
                recordCurrent()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) return
                recordCurrent()
            }
            private fun recordCurrent() {
                if (!player.isPlaying) return
                val item = player.currentMediaItem ?: return
                if (recordedId == item.mediaId || item.mediaId.isBlank()) return
                recordedId = item.mediaId
                LocalLibrary.get(this@PlaybackService).recordPlayed(Track(item.mediaId,
                    item.mediaMetadata.title?.toString().orEmpty(), item.mediaMetadata.artist?.toString().orEmpty(),
                    item.mediaMetadata.artworkUri?.toString(), player.duration.coerceAtLeast(0) / 1000))
            }
        })
        val openApp = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(openApp).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.let { current ->
            current.release()
            current.player.release()
        }
        session = null
        super.onDestroy()
    }
}
