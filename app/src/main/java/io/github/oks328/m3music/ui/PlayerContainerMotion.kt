@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.oks328.m3music.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal val CompactPlayerCorner = 23.dp

internal class PlayerContainerMotion(initiallyExpanded: Boolean) {
    private var position by mutableFloatStateOf(if (initiallyExpanded) 1f else 0f)
    val progress: Float get() = position.coerceIn(0f, 1f)
    var anchor by mutableStateOf<Rect?>(null)
    private var settleJob: Job? = null
    private var gestureDistance = 0f
    private val travel get() = (anchor?.top ?: 1f).coerceAtLeast(1f)

    fun beginDrag() {
        settleJob?.cancel()
        position = progress
        gestureDistance = 0f
    }

    fun dragBy(pixels: Float) {
        gestureDistance += pixels
        position = (position - pixels / travel).coerceIn(0f, 1f)
    }

    fun endsExpanded(expanded: Boolean, velocity: Float, threshold: Float, flingThreshold: Float): Boolean {
        val towardOtherState = if (expanded) gestureDistance else -gestureDistance
        val speed = if (expanded) velocity else -velocity
        val commit = towardOtherState >= threshold ||
            (towardOtherState >= threshold / 4f && speed >= flingThreshold)
        return if (commit) !expanded else expanded
    }

    fun settle(scope: CoroutineScope, expanded: Boolean, spec: AnimationSpec<Float>, velocity: Float = 0f) {
        settleJob?.cancel()
        val target = if (expanded) 1f else 0f
        settleJob = scope.launch {
            val releaseVelocity = -velocity / travel
            val towardTarget = if ((target - progress) * releaseVelocity > 0f) releaseVelocity else 0f
            animate(progress, target, initialVelocity = towardTarget, animationSpec = spec) { value, _ ->
                position = value
            }
        }
    }

    fun bounds(width: Float, height: Float): Rect {
        val p = progress
        val mini = anchor ?: Rect(0f, height, width, height)
        return Rect(
            left = mini.left * (1f - p),
            top = mini.top * (1f - p),
            right = mini.right + (width - mini.right) * p,
            bottom = mini.bottom + (height - mini.bottom) * p,
        )
    }
}

@Composable
internal fun Modifier.playerSwipe(
    motion: PlayerContainerMotion,
    expanded: Boolean,
    enabled: Boolean = true,
    settle: (Boolean, Float) -> Unit,
): Modifier {
    val state = rememberDraggableState(motion::dragBy)
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val flingThreshold = with(LocalDensity.current) { 900.dp.toPx() }
    return draggable(state, Orientation.Vertical, enabled = enabled,
        onDragStarted = { motion.beginDrag() },
        onDragStopped = { velocity ->
            settle(motion.endsExpanded(expanded, velocity, threshold, flingThreshold), velocity)
        })
}

internal fun Modifier.playerContainer(motion: PlayerContainerMotion, compactColor: Color, expandedColor: Color): Modifier =
    drawWithContent {
        val p = motion.progress
        val bounds = motion.bounds(size.width, size.height)
        if (bounds.width > 0f && bounds.height > 0f) {
            val radius = CompactPlayerCorner.toPx() * (1f - p)
            val outline = Path().apply { addRoundRect(RoundRect(bounds, CornerRadius(radius))) }
            clipPath(outline) {
                drawRect(lerp(compactColor, expandedColor, p), topLeft = Offset(bounds.left, bounds.top), size = bounds.size)
                this@drawWithContent.drawContent()
            }
        }
    }
