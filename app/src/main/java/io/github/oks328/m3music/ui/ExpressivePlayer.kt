@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.oks328.m3music.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import io.github.oks328.m3music.playback.RepeatMode
import io.github.oks328.m3music.downloads.DownloadEntry
import io.github.oks328.m3music.downloads.DownloadStatus
import io.github.oks328.m3music.downloads.active
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.graphics.shapes.Morph
import io.github.oks328.m3music.R
import io.github.oks328.m3music.ui.theme.LocalMusicTypeStyles
import io.github.oks328.m3music.playback.PlaybackState

@Composable
internal fun ExpressivePlayer(state: PlaybackState, favorite: Boolean, onToggle: () -> Unit, onSeek: (Long) -> Unit,
    onRetry: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onQueueItem: (Int) -> Unit,
    onFavorite: () -> Unit, onDownload: () -> Unit, downloadEntry: DownloadEntry? = null, onShuffle: () -> Unit, onRepeat: () -> Unit, onAutoplay: () -> Unit = {}, onClose: () -> Unit = {},
    swipeModifier: @Composable (Boolean) -> Modifier = { Modifier },
    onArtist: ((String) -> Unit)? = null,
    onAddToPlaylist: () -> Unit = {},
    onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
    onRemoveQueueItem: (Int) -> Unit = {},
    onSaveQueue: (() -> Unit)? = null,
) {
    val track = state.track ?: return
    val (swipe, swipeLayer, skipPrevious, skipNext) = rememberTrackSwipe(
        state.queueEntryIds.getOrNull(state.queueIndex) ?: track.id,
        state.canPrevious, state.canNext, onPrevious, onNext)
    val type = LocalMusicTypeStyles.current
    val motionScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    var queueVisible by remember { mutableStateOf(false) }
    BackHandler(queueVisible) { queueVisible = false }
    var scrub by remember(track.id) { mutableStateOf<Float?>(null) }
    val morph = remember { Morph(MaterialShapes.Cookie4Sided, MaterialShapes.Circle) }
    val progress by animateFloatAsState(if (state.playing) 0f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "Playback shape")
    val buttonShape = remember(progress) { GenericShape { size, _ ->
        val path = morph.toPath(progress.coerceIn(0f, 1f))
        path.transform(Matrix().apply { scale(size.width, size.height) }); addPath(path)
    } }
    Column(Modifier.fillMaxSize().then(swipeModifier(!queueVisible))
        .padding(horizontal = 24.dp).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (queueVisible) BackIconButton(onClick = { queueVisible = false }, description = "Back to player")
            else IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_collapse), "Close now playing") }
            Text(if (queueVisible) { if (state.shuffle) "Queue" else "Up next" } else "Now playing", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (queueVisible && onSaveQueue != null && state.queue.isNotEmpty()) {
                IconButton(onClick = onSaveQueue) {
                    Icon(painterResource(R.drawable.ic_add_playlist), "Save queue as playlist")
                }
            }
            if (queueVisible) IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_collapse), "Close now playing") }
        }
        if (queueVisible && state.queue.isNotEmpty()) {
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                color = if (state.autoplay) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Autoplay", style = MaterialTheme.typography.titleMedium)
                        Text(when {
                            state.downloadQueue -> "Unavailable for Downloads queues"
                            state.repeat != RepeatMode.OFF -> "Starts when Repeat is off"
                            else -> "Keep playing similar songs"
                        }, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.autoplay && !state.downloadQueue, onCheckedChange = { onAutoplay() },
                        enabled = !state.downloadQueue,
                        modifier = Modifier.semantics { contentDescription = "Autoplay" })
                }
            }
        }
        val panelTravel = with(LocalDensity.current) { 72.dp.roundToPx() }
        val spatial = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
        val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(
            targetState = queueVisible,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val direction = if (targetState) 1 else -1
                (slideInVertically(spatial) { direction * panelTravel } + fadeIn(effects)) togetherWith
                    (slideOutVertically(spatial) { -direction * panelTravel } + fadeOut(effects))
            },
            label = "Now Playing and Up next",
        ) { showQueue ->
            if (showQueue) {
                ReorderableQueue(state, onQueueItem, onMoveQueueItem, Modifier.fillMaxSize(), onRemoveQueueItem,
                    onPullDown = { queueVisible = false })
            } else BoxWithConstraints(Modifier.fillMaxSize()) {
                val compact = maxHeight < 470.dp || maxWidth < 300.dp || LocalDensity.current.fontScale >= 1.3f
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false).then(swipe),
                        contentAlignment = Alignment.Center) {
                        val artworkSize = minOf(maxWidth, maxHeight, 420.dp)
                        if (artworkSize >= 64.dp) {
                            Artwork(track.artworkUrl, Modifier.size(artworkSize).then(swipeLayer), state.offline, large = true,
                                cornerRadius = 28.dp)
                        }
                    }
                    Column(Modifier.fillMaxWidth().then(swipe).then(swipeLayer)
                        .padding(top = 12.dp, bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(track.title, style = if (compact) type.condensedPlayerTitle else type.expandedTrackTitle,
                            maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                        if (track.artist.isNotBlank()) {
                            val opensArtist = onArtist != null
                            Text(track.artist, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                                modifier = if (opensArtist) Modifier.clickable(
                                    onClickLabel = "Open artist page", onClick = { onArtist?.invoke(track.artist) }
                                ).heightIn(min = 32.dp).wrapContentHeight(Alignment.CenterVertically) else Modifier,
                                overflow = TextOverflow.Ellipsis,
                                color = if (opensArtist) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (state.offline) Text("Playing download", style = MaterialTheme.typography.labelMedium)
                        if (state.resolving || state.buffering) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LoadingIndicator(Modifier.size(24.dp))
                                Text("Loading your music", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        state.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onRetry) { Text("Retry playback") }
                        }
                    }
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
                                val moving = state.playing && !state.buffering && scrub == null && motionScale?.scaleFactor != 0f
                                val amplitude = animateFloatAsState(
                                    if (moving) 0.65f else 0f,
                                    MaterialTheme.motionScheme.fastEffectsSpec(),
                                    label = "Seek wave amplitude",
                                )
                                LinearWavyProgressIndicator(
                                    progress = { (sliderState.value / maximum).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(16.dp).clearAndSetSemantics {},
                                    color = activeColor,
                                    trackColor = trackColor,
                                    amplitude = { amplitude.value },
                                    wavelength = 32.dp,
                                    waveSpeed = if (moving) 32.dp else 0.dp,
                                )
                            })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(time(position.toLong()), style = type.playbackTime)
                            Text(time(state.durationMs), style = type.playbackTime)
                        }
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        val mainButtonSize = (maxWidth - 192.dp).coerceIn(48.dp, 80.dp)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                            FilledTonalIconToggleButton(
                                checked = state.shuffle, onCheckedChange = { onShuffle() },
                                enabled = state.connected && state.queueSize > 1,
                            ) { Icon(painterResource(R.drawable.ic_shuffle), "Shuffle") }
                            IconButton(onClick = skipPrevious, enabled = state.canPrevious, modifier = Modifier.size(48.dp)) {
                                Icon(painterResource(R.drawable.ic_previous), "Previous", Modifier.size(32.dp))
                            }
                            Surface(onClick = onToggle, enabled = state.connected && !state.resolving && state.error == null,
                                shape = buttonShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(mainButtonSize).semantics { contentDescription = if (state.playing) "Pause" else "Play" }) {
                                Box(contentAlignment = Alignment.Center) {
                                    Crossfade(targetState = state.playing,
                                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                                        label = "Playback icon") { playing ->
                                        Icon(painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                                            null, Modifier.size(40.dp))
                                    }
                                }
                            }
                            IconButton(onClick = skipNext, enabled = state.canNext, modifier = Modifier.size(48.dp)) {
                                Icon(painterResource(R.drawable.ic_next), "Next", Modifier.size(32.dp))
                            }
                            val repeatLabel = when (state.repeat) {
                                RepeatMode.OFF -> "Repeat off"
                                RepeatMode.ALL -> "Repeat queue"
                                RepeatMode.ONE -> "Repeat song"
                            }
                            FilledTonalIconButton(
                                onClick = onRepeat, enabled = state.connected && state.queueSize > 0,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (state.repeat == RepeatMode.OFF) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = if (state.repeat == RepeatMode.OFF) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            ) {
                                Icon(painterResource(if (state.repeat == RepeatMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat), repeatLabel)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = { queueVisible = true }, Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Queue", maxLines = 1) }
                        val downloaded = downloadEntry?.status == DownloadStatus.COMPLETED
                        val downloading = downloadEntry?.active == true
                        val downloadLabel = when {
                            downloaded -> "Downloaded"
                            downloading -> "Downloading"
                            else -> "Download"
                        }
                        if (compact || downloading) {
                            IconButton(onClick = onDownload, enabled = !downloaded && !downloading,
                                modifier = Modifier.semantics { contentDescription = downloadLabel }) {
                                if (downloading) DownloadProgressRing(downloadEntry?.percent?.div(100f))
                                else Icon(painterResource(R.drawable.ic_download), null)
                            }
                        } else {
                            TextButton(onClick = onDownload, Modifier.weight(1.35f), enabled = !downloaded,
                                contentPadding = PaddingValues(horizontal = 6.dp)) {
                                Text(downloadLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton(onClick = onAddToPlaylist) {
                            Icon(painterResource(R.drawable.ic_add_playlist), "Add to playlist")
                        }
                        IconToggleButton(checked = favorite, onCheckedChange = { onFavorite() }) {
                            Icon(painterResource(R.drawable.ic_heart),
                                if (favorite) "Remove local favorite" else "Save to local favorites",
                                tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

    }
}

private fun time(ms: Long): String = "%d:%02d".format(ms.coerceAtLeast(0) / 60000, ms.coerceAtLeast(0) / 1000 % 60)
