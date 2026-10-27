package com.movies.premium.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Default = Typography()

val ChqTypography = Typography(
    displayLarge = Default.displayLarge.copy(fontWeight = FontWeight.Bold),
    displayMedium = Default.displayMedium.copy(fontWeight = FontWeight.Bold),
    displaySmall = Default.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineLarge = Default.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = Default.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = Default.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Default.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Default.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Default.titleSmall.copy(fontWeight = FontWeight.Medium),
    bodyLarge = Default.bodyLarge,
    bodyMedium = Default.bodyMedium,
    bodySmall = Default.bodySmall,
    labelLarge = Default.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Default.labelMedium,
    labelSmall = Default.labelSmall,
)

/** Typography scaled for Android TV, where viewing distance is greater than on phones. */
fun tvTypography(scale: Float): Typography {
    if (scale == 1f) return ChqTypography
    fun TextStyle.scaled(): TextStyle = copy(
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale,
    )
    return ChqTypography.copy(
        displayLarge = ChqTypography.displayLarge.scaled(),
        displayMedium = ChqTypography.displayMedium.scaled(),
        displaySmall = ChqTypography.displaySmall.scaled(),
        headlineLarge = ChqTypography.headlineLarge.scaled(),
        headlineMedium = ChqTypography.headlineMedium.scaled(),
        headlineSmall = ChqTypography.headlineSmall.scaled(),
        titleLarge = ChqTypography.titleLarge.scaled(),
        titleMedium = ChqTypography.titleMedium.scaled(),
        titleSmall = ChqTypography.titleSmall.scaled(),
        bodyLarge = ChqTypography.bodyLarge.scaled(),
        bodyMedium = ChqTypography.bodyMedium.scaled(),
        bodySmall = ChqTypography.bodySmall.scaled(),
        labelLarge = ChqTypography.labelLarge.scaled(),
        labelMedium = ChqTypography.labelMedium.scaled(),
        labelSmall = ChqTypography.labelSmall.scaled(),
    )
}
