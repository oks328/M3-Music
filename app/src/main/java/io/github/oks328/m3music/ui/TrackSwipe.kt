@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.oks328.m3music.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

internal class TrackSwipeMotion(private val scope: CoroutineScope) {
    private var offset by mutableFloatStateOf(0f)
    private var trackAlpha by mutableFloatStateOf(1f)
    private var gestureDistance = 0f
    private var startOffset = 0f
    private var direction = 0
    private var animation: Job? = null

    fun start() {
        animation?.cancel()
        direction = 0
        trackAlpha = 1f
        startOffset = offset
        gestureDistance = 0f
    }

    fun drag(delta: Float, travel: Float, canPrevious: Boolean, canNext: Boolean) {
        gestureDistance += delta
        val available = if (gestureDistance < 0) canNext else canPrevious
        val movement = if (available) gestureDistance else gestureDistance * 0.2f
        offset = (startOffset + movement).coerceIn(-travel * 0.7f, travel * 0.7f)
    }

    fun release(
        velocity: Float,
        travel: Float,
        threshold: Float,
        flingThreshold: Float,
        spec: AnimationSpec<Float>,
        canPrevious: Boolean,
        canNext: Boolean,
        previous: () -> Unit,
        next: () -> Unit,
    ) {
        val towardNext = gestureDistance < 0 && canNext
        val towardPrevious = gestureDistance > 0 && canPrevious
        val fling = abs(gestureDistance) >= threshold / 4 && abs(velocity) >= flingThreshold &&
            gestureDistance * velocity > 0
        direction = if (abs(gestureDistance) >= threshold || fling) {
            if (towardNext) -1 else if (towardPrevious) 1 else 0
        } else 0
        transition(direction, travel, spec, previous, next)
    }

    fun transition(
        targetDirection: Int,
        travel: Float,
        spec: AnimationSpec<Float>,
        previous: () -> Unit,
        next: () -> Unit,
    ) {
        animation?.cancel()
        trackAlpha = 1f
        direction = targetDirection
        if (direction == 0) {
            animation = scope.launch { settle(0f, spec) }
            return
        }
        val swipeDirection = direction
        animation = scope.launch {
            settle(swipeDirection * travel, spec)
            // If playback cannot advance, bring the current content back.
            delay(350)
            direction = 0
            settle(0f, spec)
        }
        if (swipeDirection < 0) next() else previous()
    }

    fun trackChanged(travel: Float, spec: AnimationSpec<Float>, fade: AnimationSpec<Float>) {
        animation?.cancel()
        if (direction == 0) {
            offset = 0f
            trackAlpha = 0.45f
            animation = scope.launch {
                animate(trackAlpha, 1f, animationSpec = fade) { value, _ -> trackAlpha = value }
            }
            return
        }
        trackAlpha = 1f
        offset = -direction * travel
        direction = 0
        animation = scope.launch { settle(0f, spec) }
    }

    private suspend fun settle(target: Float, spec: AnimationSpec<Float>) {
        animate(offset, target, animationSpec = spec) { value, _ -> offset = value }
    }

    fun layer(travel: Float, motionEnabled: Boolean): Modifier = Modifier.graphicsLayer {
        if (motionEnabled) {
            translationX = offset
            alpha = trackAlpha.coerceIn(0f, 1f) * (1f - abs(offset) / travel).coerceIn(0f, 1f)
        }
    }
}

internal data class TrackSwipeControls(
    val gesture: Modifier,
    val layer: Modifier,
    val previous: () -> Unit,
    val next: () -> Unit,
)

@Composable
internal fun rememberTrackSwipe(
    trackKey: String,
    canPrevious: Boolean,
    canNext: Boolean,
    previous: () -> Unit,
    next: () -> Unit,
): TrackSwipeControls {
    val scope = rememberCoroutineScope()
    val motion = remember { TrackSwipeMotion(scope) }
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val density = LocalDensity.current
    val travel = with(density) { 96.dp.toPx() }
    val threshold = with(density) { 44.dp.toPx() }
    val flingThreshold = with(density) { 900.dp.toPx() }
    val motionEnabled = scope.coroutineContext[MotionDurationScale]?.scaleFactor != 0f
    val drag = rememberDraggableState { delta -> motion.drag(delta, travel, canPrevious, canNext) }
    val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    var previousTrack by remember { mutableStateOf(trackKey) }
    LaunchedEffect(trackKey) {
        if (previousTrack != trackKey) {
            previousTrack = trackKey
            motion.trackChanged(travel, spec, fade)
        }
    }
    val gesture = Modifier.draggable(drag, Orientation.Horizontal,
        onDragStarted = { motion.start() },
        onDragStopped = { velocity ->
            motion.release(velocity, travel, threshold, flingThreshold, spec, canPrevious, canNext, previous, next)
        })
    return TrackSwipeControls(gesture, motion.layer(travel, motionEnabled),
        previous = { if (canPrevious) motion.transition(1, travel, spec, previous, next) },
        next = { if (canNext) motion.transition(-1, travel, spec, previous, next) })
}
