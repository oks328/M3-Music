package io.github.oks328.m3music.ui

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy
import androidx.compose.ui.platform.LocalContext
import io.github.oks328.m3music.downloads.DownloadsState
import io.github.oks328.m3music.downloads.DownloadEntry
import io.github.oks328.m3music.downloads.DownloadStatus
import io.github.oks328.m3music.R
import io.github.oks328.m3music.music.SearchState
import io.github.oks328.m3music.music.Track
import io.github.oks328.m3music.playback.PlaybackState
import io.github.oks328.m3music.library.LibraryState
import io.github.oks328.m3music.library.LocalPlaylist
import io.github.oks328.m3music.account.AccountState

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
fun MusicScreen(
    search: SearchState,
    playback: PlaybackState,
    onSearch: (String) -> Unit,
    onPlay: (Track) -> Unit,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    onRetryPlayback: () -> Unit,
    downloads: DownloadsState = DownloadsState(loading = false),
    onDownload: (Track) -> Unit = {},
    onRemoveDownload: (String) -> Unit = {},
    onWifiOnlyChanged: (Boolean) -> Unit = {},
    library: LibraryState = LibraryState(loading = false),
    onFavorite: (Track, Boolean) -> Unit = { _, _ -> },
    onCreatePlaylist: (String, Track?) -> Unit = { _, _ -> },
    onRenamePlaylist: (String, String) -> Unit = { _, _ -> },
    onDeletePlaylist: (String) -> Unit = {},
    onAddToPlaylist: (String, Track) -> Unit = { _, _ -> },
    onRemovePlaylistSong: (String, String) -> Unit = { _, _ -> },
    onMovePlaylistSong: (String, String, Int) -> Unit = { _, _, _ -> },
    onClearHistory: () -> Unit = {},
    onDismissLibraryError: () -> Unit = {},
    onPlayPlaylist: (List<Track>, Int) -> Unit = { _, _ -> },
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    account: AccountState = AccountState(initializing = false),
    accountActions: AccountActions = AccountActions(),
    onQueueItem: (Int) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf(search.query) }
    var destination by rememberSaveable { mutableStateOf("Home") }
    val showDownloads = destination == "Downloads"
    val showLibrary = destination == "Library"
    val showHome = destination == "Home"
    var showAccount by rememberSaveable { mutableStateOf(false) }
    var expandedPlayer by rememberSaveable { mutableStateOf(false) }
    BackHandler(!showHome && !showAccount && !expandedPlayer) { destination = "Home" }
    var addTrack by remember { mutableStateOf<Track?>(null) }
    var creating by remember { mutableStateOf(false) }
    var createWithTrack by remember { mutableStateOf<Track?>(null) }
    var renaming by remember { mutableStateOf<LocalPlaylist?>(null) }
    var deleting by remember { mutableStateOf<LocalPlaylist?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(library.error) {
        library.error?.let { snackbar.showSnackbar(it); onDismissLibraryError() }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        keyboard?.hide()
        onSearch(query)
    }
    Box(Modifier.fillMaxSize()) {
    Scaffold(modifier = Modifier.fillMaxSize().imePadding(), snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!expandedPlayer || playback.track == null) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (playback.track != null) CompactPlayer(playback, onTogglePlayback, { expandedPlayer = true })
                    Box(
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
                            .widthIn(max = 480.dp).fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(28.dp), clip = true)
                    ) {
                        ShortNavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            listOf("Home" to R.drawable.ic_home, "Search" to R.drawable.ic_search,
                                "Library" to R.drawable.ic_library, "Downloads" to R.drawable.ic_download)
                                .forEach { (label, icon) ->
                                    ShortNavigationBarItem(
                                        selected = destination == label,
                                        onClick = { destination = label },
                                        icon = { Icon(painterResource(icon), null, Modifier.size(26.dp)) },
                                        label = {
                                            Text(label, style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        },
                                    )
                                }
                        }
                    }
                }
            }
        }) { insets ->
        Column(Modifier.fillMaxSize().padding(
            start = insets.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
            top = insets.calculateTopPadding(),
            end = insets.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
        ).widthIn(max = 720.dp)
            .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (showHome && account.collection != null) when (account.collection.kind) {
                    io.github.oks328.m3music.account.AccountItemKind.ALBUM -> "Album"
                    io.github.oks328.m3music.account.AccountItemKind.ARTIST -> "Artist"
                    else -> "Playlist"
                } else if (showHome) "M3 Music" else destination,
                    style = MaterialTheme.typography.titleLarge)
            }
                FilledTonalIconButton(onClick = { showAccount = true }, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_account), "Account")
                }
            }
            if (showHome || showLibrary) Unit else if (showDownloads) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Download on Wi-Fi only", modifier = Modifier.weight(1f))
                    Switch(checked = downloads.wifiOnly, onCheckedChange = onWifiOnlyChanged,
                        modifier = Modifier.semantics { contentDescription = "Download on Wi-Fi only" })
                }
            } else OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Songs or artists") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                trailingIcon = {
                    IconButton(onClick = { submit() }, enabled = query.isNotBlank()) { Icon(painterResource(R.drawable.ic_search), "Submit search") }
                },
            )
            if (showHome) {
                AccountPanel(account, accountActions, playbackConnected = playback.connected,
                    onPlay = onPlay, onPlaySongs = onPlayPlaylist, onDownload = onDownload,
                    candidates = (search.tracks + library.favorites + downloads.items.map { it.track } + library.history).distinctBy { it.id },
                    recent = library.history, showSettings = showAccount, onDismissSettings = { showAccount = false },
                    playingTrackId = playback.track?.id,
                    bottomClearance = insets.calculateBottomPadding(),
                    modifier = Modifier.weight(1f).fillMaxWidth())
            } else if (showLibrary) {
                LibraryPanel(library, playback, downloads, onPlay, onPlayPlaylist, onFavorite,
                    onAdd = { addTrack = it }, onCreate = { createWithTrack = null; creating = true },
                    onRename = { renaming = it }, onDelete = { deleting = it },
                    onRemoveSong = onRemovePlaylistSong, onMoveSong = onMovePlaylistSong,
                    onClearHistory = onClearHistory, bottomClearance = insets.calculateBottomPadding(), modifier = Modifier.weight(1f).fillMaxWidth())
            } else LazyColumn(Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp + insets.calculateBottomPadding())) {
                when {
                    showDownloads -> {
                        downloads.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                        when {
                            downloads.loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                            downloads.items.isEmpty() -> item {
                                Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Take your music with you", style = MaterialTheme.typography.headlineSmall)
                                    Text("Download a song from search to listen without a connection.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            else -> {
                                item {
                                    Text("${downloads.items.count { it.status == DownloadStatus.COMPLETED }} songs ready offline",
                                        style = MaterialTheme.typography.titleMedium)
                                }
                                items(downloads.items, key = { it.track.id }) { entry ->
                                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Column(Modifier.padding(8.dp)) {
                                        SongRow(entry.track, playback.track?.id == entry.track.id,
                                            enabled = playback.connected && entry.status == DownloadStatus.COMPLETED,
                                            offline = entry.status == DownloadStatus.COMPLETED,
                                            onClick = { onPlay(entry.track) })
                                        DownloadActions(entry, downloads, { onDownload(entry.track) }, { onRemoveDownload(entry.track.id) })
                                        LibrarySongActions(entry.track, library.favorites.any { it.id == entry.track.id }, onFavorite, { addTrack = it })
                                    }
                                    }
                                }
                            }
                        }
                    }
                    search.loading -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            LoadingIndicator()
                            Text("Searching YouTube Music…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    search.error != null -> item {
                        Text(search.error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { onSearch(search.query) }) { Text("Try again") }
                    }
                    !search.hasSearched -> item {
                        Column(Modifier.padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(painterResource(R.drawable.ic_music_note), null,
                                Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Your music starts here", style = MaterialTheme.typography.headlineSmall)
                            Text("Search for a song or artist on YouTube Music.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    search.tracks.isEmpty() -> item {
                        Text("No songs found. Try a different song or artist.")
                    }
                    else -> {
                        item { Text("Songs", style = MaterialTheme.typography.titleMedium) }
                        items(search.tracks, key = { it.id }) { track ->
                            val entry = downloads.items.firstOrNull { it.track.id == track.id }
                            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(Modifier.padding(8.dp)) {
                                SongRow(track, playback.track?.id == track.id,
                                    enabled = playback.connected, onClick = { onPlay(track) })
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                LibrarySongActions(track, library.favorites.any { it.id == track.id }, onFavorite, { addTrack = it })
                                Spacer(Modifier.weight(1f))
                                if (entry == null) FilledTonalIconButton(onClick = { onDownload(track) },
                                    modifier = Modifier.semantics { contentDescription = "Download ${track.title}" }) { Icon(painterResource(R.drawable.ic_download), null) }
                                }
                                if (entry != null) DownloadActions(entry, downloads, { onDownload(track) }, { onRemoveDownload(track.id) })
                            }
                            }
                        }
                        item {
                            Text("Showing the first results from YouTube Music.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (destination == "Search" && downloads.error != null) {
                Text(downloads.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (!playback.connected) {
                Text(playback.error ?: "Starting the player…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    if (showAccount && !showHome) AccountSettingsDialog(account, accountActions, { showAccount = false })
    val currentTrack = playback.track
    if (currentTrack != null) {
        BackHandler(expandedPlayer) { expandedPlayer = false }
        val motion = MaterialTheme.motionScheme
        AnimatedVisibility(
            visible = expandedPlayer,
            enter = slideInVertically(motion.defaultSpatialSpec()) { it },
            exit = slideOutVertically(motion.defaultSpatialSpec()) { it },
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Box(Modifier.statusBarsPadding()) {
                    ExpressivePlayer(playback, library.favorites.any { it.id == currentTrack.id }, onTogglePlayback,
                        onSeek, onRetryPlayback, onPrevious, onNext, onQueueItem,
                        onFavorite = { onFavorite(currentTrack, library.favorites.none { it.id == currentTrack.id }) },
                        onDownload = { onDownload(currentTrack) }, onClose = { expandedPlayer = false })
                }
            }
        }
    }
    }
    addTrack?.let { track ->
        AlertDialog(onDismissRequest = { addTrack = null }, title = { Text("Add to playlist") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium)
                    library.playlists.forEach { playlist ->
                        val contains = playlist.tracks.any { it.id == track.id }
                        TextButton(enabled = !contains, onClick = { onAddToPlaylist(playlist.id, track); addTrack = null }) {
                            Text(if (contains) "${playlist.name} · Already added" else playlist.name)
                        }
                    }
                    TextButton(onClick = { createWithTrack = track; addTrack = null; creating = true }) { Text("New playlist") }
                }
            }, confirmButton = { TextButton(onClick = { addTrack = null }) { Text("Done") } })
    }
    if (creating || renaming != null) {
        val editing = renaming
        PlaylistNameDialog(editing?.name.orEmpty(), editing != null,
            onDismiss = { creating = false; renaming = null; createWithTrack = null },
            onConfirm = { name ->
                if (editing == null) onCreatePlaylist(name, createWithTrack) else onRenamePlaylist(editing.id, name)
                creating = false; renaming = null; createWithTrack = null
            })
    }
    deleting?.let { playlist ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${playlist.name}?") },
            text = { Text("Only this playlist will be removed. Your favorites and downloaded songs will stay.") },
            confirmButton = { TextButton(onClick = { onDeletePlaylist(playlist.id); deleting = null }) { Text("Delete playlist") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep playlist") } })
    }
}

@Composable
internal fun SongRow(track: Track, selected: Boolean, enabled: Boolean, offline: Boolean = false, onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color? = null) {
    Surface(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "Play ${track.title} by ${track.artist}"
        },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else containerColor ?: MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Artwork(track.artworkUrl, Modifier.size(56.dp), offline)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (track.durationSeconds > 0) {
                Text(formatTime(track.durationSeconds * 1000), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PlaylistNameDialog(initialName: String, editing: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (editing) "Rename playlist" else "New playlist") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Playlist name") },
                singleLine = true, isError = name.trim().length > 80,
                supportingText = { Text("${name.trim().length}/80") })
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) },
            enabled = name.isNotBlank() && name.trim().length <= 80) { Text(if (editing) "Save" else "Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun Artwork(url: String?, modifier: Modifier, offline: Boolean = false, large: Boolean = false) {
    var useOriginal by remember(url, large, offline) { mutableStateOf(false) }
    val requested = if (large && !offline && !useOriginal) largeArtworkUrl(url) else url
    Surface(modifier = modifier.clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        if (url != null) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(requested)
            .networkCachePolicy(if (offline) CachePolicy.DISABLED else CachePolicy.ENABLED).build(), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            error = painterResource(R.drawable.ic_music_note), onError = { if (requested != url) useOriginal = true })
        else Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_music_note), null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DownloadActions(entry: DownloadEntry, state: DownloadsState, onDownload: () -> Unit, onRemove: () -> Unit) {
    Column {
        if (entry.status == DownloadStatus.DOWNLOADING) {
            entry.percent?.let { percent -> LinearWavyProgressIndicator(progress = { (percent / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val label = when (entry.status) {
                DownloadStatus.COMPLETED -> "Saved · ${formatBytes(entry.bytes)}"
                DownloadStatus.PREPARING -> "Preparing download…"
                DownloadStatus.DOWNLOADING -> "Downloading ${entry.percent?.let { "$it%" }.orEmpty()} · ${formatBytes(entry.bytes)}"
                DownloadStatus.QUEUED -> if (state.waitingForNetwork)
                    if (state.wifiOnly) "Waiting for Wi-Fi" else "Waiting for connection" else "Queued"
                DownloadStatus.FAILED -> "Download failed. Check connection or storage."
                DownloadStatus.REMOVING -> "Removing…"
                DownloadStatus.PAUSED -> "Download paused"
            }
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = if (entry.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (entry.status == DownloadStatus.FAILED) TextButton(onClick = onDownload) { Text("Retry") }
            TextButton(onClick = onRemove, enabled = entry.status != DownloadStatus.REMOVING,
                modifier = Modifier.semantics { contentDescription = "Remove download of ${entry.track.title}" }) {
                Text(if (entry.status in listOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED)) "Remove" else "Cancel")
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = "%.1f MB".format(bytes / 1_000_000.0)

private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
