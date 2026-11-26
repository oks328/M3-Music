@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.oks328.m3music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import io.github.oks328.m3music.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.oks328.m3music.account.*
import io.github.oks328.m3music.music.Track
import io.github.oks328.m3music.playback.PlaybackState
import kotlinx.coroutines.CancellationException

@Composable
internal fun DiscoveryPanel(
    account: AccountState,
    load: suspend () -> List<DiscoveryShelf>,
    loadCollection: suspend (String) -> AccountPage,
    playback: PlaybackState,
    play: (List<Track>, Int) -> Unit,
    bottomClearance: Dp,
    collectionDownload: @Composable (AccountItem, AccountPage) -> Unit,
    modifier: Modifier = Modifier,
    networkKey: Long? = null,
    queueCollection: (AccountItem, AccountPage, Boolean) -> Unit = { _, _, _ -> },
) {
    var shelves by remember(account.profile, account.expired) { mutableStateOf<List<DiscoveryShelf>?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val selected = shelves?.asSequence()?.flatMap { it.items.asSequence() }?.firstOrNull { it.id == selectedId }
    LaunchedEffect(account.initializing, account.profile, account.expired, refresh, networkKey) {
        if (account.initializing) return@LaunchedEffect
        error = null
        try {
            shelves = load()
            if (shelves.orEmpty().none { shelf -> shelf.items.any { it.id == selectedId } }) selectedId = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "Couldn't load recommendations from YouTube Music."
        }
    }
    val feedScroll = rememberLazyListState()
    val panelModifier = modifier.collectionMotion(selected?.id)
    if (selected != null) {
        CatalogCollectionPanel(selected, loadCollection, playback, play, bottomClearance, collectionDownload,
            onBack = { selectedId = null }, modifier = panelModifier, queueCollection = queueCollection)
    } else {
        LazyColumn(panelModifier, state = feedScroll, verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = bottomClearance + 16.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Discover", style = MaterialTheme.typography.displaySmall)
                        FilledTonalIconButton(onClick = { refresh++ }) {
                            Icon(painterResource(R.drawable.ic_refresh), "Refresh recommendations")
                        }
                    }
                }
                if (error != null) item { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
                else if (shelves == null) item { LinearWavyProgressIndicator(Modifier.fillMaxWidth()) }
                else if (shelves.isNullOrEmpty()) item { Text("YouTube Music returned no recommendations.") }
                items(shelves.orEmpty()) { shelf ->
                    val songs = shelf.items.mapNotNull { it.track }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(shelf.title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                            if (songs.isNotEmpty()) TextButton(enabled = playback.connected,
                                onClick = { play(songs, 0) }) { Text("Play all") }
                        }
                        if (songs.size == shelf.items.size) {
                            BoxWithConstraints {
                                val pageWidth = maxWidth - 24.dp
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(songs.chunked(4)) { group ->
                                        Column(Modifier.width(pageWidth), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            group.forEach { track ->
                                                SongRow(track, playback.track?.id == track.id, playback.connected,
                                                    onClick = { play(songs, songs.indexOf(track)) })
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(shelf.items, key = { it.key }) { entry ->
                                    Surface(onClick = {
                                        val song = entry.track
                                        if (song != null) play(songs, songs.indexOf(song)) else selectedId = entry.id
                                    }, enabled = entry.track == null || playback.connected,
                                        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                                        color = Color.Transparent) {
                                        Column(Modifier.width(144.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Artwork(entry.artworkUrl, Modifier.fillMaxWidth().aspectRatio(1f), cornerRadius = 18.dp)
                                            Text(entry.title, modifier = Modifier.padding(horizontal = 2.dp), minLines = 2,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.titleSmall)
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
