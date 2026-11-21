package io.github.oks328.m3music.library

import android.content.Context
import io.github.oks328.m3music.music.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes database writes and publishes only committed snapshots. */
class LocalLibrary private constructor(context: Context) {
    private val store = LocalLibraryStore(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(LibraryState())
    val state = mutableState.asStateFlow()

    init { update { } }

    fun setFavorite(track: Track, favorite: Boolean) = update { setFavorite(track, favorite) }
    fun recordPlayed(track: Track) = update { recordPlayed(track) }
    fun clearHistory() = update { clearHistory() }
    fun createPlaylist(name: String, initialTrack: Track? = null) = update {
        createPlaylist(name, initialTrack)
    }
    fun renamePlaylist(id: String, name: String) = update { renamePlaylist(id, name) }
    fun deletePlaylist(id: String) = update { deletePlaylist(id) }
    fun addToPlaylist(id: String, track: Track) = update { addToPlaylist(id, track) }
    fun removeFromPlaylist(id: String, trackId: String) = update { removeFromPlaylist(id, trackId) }
    fun moveTrack(id: String, trackId: String, direction: Int) = update { moveTrack(id, trackId, direction) }
    fun dismissError() { mutableState.value = mutableState.value.copy(error = null) }

    private fun update(action: LocalLibraryStore.() -> Unit): Job = scope.launch {
        mutex.withLock {
            try {
                val next = withContext(Dispatchers.IO) { store.action(); store.snapshot() }
                mutableState.value = next
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: IllegalArgumentException) {
                mutableState.value = mutableState.value.copy(loading = false, error = failure.message)
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(loading = false,
                    error = "Couldn't save your library. Check free storage and try again.")
            }
        }
    }

    companion object {
        @Volatile private var instance: LocalLibrary? = null
        fun get(context: Context): LocalLibrary = instance ?: synchronized(this) {
            instance ?: LocalLibrary(context).also { instance = it }
        }
    }
}
