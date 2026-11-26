@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.oks328.m3music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.oks328.m3music.account.AccountItem
import io.github.oks328.m3music.account.AccountItemKind
import io.github.oks328.m3music.account.AccountPage
import io.github.oks328.m3music.music.Track
import io.github.oks328.m3music.playback.PlaybackState
import kotlinx.coroutines.CancellationException

@Composable
internal fun CatalogCollectionPanel(
    root: AccountItem,
    load: suspend (String) -> AccountPage,
    playback: PlaybackState,
    play: (List<Track>, Int) -> Unit,
    bottomClearance: Dp,
    collectionDownload: @Composable (AccountItem, AccountPage) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    queueCollection: (AccountItem, AccountPage, Boolean) -> Unit = { _, _, _ -> },
) {
    var trail by remember(root.id) { mutableStateOf(listOf(root)) }
    val selected = trail.last()
    val back = { if (trail.size > 1) trail = trail.dropLast(1) else onBack() }
    BackHandler(onBack = back)
    var collection by remember(selected.id) { mutableStateOf<AccountPage?>(null) }
    var error by remember(selected.id) { mutableStateOf(false) }
    var retry by remember(selected.id) { mutableIntStateOf(0) }
    LaunchedEffect(selected.id, retry) {
        error = false
        try {
            collection = load(selected.id).withAlbumMetadata(selected)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = true
        }
    }
    val artist = selected.kind == AccountItemKind.ARTIST
    val shelves = collection?.shelves.orEmpty()
    var showAllSongs by remember(selected.id) { mutableStateOf(false) }
    val topSongs = shelves.firstOrNull { shelf -> artist && shelf.title in setOf("Songs", "Top songs", "Popular") }
        ?.items?.mapNotNull { it.track }?.takeIf { it.isNotEmpty() }
        ?: collection?.items.orEmpty().mapNotNull { it.track }
    val songs = if (artist) topSongs.map { if (it.artist.isBlank()) it.copy(artist = selected.title) else it } else topSongs
    val albums = if (artist) collection?.items.orEmpty().filter { it.kind == AccountItemKind.ALBUM } else emptyList()
    val releases = shelves.mapNotNull { shelf ->
        if (!artist || shelf.title !in setOf("Albums", "Singles", "Singles & EPs", "Singles and EPs", "Featured on")) {
            return@mapNotNull null
        }
        val entries = shelf.items.filter { it.kind == AccountItemKind.ALBUM || it.kind == AccountItemKind.PLAYLIST }
        if (entries.isEmpty()) null else shelf.title to entries
    }.ifEmpty { if (albums.isEmpty()) emptyList() else listOf("Albums" to albums) }
    val panelModifier = modifier.collectionMotion(selected.id, trail.size)
    key(selected.id) {
        LazyColumn(panelModifier, verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = bottomClearance + 16.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (artist) {
                        val cover = collection?.artworkUrl ?: selected.artworkUrl
                        ArtistHeader(selected.title, cover, back)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BackIconButton(onClick = back)
                            Column(Modifier.weight(1f)) {
                                Text(selected.title, style = MaterialTheme.typography.headlineMedium)
                                val credit = collection?.albumArtist ?: selected.albumArtist
                                if (selected.kind == AccountItemKind.ALBUM && credit != null) {
                                    Text(credit, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            collection?.let { page ->
                                if (playback.connected && (songs.isNotEmpty() || page.continuation != null)) {
                                    CollectionQueueMenu { next -> queueCollection(selected, page, next) }
                                }
                            }
                        }
                        val cover = collection?.artworkUrl ?: selected.artworkUrl
                        if (cover != null) {
                            Artwork(cover, Modifier.widthIn(max = 208.dp).fillMaxWidth().aspectRatio(1f)
                                .align(Alignment.CenterHorizontally), large = true, cornerRadius = 24.dp)
                        }
                    }
                    collection?.let { page ->
                        if (songs.isNotEmpty() || page.continuation != null) {
                            CollectionPlaybackButtons(songs, page.continuation != null, playback.connected, play,
                                downloadAction = if (selected.kind != AccountItemKind.ARTIST) {
                                    { collectionDownload(selected, page) }
                                } else null)
                        }
                    }
                }
            }
            when {
                error -> item {
                    Text("Couldn't load this page.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { retry++ }) { Text("Try again") }
                }
                collection == null -> item { LinearWavyProgressIndicator(Modifier.fillMaxWidth()) }
                songs.isEmpty() && releases.isEmpty() -> item { Text("No songs or releases available.") }
                else -> {
                    if (songs.isNotEmpty() && artist) {
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Top songs", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                                if (songs.size > 5) TextButton(onClick = { showAllSongs = !showAllSongs }) {
                                    Text(if (showAllSongs) "Show fewer" else "See all")
                                }
                            }
                        }
                    }
                    items(if (artist && !showAllSongs) minOf(5, songs.size) else songs.size) { index ->
                        val track = songs[index]
                        SongRow(track, playback.track?.id == track.id, playback.connected,
                            onClick = { play(songs, index) })
                    }
                    releases.forEach { (title, entries) ->
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(title, style = MaterialTheme.typography.headlineSmall)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(entries, key = { it.id }) { release ->
                                        Surface(onClick = { trail = trail + release }, shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                            Column(Modifier.width(156.dp).padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Artwork(release.artworkUrl, Modifier.fillMaxWidth().aspectRatio(1f))
                                                Text(release.title, style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistHeader(title: String, cover: String?, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        if (cover != null) {
            Artwork(cover, Modifier.fillMaxWidth().heightIn(max = 264.dp).aspectRatio(1.8f),
                large = true, cornerRadius = 28.dp)
        }
        BackIconButton(onClick = onBack, tonal = cover != null)
    }
    Text(title, style = MaterialTheme.typography.displaySmall)
}

@Composable
internal fun CatalogResultRow(item: AccountItem, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Artwork(item.artworkUrl, Modifier.size(56.dp),
                cornerRadius = if (item.kind == AccountItemKind.ARTIST) 28.dp else 12.dp)
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val subtitle = item.albumArtist ?: item.subtitle
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
