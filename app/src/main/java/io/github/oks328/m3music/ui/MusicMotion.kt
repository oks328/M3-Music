@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.oks328.m3music.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun Modifier.musicContentFade(page: Any): Modifier {
    val alpha = remember { Animatable(1f) }
    var lastPage by remember { mutableStateOf(page) }
    val spec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    LaunchedEffect(page) {
        if (lastPage != page) {
            lastPage = page
            alpha.snapTo(0.86f)
            alpha.animateTo(1f, spec)
        }
    }
    return graphicsLayer { this.alpha = alpha.value.coerceIn(0f, 1f) }
}


@Composable
internal fun Modifier.collectionMotion(page: String?, depth: Int = if (page == null) 0 else 1): Modifier {
    val alpha = remember { Animatable(1f) }
    val shift = remember { Animatable(0f) }
    var previousPage by remember { mutableStateOf(page) }
    var previousDepth by remember { mutableStateOf(depth) }
    val travel = with(LocalDensity.current) { 24.dp.toPx() }
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    LaunchedEffect(page, depth) {
        if (previousPage != page) {
            val direction = if (depth >= previousDepth) 1 else -1
            previousPage = page
            previousDepth = depth
            shift.snapTo(direction * travel)
            alpha.snapTo(0.72f)
            launch { shift.animateTo(0f, spatial) }
            alpha.animateTo(1f, effects)
        }
    }
    return graphicsLayer {
        translationX = shift.value
        this.alpha = alpha.value.coerceIn(0f, 1f)
    }
}
