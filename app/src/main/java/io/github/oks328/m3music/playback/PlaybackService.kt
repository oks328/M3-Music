@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package io.github.oks328.m3music.playback

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences by lazy { getSharedPreferences(AutoplayPreferences, MODE_PRIVATE) }
    private var autoplayJob: Job? = null
    private var autoplayRequest: AutoplayRequest? = null
    private var retryAutoplayAtEnd = false
    private data class AutoplayRequest(val artist: String, val title: String, val queuedIds: Set<String>)

    private val autoplayPreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AutoplayEnabled) (session?.player as? ExoPlayer)?.let(::updateAutoplay)
    }

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
        val engine = player
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
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED)) updateAutoplay(engine)
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
        session = MediaSession.Builder(this, player).setCallback(queueCommands(player)).setSessionActivity(openApp).build()
        preferences.registerOnSharedPreferenceChangeListener(autoplayPreferenceListener)
    }

    private fun queueCommands(player: ExoPlayer) = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            if (controller.packageName != packageName) return super.onConnect(session, controller)
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon().add(InsertShuffledQueue).build())
                .build()
        }

        @Suppress("DEPRECATION")
        override fun onCustomCommand(
            session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (controller.packageName != packageName || customCommand != InsertShuffledQueue) {
                return super.onCustomCommand(session, controller, customCommand, args)
            }
            val items = args.getParcelableArrayList<Bundle>("items").orEmpty().map { MediaItem.fromBundle(it) }
            val next = args.getBoolean("next")
            if (!player.shuffleModeEnabled || player.mediaItemCount == 0) {
                val index = if (next && player.mediaItemCount > 0) player.currentMediaItemIndex + 1
                    else player.mediaItemCount
                player.addMediaItems(index, items)
            } else {
                val first = player.mediaItemCount
                val order = player.currentTimeline.queueOrder(true).toMutableList()
                val index = if (next) order.indexOf(player.currentMediaItemIndex) + 1 else order.size
                player.addMediaItems(items)
                order.addAll(index, (first until player.mediaItemCount).toList())
                setShuffleOrder(player, order)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private fun setShuffleOrder(player: ExoPlayer, order: List<Int>) {
        player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), kotlin.random.Random.nextLong()))
    }

    private fun canAutoplay(player: Player): Boolean =
        player.playbackState == Player.STATE_ENDED && player.playWhenReady &&
            player.repeatMode == Player.REPEAT_MODE_OFF && !player.hasNextMediaItem() &&
            preferences.getBoolean(AutoplayEnabled, false)

    private fun autoplayRequest(player: ExoPlayer): AutoplayRequest? {
        if (!preferences.getBoolean(AutoplayEnabled, false) || player.mediaItemCount == 0) return null
        val queued = List(player.mediaItemCount, player::getMediaItemAt)
        if (queued.isDownloadQueue()) return null
        val last = player.currentTimeline.getLastWindowIndex(player.shuffleModeEnabled)
        val metadata = queued.getOrNull(last)?.mediaMetadata ?: return null
        val artist = metadata.artist?.toString().orEmpty().trim()
        val title = metadata.title?.toString().orEmpty().trim()
        if (artist.isEmpty() && title.isEmpty()) return null
        return AutoplayRequest(artist, title, queued.map { it.mediaId }.toSet())
    }

    private fun updateAutoplay(player: ExoPlayer) {
        val request = autoplayRequest(player)
        if (request != autoplayRequest || (retryAutoplayAtEnd && canAutoplay(player))) {
            retryAutoplayAtEnd = false
            autoplayRequest = request
            autoplayJob?.cancel()
            preview.value = AutoplayPreview(loading = request != null)
            if (request != null) autoplayJob = scope.launch {
                val tracks = try {
                    val suggestions = if (request.artist.isBlank()) emptyList()
                    else YouTubeMusicRepository.search(request.artist).filterNot { it.id in request.queuedIds }
                    if (suggestions.isNotEmpty() || request.title.isBlank()) suggestions.take(10)
                    else YouTubeMusicRepository.search(request.title).filterNot { it.id in request.queuedIds }.take(10)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    retryAutoplayAtEnd = !canAutoplay(player)
                    emptyList()
                }
                if (autoplayRequest != request) return@launch
                preview.value = AutoplayPreview(tracks)
                continuePlayback(player)
            }
        }
        continuePlayback(player)
    }

    private fun continuePlayback(player: ExoPlayer) {
        val suggestions = preview.value.tracks
        if (suggestions.isEmpty() || !canAutoplay(player)) return
        preview.value = AutoplayPreview()
        val first = player.mediaItemCount
        val previousOrder = player.currentTimeline.queueOrder(player.shuffleModeEnabled)
        player.addMediaItems(suggestions.map { it.queueItem(saved = false) })
        if (player.shuffleModeEnabled) {
            setShuffleOrder(player, previousOrder + (first until player.mediaItemCount))
        }
        player.seekTo(first, 0)
        player.play()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        preferences.unregisterOnSharedPreferenceChangeListener(autoplayPreferenceListener)
        scope.cancel()
        preview.value = AutoplayPreview()
        session?.let { current ->
            current.release()
            current.player.release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        private val preview = MutableStateFlow(AutoplayPreview())
        internal val autoplayPreview = preview.asStateFlow()
    }
}
