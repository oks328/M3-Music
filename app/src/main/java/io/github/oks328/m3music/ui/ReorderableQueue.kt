@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.oks328.m3music.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.oks328.m3music.R
import io.github.oks328.m3music.music.Track
import io.github.oks328.m3music.playback.PlaybackState
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class QueueRow(val key: String, val song: Track)
private val LazyListItemInfo.center get() = offset + size / 2f

@Composable
internal fun ReorderableQueue(
    state: PlaybackState,
    onPlay: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (Int) -> Unit = {},
    onPullDown: () -> Unit = {},
) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (state.queueIndex - 1).coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    val placement = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val landing = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val rows = remember(state.queue, state.queueEntryIds) {
        val duplicates = mutableMapOf<String, Int>()
        state.queue.mapIndexed { index, song ->
            val occurrence = duplicates.getOrDefault(song.id, 0)
            duplicates[song.id] = occurrence + 1
            QueueRow(state.queueEntryIds.getOrNull(index) ?: "track:${song.id}:$occurrence", song)
        }
    }
    val currentRows by rememberUpdatedState(rows)
    val move by rememberUpdatedState(onMove)
    val remove by rememberUpdatedState(onRemove)
    var preview by remember(rows) { mutableStateOf<List<QueueRow>?>(null) }
    var draggedKey by remember(rows) { mutableStateOf<String?>(null) }
    var dragCenter by remember(rows) { mutableFloatStateOf(0f) }
    var settling by remember(rows) { mutableStateOf<Job?>(null) }
    val playingKey = rows.getOrNull(state.queueIndex)?.key

    fun showOrder(order: List<QueueRow>?) {
        if (preview == order) return
        // Keep the viewport at the same index even when its first row changes identity.
        list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
        preview = order
    }

    fun finish(key: String, cancelled: Boolean) {
        if (draggedKey != key) return
        if (cancelled) showOrder(null)
        val from = rows.indexOfFirst { it.key == key }
        val to = (preview ?: rows).indexOfFirst { it.key == key }
        settling = scope.launch {
            withFrameNanos { }
            list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.let { slot ->
                animate(dragCenter, slot.center, animationSpec = landing) { value, _ -> dragCenter = value }
            }
            if (currentRows == rows && draggedKey == key) {
                draggedKey = null
                if (from >= 0 && to >= 0 && from != to) move(from, to) else preview = null
            }
        }
    }

    val pullThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    val motionEnabled = scope.coroutineContext[MotionDurationScale]?.scaleFactor != 0f
    val returnToPlayer by rememberUpdatedState(onPullDown)
    var pullDistance by remember { mutableFloatStateOf(0f) }
    val pullToPlayer = remember(list, pullThreshold, landing) {
        object : NestedScrollConnection {
            private var settlingPull: Job? = null

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || pullDistance <= 0f || available.y >= 0f) return Offset.Zero
                settlingPull?.cancel()
                val consumed = available.y.coerceAtLeast(-pullDistance)
                pullDistance += consumed
                return Offset(0f, consumed)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || list.canScrollBackward || available.y <= 0f) return Offset.Zero
                settlingPull?.cancel()
                pullDistance = (pullDistance + available.y).coerceAtMost(pullThreshold)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullDistance >= pullThreshold) returnToPlayer()
                else if (pullDistance > 0f) {
                    settlingPull = scope.launch {
                        animate(pullDistance, 0f, animationSpec = landing) { value, _ -> pullDistance = value }
                    }
                }
                return Velocity.Zero
            }
        }
    }
    LazyColumn(modifier.nestedScroll(pullToPlayer).graphicsLayer {
        if (motionEnabled) translationY = pullDistance * 0.65f
    }, state = list, contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (rows.isEmpty()) item { Text("The queue is empty.") }
        itemsIndexed(preview ?: rows, key = { _, row -> row.key }) { index, row ->
            val song = row.song
            var menu by remember { mutableStateOf(false) }
            val canMove = state.connected && rows.size > 1
            val dragging = row.key == draggedKey
            val canRemove = state.connected && row.key != playingKey && draggedKey == null
            val removeRow by rememberUpdatedState {
                if (canRemove) remove(rows.indexOf(row))
            }
            val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                if (value != SwipeToDismissBoxValue.Settled) removeRow()
                false
            })
            val shape = RoundedCornerShape(16.dp)
            val containerColor by animateColorAsState(
                when {
                    dragging -> MaterialTheme.colorScheme.surfaceContainerHighest
                    row.key == playingKey -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                },
                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                label = "Queue row tone",
            )
            SwipeToDismissBox(
                state = dismiss,
                modifier = Modifier.zIndex(if (dragging) 1f else 0f)
                    .animateItem(fadeInSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        placementSpec = if (dragging) null else placement)
                    .graphicsLayer {
                        if (dragging) list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == row.key }?.let {
                            translationY = dragCenter - it.center
                        }
                    },
                enableDismissFromStartToEnd = canRemove,
                enableDismissFromEndToStart = canRemove,
                backgroundContent = {
                    if (dismiss.dismissDirection != SwipeToDismissBoxValue.Settled) {
                        Surface(Modifier.fillMaxSize(), shape = shape,
                            color = MaterialTheme.colorScheme.errorContainer) {
                            Box(Modifier.padding(horizontal = 16.dp),
                                contentAlignment = if (dismiss.dismissDirection == SwipeToDismissBoxValue.StartToEnd)
                                    Alignment.CenterStart else Alignment.CenterEnd) {
                                Text("Remove", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                },
            ) {
                Row(Modifier.fillMaxWidth().background(containerColor, shape),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        SongRow(song, selected = false, enabled = state.connected && draggedKey == null,
                            onClick = { onPlay(rows.indexOf(row)) }, containerColor = Color.Transparent)
                    }
                    Box {
                        IconButton(onClick = { if (draggedKey == null) menu = true }, enabled = canMove,
                            modifier = if (!canMove) Modifier else Modifier.pointerInput(row.key, rows) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        if (draggedKey != null && draggedKey != row.key) return@detectVerticalDragGestures
                                        settling?.cancel()
                                        val slot = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == row.key }
                                        if (slot != null) {
                                            if (draggedKey != row.key) dragCenter = slot.center
                                            draggedKey = row.key
                                        }
                                    },
                                    onVerticalDrag = { change, amount ->
                                        if (draggedKey == row.key) {
                                            change.consume()
                                            dragCenter += amount
                                            // Slot coordinates stay usable when input arrives ahead of remeasurement.
                                            val to = list.layoutInfo.visibleItemsInfo
                                                .filter { it.index < rows.size }
                                                .minByOrNull { abs(it.center - dragCenter) }?.index
                                            val order = preview ?: rows
                                            val from = order.indexOfFirst { it.key == row.key }
                                            if (from >= 0 && to != null && to in order.indices && from != to) {
                                                showOrder(order.toMutableList().apply { add(to, removeAt(from)) })
                                            }
                                        }
                                    },
                                    onDragCancel = { finish(row.key, cancelled = true) },
                                    onDragEnd = { finish(row.key, cancelled = false) },
                                )
                            }) {
                            if (dismiss.dismissDirection == SwipeToDismissBoxValue.Settled) {
                                Icon(painterResource(R.drawable.ic_reorder), "Reorder ${song.title}")
                            }
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Move up") }, enabled = index > 0, onClick = {
                                menu = false
                                move(index, index - 1)
                            })
                            if (canRemove) DropdownMenuItem(text = { Text("Remove from queue") }, onClick = {
                                menu = false
                                removeRow()
                            })
                            DropdownMenuItem(text = { Text("Move down") }, enabled = index < rows.lastIndex, onClick = {
                                menu = false
                                move(index, index + 1)
                            })
                        }
                    }
                }
            }
        }
        if (rows.isNotEmpty() && state.autoplay && !state.downloadQueue) {
            item(key = "autoplay-label") {
                Column(Modifier.padding(top = 12.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Autoplay next", style = MaterialTheme.typography.titleMedium)
                    when {
                        state.autoplayLoading -> LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                        state.autoplayTracks.isEmpty() -> Text("No recommendations available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(state.autoplayTracks, key = { "autoplay:${it.id}" }) { track ->
                ListItem(
                    headlineContent = { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Artwork(track.artworkUrl, Modifier.size(56.dp)) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.animateItem(fadeInSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
                )
            }
        }
    }
}
