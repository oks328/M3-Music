package io.github.oks328.m3music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
    val collection = downloads.albums.firstOrNull { "album:${it.id}" == selected || it.id == selected }
        ?: downloads.playlists.firstOrNull { "playlist:${it.id}" == selected }
    val viewingSongs = selected == "songs"
    val detail = collection?.trackIds?.mapNotNull(entries::get) ?: allSongs
    val songs = detail.filter { it.status == DownloadStatus.COMPLETED }.map { it.track }
    val groupedIds = (downloads.albums + downloads.playlists).flatMap { it.trackIds }.toSet()
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
        LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = bottomClearance + 16.dp)) {
            downloads.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            if (downloads.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (viewingSongs || collection != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BackIconButton(onClick = { selected = null })
                        LibraryCollectionTitle(collection?.title ?: "Downloaded Songs", collection?.artworkUrl,
                            subtitle = collection?.artist.orEmpty(), metadata = offlineSongCount(songs.size),
                            offline = true, showArtwork = collection != null)
                        CollectionPlaybackButtons(songs, false, playback.connected, onPlayPlaylist)
                        Spacer(Modifier.height(4.dp))
                    }
                }
                if (detail.isEmpty() && !downloads.loading) item { Text("Songs appear here when they’re available.") }
                items(detail, key = { it.track.id }) { downloadRow(it) }
            } else {
                item {
                    LibraryCollectionRow("Downloaded Songs", offlineSongCount(allSongs.size), artwork = null,
                        fallbackIcon = R.drawable.ic_download, onClick = { selected = "songs" })
                }
                downloadedGroups("Playlists", "playlist", downloads.playlists, entries) { selected = it }
                downloadedGroups("Albums", "album", downloads.albums, entries) { selected = it }
                if (outstanding.isNotEmpty()) item { DownloadSectionTitle("In progress") }
                items(outstanding, key = { it.track.id }) { downloadRow(it) }
            }
        }
    }
}

private fun LazyListScope.downloadedGroups(
    title: String,
    kind: String,
    collections: List<DownloadedCollection>,
    entries: Map<String, DownloadEntry>,
    onOpen: (String) -> Unit,
) {
    val visible = collections.filter { group -> group.trackIds.any(entries::containsKey) }
    if (visible.isEmpty()) return
    item { DownloadSectionTitle(title) }
    itemsIndexed(visible, key = { _, group -> "$kind:${group.id}" }) { index, group ->
        val completed = group.trackIds.count { entries[it]?.status == DownloadStatus.COMPLETED }
        val summary = if (completed == group.trackIds.size) offlineSongCount(completed)
            else "$completed of ${group.trackIds.size} songs ready offline"
        LibraryCollectionRow(group.title, summary, group.artworkUrl, offline = true,
            first = index == 0, last = index == visible.lastIndex) { onOpen("$kind:${group.id}") }
    }
}

@Composable
private fun DownloadSectionTitle(title: String) {
    Text(title, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleLarge)
}

private fun offlineSongCount(count: Int): String =
    if (count == 1) "1 song ready offline" else "$count songs ready offline"
