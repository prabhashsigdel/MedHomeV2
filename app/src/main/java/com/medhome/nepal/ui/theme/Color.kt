package com.medhome.nepal.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** The one place to change the brand color. Links and accent text derive from it. */
val DefaultAccent = Color(0xFF4F5BD5)

/** How much darker links are than the accent in the light theme, so they stay readable on glass. */
const val LINK_DARKEN_FRACTION = 0.28f

/** How much lighter links are than the accent in the dark theme. */
const val DARK_LINK_LIGHTEN_FRACTION = 0.5f

/** How much lighter accent borders and indicators are in the dark theme (3:1 on dark glass). */
const val DARK_EMPHASIS_LIGHTEN_FRACTION = 0.25f

/**
 * Every color the glass design uses. Light and dark are two instances of this class; components
 * read colors from here (or the Material scheme mapped from it), never hardcoded.
 */
@Immutable
data class GlassColors(
    val isDark: Boolean,
    /** Filled surfaces (primary buttons, selected chips) under [onAccent] text. */
    val accent: Color,
    val onAccent: Color,
    /** The accent used as a line or mark on glass: focus borders, cursor, spinners, indicators. */
    val accentEmphasis: Color,
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
    /** Dialogs live in their own window with nothing to blur, so their glass is denser. */
    val dialogFill: Color,
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

/** Tint behind inline status messages: the message's own color at this alpha. */
const val STATUS_TINT_ALPHA = 0.10f

/** Multiplies RGB toward black by [fraction], keeping alpha. */
fun Color.darken(fraction: Float): Color {
    val keep = 1f - fraction.coerceIn(0f, 1f)
    return Color(red = red * keep, green = green * keep, blue = blue * keep, alpha = alpha)
}

/** Mixes toward white by [fraction], keeping alpha. */
fun Color.lighten(fraction: Float): Color =
    lerp(this, Color.White, fraction.coerceIn(0f, 1f)).copy(alpha = alpha)

fun lightGlassColors(accent: Color = DefaultAccent): GlassColors = GlassColors(
    isDark = false,
    accent = accent,
    onAccent = Color.White,
    accentEmphasis = accent,
    link = accent.darken(LINK_DARKEN_FRACTION),
    textPrimary = Color(0xFF1A1E1C),
    textSecondary = Color(0xFF343B38),
    // Dark enough for 4.5:1 inside tinted message boxes on every light backdrop.
    warning = Color(0xFF803905),
    error = Color(0xFF931F17),
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
    dialogFill = Color.White.copy(alpha = 0.88f),
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

/**
 * Dark glass: deep navy-charcoal base, dimmed blobs, dark translucent cards with a faint light
 * border, light text. Links and accent marks are lighter tints of the accent, because the light
 * theme's darker link would vanish on dark glass. Contrast is checked by GlassThemeTest.
 */
fun darkGlassColors(accent: Color = DefaultAccent): GlassColors {
    val glass = Color(0xFF151B2C)
    return GlassColors(
        isDark = true,
        accent = accent,
        onAccent = Color.White,
        accentEmphasis = accent.lighten(DARK_EMPHASIS_LIGHTEN_FRACTION),
        link = accent.lighten(DARK_LINK_LIGHTEN_FRACTION),
        textPrimary = Color(0xFFEEF1F7),
        textSecondary = Color(0xFFB9C1D0),
        warning = Color(0xFFF5B26B),
        error = Color(0xFFFF8A80),
        backgroundBase = Color(0xFF0E1320),
        blobLavender = Color(red = 156, green = 140, blue = 230).copy(alpha = 0.28f),
        blobSky = Color(red = 120, green = 170, blue = 235).copy(alpha = 0.22f),
        blobPink = Color(red = 240, green = 170, blue = 200).copy(alpha = 0.16f),
        glassFill = glass.copy(alpha = 0.55f),
        glassFillFloating = glass.copy(alpha = 0.45f),
        glassFallback = glass.copy(alpha = 0.85f),
        glassBorder = Color.White.copy(alpha = 0.12f),
        glassHighlight = Color.White.copy(alpha = 0.22f),
        fieldFill = Color.White.copy(alpha = 0.06f),
        fieldBorder = Color.White.copy(alpha = 0.18f),
        shadow = Color.Black.copy(alpha = 0.45f),
        dialogFill = Color(0xFF182032).copy(alpha = 0.96f),
        materialSurfaces = MaterialSurfaces(
            surface = Color(0xFF121826),
            containerLowest = Color(0xFF0B0F1A),
            containerLow = Color(0xFF141A28),
            container = Color(0xFF182032),
            containerHigh = Color(0xFF1D2638),
            containerHighest = Color(0xFF232D42),
            outline = Color(0xFF8C95A8),
            outlineVariant = Color(0xFF3A4458),
        ),
    )
}

val LocalGlassColors = staticCompositionLocalOf { lightGlassColors() }
