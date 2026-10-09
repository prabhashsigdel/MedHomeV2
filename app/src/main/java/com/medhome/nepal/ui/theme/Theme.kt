package com.medhome.nepal.ui.theme

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.medhome.nepal.ui.motion.LocalReducedMotion
import com.medhome.nepal.ui.motion.MotionTokens
import com.medhome.nepal.ui.motion.isReducedMotion

/**
 * The glass theme in light or dark. Dynamic color stays off so the brand accent is never
 * replaced. Switching between light and dark crossfades every color over ~300ms (instant when
 * animations are turned off), so the app never jumps or restarts.
 */
@Composable
fun MedHomeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val languageTag = LocalConfiguration.current.locales.takeIf { !it.isEmpty }?.get(0)?.language
    val reducedMotion = remember(context) {
        isReducedMotion(
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
        )
    }
    val colors = animateGlassColors(
        target = glassColorsFor(darkTheme),
        reducedMotion = reducedMotion,
    )
    CompositionLocalProvider(
        LocalGlassColors provides colors,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = typographyFor(languageTag),
            shapes = MedHomeShapes,
            content = content,
        )
    }
}

/** Every token animates on its own; Material's scheme is derived from the animated values. */
@Composable
private fun animateGlassColors(target: GlassColors, reducedMotion: Boolean): GlassColors {
    val spec = if (reducedMotion) snap() else tween<Color>(THEME_CROSSFADE_MS, easing = MotionTokens.EaseOut)

    @Composable
    fun Color.animated(label: String): Color = animateColorAsState(this, spec, label = label).value

    val surfaces = target.materialSurfaces
    return target.copy(
        accent = target.accent.animated("accent"),
        onAccent = target.onAccent.animated("onAccent"),
        accentEmphasis = target.accentEmphasis.animated("accentEmphasis"),
        link = target.link.animated("link"),
        textPrimary = target.textPrimary.animated("textPrimary"),
        textSecondary = target.textSecondary.animated("textSecondary"),
        warning = target.warning.animated("warning"),
        error = target.error.animated("error"),
        onError = target.onError.animated("onError"),
        background = animateMesh(target.background, reducedMotion),
        glassFill = target.glassFill.animated("glassFill"),
        controlFill = target.controlFill.animated("controlFill"),
        glassBorderTop = target.glassBorderTop.animated("glassBorderTop"),
        glassBorderBottom = target.glassBorderBottom.animated("glassBorderBottom"),
        selectedPill = target.selectedPill.animated("selectedPill"),
        selectedTabContent = target.selectedTabContent.animated("selectedTabContent"),
        divider = target.divider.animated("divider"),
        floatingScrim = target.floatingScrim.animated("floatingScrim"),
        glassFallback = target.glassFallback.animated("glassFallback"),
        statusUnderlay = target.statusUnderlay.animated("statusUnderlay"),
        materialSurfaces = surfaces.copy(
            surface = surfaces.surface.animated("surface"),
            containerLowest = surfaces.containerLowest.animated("containerLowest"),
            containerLow = surfaces.containerLow.animated("containerLow"),
            container = surfaces.container.animated("container"),
            containerHigh = surfaces.containerHigh.animated("containerHigh"),
            containerHighest = surfaces.containerHighest.animated("containerHighest"),
            outline = surfaces.outline.animated("outline"),
            outlineVariant = surfaces.outlineVariant.animated("outlineVariant"),
        ),
    )
}

/**
 * The glows move as well as recolor, since the light and dark palettes place them differently.
 * Only during a theme switch: the background itself never animates.
 */
@Composable
private fun animateMesh(target: MeshPalette, reducedMotion: Boolean): MeshPalette {
    val colorSpec = if (reducedMotion) snap() else tween<Color>(THEME_CROSSFADE_MS, easing = MotionTokens.EaseOut)
    val offsetSpec = if (reducedMotion) snap() else tween<Offset>(THEME_CROSSFADE_MS, easing = MotionTokens.EaseOut)
    val floatSpec = if (reducedMotion) snap() else tween<Float>(THEME_CROSSFADE_MS, easing = MotionTokens.EaseOut)
    return MeshPalette(
        base = animateColorAsState(target.base, colorSpec, label = "meshBase").value,
        glows = target.glows.mapIndexed { index, glow ->
            Glow(
                color = animateColorAsState(glow.color, colorSpec, label = "glowColor$index").value,
                center = animateOffsetAsState(glow.center, offsetSpec, label = "glowCenter$index").value,
                radius = animateFloatAsState(glow.radius, floatSpec, label = "glowRadius$index").value,
            )
        },
    )
}

private const val THEME_CROSSFADE_MS = 300

/** Every Material color is opaque, so stock components stay readable wherever they appear. */
internal fun GlassColors.toColorScheme(): ColorScheme {
    val solid = materialSurfaces.containerLowest
    val scheme = if (isDark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.22f).compositeOver(solid),
        onPrimaryContainer = link,
        secondary = link,
        onSecondary = if (isDark) materialSurfaces.surface else onAccent,
        secondaryContainer = accent.copy(alpha = 0.14f).compositeOver(solid),
        onSecondaryContainer = link,
        tertiary = warning,
        onTertiary = if (isDark) materialSurfaces.surface else onAccent,
        background = background.base,
        onBackground = textPrimary,
        surface = materialSurfaces.surface,
        onSurface = textPrimary,
        surfaceVariant = materialSurfaces.containerHighest,
        onSurfaceVariant = textSecondary,
        surfaceTint = accent,
        surfaceBright = if (isDark) materialSurfaces.containerHighest else materialSurfaces.containerLowest,
        surfaceDim = if (isDark) materialSurfaces.containerLowest else materialSurfaces.containerHighest,
        surfaceContainerLowest = materialSurfaces.containerLowest,
        surfaceContainerLow = materialSurfaces.containerLow,
        surfaceContainer = materialSurfaces.container,
        surfaceContainerHigh = materialSurfaces.containerHigh,
        surfaceContainerHighest = materialSurfaces.containerHighest,
        inverseSurface = textPrimary,
        inverseOnSurface = materialSurfaces.surface,
        inversePrimary = accentEmphasis,
        outline = materialSurfaces.outline,
        outlineVariant = materialSurfaces.outlineVariant,
        error = error,
        onError = onError,
        errorContainer = error.copy(alpha = 0.16f).compositeOver(solid),
        onErrorContainer = error,
    )
}

/** Shorthand for the glass tokens inside composables. */
object GlassTheme {
    val colors: GlassColors
        @Composable get() = LocalGlassColors.current
}
