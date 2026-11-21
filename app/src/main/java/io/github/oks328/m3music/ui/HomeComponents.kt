@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.oks328.m3music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.oks328.m3music.R
import io.github.oks328.m3music.account.*
import io.github.oks328.m3music.music.Track

@Composable
internal fun HomeIntro(onLiked: () -> Unit, onPlayLiked: () -> Unit = onLiked, canPlay: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Surface(onClick = onLiked, shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp, bottomStart = 12.dp, bottomEnd = 36.dp),
            color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Liked songs", style = MaterialTheme.typography.headlineSmall)
                    Text("Your liked songs", style = MaterialTheme.typography.bodyMedium)
                }
                Surface(shape = MaterialShapes.Cookie4Sided.toShape(), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Icon(painterResource(R.drawable.ic_heart), null, Modifier.padding(10.dp).size(24.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                FilledIconButton(onClick = onPlayLiked, enabled = canPlay, modifier = Modifier.size(56.dp)) {
                    Icon(painterResource(R.drawable.ic_play), "Play liked songs")
                }
            }
        }
    }
}

@Composable
internal fun RecentShelf(recent: List<Track>, canPlay: Boolean, onPlay: (Track) -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Recently played", style = MaterialTheme.typography.titleLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(recent.take(8), key = { it.id }) { song ->
                    Surface(onClick = { onPlay(song) }, enabled = canPlay, shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.width(132.dp).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Artwork(song.artworkUrl, Modifier.fillMaxWidth().aspectRatio(1f))
                            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
}

@Composable
internal fun CollectionCard(item: AccountItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Artwork(item.artworkUrl, Modifier.fillMaxWidth().aspectRatio(1f))
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.subtitle.isNotBlank()) Text(item.subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun AccountSettingsDialog(state: AccountState, actions: AccountActions, onDismiss: () -> Unit) {
    var confirmDisconnect by remember { mutableStateOf(false) }
    if (confirmDisconnect) AlertDialog(onDismissRequest = { confirmDisconnect = false },
        title = { Text("Disconnect YouTube Music?") }, text = { Text("Removes the saved session. Your local playlists and downloads stay.") },
        confirmButton = { TextButton(onClick = { actions.disconnect(); onDismiss() }) { Text("Disconnect") } },
        dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") } })
    else AlertDialog(onDismissRequest = onDismiss, title = { Text("Your account") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val appearance = io.github.oks328.m3music.ui.theme.LocalMusicAppearance.current
            if (android.os.Build.VERSION.SDK_INT >= 31) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Use wallpaper colors", Modifier.weight(1f))
                Switch(checked = appearance.wallpaperColors, onCheckedChange = appearance.changeWallpaperColors)
            }
            if (state.profile != null) {
                Text(state.profile.name, style = MaterialTheme.typography.titleLarge)
                if (state.profile.handle.isNotBlank()) Text(state.profile.handle)
                Text("Connected to YouTube Music", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { confirmDisconnect = true }) { Text("Disconnect account") }
            } else {
                Text("Connect your YouTube Music library to make yourself at home.")
                Button(onClick = { onDismiss(); actions.signIn() }) { Text("Connect Google account") }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
