package io.github.oks328.m3music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.oks328.m3music.R
import io.github.oks328.m3music.account.AccountItem
import io.github.oks328.m3music.music.Track

@Composable
internal fun CollectionHeader(
    item: AccountItem, songs: List<Track>, hasMore: Boolean, connected: Boolean,
    editable: Boolean, busy: Boolean, onBack: () -> Unit, onPlay: (List<Track>, Int) -> Unit,
    onRefresh: () -> Unit, onAdd: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit,
) {
    var menu by remember(item.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back to your music") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_more), "Collection options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Refresh collection") }, enabled = !busy, onClick = { menu = false; onRefresh() })
                    if (editable) {
                        DropdownMenuItem(text = { Text("Add a song") }, enabled = !busy, onClick = { menu = false; onAdd() })
                        DropdownMenuItem(text = { Text("Rename on YouTube") }, enabled = !busy, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Delete on YouTube") }, enabled = !busy, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
        Artwork(item.artworkUrl, Modifier.widthIn(max = 240.dp).fillMaxWidth().aspectRatio(1f).align(Alignment.CenterHorizontally), large = true)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.title, style = MaterialTheme.typography.headlineLarge)
            if (item.subtitle.isNotBlank()) Text(item.subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (songs.isNotEmpty()) {
                val seconds = songs.sumOf { it.durationSeconds.toLong() }
                val duration = if (seconds > 0 && songs.all { it.durationSeconds > 0 }) " · ${seconds / 60} min" else ""
                Text("${songs.size} ${if (songs.size == 1) "song" else "songs"}${if (hasMore) " loaded" else ""}$duration",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        CollectionPlaybackButtons(songs, hasMore, connected, onPlay)
        if (hasMore) Text("Play and Shuffle use the songs loaded below. Load more to include the rest.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CollectionPlaybackButtons(songs: List<Track>, hasMore: Boolean, connected: Boolean, onPlay: (List<Track>, Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Button(onClick = { onPlay(songs, 0) }, enabled = connected && songs.isNotEmpty(),
            modifier = Modifier.weight(1.4f).heightIn(min = 56.dp), shape = RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp, topEnd = 8.dp, bottomEnd = 8.dp)) {
            Icon(painterResource(R.drawable.ic_play), null)
            Spacer(Modifier.width(8.dp))
            Text(if (hasMore) "Play loaded" else "Play")
        }
        FilledTonalButton(onClick = { onPlay(songs.shuffled(), 0) }, enabled = connected && songs.isNotEmpty(),
            modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 32.dp, bottomEnd = 32.dp)) {
            Icon(painterResource(R.drawable.ic_shuffle), null)
            Spacer(Modifier.width(8.dp))
            Text("Shuffle")
        }
    }
}

@Composable
internal fun CollectionSongRow(track: Track, selected: Boolean, connected: Boolean, busy: Boolean,
    unlike: Boolean, removable: Boolean, onPlay: () -> Unit, onDownload: () -> Unit,
    onLike: () -> Unit, onRemove: () -> Unit) {
    var menu by remember(track.id) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { SongRow(track, selected, connected, onClick = onPlay,
            containerColor = MaterialTheme.colorScheme.secondaryContainer) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_more), "More options for ${track.title}") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Download") }, onClick = { menu = false; onDownload() })
                DropdownMenuItem(text = { Text(if (unlike) "Unlike on YouTube" else "Like on YouTube") }, enabled = !busy,
                    onClick = { menu = false; onLike() })
                if (removable) DropdownMenuItem(text = { Text("Remove from YouTube playlist") }, enabled = !busy,
                    onClick = { menu = false; onRemove() })
            }
        }
    }
    }
}
