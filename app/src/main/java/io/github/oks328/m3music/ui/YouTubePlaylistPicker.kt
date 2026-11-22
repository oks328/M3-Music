@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.oks328.m3music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.oks328.m3music.account.AccountItem
import io.github.oks328.m3music.music.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun YouTubePlaylistPicker(
    track: Track,
    actions: AccountActions,
    onDismiss: () -> Unit,
) {
    var playlists by remember { mutableStateOf(emptyList<AccountItem>()) }
    var continuation by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var savedIds by remember { mutableStateOf(emptySet<String>()) }
    var savedTitle by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val visited = remember { mutableSetOf<String>() }
    val scope = rememberCoroutineScope()
    val load: suspend (String?) -> Unit = { token ->
        loading = true
        error = null
        try {
            var pageToken = token
            do {
                val page = actions.editablePlaylists(pageToken)
                if (pageToken != null) visited.add(pageToken)
                playlists = (playlists + page.items).distinctBy { it.id }
                pageToken = page.continuation?.takeUnless(visited::contains)
                continuation = pageToken
            } while (playlists.isEmpty() && pageToken != null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "Couldn't load YouTube Music playlists."
        } finally {
            loading = false
        }
    }
    LaunchedEffect(track.id) { load(null) }

    AlertDialog(onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Add to YouTube Music playlist") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text(track.title, style = MaterialTheme.typography.titleMedium) }
                savedTitle?.let { title -> item { Text("Added to $title", color = MaterialTheme.colorScheme.primary) } }
                items(playlists, key = { it.id }) { playlist ->
                    TextButton(enabled = !saving && !loading && playlist.id !in savedIds, onClick = {
                        saving = true
                        error = null
                        scope.launch {
                            try {
                                if (actions.savePlaylistTrack(playlist, track)) {
                                    savedIds = savedIds + playlist.id
                                    savedTitle = playlist.title
                                } else error = "Couldn't confirm the add. Check the playlist before trying again."
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                error = "Couldn't confirm the add. Check the playlist before trying again."
                            } finally {
                                saving = false
                            }
                        }
                    }) { Text(playlist.title) }
                }
                if (loading || saving) item { LinearWavyProgressIndicator(Modifier.fillMaxWidth()) }
                error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                if (!loading && !saving) {
                    if (continuation != null || error != null && playlists.isEmpty()) {
                        item { TextButton(onClick = { scope.launch { load(continuation) } }) { Text("Load playlists") } }
                    } else if (playlists.isEmpty() && error == null) {
                        item { Text("Create a YouTube Music playlist in Library to add this song.") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Done") } },
    )
}
