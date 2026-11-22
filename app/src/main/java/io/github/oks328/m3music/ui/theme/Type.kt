package io.github.oks328.m3music.ui.theme

import android.content.res.AssetManager
import android.graphics.Typeface
import android.graphics.fonts.FontVariationAxis
import android.util.LruCache
import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density

internal class MusicTypeStyles private constructor(private val assets: AssetManager, private val density: Density) {
    companion object {
        private val cache = LruCache<Pair<AssetManager, Density>, MusicTypeStyles>(3)

        fun get(assets: AssetManager, density: Density): MusicTypeStyles =
            cache[assets to density] ?: MusicTypeStyles(assets, density).also {
                cache.put(assets to density, it)
            }
    }

    private val baseline = Typography()

    // Build each face with its axes before passing it to Compose.
    private fun TextStyle.googleSans(
        weight: Int = 430,
        width: Float = 100f,
        slant: Float = 0f,
        roundness: Float = 24f,
    ): TextStyle {
        val opticalSize = with(density) { fontSize.toDp().value }.coerceIn(1f, 144f)
        val typeface = Typeface.Builder(assets, "fonts/google_sans_flex.ttf")
            .setWeight(weight)
            .setFontVariationSettings(arrayOf(
                FontVariationAxis("wght", weight.toFloat()),
                FontVariationAxis("wdth", width),
                FontVariationAxis("slnt", slant),
                FontVariationAxis("ROND", roundness),
                FontVariationAxis("opsz", opticalSize),
            ))
            .build()
        return copy(fontFamily = typeface?.let { FontFamily(it) } ?: FontFamily.Default,
            fontWeight = FontWeight(weight))
    }

    private fun TextStyle.heading1() = googleSans(weight = 500, width = 25f, roundness = 100f)
    private fun TextStyle.heading2() = googleSans(weight = 400, width = 62.5f, roundness = 100f)
    private fun TextStyle.heading3() = googleSans(weight = 400, width = 75f, roundness = 100f)

    val typography = Typography(
        displayLarge = baseline.displayLarge.heading1(),
        displayMedium = baseline.displayMedium.heading1(),
        displaySmall = baseline.displaySmall.heading1(),
        headlineLarge = baseline.headlineLarge.heading1(),
        headlineMedium = baseline.headlineMedium.heading1(),
        headlineSmall = baseline.headlineSmall.heading2(),
        titleLarge = baseline.titleLarge.heading3(),
        titleMedium = baseline.titleMedium.googleSans(630, 99f, 0f, 43f),
        titleSmall = baseline.titleSmall.googleSans(625, 99f, 0f, 43f),
        bodyLarge = baseline.bodyLarge.googleSans(),
        bodyMedium = baseline.bodyMedium.googleSans(),
        bodySmall = baseline.bodySmall.googleSans(),
        labelLarge = baseline.labelLarge.googleSans(650, 99f, 0f, 47f),
        labelMedium = baseline.labelMedium.googleSans(570, 99f, 0f, 34f),
        labelSmall = baseline.labelSmall.googleSans(570, 99f, 0f, 34f),
    )

    val brandTitle = baseline.titleLarge.heading1()
    val selectedNavigationLabel = baseline.labelMedium.googleSans(650, 98f, 0f, 48f)
    val compactTrackTitle = baseline.titleMedium.googleSans(650, 98f, 0f, 43f)
    val expandedTrackTitle = baseline.headlineMedium.heading2()
    val playbackTime = typography.labelMedium.copy(fontFeatureSettings = "tnum")
    val condensedPlayerTitle = baseline.headlineSmall.heading3()
}

internal val LocalMusicTypeStyles = staticCompositionLocalOf<MusicTypeStyles> {
    error("Music typography is available inside M3MusicTheme.")
}
