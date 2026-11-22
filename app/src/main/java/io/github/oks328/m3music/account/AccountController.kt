package io.github.oks328.m3music.account

import io.github.oks328.m3music.music.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class AccountController(
    private val scope: CoroutineScope,
    private val store: AccountSessionStore,
    private val gateway: AccountGateway,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutableState = MutableStateFlow(AccountState())
    val state = mutableState.asStateFlow()
    private var session: AccountSession? = null
    private var generation = 0
    private var request = 0
    private var browseJob: Job? = null
    private val persistence = Mutex()

    init {
        val stamp = generation
        scope.launch {
            try {
                val saved = withContext(io) { store.load() }
                if (stamp != generation) return@launch
                session = saved?.session
                mutableState.value = AccountState(initializing = false, profile = saved?.profile)
                if (saved != null) {
                    try {
                        val profile = gateway.profile(saved.session)
                        if (stamp == generation) mutableState.value = mutableState.value.copy(profile = profile)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { if (stamp == generation) showFailure(failure) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (stamp == generation) mutableState.value = AccountState(initializing = false, error = "Couldn't restore the account. Connect again.")
            }
        }
    }

    suspend fun connect(candidate: AccountSession): Boolean {
        if (state.value.connecting || state.value.initializing) return false
        val stamp = ++generation
        browseJob?.cancel(); request++
        mutableState.value = state.value.copy(connecting = true, error = null, loading = false)
        try {
            SignedAccountRequest.authorization(candidate, 1)
            val profile = gateway.profile(candidate)
            currentCoroutineContext().ensureActive()
            return persistence.withLock {
                if (stamp != generation) return@withLock false
                withContext(NonCancellable) {
                    withContext(io) { store.save(StoredAccount(candidate, profile)) }
                    if (stamp != generation) false else {
                        session = candidate
                        mutableState.value = AccountState(initializing = false, profile = profile)
                        true
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            if (stamp == generation) mutableState.value = state.value.copy(connecting = false)
            throw cancelled
        } catch (_: Exception) {
            if (stamp == generation) mutableState.value = state.value.copy(connecting = false,
                error = "Couldn't verify this Google session. Complete sign-in on YouTube Music and try again.")
            return false
        }
    }

    fun disconnect() {
        val stamp = ++generation
        browseJob?.cancel(); request++
        session = null
        mutableState.value = AccountState(initializing = true)
        scope.launch {
            persistence.withLock {
                try {
                    withContext(io) { store.clear() }
                    if (stamp == generation) mutableState.value = AccountState(initializing = false)
                } catch (_: Exception) {
                    if (stamp == generation) mutableState.value = AccountState(initializing = false,
                        error = "Couldn't remove the stored session. Close and reopen the app, then disconnect again.")
                }
            }
        }
    }

    fun loadCategory(category: AccountCategory) {
        if (session == null || state.value.expired) return
        mutableState.value = state.value.copy(category = category, page = AccountPage(), collection = null, detail = AccountPage())
        fetch(false)
    }

    fun openCollection(item: AccountItem) {
        if (item.track != null) return
        mutableState.value = state.value.copy(collection = item, detail = AccountPage())
        fetch(false)
    }

    fun back() {
        browseJob?.cancel(); request++
        mutableState.value = state.value.copy(collection = null, detail = AccountPage(), loading = false, error = null)
    }

    suspend fun discovery(): List<DiscoveryShelf> = discover(gateway::discovery)

    suspend fun discoveryCollection(id: String): AccountPage =
        discover { gateway.discoveryCollection(it, id) }

    suspend fun collectionTracks(id: String, first: AccountPage, fromLibrary: Boolean): List<Track> {
        val stamp = generation
        val active = session?.takeUnless { state.value.expired }
        if (fromLibrary && active == null) throw AccountSessionExpired()
        val songs = linkedMapOf<String, Track>()
        val visited = mutableSetOf<String>()
        var page = first
        while (true) {
            currentCoroutineContext().ensureActive()
            if (stamp != generation) throw CancellationException("Account changed")
            val album = if (id.startsWith("MPRE")) page.copy(
                artworkUrl = first.artworkUrl ?: page.artworkUrl,
                albumArtist = first.albumArtist ?: page.albumArtist,
            ) else null
            page.items.mapNotNull { it.track }.forEach { track ->
                val song = album?.fillAlbumCredits(track) ?: track
                songs.putIfAbsent(song.id, song)
            }
            val token = page.continuation ?: return songs.values.toList()
            if (!visited.add(token)) throw AccountResponseChanged()
            page = if (active == null) gateway.discoveryCollection(null, id, token)
            else gateway.browse(active, id, token)
        }
    }

    private suspend fun <T> discover(load: suspend (AccountSession?) -> T): T {
        val active = session?.takeUnless { state.value.expired } ?: throw AccountSessionExpired()
        return try {
            load(active)
        } catch (expired: AccountSessionExpired) {
            if (active === session) showFailure(expired)
            throw expired
        }
    }

    suspend fun playlistSuggestions(id: String, token: String?): List<Track> {
        val active = session?.takeUnless { state.value.expired } ?: return emptyList()
        val suggested = token?.let { gateway.playlistSuggestions(active, id, it) }.orEmpty()
        val songs = suggested.ifEmpty {
            gateway.discovery(active).flatMap { shelf -> shelf.items.mapNotNull { it.track } }.distinctBy { it.id }
        }
        return if (active === session && !state.value.expired) songs else emptyList()
    }

    fun refresh() = fetch(false)
    fun loadMore() = fetch(true)
    fun dismissMessage() { mutableState.value = state.value.copy(message = null) }

    private fun fetch(append: Boolean) {
        val active = session ?: return
        if (state.value.expired) return
        val before = state.value
        val page = if (before.collection == null) before.page else before.detail
        val continuation = if (append) page.continuation ?: return else null
        val browseId = before.collection?.id ?: before.category.browseId
        val stamp = generation
        val ticket = ++request
        browseJob?.cancel()
        mutableState.value = before.copy(loading = true, error = null)
        browseJob = scope.launch {
            try {
                val next = gateway.browse(active, browseId, continuation)
                currentCoroutineContext().ensureActive()
                if (stamp != generation || ticket != request) return@launch
                val combined = if (append) AccountPage((page.items + next.items).distinctBy { it.key },
                    next.continuation?.takeUnless { it == continuation }, page.editable || next.editable,
                    page.artworkUrl ?: next.artworkUrl, page.albumArtist ?: next.albumArtist,
                    suggestionsToken = page.suggestionsToken ?: next.suggestionsToken) else next
                mutableState.value = if (before.collection == null) state.value.copy(page = combined, loading = false)
                    else state.value.copy(detail = combined.withAlbumMetadata(before.collection), loading = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (stamp == generation && ticket == request) showFailure(failure) }
        }
    }

    suspend fun editablePlaylists(continuation: String?): AccountPage {
        val active = session?.takeUnless { state.value.expired } ?: throw AccountSessionExpired()
        val stamp = generation
        return try {
            val page = gateway.browse(active, AccountCategory.PLAYLISTS.browseId, continuation)
            if (stamp != generation) throw CancellationException("Account changed")
            page.copy(items = page.items.filter { it.kind == AccountItemKind.PLAYLIST && it.editable })
        } catch (expired: AccountSessionExpired) {
            if (stamp == generation) showFailure(expired)
            throw expired
        }
    }

    suspend fun savePlaylistTrack(playlist: AccountItem, track: Track): Boolean {
        if (!playlist.editable || playlist.kind != AccountItemKind.PLAYLIST) return false
        return startWrite(AccountAction.AddSong(playlist.id, track.id))?.await() ?: false
    }

    fun perform(action: AccountAction) {
        startWrite(action)
    }

    private fun startWrite(action: AccountAction): Deferred<Boolean>? {
        val active = session ?: return null
        if (state.value.expired || state.value.mutating) return null
        val stamp = generation
        browseJob?.cancel(); request++
        mutableState.value = state.value.copy(mutating = true, loading = false, error = null, message = null)
        return scope.async {
            try {
                gateway.perform(active, action)
                if (stamp != generation) return@async false
                mutableState.value = state.value.copy(mutating = false, message = "YouTube Music updated.")
                when (action) {
                    is AccountAction.CreatePlaylist -> loadCategory(AccountCategory.PLAYLISTS)
                    is AccountAction.DeletePlaylist -> { back(); refresh() }
                    is AccountAction.LikeSong -> if (state.value.collection == null && state.value.category == AccountCategory.LIKED_SONGS) refresh()
                    is AccountAction.RenamePlaylist -> {
                        mutableState.value = state.value.copy(collection = state.value.collection?.copy(title = action.name.trim()))
                        refresh()
                    }
                    else -> refresh()
                }
                true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (stamp == generation) showFailure(failure, writing = true)
                false
            }
        }
    }

    private fun showFailure(failure: Exception, writing: Boolean = false) {
        val expired = failure is AccountSessionExpired
        mutableState.value = state.value.copy(connecting = false, loading = false, mutating = false,
            expired = expired || state.value.expired,
            page = if (expired) AccountPage() else state.value.page,
            detail = if (expired) AccountPage() else state.value.detail,
            error = when (failure) {
                is AccountSessionExpired -> failure.message
                is AccountResponseChanged -> failure.message
                is IllegalArgumentException -> failure.message
                else -> if (writing) "Couldn't confirm the YouTube Music update. Refresh before trying again."
                    else "Couldn't reach YouTube Music. Check your connection and retry."
            })
    }
}
