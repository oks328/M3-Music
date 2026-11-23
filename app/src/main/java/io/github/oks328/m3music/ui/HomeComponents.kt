@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.oks328.m3music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
internal fun AccountSettingsDialog(
    state: AccountState,
    actions: AccountActions,
    wifiOnly: Boolean,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    if (showSettings) {
        val appearance = io.github.oks328.m3music.ui.theme.LocalMusicAppearance.current
        AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("Settings") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsToggle("Download on Wi-Fi only", wifiOnly, onWifiOnlyChanged)
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    SettingsToggle("Use wallpaper colors", appearance.wallpaperColors, appearance.changeWallpaperColors)
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Back") } })
        return
    }
    var confirmDisconnect by remember { mutableStateOf(false) }
    if (confirmDisconnect) AlertDialog(onDismissRequest = { confirmDisconnect = false },
        title = { Text("Disconnect YouTube Music?") }, text = { Text("Removes the saved session. Your local playlists and downloads stay.") },
        confirmButton = { TextButton(onClick = { actions.disconnect(); onDismiss() }) { Text("Disconnect") } },
        dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") } })
    else AlertDialog(onDismissRequest = onDismiss, title = { Text("Your account") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.profile != null) {
                Text(state.profile.name, style = MaterialTheme.typography.titleLarge)
                if (state.profile.handle.isNotBlank()) Text(state.profile.handle)
                Text("Connected to YouTube Music", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { confirmDisconnect = true }) { Text("Disconnect account") }
            } else {
                Text("Connect your YouTube Music library to make yourself at home.")
                Button(onClick = { onDismiss(); actions.signIn() }) { Text("Connect Google account") }
            }
            OutlinedButton(onClick = { showSettings = true }) { Text("Settings") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
        .heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
