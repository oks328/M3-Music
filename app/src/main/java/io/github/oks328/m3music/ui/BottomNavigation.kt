@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.oks328.m3music.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.oks328.m3music.R
import io.github.oks328.m3music.ui.theme.LocalMusicTypeStyles

@Composable
internal fun MusicBottomNavigation(destination: String, onNavigate: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 2.dp) {
        BoxWithConstraints {
            val fontScale = LocalDensity.current.fontScale
            val horizontal = maxWidth >= 354.dp && fontScale <= 1.15f
            ShortNavigationBar(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface,
                windowInsets = WindowInsets(0, 0, 0, 0)) {
                listOf("Home" to R.drawable.ic_home, "Search" to R.drawable.ic_search,
                    "Library" to R.drawable.ic_library).forEach { (label, icon) ->
                    val selected = destination == label
                    ShortNavigationBarItem(
                        selected = selected,
                        onClick = { onNavigate(label) },
                        iconPosition = if (horizontal) NavigationItemIconPosition.Start else NavigationItemIconPosition.Top,
                        icon = { Icon(painterResource(icon), null, Modifier.size(22.dp)) },
                        label = {
                            Text(label,
                                style = if (selected) LocalMusicTypeStyles.current.selectedNavigationLabel else MaterialTheme.typography.labelMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        colors = ShortNavigationBarItemDefaults.colors(
                            selectedIndicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColorTopIconPosition = MaterialTheme.colorScheme.onSurface,
                            selectedTextColorStartIconPosition = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
            }
        }
    }
}
