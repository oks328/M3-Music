package io.github.oks328.m3music.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF465D09), onPrimary = Color.White, primaryContainer = Color(0xFFD5ED79), onPrimaryContainer = Color(0xFF253200),
    secondary = Color(0xFF645477), onSecondary = Color.White, secondaryContainer = Color(0xFFE9DDF3), onSecondaryContainer = Color(0xFF291D37),
    tertiary = Color(0xFF81503A), onTertiary = Color.White, tertiaryContainer = Color(0xFFF7CEB7), onTertiaryContainer = Color(0xFF321407),
    background = Color(0xFFFFF9F0), onBackground = Color(0xFF241D28), surface = Color(0xFFFFF9F0), onSurface = Color(0xFF241D28),
    surfaceContainerLowest = Color(0xFFFFFCF7), surfaceContainerLow = Color(0xFFF8F0E9), surfaceContainer = Color(0xFFF0E8E1),
    surfaceContainerHigh = Color(0xFFEAE0DF), surfaceContainerHighest = Color(0xFFE5DADD), onSurfaceVariant = Color(0xFF635967),
    outline = Color(0xFF7C717D), outlineVariant = Color(0xFFD1C6D0),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFD5ED79), onPrimary = Color(0xFF253200), primaryContainer = Color(0xFF3A4820), onPrimaryContainer = Color(0xFFE8F4B7),
    secondary = Color(0xFFD2BDE5), onSecondary = Color(0xFF382646), secondaryContainer = Color(0xFF44334F), onSecondaryContainer = Color(0xFFF0DFFF),
    tertiary = Color(0xFFF4BA98), onTertiary = Color(0xFF492715), tertiaryContainer = Color(0xFF603C2A), onTertiaryContainer = Color(0xFFFFDBC7),
    background = Color(0xFF1B171F), onBackground = Color(0xFFF2E8F0), surface = Color(0xFF1B171F), onSurface = Color(0xFFF2E8F0),
    surfaceContainerLowest = Color(0xFF151118), surfaceContainerLow = Color(0xFF241F29), surfaceContainer = Color(0xFF2B2530),
    surfaceContainerHigh = Color(0xFF362F3A), surfaceContainerHighest = Color(0xFF413946), onSurfaceVariant = Color(0xFFD0C2D2),
    outline = Color(0xFF9B8D9E), outlineVariant = Color(0xFF504655),
)

data class MusicAppearance(val wallpaperColors: Boolean = false, val changeWallpaperColors: (Boolean) -> Unit = {})
val LocalMusicAppearance = staticCompositionLocalOf { MusicAppearance() }

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun M3MusicTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE) }
    var useWallpaper by remember { mutableStateOf(preferences.getBoolean("wallpaperColors", true)) }
    val dynamic = dynamicColor ?: useWallpaper
    val colors = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) Dark else Light
    CompositionLocalProvider(LocalMusicAppearance provides MusicAppearance(dynamic) {
        useWallpaper = it; preferences.edit().putBoolean("wallpaperColors", it).apply()
    }) {
        MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.expressive(), typography = Typography, content = content)
    }
}
