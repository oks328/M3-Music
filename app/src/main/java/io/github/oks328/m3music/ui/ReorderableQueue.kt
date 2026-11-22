@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.oks328.m3music.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.oks328.m3music.R
import io.github.oks328.m3music.music.Track
import io.github.oks328.m3music.playback.PlaybackState
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
) {
    val list = rememberLazyListState()
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

    LazyColumn(modifier, state = list, contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (rows.isEmpty()) item { Text("The queue is empty.") }
        itemsIndexed(preview ?: rows, key = { _, row -> row.key }) { index, row ->
            val song = row.song
            var menu by remember { mutableStateOf(false) }
            val canMove = state.connected && rows.size > 1
            val dragging = row.key == draggedKey
            Row(Modifier.fillMaxWidth().zIndex(if (dragging) 1f else 0f)
                .animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = if (dragging) null else placement)
                .graphicsLayer {
                    if (dragging) list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == row.key }?.let {
                        translationY = dragCenter - it.center
                    }
                }
                .shadow(if (dragging) 4.dp else 0.dp, RoundedCornerShape(16.dp)),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SongRow(song, row.key == playingKey, state.connected && draggedKey == null,
                        onClick = { onPlay(rows.indexOf(row)) },
                        containerColor = if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else null)
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
                        Icon(painterResource(R.drawable.ic_reorder), "Reorder ${song.title}")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Move up") }, enabled = index > 0, onClick = {
                            menu = false
                            move(index, index - 1)
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
}
