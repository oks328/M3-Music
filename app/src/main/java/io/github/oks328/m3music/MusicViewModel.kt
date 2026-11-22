@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package io.github.oks328.m3music

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import java.util.UUID
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
import io.github.oks328.m3music.playback.RepeatMode
import io.github.oks328.m3music.downloads.OfflineDownloads
import io.github.oks328.m3music.downloads.DownloadedAlbum
import io.github.oks328.m3music.downloads.DownloadStatus
import io.github.oks328.m3music.library.LocalLibrary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val localLibrary = LocalLibrary.get(application)
    val library = localLibrary.state
    private val offlineDownloads = OfflineDownloads.get(application)
    val downloads = offlineDownloads.state
    private val catalog = io.github.oks328.m3music.account.YouTubeAccountClient()
    private val searchController = SearchController(viewModelScope, catalog::searchCollections, YouTubeMusicRepository::search)
    val search = searchController.state
    suspend fun findTrackArtists(track: Track) = catalog.findTrackArtists(track)
    suspend fun findSongCollection(track: Track, kind: io.github.oks328.m3music.account.AccountItemKind) =
        catalog.findSongCollection(track, kind)
    private val mutablePlayback = MutableStateFlow(PlaybackState())
    val playback = mutablePlayback.asStateFlow()
    private var controller: MediaController? = null
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

    private val searchPreferences = application.getSharedPreferences("search_history", 0)
    private val recentSearches = kotlinx.coroutines.flow.MutableStateFlow(
        runCatching {
            val saved = org.json.JSONArray(searchPreferences.getString("queries", "[]"))
            List(saved.length()) { saved.getString(it) }
        }.getOrDefault(emptyList())
    )
    val searchHistory: kotlinx.coroutines.flow.StateFlow<List<String>> = recentSearches

    fun clearSearchHistory() {
        recentSearches.value = emptyList()
        searchPreferences.edit().remove("queries").apply()
    }

    fun search(query: String) {
        val term = query.trim()
        if (term.isNotEmpty()) {
            val history = (listOf(term) + recentSearches.value.filterNot { it.equals(term, ignoreCase = true) }).take(20)
            recentSearches.value = history
            searchPreferences.edit().putString("queries", org.json.JSONArray(history).toString()).apply()
        }
        searchController.search(term)
    }

    fun play(track: Track) = playPlaylist(listOf(track))

    fun stopPlaying() {
        controller?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
        mutablePlayback.value = PlaybackState(connected = controller != null)
    }

    fun togglePlayback() {
        val player = controller ?: return
        if (player.isPlaying || player.playWhenReady) player.pause() else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
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
        player.stop()
        val saved = savedTrackIds()
        val items = tracks.map { it.queueItem(it.id in saved) }
        mutablePlayback.value = PlaybackState(connected = true, track = tracks[startIndex], offline = tracks[startIndex].id in saved)
        player.setMediaItems(items, startIndex, 0)
        player.prepare()
        player.play()
        updatePlayback()
    }

    fun enqueue(track: Track) {
        val player = controller ?: return
        if (!player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) return
        if (player.mediaItemCount == 0) player.pause()
        player.addMediaItem(track.queueItem(track.id in savedTrackIds()))
        updatePlayback()
    }

    fun moveQueueItem(from: Int, to: Int) {
        val player = controller ?: return
        if (from == to || from !in 0 until player.mediaItemCount || to !in 0 until player.mediaItemCount ||
            !player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) return
        player.moveMediaItem(from, to)
        updatePlayback()
    }

    private fun savedTrackIds() = downloads.value.items
        .filter { it.status == DownloadStatus.COMPLETED }.map { it.track.id }.toSet()

    private companion object {
        const val QueueEntryId = "io.github.oks328.m3music.QUEUE_ENTRY_ID"
    }

    private fun Track.queueItem(saved: Boolean): MediaItem =
        MediaItem.Builder().setMediaId(id)
            .setUri(if (saved) "offline:///$id" else "track:///$id")
            .setCustomCacheKey(id)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist)
                .setArtworkUri(artworkUrl?.let(Uri::parse))
                .setExtras(Bundle().apply { putString(QueueEntryId, UUID.randomUUID().toString()) })
                .build())
            .build()

    fun previous() { controller?.seekToPreviousMediaItem(); updatePlayback() }
    fun next() { controller?.seekToNextMediaItem(); updatePlayback() }
    fun toggleShuffle() {
        val player = controller ?: return
        if (player.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)) {
            player.shuffleModeEnabled = !player.shuffleModeEnabled
        }
    }

    fun cycleRepeat() {
        val player = controller ?: return
        if (player.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE)) {
            player.repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

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

    fun downloadAlbum(album: DownloadedAlbum, tracks: List<Track>) = offlineDownloads.downloadAlbum(album, tracks)
    fun download(tracks: List<Track>) = offlineDownloads.download(tracks)
    fun download(track: Track) = offlineDownloads.download(track)
    fun setWifiOnly(enabled: Boolean) = offlineDownloads.setWifiOnly(enabled)
    fun removeDownload(id: String) {
        if (playback.value.offline && playback.value.track?.id == id) {
            controller?.stop()
            controller?.clearMediaItems()
            mutablePlayback.value = PlaybackState(connected = controller?.isConnected == true)
        }
        offlineDownloads.remove(id)
    }

    private var queueTimeline: androidx.media3.common.Timeline? = null
    private var cachedQueue: List<Track> = emptyList()
    private var cachedQueueEntryIds: List<String> = emptyList()

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
            val mediaItems = List(player.mediaItemCount, player::getMediaItemAt)
            cachedQueueEntryIds = mediaItems.mapIndexed { index, queued ->
                queued.mediaMetadata.extras?.getString(QueueEntryId) ?: "external:${queued.mediaId}:$index"
            }
            cachedQueue = mediaItems.map { queued ->
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
            shuffle = player.shuffleModeEnabled,
            repeat = when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            queue = cachedQueue,
            queueEntryIds = cachedQueueEntryIds,
            error = if (player.playerError != null && previous.offline)
                "Couldn't read this download. Remove it and download the song again."
            else if (player.playerError != null) "Playback stopped. Check your connection, then try again."
            else if (item?.mediaId != previous.track?.id) null else previous.error,
        )
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        super.onCleared()
    }
}
