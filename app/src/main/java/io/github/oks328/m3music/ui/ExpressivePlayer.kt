@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.oks328.m3music.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import io.github.oks328.m3music.R
import io.github.oks328.m3music.playback.PlaybackState
import kotlin.math.sin

@Composable
internal fun ExpressivePlayer(state: PlaybackState, favorite: Boolean, onToggle: () -> Unit, onSeek: (Long) -> Unit,
    onRetry: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onQueueItem: (Int) -> Unit,
    onFavorite: () -> Unit, onDownload: () -> Unit, onClose: () -> Unit = {}) {
    val track = state.track ?: return
    var queueVisible by remember { mutableStateOf(false) }
    BackHandler(queueVisible) { queueVisible = false }
    var scrub by remember(track.id) { mutableStateOf<Float?>(null) }
    val morph = remember { Morph(MaterialShapes.Cookie4Sided, MaterialShapes.Circle) }
    val progress by animateFloatAsState(if (state.playing) 0f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "Playback shape")
    val buttonShape = remember(progress) { GenericShape { size, _ ->
        val path = morph.toPath(progress.coerceIn(0f, 1f))
        path.transform(Matrix().apply { scale(size.width, size.height) }); addPath(path)
    } }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_collapse), "Close now playing") }
            Text(if (queueVisible) "Up next" else "Now playing", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (queueVisible) TextButton(onClick = { queueVisible = false }) { Text("Back to player") }
        }
        if (queueVisible) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.queue.isEmpty()) item { Text("The queue is empty.") }
                itemsIndexed(state.queue, key = { index, _ -> index }) { index, song ->
                    SongRow(song, index == state.queueIndex, state.connected, onClick = { onQueueItem(index) })
                }
            }
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val artworkSize = minOf(maxWidth, (maxHeight - 16.dp).coerceAtLeast(0.dp), 420.dp)
                if (artworkSize >= 64.dp) {
                    Artwork(track.artworkUrl, Modifier.size(artworkSize), state.offline, large = true)
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(track.title, style = MaterialTheme.typography.headlineMedium, maxLines = 1,
                    modifier = Modifier.fillMaxWidth().basicMarquee(spacing = MarqueeSpacing(32.dp)))
                Text(track.artist, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.offline) Text("Playing download", style = MaterialTheme.typography.labelMedium)
                if (state.resolving || state.buffering) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LoadingIndicator(Modifier.size(32.dp))
                        Text("Loading your music…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRetry) { Text("Retry playback") }
                }
            }
        }
        if (!queueVisible) {
            val maximum = state.durationMs.coerceAtLeast(1).toFloat()
            val position = (scrub ?: state.positionMs.toFloat()).coerceIn(0f, maximum)
            val sliderState = remember(track.id, maximum) { SliderState(position, trackRange = 0f..maximum) }
            SideEffect { sliderState.value = position }
            val activeColor = MaterialTheme.colorScheme.primary
            val trackColor = MaterialTheme.colorScheme.secondaryContainer
            Column {
                Slider(state = sliderState, onValueChange = { scrub = it }, onValueChangeFinished = { scrub?.let { onSeek(it.toLong()) }; scrub = null },
                    enabled = state.durationMs > 0 && state.connected,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Song position" },
                    track = {
                        val playing = state.playing
                        Spacer(Modifier.fillMaxWidth().height(24.dp).drawWithCache {
                            val amplitude = if (playing) 3.dp.toPx() else 0f
                            val wavelength = 32.dp.toPx()
                            val center = size.height / 2
                            val wave = Path().apply {
                                moveTo(0f, center)
                                for (x in 1..size.width.toInt()) {
                                    lineTo(x.toFloat(), center + amplitude * sin(x / wavelength * 2 * Math.PI).toFloat())
                                }
                            }
                            val stroke = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
                            val rtl = layoutDirection == LayoutDirection.Rtl
                            onDrawBehind {
                                val filled = size.width * (sliderState.value / maximum).coerceIn(0f, 1f)
                                drawPath(wave, trackColor, style = stroke)
                                clipRect(left = if (rtl) size.width - filled else 0f,
                                    right = if (rtl) size.width else filled) {
                                    drawPath(wave, activeColor, style = stroke)
                                }
                            }
                        })
                    })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(time(position.toLong()), style = MaterialTheme.typography.labelMedium)
                    Text(time(state.durationMs), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (!queueVisible) Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = onPrevious, enabled = state.canPrevious, modifier = Modifier.size(56.dp)) {
                    Icon(painterResource(R.drawable.ic_previous), "Previous", Modifier.size(32.dp))
                }
                Surface(onClick = onToggle, enabled = state.connected && !state.resolving && state.error == null,
                    shape = buttonShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(88.dp).semantics { contentDescription = if (state.playing) "Pause" else "Play" }) {
                    Box(contentAlignment = Alignment.Center) {
                        Crossfade(targetState = state.playing,
                            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                            label = "Playback icon") { playing ->
                            Icon(painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                                null, Modifier.size(40.dp))
                        }
                    }
                }
                IconButton(onClick = onNext, enabled = state.canNext, modifier = Modifier.size(56.dp)) {
                    Icon(painterResource(R.drawable.ic_next), "Next", Modifier.size(32.dp))
                }
            }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledTonalButton(onClick = { queueVisible = !queueVisible }, Modifier.weight(1f)) { Text(if (queueVisible) "Player" else "Queue") }
            FilledTonalButton(onClick = onDownload, Modifier.weight(1.3f)) { Text("Download") }
            FilledTonalIconToggleButton(checked = favorite, onCheckedChange = { onFavorite() }) { Icon(painterResource(R.drawable.ic_heart), if (favorite) "Remove local favorite" else "Save to local favorites",
                tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer) }
        }
    }
}

private fun time(ms: Long): String = "%d:%02d".format(ms.coerceAtLeast(0) / 60000, ms.coerceAtLeast(0) / 1000 % 60)
