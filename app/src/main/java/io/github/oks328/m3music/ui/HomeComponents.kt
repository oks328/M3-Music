@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.oks328.m3music.ui

import androidx.compose.foundation.layout.*
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
