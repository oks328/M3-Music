@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.oks328.m3music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.painterResource
import io.github.oks328.m3music.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.oks328.m3music.account.*
import io.github.oks328.m3music.music.Track
import kotlinx.coroutines.CancellationException

data class AccountActions(
    val signIn: () -> Unit = {},
    val disconnect: () -> Unit = {},
    val category: (AccountCategory) -> Unit = {},
    val open: (AccountItem) -> Unit = {},
    val back: () -> Unit = {},
    val refresh: () -> Unit = {},
    val more: () -> Unit = {},
    val perform: (AccountAction) -> Unit = {},
    val suggestions: suspend (String, String?) -> List<Track> = { _, _ -> emptyList() },
    val searchSongs: suspend (String) -> List<Track> = { emptyList() },
    val editablePlaylists: suspend (String?) -> AccountPage = { AccountPage() },
    val savePlaylistTrack: suspend (AccountItem, Track) -> Boolean = { _, _ -> false },
)

@Composable
internal fun AccountPanel(
    state: AccountState,
    actions: AccountActions,
    playbackConnected: Boolean,
    onPlaySongs: (List<Track>, Int) -> Unit,
    onDownload: (Track) -> Unit,
    collectionDownload: @Composable (AccountItem, AccountPage) -> Unit,
    candidates: List<Track>,
    showSettings: Boolean = false,
    onDismissSettings: () -> Unit = {},
    playingTrackId: String? = null,
    modifier: Modifier = Modifier,
    bottomClearance: androidx.compose.ui.unit.Dp = 0.dp,
) {
    var deleting by remember { mutableStateOf<AccountItem?>(null) }
    var removing by remember { mutableStateOf<AccountItem?>(null) }
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var playLikedWhenLoaded by remember { mutableStateOf(false) }
    val collection = state.collection
    val page = if (collection == null) state.page else state.detail
    val editable = collection?.kind == AccountItemKind.PLAYLIST && (collection.editable || page.editable)
    var adding by rememberSaveable(collection?.id) { mutableStateOf(false) }
    val currentIds = remember(page.items) { page.items.mapNotNull { it.track?.id }.toSet() }
    val addSong: (Track) -> Unit = { track ->
        if (collection != null && editable && !state.mutating && track.id !in currentIds) {
            actions.perform(AccountAction.AddSong(collection.id, track.id))
        }
    }
    var suggestions by remember(collection?.id, state.expired) { mutableStateOf<List<Track>?>(null) }
    val token = page.suggestionsToken
    LaunchedEffect(collection?.id, token, editable, state.loading, state.expired) {
        if (collection != null && editable && !state.loading && !state.expired) {
            suggestions = try {
                actions.suggestions(collection.id, token)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(playLikedWhenLoaded, state.category, state.page, state.loading, state.error) {
        if (playLikedWhenLoaded && state.category == AccountCategory.LIKED_SONGS && !state.loading) {
            val songs = state.page.items.mapNotNull { it.track }
            if (songs.isNotEmpty() && state.error == null && !state.expired && state.profile != null) onPlaySongs(songs, 0)
            playLikedWhenLoaded = false
        }
    }
    val pageKey = collection?.id ?: state.category.name
    var lastPageKey by rememberSaveable { mutableStateOf(pageKey) }
    LaunchedEffect(pageKey) {
        if (lastPageKey != pageKey) {
            lastPageKey = pageKey
            listState.scrollToItem(0)
        }
    }
    BackHandler(collection != null) { actions.back() }
    LaunchedEffect(state.profile, state.expired) {
        if (state.profile != null && !state.expired) actions.refresh()
    }
    if (adding && collection != null && editable && !state.expired) {
        PlaylistSongPicker(collection.title, actions.searchSongs, currentIds, state.mutating || state.loading,
            state.error, onAdd = addSong, onBack = { adding = false }, bottomClearance = bottomClearance, modifier = modifier)
        return
    }
    LazyColumn(modifier.fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(if (collection == null) 4.dp else 12.dp),
        contentPadding = PaddingValues(bottom = 16.dp + bottomClearance)) {
        when {
            state.initializing -> item { LinearWavyProgressIndicator(Modifier.fillMaxWidth()) }
            state.profile == null -> item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("All your music.\nRight at home.", style = MaterialTheme.typography.headlineLarge)
                            Text("Bring your YouTube Music playlists, liked songs, and favorite artists together.")
                            Button(onClick = actions.signIn, enabled = !state.connecting) { Text("Connect Google account") }
                        }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            else -> {
                if (state.expired) item {
                    Text("Your session has expired. Reconnect to load account music.", color = MaterialTheme.colorScheme.error)
                    Button(onClick = actions.signIn) { Text("Reconnect Google account") }
                } else {
                    if (collection == null) item {
                        Row(Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            AccountCategory.entries.forEach { category ->
                                FilterChip(selected = state.category == category,
                                    onClick = { playLikedWhenLoaded = false; actions.category(category) },
                                    label = {
                                        Text(if (category == AccountCategory.ARTISTS) "Artists" else category.label,
                                            style = MaterialTheme.typography.labelMedium)
                                    })
                            }
                        }
                    }
                    if (collection != null) item {
                        CollectionHeader(collection.copy(artworkUrl = page.artworkUrl ?: collection.artworkUrl), page.items.mapNotNull { it.track }, page.continuation != null,
                            playbackConnected, editable, state.loading || state.mutating, actions.back, onPlaySongs,
                            actions.refresh, onAdd = { adding = true }, onRename = { name = collection.title; naming = true },
                            onDelete = { deleting = collection },
                            downloadAction = if (collection.kind in listOf(AccountItemKind.ALBUM, AccountItemKind.PLAYLIST)) {
                                { collectionDownload(collection, page) }
                            } else null)
                    }
                    else when (state.category) {
                        AccountCategory.PLAYLISTS -> item {
                            FilledTonalButton(onClick = { name = ""; naming = true }, enabled = !state.mutating) {
                                Icon(painterResource(R.drawable.ic_add_playlist), null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("New playlist")
                            }
                        }
                        AccountCategory.LIKED_SONGS -> item {
                            FilledTonalButton(onClick = { playLikedWhenLoaded = true },
                                enabled = playbackConnected && (state.loading || page.items.any { it.track != null })) {
                                Icon(painterResource(R.drawable.ic_play), null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Play liked songs")
                            }
                        }
                        else -> Unit
                    }
                    if (editable) item {
                        FilledTonalButton(onClick = { adding = true }, enabled = !state.mutating,
                            modifier = Modifier.fillMaxWidth()) {
                            Icon(painterResource(R.drawable.ic_add_playlist), null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add a song")
                        }
                    }
                    state.message?.let { message -> item { Text(message, style = MaterialTheme.typography.bodySmall) } }
                    state.error?.let { message -> item {
                        Text(message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = actions.refresh, enabled = !state.loading) { Text("Retry account request") }
                    } }
                    if (!state.loading && state.error == null && page.items.isEmpty()) item {
                        Text(if (collection == null) "Nothing here yet. Try another category or refresh."
                            else if (editable) "No songs in this playlist yet."
                            else "No songs are available in this collection.")
                    }
                    if (collection == null && page.items.isNotEmpty() && page.items.all { it.track == null }) {
                        items(page.items, key = { it.key }) { entry ->
                            AccountLibraryRow(entry, onClick = { actions.open(entry) })
                        }
                    } else if (page.items.isNotEmpty() && page.items.all { it.track == null }) {
                        items(page.items.chunked(2), key = { it.first().key }) { pair ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { entry -> CollectionCard(entry, { actions.open(entry) }, Modifier.weight(1f)) }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else itemsIndexed(page.items, key = { _, item -> item.key }) { index, item ->
                        val track = item.track
                        if (track != null) {
                            CollectionSongRow(track, track.id == playingTrackId, playbackConnected, state.mutating,
                                unlike = collection == null && state.category == AccountCategory.LIKED_SONGS,
                                removable = editable && !item.setVideoId.isNullOrBlank(), onPlay = {
                                    val songs = page.items.mapNotNull { it.track }
                                    onPlaySongs(songs, page.items.take(index).count { it.track != null })
                                }, onDownload = { onDownload(track) },
                                onLike = { actions.perform(AccountAction.LikeSong(track.id, !(collection == null && state.category == AccountCategory.LIKED_SONGS))) },
                                onRemove = { removing = item })
                        } else {
                            CollectionCard(item, { actions.open(item) }, Modifier.fillMaxWidth())
                        }
                    }
                    if (state.loading || state.mutating) item { LinearWavyProgressIndicator(Modifier.fillMaxWidth()) }
                    if (page.continuation != null) item {
                        Button(onClick = actions.more, enabled = !state.loading && !state.mutating) { Text("Load more") }
                    }
                    if (editable) {
                        val suggested = (suggestions?.takeIf { it.isNotEmpty() } ?: candidates)
                            .asSequence().filter { it.id !in currentIds }.distinctBy { it.id }.take(8).toList()
                        if (suggested.isNotEmpty()) {
                            item {
                                Text("Suggestions", style = MaterialTheme.typography.headlineSmall)
                                if (suggestions.isNullOrEmpty()) {
                                    Text("From your listening and searches", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            items(suggested, key = { "suggestion:${it.id}" }) { track ->
                                PlaylistAddSongRow(track, added = false, enabled = !state.mutating && !state.loading,
                                    onAdd = { addSong(track) })
                            }
                        }
                    }
                }
            }
        }
    }
    if (showSettings) AccountSettingsDialog(state, actions, onDismissSettings)
    if (naming) AlertDialog(onDismissRequest = { naming = false },
        title = { Text(if (collection == null) "New private YouTube playlist" else "Rename YouTube playlist") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("YouTube playlist name") }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank() && name.trim().length <= 80, onClick = {
            actions.perform(if (collection == null) AccountAction.CreatePlaylist(name.trim()) else AccountAction.RenamePlaylist(collection.id, name.trim()))
            naming = false
        }) { Text(if (collection == null) "Create on YouTube" else "Save on YouTube") } },
        dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } })
    deleting?.let { target -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${target.title} on YouTube?") },
        text = { Text("This deletes the playlist from your YouTube Music account. Local playlists and downloads stay.") },
        confirmButton = { TextButton(onClick = { actions.perform(AccountAction.DeletePlaylist(target.id)); deleting = null }) { Text("Delete on YouTube") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep playlist") } }) }
    removing?.let { target -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remove ${target.title}?") },
        text = { Text("Remove this song from ${collection?.title} on YouTube Music? Its downloaded copy stays.") },
        confirmButton = { TextButton(onClick = {
            collection?.let { actions.perform(AccountAction.RemoveSong(it.id, target.id, requireNotNull(target.setVideoId))) }
            removing = null
        }) { Text("Remove on YouTube") } }, dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } }) }
}

@Composable
private fun AccountLibraryRow(entry: AccountItem, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Artwork(entry.artworkUrl, Modifier.size(52.dp),
                cornerRadius = if (entry.kind == AccountItemKind.ARTIST) 26.dp else 10.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (entry.subtitle.isNotBlank()) {
                    Text(entry.subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
