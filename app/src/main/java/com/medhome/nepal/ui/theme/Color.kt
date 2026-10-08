package com.medhome.nepal.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The one place to change the brand color. Links and accent text derive from it. */
val DefaultAccent = Color(0xFF2F7D74)

/** How much darker links are than the accent, so they stay readable on glass. */
const val LINK_DARKEN_FRACTION = 0.28f

/**
 * Every color the glass design uses. A dark theme is a second instance of this class;
 * components read colors from here (or the Material scheme mapped from it), never hardcoded.
 */
@Immutable
data class GlassColors(
    val accent: Color,
    val onAccent: Color,
    val link: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val warning: Color,
    val error: Color,
    val backgroundBase: Color,
    val blobAccent: Color,
    val blobPeach: Color,
    val blobBlue: Color,
    /** Cards. */
    val glassFill: Color,
    /** Floating bars: the most transparent surface. */
    val glassFillFloating: Color,
    /** Used instead of blur below Android 12. */
    val glassFallback: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val fieldFill: Color,
    val fieldBorder: Color,
    val shadow: Color,
)

/** Multiplies RGB toward black by [fraction], keeping alpha. */
fun Color.darken(fraction: Float): Color {
    val keep = 1f - fraction.coerceIn(0f, 1f)
    return Color(red = red * keep, green = green * keep, blue = blue * keep, alpha = alpha)
}

fun lightGlassColors(accent: Color = DefaultAccent): GlassColors = GlassColors(
    accent = accent,
    onAccent = Color.White,
    link = accent.darken(LINK_DARKEN_FRACTION),
    textPrimary = Color(0xFF1A1E1C),
    textSecondary = Color(0xFF343B38),
    warning = Color(0xFF9A4508),
    error = Color(0xFFA3231B),
    backgroundBase = Color(0xFFE3E8E6),
    blobAccent = accent.copy(alpha = 0.55f),
    blobPeach = Color(red = 244, green = 176, blue = 142).copy(alpha = 0.55f),
    blobBlue = Color(red = 118, green = 148, blue = 204).copy(alpha = 0.5f),
    glassFill = Color.White.copy(alpha = 0.38f),
    glassFillFloating = Color.White.copy(alpha = 0.30f),
    glassFallback = Color.White.copy(alpha = 0.70f),
    glassBorder = Color.White.copy(alpha = 0.60f),
    glassHighlight = Color.White.copy(alpha = 0.90f),
    fieldFill = Color.White.copy(alpha = 0.50f),
    fieldBorder = Color.White,
    shadow = Color(0xFF1A1E1C).copy(alpha = 0.18f),
)

val LocalGlassColors = staticCompositionLocalOf { lightGlassColors() }
