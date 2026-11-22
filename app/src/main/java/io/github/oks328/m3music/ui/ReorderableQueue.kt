package io.github.oks328.m3music.ui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.oks328.m3music.R
import io.github.oks328.m3music.playback.PlaybackState
import kotlin.math.abs

@Composable
internal fun ReorderableQueue(
    state: PlaybackState,
    onPlay: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    val move by rememberUpdatedState(onMove)
    var dragging by remember(state.queue) { mutableIntStateOf(-1) }
    var target by remember(state.queue) { mutableIntStateOf(-1) }
    var offset by remember(state.queue) { mutableFloatStateOf(0f) }
    val reset = { dragging = -1; target = -1; offset = 0f }
    LazyColumn(modifier, state = list, contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.queue.isEmpty()) item { Text("The queue is empty.") }
        itemsIndexed(state.queue) { index, song ->
            var menu by remember(state.queue, index) { mutableStateOf(false) }
            val canMove = state.connected && state.queue.size > 1
            val highlighted = index == state.queueIndex || index == target && target != dragging
            Row(Modifier.fillMaxWidth().zIndex(if (index == dragging) 1f else 0f)
                .graphicsLayer { if (index == dragging) translationY = offset },
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SongRow(song, highlighted, state.connected, onClick = { onPlay(index) })
                }
                Box {
                    IconButton(onClick = { menu = true }, enabled = canMove,
                        modifier = if (!canMove) Modifier else Modifier.pointerInput(index, state.queue) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragging = index
                                    target = index
                                    offset = 0f
                                },
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    val visible = list.layoutInfo.visibleItemsInfo
                                    val start = visible.firstOrNull { it.index == index }
                                    if (start != null) {
                                        offset += amount
                                        val center = start.offset + start.size / 2f + offset
                                        target = visible.minByOrNull { row ->
                                            abs(row.offset + row.size / 2f - center)
                                        }?.index ?: index
                                    }
                                },
                                onDragCancel = reset,
                                onDragEnd = {
                                    if (target >= 0 && target != index) move(index, target)
                                    reset()
                                },
                            )
                        }) {
                        Icon(painterResource(R.drawable.ic_reorder), "Reorder ${song.title}")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Move up") }, enabled = index > 0, onClick = {
                            menu = false
                            move(index, index - 1)
                        })
                        DropdownMenuItem(text = { Text("Move down") }, enabled = index < state.queue.lastIndex, onClick = {
                            menu = false
                            move(index, index + 1)
                        })
                    }
                }
            }
        }
    }
}
