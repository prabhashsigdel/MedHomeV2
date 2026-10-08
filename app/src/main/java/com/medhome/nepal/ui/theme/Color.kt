package com.medhome.nepal.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The one place to change the brand color. Links and accent text derive from it. */
val DefaultAccent = Color(0xFF4F5BD5)

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
    val blobLavender: Color,
    val blobSky: Color,
    val blobPink: Color,
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
    /**
     * Solid colors for stock Material 3 components (date pickers, menus, sheets, snackbars),
     * which must never be translucent. Glass is applied only through our own components.
     */
    val materialSurfaces: MaterialSurfaces,
)

@Immutable
data class MaterialSurfaces(
    val surface: Color,
    val containerLowest: Color,
    val containerLow: Color,
    val container: Color,
    val containerHigh: Color,
    val containerHighest: Color,
    /** Borders of stock outlined components; dark enough for 3:1 on these surfaces. */
    val outline: Color,
    val outlineVariant: Color,
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
    blobLavender = Color(red = 156, green = 140, blue = 230).copy(alpha = 0.50f),
    blobSky = Color(red = 120, green = 170, blue = 235).copy(alpha = 0.45f),
    blobPink = Color(red = 240, green = 170, blue = 200).copy(alpha = 0.40f),
    glassFill = Color.White.copy(alpha = 0.38f),
    glassFillFloating = Color.White.copy(alpha = 0.30f),
    glassFallback = Color.White.copy(alpha = 0.70f),
    glassBorder = Color.White.copy(alpha = 0.60f),
    glassHighlight = Color.White.copy(alpha = 0.90f),
    fieldFill = Color.White.copy(alpha = 0.50f),
    fieldBorder = Color.White,
    shadow = Color(0xFF1A1E1C).copy(alpha = 0.18f),
    materialSurfaces = MaterialSurfaces(
        surface = Color(0xFFFAFBFB),
        containerLowest = Color.White,
        containerLow = Color(0xFFF5F7F6),
        container = Color(0xFFEFF2F1),
        containerHigh = Color(0xFFE9EDEB),
        containerHighest = Color(0xFFE3E8E6),
        outline = Color(0xFF6E7673),
        outlineVariant = Color(0xFFC3C9C6),
    ),
)

val LocalGlassColors = staticCompositionLocalOf { lightGlassColors() }
