package io.github.oks328.m3music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.oks328.m3music.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.oks328.m3music.downloads.DownloadStatus
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
    onDownloadTracks: (List<Track>) -> Unit,
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (selected == null) {
            Text("Playlists and favorites on this device", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Playlists", "Favorites", "History").forEach { name ->
                    FilterChip(selected = section == name, onClick = { section = name }, label = { Text(name) })
                }
            }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        else LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp + bottomClearance)) {
            when {
                selected != null -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            BackIconButton(onClick = { playlistId = null })
                            Artwork(selected.tracks.firstOrNull()?.artworkUrl, Modifier.size(160.dp).align(Alignment.CenterHorizontally))
                            Text(selected.name, style = MaterialTheme.typography.headlineLarge)
                            Text("${selected.tracks.size} songs · On this device", style = MaterialTheme.typography.bodyMedium)
                            CollectionPlaybackButtons(selected.tracks, false, playback.connected, onPlayPlaylist,
                                downloadAction = {
                                    CollectionDownloadButton("playlist", selected.tracks, false, downloads, false) {
                                        onDownloadTracks(selected.tracks)
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
                    item { Button(onClick = onCreate) { Text("New playlist") } }
                    if (state.playlists.isEmpty()) item { Text("Make a playlist for your next listen.") }
                    items(state.playlists, key = { it.id }) { playlist ->
                        Surface(onClick = { playlistId = playlist.id }, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Artwork(playlist.tracks.firstOrNull()?.artworkUrl, Modifier.size(64.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                                Text("${playlist.tracks.size} songs", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            }
                        }
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
