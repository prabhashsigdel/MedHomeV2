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
import kotlin.math.roundToInt
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

/**
 * Bundled Noto Sans Devanagari variable font (SIL OFL). Manrope has no Devanagari letters; this
 * font covers Devanagari and Latin, so Nepali screens (names, emails included) use one family.
 */
@OptIn(ExperimentalTextApi::class)
val NotoSansDevanagari = FontFamily(
    ManropeWeights.map { weight ->
        Font(
            resId = R.font.noto_sans_devanagari,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

/**
 * How a script is set. Devanagari gets no letter spacing (tracking breaks the joined headline
 * stroke) and taller lines for vowel signs above and below the letters.
 */
private class ScriptStyle(val family: FontFamily, val trackingScale: Double, val lineHeightScale: Float)

private val LatinStyle = ScriptStyle(Manrope, trackingScale = 1.0, lineHeightScale = 1f)
private val DevanagariStyle = ScriptStyle(NotoSansDevanagari, trackingScale = 0.0, lineHeightScale = 1.15f)

private fun ScriptStyle.text(size: Int, weight: FontWeight, lineHeight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (lineHeight * lineHeightScale).roundToInt().sp,
    letterSpacing = (tracking * trackingScale).em,
)

/** Text on glass is Medium or heavier, so nothing in this scale is lighter than Medium. */
private fun ScriptStyle.typography() = Typography(
    displaySmall = text(34, FontWeight.ExtraBold, 40, tracking = -0.02),
    headlineLarge = text(32, FontWeight.ExtraBold, 38, tracking = -0.02),
    headlineMedium = text(28, FontWeight.ExtraBold, 34, tracking = -0.02),
    headlineSmall = text(24, FontWeight.ExtraBold, 30, tracking = -0.015),
    titleLarge = text(20, FontWeight.Bold, 26),
    titleMedium = text(17, FontWeight.Bold, 24),
    titleSmall = text(15, FontWeight.Bold, 20),
    bodyLarge = text(16, FontWeight.Medium, 24),
    bodyMedium = text(15, FontWeight.Medium, 22),
    bodySmall = text(13, FontWeight.Medium, 18),
    labelLarge = text(15, FontWeight.Bold, 20),
    labelMedium = text(13, FontWeight.Bold, 18),
    labelSmall = text(12, FontWeight.Bold, 16),
)

private val LatinTypography = LatinStyle.typography()
private val DevanagariTypography = DevanagariStyle.typography()

/** Typography for the app's current language (e.g. "ne" for Nepali). */
fun typographyFor(languageTag: String?): Typography =
    if (languageTag == "ne") DevanagariTypography else LatinTypography
