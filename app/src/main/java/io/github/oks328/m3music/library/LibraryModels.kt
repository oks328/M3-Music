package io.github.oks328.m3music.library

import io.github.oks328.m3music.music.Track

data class LocalPlaylist(val id: String, val name: String, val tracks: List<Track> = emptyList())

data class LibraryState(
    val favorites: List<Track> = emptyList(),
    val history: List<Track> = emptyList(),
    val playlists: List<LocalPlaylist> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

internal fun normalizePlaylistName(name: String): String = name.trim().also {
    require(it.isNotEmpty()) { "Enter a playlist name." }
    require(it.length <= 80) { "Playlist names can have up to 80 characters." }
}
