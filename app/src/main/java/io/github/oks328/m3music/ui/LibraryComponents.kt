package io.github.oks328.m3music.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.oks328.m3music.R

@Composable
internal fun <T> LibraryFilters(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) },
                label = { Text(label(option), style = MaterialTheme.typography.labelMedium) })
        }
    }
}

@Composable
internal fun NewLibraryPlaylistButton(onClick: () -> Unit, enabled: Boolean = true) {
    FilledTonalButton(onClick = onClick, enabled = enabled) {
        Icon(painterResource(R.drawable.ic_add_playlist), null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("New playlist")
    }
}

@Composable
internal fun LibraryCollectionRow(
    title: String,
    subtitle: String,
    artwork: String?,
    offline: Boolean = false,
    artist: Boolean = false,
    fallbackIcon: Int? = null,
    first: Boolean = true,
    last: Boolean = true,
    onClick: () -> Unit,
) {
    val top = if (first) 20.dp else 6.dp
    val bottom = if (last) 20.dp else 6.dp
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (artwork == null && fallbackIcon != null) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        Icon(painterResource(fallbackIcon), null)
                    }
                }
            } else {
                Artwork(artwork, Modifier.size(52.dp), offline = offline,
                    cornerRadius = if (artist) 26.dp else 12.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun LibraryCollectionTitle(
    title: String,
    artwork: String?,
    subtitle: String = "",
    metadata: String? = null,
    offline: Boolean = false,
    showArtwork: Boolean = true,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showArtwork) {
            Artwork(artwork, Modifier.widthIn(max = 208.dp).fillMaxWidth().aspectRatio(1f)
                .align(Alignment.CenterHorizontally), offline = offline, large = true, cornerRadius = 24.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (metadata != null) Text(metadata, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
