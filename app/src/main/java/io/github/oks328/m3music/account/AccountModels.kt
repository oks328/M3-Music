package io.github.oks328.m3music.account

import io.github.oks328.m3music.music.Track

/** Deliberately not a data class: generated toString must never expose cookies. */
class AccountSession(
    val cookie: String,
    val visitorData: String = "",
    val dataSyncId: String = "",
    val pageId: String = "",
    val authUser: String = "0",
    val clientVersion: String = "1.20260114.03.00",
) {
    override fun toString(): String = "AccountSession(redacted)"
}

data class AccountProfile(val name: String, val handle: String = "")
data class StoredAccount(val session: AccountSession, val profile: AccountProfile)

enum class AccountCategory(val label: String, val browseId: String) {
    PLAYLISTS("Playlists", "FEmusic_liked_playlists"),
    LIKED_SONGS("Liked songs", "VLLM"),
    ALBUMS("Albums", "FEmusic_liked_albums"),
    ARTISTS("Followed artists", "FEmusic_library_corpus_artists"),
}

enum class AccountItemKind { SONG, PLAYLIST, ALBUM, ARTIST }
data class AccountItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val kind: AccountItemKind,
    val artworkUrl: String? = null,
    val track: Track? = null,
    val editable: Boolean = false,
    val setVideoId: String? = null,
) {
    val key: String get() = "$id:${setVideoId.orEmpty()}"
}
data class AccountPage(val items: List<AccountItem> = emptyList(), val continuation: String? = null, val editable: Boolean = false)
data class AccountState(
    val initializing: Boolean = true,
    val profile: AccountProfile? = null,
    val expired: Boolean = false,
    val connecting: Boolean = false,
    val category: AccountCategory = AccountCategory.PLAYLISTS,
    val page: AccountPage = AccountPage(),
    val collection: AccountItem? = null,
    val detail: AccountPage = AccountPage(),
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val mutating: Boolean = false,
)

class AccountSessionExpired : Exception("Your YouTube Music session has expired. Connect again.")
class AccountResponseChanged : Exception("YouTube Music returned an unsupported page. Please try again later.")

interface AccountSessionStore {
    fun load(): StoredAccount?
    fun save(account: StoredAccount)
    fun clear()
}

interface AccountGateway {
    suspend fun profile(session: AccountSession): AccountProfile
    suspend fun browse(session: AccountSession, browseId: String, continuation: String? = null): AccountPage
    suspend fun perform(session: AccountSession, action: AccountAction) { throw UnsupportedOperationException() }
}

sealed interface AccountAction {
    data class LikeSong(val videoId: String, val like: Boolean) : AccountAction
    data class CreatePlaylist(val name: String) : AccountAction
    data class RenamePlaylist(val id: String, val name: String) : AccountAction
    data class DeletePlaylist(val id: String) : AccountAction
    data class AddSong(val playlistId: String, val videoId: String) : AccountAction
    data class RemoveSong(val playlistId: String, val videoId: String, val setVideoId: String) : AccountAction
}
