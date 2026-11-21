package io.github.oks328.m3music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.oks328.m3music.R
import io.github.oks328.m3music.playback.PlaybackState

@Composable
internal fun CompactPlayer(state: PlaybackState, onToggle: () -> Unit, onExpand: () -> Unit) {
    val track = state.track ?: return
    Surface(onClick = onExpand, shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().semantics { contentDescription = "Open now playing" }) {
        Column {
            Row(Modifier.padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Artwork(track.artworkUrl, Modifier.size(48.dp), state.offline)
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(when { state.error != null -> "Playback needs attention"; state.resolving || state.buffering -> "Loading…"; else -> track.artist },
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledIconButton(onClick = onToggle, enabled = state.connected && !state.resolving && state.error == null,
                    modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(if (state.playing) R.drawable.ic_pause else R.drawable.ic_play), if (state.playing) "Pause" else "Play")
                }
            }
            if (state.durationMs > 0) LinearProgressIndicator(
                progress = { (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp))
        }
    }
}
