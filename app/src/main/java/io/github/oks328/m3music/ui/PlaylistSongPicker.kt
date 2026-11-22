@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.oks328.m3music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.oks328.m3music.R
import io.github.oks328.m3music.music.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun PlaylistSongPicker(
    playlist: String,
    search: suspend (String) -> List<Track>,
    addedIds: Set<String>,
    saving: Boolean,
    saveError: String?,
    onAdd: (Track) -> Unit,
    onBack: () -> Unit,
    bottomClearance: Dp,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<Track>?>(null) }
    var error by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val back = { keyboard?.hide(); onBack() }
    BackHandler(onBack = back)
    LaunchedEffect(Unit) { focus.requestFocus() }
    val term = query.trim()
    val submit = {
        keyboard?.hide()
        submitted = term
    }
    LaunchedEffect(term, submitted, retry) {
        results = null
        error = false
        if (term.isEmpty()) return@LaunchedEffect
        if (term != submitted) delay(350)
        try {
            results = search(term).distinctBy { it.id }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = true
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackIconButton(onClick = back)
            Column {
                Text("Add a song", style = MaterialTheme.typography.titleLarge)
                Text(playlist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(value = query, onValueChange = { query = it },
            label = { Text("Search songs") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            shape = RoundedCornerShape(24.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            trailingIcon = {
                IconButton(onClick = submit, enabled = term.isNotEmpty()) {
                    Icon(painterResource(R.drawable.ic_search), "Submit song search")
                }
            })
        saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (saving) Text("Updating playlist…", style = MaterialTheme.typography.bodySmall)
        when {
            term.isEmpty() -> Unit
            error -> {
                Text("Couldn't load songs from YouTube Music.", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retry++ }) { Text("Try again") }
            }
            results == null -> LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            results.isNullOrEmpty() -> Text("No songs found.")
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = bottomClearance + 16.dp)) {
                items(results.orEmpty(), key = { it.id }) { track ->
                    PlaylistAddSongRow(track, track.id in addedIds, !saving) { onAdd(track) }
                }
            }
        }
    }
}

@Composable
internal fun PlaylistAddSongRow(track: Track, added: Boolean, enabled: Boolean, onAdd: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.artworkUrl, Modifier.size(48.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (track.artist.isNotBlank()) Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (added) Text("Added", style = MaterialTheme.typography.labelMedium)
            else FilledTonalIconButton(onClick = onAdd, enabled = enabled) {
                Icon(painterResource(R.drawable.ic_add_playlist), "Add ${track.title} to playlist")
            }
        }
    }
}
