package io.github.oks328.m3music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.oks328.m3music.R

private fun TextStyle.googleSans(weight: FontWeight = FontWeight.Normal): TextStyle =
    copy(fontFamily = FontFamily(Font(R.font.google_sans_flex, weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))), fontWeight = weight)

private val baseline = Typography()
val Typography = Typography(
    displaySmall = baseline.displaySmall.googleSans(FontWeight.ExtraBold),
    headlineLarge = baseline.headlineLarge.googleSans(FontWeight.ExtraBold),
    headlineMedium = baseline.headlineMedium.googleSans(FontWeight.Bold),
    headlineSmall = baseline.headlineSmall.googleSans(FontWeight.Bold),
    titleLarge = baseline.titleLarge.googleSans(FontWeight.Bold),
    titleMedium = baseline.titleMedium.googleSans(FontWeight.SemiBold),
    titleSmall = baseline.titleSmall.googleSans(FontWeight.SemiBold),
    bodyLarge = baseline.bodyLarge.googleSans(),
    bodyMedium = baseline.bodyMedium.googleSans(),
    bodySmall = baseline.bodySmall.googleSans(),
    labelLarge = baseline.labelLarge.googleSans(FontWeight.Bold),
)
