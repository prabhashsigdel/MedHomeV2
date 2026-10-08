package com.medhome.nepal.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.medhome.nepal.R

private val ManropeWeights = listOf(
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
    FontWeight.ExtraBold,
)

/**
 * Bundled Manrope variable font (SIL OFL, license in assets/licenses). Weight variation needs
 * Android 8+; on Android 7 the in-between weights fall back to regular or synthetic bold.
 */
@OptIn(ExperimentalTextApi::class)
val Manrope = FontFamily(
    ManropeWeights.map { weight ->
        Font(
            resId = R.font.manrope,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

private fun manrope(size: Int, weight: FontWeight, lineHeight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.em,
)

/** Text on glass is Medium or heavier, so nothing in this scale is lighter than Medium. */
val Typography = Typography(
    displaySmall = manrope(34, FontWeight.ExtraBold, 40, tracking = -0.02),
    headlineLarge = manrope(32, FontWeight.ExtraBold, 38, tracking = -0.02),
    headlineMedium = manrope(28, FontWeight.ExtraBold, 34, tracking = -0.02),
    headlineSmall = manrope(24, FontWeight.ExtraBold, 30, tracking = -0.015),
    titleLarge = manrope(20, FontWeight.Bold, 26),
    titleMedium = manrope(17, FontWeight.Bold, 24),
    titleSmall = manrope(15, FontWeight.Bold, 20),
    bodyLarge = manrope(16, FontWeight.Medium, 24),
    bodyMedium = manrope(15, FontWeight.Medium, 22),
    bodySmall = manrope(13, FontWeight.Medium, 18),
    labelLarge = manrope(15, FontWeight.Bold, 20),
    labelMedium = manrope(13, FontWeight.Bold, 18),
    labelSmall = manrope(12, FontWeight.Bold, 16),
)
