@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.oks328.m3music.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.oks328.m3music.R
import io.github.oks328.m3music.playback.PlaybackState
import io.github.oks328.m3music.ui.theme.LocalMusicTypeStyles

@Composable
internal fun CompactPlayer(
    state: PlaybackState,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    inTransform: Boolean = false,
) {
    val track = state.track ?: return
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val actionCorner by animateDpAsState(if (state.playing) 16.dp else 24.dp, motion.fastSpatialSpec(),
        label = "Mini-player control shape")
    Surface(onClick = onExpand, shape = RoundedCornerShape(CompactPlayerCorner),
        color = if (inTransform) androidx.compose.ui.graphics.Color.Transparent else colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "Open now playing" }) {
        BoxWithConstraints {
            val showNext = maxWidth >= 350.dp && LocalDensity.current.fontScale <= 1.2f && state.canNext
            Column {
                Row(Modifier.padding(start = 8.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Artwork(track.artworkUrl, Modifier.size(44.dp), state.offline, cornerRadius = 14.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(track.title, style = LocalMusicTypeStyles.current.compactTrackTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(when {
                            state.error != null -> "Playback needs attention"
                            state.resolving || state.buffering -> "Loading…"
                            else -> track.artist
                        }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (showNext) IconButton(onClick = onNext) {
                        Icon(painterResource(R.drawable.ic_next), "Next track", Modifier.size(22.dp))
                    }
                    FilledIconButton(onClick = onToggle, enabled = state.connected && !state.resolving && state.error == null,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = if (state.playing) "Pause" else "Play" },
                        shape = RoundedCornerShape(actionCorner)) {
                        Crossfade(state.playing, animationSpec = motion.fastEffectsSpec(), label = "Mini-player play icon") { playing ->
                            Icon(painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play), null)
                        }
                    }
                }
                if (state.durationMs > 0) LinearProgressIndicator(
                    progress = { (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 5.dp).height(2.dp),
                    color = colors.primary, trackColor = colors.onSecondaryContainer.copy(alpha = 0.16f),
                    gapSize = 0.dp, drawStopIndicator = {},
                )
            }
        }
    }
}
