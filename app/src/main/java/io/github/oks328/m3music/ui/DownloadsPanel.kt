package io.github.oks328.m3music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.oks328.m3music.R
import io.github.oks328.m3music.downloads.*
import io.github.oks328.m3music.music.Track
import io.github.oks328.m3music.playback.PlaybackState

@Composable
internal fun DownloadsPanel(
    downloads: DownloadsState,
    playback: PlaybackState,
    onPlayPlaylist: (List<Track>, Int) -> Unit,
    onDownload: (Track) -> Unit,
    onRemove: (String) -> Unit,
    bottomClearance: Dp,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(selected != null) { selected = null }
    val entries = downloads.items.associateBy { it.track.id }
    val allSongs = downloads.items.filter { it.status == DownloadStatus.COMPLETED }
    val album = downloads.albums.firstOrNull { it.id == selected }
    val viewingSongs = selected == "songs"
    val detail = album?.trackIds?.mapNotNull(entries::get) ?: allSongs
    val songs = detail.filter { it.status == DownloadStatus.COMPLETED }.map { it.track }
    val groupedIds = downloads.albums.flatMap { it.trackIds }.toSet()
    val outstanding = downloads.items.filter { it.status != DownloadStatus.COMPLETED && it.track.id !in groupedIds }
    val downloadRow: @Composable (DownloadEntry) -> Unit = { entry ->
        val saved = entry.status == DownloadStatus.COMPLETED
        if (saved) {
            var menu by remember(entry.track.id) { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SongRow(entry.track, playback.track?.id == entry.track.id,
                        enabled = playback.connected, offline = true, compact = true,
                        containerColor = Color.Transparent, onClick = {
                            onPlayPlaylist(songs, songs.indexOfFirst { it.id == entry.track.id })
                        })
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(painterResource(R.drawable.ic_more), "Options for ${entry.track.title}")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Remove download") }, onClick = {
                            menu = false
                            onRemove(entry.track.id)
                        })
                    }
                }
            }
        } else {
            Column(Modifier.padding(vertical = 4.dp)) {
                SongRow(entry.track, selected = false, enabled = false, compact = true,
                    containerColor = Color.Transparent, onClick = {})
                DownloadActions(entry, downloads, { onDownload(entry.track) }, { onRemove(entry.track.id) })
            }
        }
    }
    key(selected) {
        LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(if (selected == null) 12.dp else 4.dp),
            contentPadding = PaddingValues(bottom = bottomClearance + 16.dp)) {
            downloads.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            if (downloads.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (viewingSongs || album != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BackIconButton(onClick = { selected = null })
                            Text(album?.title ?: "Downloaded Songs", style = MaterialTheme.typography.headlineMedium)
                        }
                        if (album != null) {
                            Artwork(album.artworkUrl, Modifier.size(160.dp).align(Alignment.CenterHorizontally),
                                offline = true, cornerRadius = 24.dp)
                            album.artist?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Text(offlineSongCount(songs.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        CollectionPlaybackButtons(songs, false, playback.connected, onPlayPlaylist)
                        Spacer(Modifier.height(4.dp))
                    }
                }
                if (detail.isEmpty() && !downloads.loading) item { Text("Songs appear here when they’re available.") }
                items(detail, key = { it.track.id }) { downloadRow(it) }
            } else {
                item {
                    DownloadGroupRow("Downloaded Songs", offlineSongCount(allSongs.size), artwork = null,
                        onClick = { selected = "songs" })
                }
                val visibleAlbums = downloads.albums.filter { group -> group.trackIds.any(entries::containsKey) }
                if (visibleAlbums.isNotEmpty()) item { Text("Albums", style = MaterialTheme.typography.headlineSmall) }
                items(visibleAlbums, key = { it.id }) { group ->
                    val completed = group.trackIds.count { entries[it]?.status == DownloadStatus.COMPLETED }
                    val summary = if (completed == group.trackIds.size) offlineSongCount(completed)
                        else "$completed of ${group.trackIds.size} songs ready offline"
                    DownloadGroupRow(group.title, summary, group.artworkUrl, onClick = { selected = group.id })
                }
                if (outstanding.isNotEmpty()) item { Text("In progress", style = MaterialTheme.typography.titleMedium) }
                items(outstanding, key = { it.track.id }) { downloadRow(it) }
            }
        }
    }
}

@Composable
private fun DownloadGroupRow(title: String, subtitle: String, artwork: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (artwork == null) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_download), null)
                }
            } else Artwork(artwork, Modifier.size(56.dp), offline = true)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun offlineSongCount(count: Int): String =
    if (count == 1) "1 song ready offline" else "$count songs ready offline"
