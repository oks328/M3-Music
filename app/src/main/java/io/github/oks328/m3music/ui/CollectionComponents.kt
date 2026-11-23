package io.github.oks328.m3music.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.oks328.m3music.R
import io.github.oks328.m3music.account.AccountItem
import io.github.oks328.m3music.music.Track
import io.github.oks328.m3music.downloads.DownloadStatus
import io.github.oks328.m3music.downloads.DownloadsState
import io.github.oks328.m3music.downloads.active
import io.github.oks328.m3music.downloads.downloadProgress
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
internal fun BackIconButton(onClick: () -> Unit, description: String = "Back", tonal: Boolean = false) {
    val icon: @Composable () -> Unit = {
        Icon(painterResource(R.drawable.ic_back), description,
            Modifier.scale(if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f, 1f))
    }
    if (tonal) FilledTonalIconButton(onClick = onClick, content = icon)
    else IconButton(onClick = onClick, content = icon)
}

@Composable
internal fun CollectionHeader(
    item: AccountItem, songs: List<Track>, hasMore: Boolean, connected: Boolean,
    editable: Boolean, busy: Boolean, onBack: () -> Unit, onPlay: (List<Track>, Int) -> Unit,
    onRefresh: () -> Unit, onAdd: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit,
    downloadAction: (@Composable () -> Unit)? = null,
) {
    var menu by remember(item.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackIconButton(onBack)
            Spacer(Modifier.weight(1f))
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
        val metadata = songs.takeIf { it.isNotEmpty() }?.let {
            val seconds = it.sumOf { song -> song.durationSeconds.toLong() }
            val duration = if (seconds > 0 && it.all { song -> song.durationSeconds > 0 }) " · ${seconds / 60} min" else ""
            "${it.size} ${if (it.size == 1) "song" else "songs"}${if (hasMore) " loaded" else ""}$duration"
        }
        LibraryCollectionTitle(item.title, item.artworkUrl, item.subtitle, metadata)
        CollectionPlaybackButtons(songs, hasMore, connected, onPlay, downloadAction)
        if (hasMore) Text("Play and Shuffle use the songs loaded below. Load more to include the rest.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CollectionPlaybackButtons(
    songs: List<Track>, hasMore: Boolean, connected: Boolean, onPlay: (List<Track>, Int) -> Unit,
    downloadAction: (@Composable () -> Unit)? = null,
    playLabel: String = if (hasMore) "Play loaded" else "Play",
) {
    val enabled = connected && songs.isNotEmpty()
    val labelStyle = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val buttonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val reservedWidth = if (downloadAction == null) 4.dp else 56.dp
        val buttonWidth = (maxWidth - reservedWidth) / 2
        fun fits(label: String): Boolean = with(density) {
            measurer.measure(label, style = labelStyle, maxLines = 1, softWrap = false).size.width.toDp() +
                24.dp + 8.dp + 24.dp <= buttonWidth
        }
        val showPlayLabel = fits(playLabel)
        val showShuffleLabel = fits("Shuffle")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { onPlay(songs, 0) }, enabled = enabled, contentPadding = buttonPadding,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                shape = RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp, topEnd = 8.dp, bottomEnd = 8.dp)) {
                Icon(painterResource(R.drawable.ic_play), if (showPlayLabel) null else playLabel)
                if (showPlayLabel) {
                    Spacer(Modifier.width(8.dp))
                    Text(playLabel, style = labelStyle, maxLines = 1, softWrap = false)
                }
            }
            FilledTonalButton(onClick = { onPlay(songs.shuffled(), 0) }, enabled = enabled, contentPadding = buttonPadding,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 32.dp, bottomEnd = 32.dp)) {
                Icon(painterResource(R.drawable.ic_shuffle), if (showShuffleLabel) null else "Shuffle")
                if (showShuffleLabel) {
                    Spacer(Modifier.width(8.dp))
                    Text("Shuffle", style = labelStyle, maxLines = 1, softWrap = false)
                }
            }
            downloadAction?.invoke()
        }
    }
}

@Composable
internal fun CollectionDownloadButton(
    kind: String, songs: List<Track>, hasMore: Boolean, downloads: DownloadsState,
    busy: Boolean, collectionId: String? = null, onClick: () -> Unit,
) {
    val groups = if (kind == "album") downloads.albums else downloads.playlists
    val collection = groups.firstOrNull { it.id == collectionId }
    val entries = downloads.items.associateBy { it.track.id }
    val visibleIds = songs.map { it.id }.distinct()
    val ids = if (hasMore) collection?.trackIds ?: visibleIds else visibleIds
    val completeSet = collection != null || !hasMore
    val registered = collectionId == null || collection?.trackIds == ids
    val active = registered && completeSet && ids.any { entries[it]?.active == true }
    val saved = registered && completeSet && ids.isNotEmpty() &&
        ids.all { entries[it]?.status == DownloadStatus.COMPLETED }
    val description = when {
        saved -> "$kind downloaded"
        busy || active -> "Downloading $kind"
        else -> "Download $kind"
    }
    FilledTonalIconButton(onClick = onClick, enabled = !busy && !active && !saved && (ids.isNotEmpty() || hasMore),
        modifier = Modifier.size(48.dp).semantics { contentDescription = description }) {
        when {
            busy -> DownloadProgressRing(null)
            active -> {
                val measured = ids.any { entries[it]?.let { entry ->
                    entry.status == DownloadStatus.COMPLETED || entry.percent != null
                } == true }
                DownloadProgressRing(if (measured) downloadProgress(ids, entries) else null)
            }
            else -> Icon(painterResource(R.drawable.ic_download), null)
        }
    }
}

@Composable
internal fun DownloadProgressRing(progress: Float?, modifier: Modifier = Modifier.size(22.dp)) {
    if (progress == null) CircularProgressIndicator(modifier, strokeWidth = 2.dp)
    else {
        val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "Download progress")
        CircularProgressIndicator(progress = { animated }, modifier = modifier, strokeWidth = 2.dp)
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
