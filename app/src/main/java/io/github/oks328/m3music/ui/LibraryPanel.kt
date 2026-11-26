package io.github.oks328.m3music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import io.github.oks328.m3music.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.oks328.m3music.downloads.DownloadStatus
import io.github.oks328.m3music.downloads.DownloadedCollection
import io.github.oks328.m3music.downloads.DownloadsState
import io.github.oks328.m3music.library.LibraryState
import io.github.oks328.m3music.library.LocalPlaylist
import io.github.oks328.m3music.music.Track
import io.github.oks328.m3music.playback.PlaybackState

@Composable
internal fun LibraryPanel(
    state: LibraryState,
    playback: PlaybackState,
    downloads: DownloadsState,
    onPlay: (Track) -> Unit,
    onPlayPlaylist: (List<Track>, Int) -> Unit,
    onFavorite: (Track, Boolean) -> Unit,
    onAdd: (Track) -> Unit,
    onCreate: () -> Unit,
    onRename: (LocalPlaylist) -> Unit,
    onDelete: (LocalPlaylist) -> Unit,
    onRemoveSong: (String, String) -> Unit,
    onMoveSong: (String, String, Int) -> Unit,
    onClearHistory: () -> Unit,
    onDownloadPlaylist: (DownloadedCollection, List<Track>) -> Unit,
    onQueueTracks: (List<Track>, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    bottomClearance: androidx.compose.ui.unit.Dp = 0.dp,
) {
    var section by rememberSaveable { mutableStateOf("Playlists") }
    var playlistId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val selected = state.playlists.firstOrNull { it.id == playlistId }
    BackHandler(playlistId != null) { playlistId = null }
    LaunchedEffect(state.playlists, state.loading) {
        if (!state.loading && playlistId != null && selected == null) playlistId = null
    }
    val overviewScroll = rememberLazyListState()
    val detailScroll = key(playlistId) { rememberLazyListState() }
    Column(modifier.collectionMotion(playlistId), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (selected == null) {
            LibraryFilters(listOf("Playlists", "Favorites", "History"), section, label = { it }) { section = it }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        else LazyColumn(Modifier.weight(1f).fillMaxWidth(),
            state = if (selected == null) overviewScroll else detailScroll, verticalArrangement = Arrangement.spacedBy(if (selected == null) 4.dp else 12.dp),
            contentPadding = PaddingValues(bottom = 16.dp + bottomClearance)) {
            when {
                selected != null -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                BackIconButton(onClick = { playlistId = null })
                                if (playback.connected && selected.tracks.isNotEmpty()) {
                                    CollectionQueueMenu { next -> onQueueTracks(selected.tracks, next) }
                                }
                            }
                            LibraryCollectionTitle(selected.name, selected.tracks.firstOrNull()?.artworkUrl,
                                metadata = "${selected.tracks.size} songs on this device")
                            CollectionPlaybackButtons(selected.tracks, false, playback.connected, onPlayPlaylist,
                                downloadAction = {
                                    val artwork = selected.tracks.firstOrNull()?.artworkUrl
                                    val collection = DownloadedCollection(
                                        "local:${selected.id}", selected.name, null, artwork,
                                        selected.tracks.map { it.id }.distinct())
                                    CollectionDownloadButton("playlist", selected.tracks, false, downloads, false,
                                        collectionId = collection.id) {
                                        onDownloadPlaylist(collection, selected.tracks)
                                    }
                                }, playLabel = "Play playlist")
                            Row {
                                TextButton(onClick = { onRename(selected) }) { Text("Rename") }
                                TextButton(onClick = { onDelete(selected) }) { Text("Delete") }
                            }
                        }
                    }
                    if (selected.tracks.isEmpty()) item {
                        Text("Find a song in Search or Downloads, then choose Add to playlist.")
                    }
                    itemsIndexed(selected.tracks, key = { _, track -> track.id }) { index, track ->
                        Column {
                            SongRow(track, playback.track?.id == track.id, playback.connected,
                                offline = downloads.items.any { it.track.id == track.id && it.status == DownloadStatus.COMPLETED },
                                onClick = { onPlayPlaylist(selected.tracks, index) })
                            Row {
                                LibrarySongActions(track, state.favorites.any { it.id == track.id }, onFavorite, onAdd, Modifier.weight(1f))
                                var showMenu by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(onClick = { showMenu = true }, modifier = Modifier.semantics { contentDescription = "Playlist options for ${track.title}" }) { Text("More") }
                                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                        DropdownMenuItem(text = { Text("Move up") }, enabled = index > 0,
                                            onClick = { showMenu = false; onMoveSong(selected.id, track.id, -1) })
                                        DropdownMenuItem(text = { Text("Move down") }, enabled = index < selected.tracks.lastIndex,
                                            onClick = { showMenu = false; onMoveSong(selected.id, track.id, 1) })
                                        DropdownMenuItem(text = { Text("Remove from playlist") },
                                            onClick = { showMenu = false; onRemoveSong(selected.id, track.id) })
                                    }
                                }
                            }
                        }
                    }
                }
                section == "Playlists" -> {
                    item { NewLibraryPlaylistButton(onClick = onCreate) }
                    if (state.playlists.isEmpty()) item { Text("Make a playlist for your next listen.") }
                    itemsIndexed(state.playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
                        LibraryCollectionRow(playlist.name, "${playlist.tracks.size} songs",
                            playlist.tracks.firstOrNull()?.artworkUrl, first = index == 0, last = index == state.playlists.lastIndex,
                            onClick = { playlistId = playlist.id })
                    }
                }
                else -> {
                    val tracks = if (section == "Favorites") state.favorites else state.history
                    if (section == "History" && tracks.isNotEmpty()) item {
                        TextButton(onClick = { confirmClear = true }) { Text("Clear history") }
                    }
                    if (tracks.isEmpty()) item {
                        Text(if (section == "Favorites") "Favorite a song to find it here." else "Songs appear here after playback starts.")
                    }
                    items(tracks, key = { it.id }) { track ->
                        Column {
                            val offline = downloads.items.any { it.track.id == track.id && it.status == DownloadStatus.COMPLETED }
                            SongRow(track, playback.track?.id == track.id, playback.connected, offline, onClick = { onPlay(track) })
                            if (offline) Text("Available offline", style = MaterialTheme.typography.labelSmall)
                            LibrarySongActions(track, state.favorites.any { it.id == track.id }, onFavorite, onAdd)
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text("Clear listening history?") }, text = { Text("Your playlists, favorites, and downloads will stay.") },
        confirmButton = { TextButton(onClick = { onClearHistory(); confirmClear = false }) { Text("Clear history") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}

@Composable
internal fun LibrarySongActions(track: Track, favorite: Boolean, onFavorite: (Track, Boolean) -> Unit,
                                onAdd: (Track) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier) {
        IconToggleButton(checked = favorite, onCheckedChange = { onFavorite(track, it) }, modifier = Modifier.semantics {
            contentDescription = if (favorite) "Unfavorite ${track.title}" else "Favorite ${track.title}"
        }) { Icon(painterResource(R.drawable.ic_heart), null, tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        IconButton(onClick = { onAdd(track) }, modifier = Modifier.semantics { contentDescription = "Add ${track.title} to playlist" }) {
            Icon(painterResource(R.drawable.ic_add_playlist), null)
        }
    }
}
