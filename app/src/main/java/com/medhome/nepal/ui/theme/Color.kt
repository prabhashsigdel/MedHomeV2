package com.medhome.nepal.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp

/** The one place to change the brand color. Links and accent text derive from it. */
val DefaultAccent = Color(0xFF4F5BD5)

/** How much darker links are than the accent in the light theme, so they stay readable on glass. */
const val LINK_DARKEN_FRACTION = 0.28f

/**
 * How much lighter links are than the accent in the dark theme. Pale, because links sit on glass
 * over the glows and must keep 4.5:1 there.
 */
const val DARK_LINK_LIGHTEN_FRACTION = 0.7f

/** How much lighter accent borders and indicators are in the dark theme (3:1 on dark glass). */
const val DARK_EMPHASIS_LIGHTEN_FRACTION = 0.5f

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
    /** Text on a filled [error] surface (the Danger button). */
    val onError: Color,
    /** The mesh gradient behind everything, and inside sheets and dialogs. */
    val background: MeshPalette,
    /** Cards, sheets, dialogs and the floating bar: a neutral translucent white. */
    val glassFill: Color,
    /**
     * Fields, secondary buttons and chips. Fainter than [glassFill], because they usually sit on
     * a card and the two stack: a second full layer would wash out text in the dark theme.
     */
    val controlFill: Color,
    /** Every glass border is 1px, fading from [glassBorderTop] down to [glassBorderBottom]. */
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    /** The selected tab: a lighter glass pill, not an accent color. */
    val selectedPill: Color,
    /** Icon and label of the selected tab: the link indigo in light, white in dark. */
    val selectedTabContent: Color,
    /** Lines between rows of a card. */
    val divider: Color,
    /** Under the floating bar's fill, so blurred content behind it can't lower its contrast. */
    val floatingScrim: Color,
    /** The floating bar's fill below Android 12, where there is no blur. */
    val glassFallback: Color,
    /** Under the tint of inline status messages: keeps their pale text readable in dark. */
    val statusUnderlay: Color,
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

fun lightGlassColors(accent: Color = DefaultAccent, background: MeshPalette = SoftDaylight): GlassColors = GlassColors(
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
    onError = Color.White,
    background = background,
    glassFill = Color.White.copy(alpha = 0.45f),
    controlFill = Color.White.copy(alpha = 0.40f),
    glassBorderTop = Color.White.copy(alpha = 0.90f),
    glassBorderBottom = Color.White.copy(alpha = 0.30f),
    selectedPill = Color.White.copy(alpha = 0.60f),
    selectedTabContent = accent.darken(LINK_DARKEN_FRACTION),
    divider = Color(0xFF1A1E1C).copy(alpha = 0.08f),
    floatingScrim = background.base.copy(alpha = FLOATING_SCRIM_ALPHA),
    glassFallback = Color.White.copy(alpha = 0.45f).compositeOver(background.base).copy(alpha = FALLBACK_ALPHA),
    statusUnderlay = Color.Transparent,
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
 * Dark glass: a dark mesh gradient, white 12% glass with a bright-to-faint border, white text.
 * Links, status colors and accent marks are pale, because they sit on glass over the glows,
 * which are brighter than the base. Contrast is checked by GlassThemeTest for every palette.
 */
fun darkGlassColors(accent: Color = DefaultAccent, background: MeshPalette = WarmDusk): GlassColors = GlassColors(
    isDark = true,
    accent = accent,
    onAccent = Color.White,
    accentEmphasis = accent.lighten(DARK_EMPHASIS_LIGHTEN_FRACTION),
    link = accent.lighten(DARK_LINK_LIGHTEN_FRACTION),
    textPrimary = Color.White,
    textSecondary = Color.White.copy(alpha = 0.75f),
    warning = Color(0xFFF9CFA0),
    error = Color(0xFFFFC9C4),
    onError = Color(0xFF3A0B0E),
    background = background,
    glassFill = Color.White.copy(alpha = 0.12f),
    controlFill = Color.White.copy(alpha = 0.06f),
    glassBorderTop = Color.White.copy(alpha = 0.40f),
    glassBorderBottom = Color.White.copy(alpha = 0.06f),
    selectedPill = Color.White.copy(alpha = 0.10f),
    selectedTabContent = Color.White,
    divider = Color.White.copy(alpha = 0.10f),
    floatingScrim = background.base.copy(alpha = FLOATING_SCRIM_ALPHA),
    glassFallback = Color.White.copy(alpha = 0.12f).compositeOver(background.base).copy(alpha = FALLBACK_ALPHA),
    statusUnderlay = Color.Black.copy(alpha = 0.20f),
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

private const val FLOATING_SCRIM_ALPHA = 0.5f
private const val FALLBACK_ALPHA = 0.94f

/** The colors for a theme: Warm dusk in dark, Soft daylight in light. */
fun glassColorsFor(dark: Boolean): GlassColors = if (dark) darkGlassColors() else lightGlassColors()

val LocalGlassColors = staticCompositionLocalOf { lightGlassColors() }
