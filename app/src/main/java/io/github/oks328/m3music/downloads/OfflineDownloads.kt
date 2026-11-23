@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package io.github.oks328.m3music.downloads

import android.content.Context
import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import io.github.oks328.m3music.music.AudioSource
import io.github.oks328.m3music.music.Track
import io.github.oks328.m3music.music.YouTubeMusicRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executor

/** One cache and manager per process. Media3's database persists the download index. */
class OfflineDownloads private constructor(private val context: Context) {
    private val preferences = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val database = StandaloneDatabaseProvider(context)
    val cache = SimpleCache(File(context.noBackupFilesDir, "downloaded-audio"), NoOpCacheEvictor(), database)
    val manager = DownloadManager(context, database, cache,
        DefaultHttpDataSource.Factory().setConnectTimeoutMs(15_000).setReadTimeoutMs(30_000),
        Executor { it.run() })
    private val ready = CompletableDeferred<Unit>()
    private val mutableState = MutableStateFlow(DownloadsState(
        wifiOnly = preferences.getBoolean("wifiOnly", true),
        albums = DownloadMetadata.decodeCollections(preferences.getString("albums", null)),
        playlists = DownloadMetadata.decodeCollections(preferences.getString("playlists", null)),
    ))
    val state = mutableState.asStateFlow()
    private val preparing = linkedMapOf<String, Track>()
    private val preparationJobs = mutableMapOf<String, Job>()
    private val sourceSlots = Semaphore(2)
    private var refreshJob: Job? = null

    init {
        manager.maxParallelDownloads = 2
        manager.requirements = requirements(mutableState.value.wifiOnly)
        manager.addListener(object : DownloadManager.Listener {
            override fun onInitialized(downloadManager: DownloadManager) {
                ready.complete(Unit)
                refresh()
            }
            override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) = refresh()
            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) = refresh()
            override fun onRequirementsStateChanged(downloadManager: DownloadManager, requirements: Requirements, notMetRequirements: Int) = refresh()
        })
        if (manager.isInitialized) ready.complete(Unit)
        scope.launch {
            ready.await()
            refresh()
            while (isActive) {
                delay(1000)
                if (manager.currentDownloads.isNotEmpty()) refresh()
            }
        }
    }

    fun downloadAlbum(album: DownloadedCollection, tracks: List<Track>) =
        downloadCollection(album, tracks, isPlaylist = false)

    fun downloadPlaylist(playlist: DownloadedCollection, tracks: List<Track>) =
        downloadCollection(playlist, tracks, isPlaylist = true)

    private fun downloadCollection(collection: DownloadedCollection, tracks: List<Track>, isPlaylist: Boolean) {
        if (tracks.isEmpty()) return
        val previous = mutableState.value
        val existing = if (isPlaylist) previous.playlists else previous.albums
        val updated = listOf(collection) + existing.filterNot { it.id == collection.id }
        val key = if (isPlaylist) "playlists" else "albums"
        preferences.edit().putString(key, DownloadMetadata.encodeCollections(updated)).apply()
        mutableState.value = if (isPlaylist) previous.copy(playlists = updated) else previous.copy(albums = updated)
        download(tracks)
    }

    fun download(tracks: List<Track>) {
        tracks.distinctBy { it.id }.forEach(::download)
    }

    fun download(track: Track) {
        if (track.id in preparing || mutableState.value.items.any {
                it.track.id == track.id && it.status != DownloadStatus.FAILED
            }) return
        val restarting = mutableState.value.items.any { it.track.id == track.id && it.status == DownloadStatus.FAILED }
        preparing[track.id] = track
        mutableState.value = mutableState.value.copy(error = null,
            items = mutableState.value.items.filterNot { it.track.id == track.id } + DownloadEntry(track, DownloadStatus.PREPARING))
        preparationJobs[track.id] = scope.launch {
            try {
                ready.await()
                val persisted = withContext(Dispatchers.IO) { manager.downloadIndex.getDownload(track.id) }
                if (persisted != null && persisted.state != Download.STATE_FAILED) return@launch
                val source = sourceSlots.withPermit { YouTubeMusicRepository.resolve(track.id) }
                if (restarting || persisted?.state == Download.STATE_FAILED) withContext(Dispatchers.IO) { cache.removeResource(track.id) }
                ensureActive()
                val request = DownloadRequest.Builder(track.id, Uri.parse(source.url))
                    .setCustomCacheKey(track.id).setMimeType(source.mimeType)
                    .setData(DownloadMetadata.encode(track)).build()
                DownloadService.sendAddDownload(context, MusicDownloadService::class.java, request, true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(
                    error = "Couldn't download ${track.title}. Check your connection or free storage, then try again.")
            } finally {
                preparing.remove(track.id)
                preparationJobs.remove(track.id)
                refresh()
            }
        }
    }

    fun remove(id: String) {
        preparationJobs[id]?.cancel()
        preparing.remove(id)
        mutableState.value = mutableState.value.copy(items = mutableState.value.items.filterNot { it.track.id == id })
        DownloadService.sendRemoveDownload(context, MusicDownloadService::class.java, id, false)
    }

    fun setWifiOnly(enabled: Boolean) {
        preferences.edit().putBoolean("wifiOnly", enabled).apply()
        mutableState.value = mutableState.value.copy(wifiOnly = enabled)
        DownloadService.sendSetRequirements(context, MusicDownloadService::class.java, requirements(enabled), false)
    }

    fun resumePending() {
        scope.launch {
            ready.await()
            if (manager.currentDownloads.isNotEmpty()) {
                try { DownloadService.start(context, MusicDownloadService::class.java) }
                catch (_: IllegalStateException) {
                    mutableState.value = mutableState.value.copy(error = "Reopen the app to resume pending songs.")
                }
            }
        }
    }

    suspend fun completedSource(id: String): AudioSource? {
        ready.await()
        return withContext(Dispatchers.IO) {
            val download = manager.downloadIndex.getDownload(id)
            if (download?.state != Download.STATE_COMPLETED) null
            else AudioSource("offline:///$id", download.request.mimeType, 0)
        }
    }

    fun clearError() { mutableState.value = mutableState.value.copy(error = null) }

    private fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            try {
                ready.await()
                val persisted = withContext(Dispatchers.IO) {
                    manager.downloadIndex.getDownloads().use { cursor ->
                        buildList { while (cursor.moveToNext()) add(cursor.download) }
                    }
                }
                ensureActive()
                val all = persisted.associateBy { it.request.id }.toMutableMap()
                manager.currentDownloads.forEach { all[it.request.id] = it }
                val entries = all.values.map { download ->
                    val track = DownloadMetadata.decode(download.request.data)
                        ?: Track(download.request.id, "Saved song", "Unknown artist", null, 0)
                    val status = when (download.state) {
                        Download.STATE_COMPLETED -> DownloadStatus.COMPLETED
                        Download.STATE_DOWNLOADING -> DownloadStatus.DOWNLOADING
                        Download.STATE_FAILED -> DownloadStatus.FAILED
                        Download.STATE_REMOVING, Download.STATE_RESTARTING -> DownloadStatus.REMOVING
                        Download.STATE_STOPPED -> DownloadStatus.PAUSED
                        else -> DownloadStatus.QUEUED
                    }
                    DownloadEntry(track, status, download.percentDownloaded.takeIf { it >= 0 }?.toInt(), download.bytesDownloaded)
                }.filterNot { it.track.id in preparing } + preparing.values.map { DownloadEntry(it, DownloadStatus.PREPARING) }
                mutableState.value = mutableState.value.copy(items = entries,
                    waitingForNetwork = manager.notMetRequirements != 0, loading = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableState.value = mutableState.value.copy(loading = false, error = "Couldn't read downloads. Close and reopen the app to retry.")
            }
        }
    }

    private fun requirements(wifiOnly: Boolean) = Requirements(
        Requirements.NETWORK or if (wifiOnly) Requirements.NETWORK_UNMETERED else 0)

    companion object {
        @Volatile private var instance: OfflineDownloads? = null
        fun get(context: Context): OfflineDownloads = instance ?: synchronized(this) {
            instance ?: OfflineDownloads(context.applicationContext).also { instance = it }
        }
    }
}
