@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package io.github.oks328.m3music

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.oks328.m3music.music.SearchController
import io.github.oks328.m3music.music.Track
import io.github.oks328.m3music.music.YouTubeMusicRepository
import io.github.oks328.m3music.playback.PlaybackService
import io.github.oks328.m3music.playback.PlaybackState
import io.github.oks328.m3music.playback.PlaybackResolver
import io.github.oks328.m3music.downloads.OfflineDownloads
import io.github.oks328.m3music.downloads.DownloadStatus
import io.github.oks328.m3music.library.LocalLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val localLibrary = LocalLibrary.get(application)
    val library = localLibrary.state
    private val offlineDownloads = OfflineDownloads.get(application)
    val downloads = offlineDownloads.state
    private val playbackResolver = PlaybackResolver(offlineDownloads::completedSource, YouTubeMusicRepository::resolve)
    private val searchController = SearchController(viewModelScope, YouTubeMusicRepository::search)
    val search = searchController.state
    private val mutablePlayback = MutableStateFlow(PlaybackState())
    val playback = mutablePlayback.asStateFlow()
    private var controller: MediaController? = null
    private var resolveJob: Job? = null
    private val controllerFuture = MediaController.Builder(application,
        SessionToken(application, ComponentName(application, PlaybackService::class.java)))
        .setListener(object : MediaController.Listener {
            override fun onDisconnected(controller: MediaController) {
                mutablePlayback.value = mutablePlayback.value.copy(
                    connected = false, playing = false, resolving = false, buffering = false,
                    error = "Playback disconnected. Close and reopen the app to reconnect.")
            }
        }).buildAsync()
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = updatePlayback()
    }

    init {
        controllerFuture.addListener({
            if (!controllerFuture.isCancelled) {
                try {
                    controller = controllerFuture.get().also { it.addListener(listener) }
                    updatePlayback()
                } catch (_: Exception) {
                    mutablePlayback.value = PlaybackState(error = "Couldn't start the player. Close and reopen the app to retry.")
                }
            }
        }, ContextCompat.getMainExecutor(application))
        viewModelScope.launch {
            while (true) {
                delay(500)
                updatePlayback()
            }
        }
    }

    fun search(query: String) = searchController.search(query)

    fun play(track: Track) {
        val player = controller ?: return
        resolveJob?.cancel()
        player.stop()
        player.clearMediaItems()
        mutablePlayback.value = PlaybackState(connected = true, track = track, resolving = true)
        resolveJob = viewModelScope.launch {
            try {
                val source = playbackResolver.resolve(track.id)
                ensureActive()
                val offline = source.url.startsWith("offline:")
                player.setMediaItem(MediaItem.Builder()
                    .setMediaId(track.id)
                    .setUri(source.url)
                    .setMimeType(source.mimeType)
                    .setCustomCacheKey(track.id)
                    .setMediaMetadata(MediaMetadata.Builder()
                        .setTitle(track.title).setArtist(track.artist)
                        .setArtworkUri(track.artworkUrl?.let(Uri::parse))
                        .build())
                    .build())
                mutablePlayback.value = mutablePlayback.value.copy(resolving = false, offline = offline)
                player.prepare()
                player.play()
                updatePlayback()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                mutablePlayback.value = mutablePlayback.value.copy(
                    resolving = false, buffering = false, playing = false,
                    error = "Couldn't load this song. Check your connection or try another result.")
            }
        }
    }

    fun togglePlayback() {
        val player = controller ?: return
        if (player.isPlaying || player.playWhenReady) player.pause() else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
        updatePlayback()
    }

    fun seek(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0))
        updatePlayback()
    }

    fun retryPlayback() {
        if ((controller?.mediaItemCount ?: 0) > 1) {
            mutablePlayback.value = mutablePlayback.value.copy(error = null)
            controller?.prepare()
            controller?.play()
        } else mutablePlayback.value.track?.let(::play)
    }

    fun playPlaylist(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty() || startIndex !in tracks.indices) return
        val player = controller ?: return
        resolveJob?.cancel()
        player.stop()
        val saved = downloads.value.items.filter { it.status == DownloadStatus.COMPLETED }.map { it.track.id }.toSet()
        val items = tracks.map { track ->
            MediaItem.Builder().setMediaId(track.id)
                .setUri(if (track.id in saved) "offline:///${track.id}" else "track:///${track.id}")
                .setCustomCacheKey(track.id)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist)
                    .setArtworkUri(track.artworkUrl?.let(Uri::parse)).build()).build()
        }
        mutablePlayback.value = PlaybackState(connected = true, track = tracks[startIndex], offline = tracks[startIndex].id in saved)
        player.setMediaItems(items, startIndex, 0)
        player.prepare()
        player.play()
        updatePlayback()
    }

    fun previous() { controller?.seekToPreviousMediaItem(); updatePlayback() }
    fun next() { controller?.seekToNextMediaItem(); updatePlayback() }
    fun playQueueItem(index: Int) {
        val player = controller ?: return
        if (index !in 0 until player.mediaItemCount) return
        player.seekTo(index, 0); player.prepare(); player.play(); updatePlayback()
    }
    fun setFavorite(track: Track, favorite: Boolean) { localLibrary.setFavorite(track, favorite) }
    fun createPlaylist(name: String, track: Track?) { localLibrary.createPlaylist(name, track) }
    fun renamePlaylist(id: String, name: String) { localLibrary.renamePlaylist(id, name) }
    fun deletePlaylist(id: String) { localLibrary.deletePlaylist(id) }
    fun addToPlaylist(id: String, track: Track) { localLibrary.addToPlaylist(id, track) }
    fun removeFromPlaylist(id: String, trackId: String) { localLibrary.removeFromPlaylist(id, trackId) }
    fun movePlaylistSong(id: String, trackId: String, direction: Int) { localLibrary.moveTrack(id, trackId, direction) }
    fun clearHistory() { localLibrary.clearHistory() }
    fun dismissLibraryError() = localLibrary.dismissError()

    fun download(track: Track) = offlineDownloads.download(track)
    fun setWifiOnly(enabled: Boolean) = offlineDownloads.setWifiOnly(enabled)
    fun removeDownload(id: String) {
        if (playback.value.offline && playback.value.track?.id == id) {
            resolveJob?.cancel()
            controller?.stop()
            controller?.clearMediaItems()
            mutablePlayback.value = PlaybackState(connected = controller?.isConnected == true)
        }
        offlineDownloads.remove(id)
    }

    private var queueTimeline: androidx.media3.common.Timeline? = null
    private var cachedQueue: List<Track> = emptyList()

    private fun updatePlayback() {
        val player = controller ?: return
        if (!player.isConnected) return
        val previous = mutablePlayback.value
        val item = player.currentMediaItem
        val track = item?.let {
            Track(it.mediaId, it.mediaMetadata.title?.toString().orEmpty(),
                it.mediaMetadata.artist?.toString().orEmpty(),
                it.mediaMetadata.artworkUri?.toString(), player.duration.coerceAtLeast(0) / 1000)
        } ?: previous.track
        val timeline = player.currentTimeline
        if (queueTimeline !== timeline) {
            cachedQueue = (0 until player.mediaItemCount).map { index ->
                val queued = player.getMediaItemAt(index)
                Track(queued.mediaId, queued.mediaMetadata.title?.toString().orEmpty(),
                    queued.mediaMetadata.artist?.toString().orEmpty(), queued.mediaMetadata.artworkUri?.toString(), 0)
            }
            queueTimeline = timeline
        }
        mutablePlayback.value = previous.copy(
            connected = true,
            track = track,
            playing = player.isPlaying,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
            offline = item?.localConfiguration?.uri?.scheme == "offline" ||
                (item?.mediaId == previous.track?.id && previous.offline),
            canPrevious = player.hasPreviousMediaItem(),
            canNext = player.hasNextMediaItem(),
            queueIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            queueSize = player.mediaItemCount,
            queue = cachedQueue,
            error = if (player.playerError != null && previous.offline)
                "Couldn't read this download. Remove it and download the song again."
            else if (player.playerError != null) "Playback stopped. Check your connection, then try again."
            else if (item?.mediaId != previous.track?.id) null else previous.error,
        )
    }

    override fun onCleared() {
        resolveJob?.cancel()
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        super.onCleared()
    }
}
