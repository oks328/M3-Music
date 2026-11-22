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
