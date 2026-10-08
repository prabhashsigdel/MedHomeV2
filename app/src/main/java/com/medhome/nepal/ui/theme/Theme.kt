package com.medhome.nepal.ui.theme

import android.provider.Settings
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import com.medhome.nepal.ui.motion.LocalReducedMotion
import com.medhome.nepal.ui.motion.isReducedMotion

/**
 * Light glass only for now, even when the phone is in dark mode (dynamic color is off so the
 * brand accent is never replaced). A dark theme is another [GlassColors] mapped the same way.
 */
@Composable
fun MedHomeTheme(
    colors: GlassColors = lightGlassColors(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        isReducedMotion(
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
        )
    }
    CompositionLocalProvider(
        LocalGlassColors provides colors,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = Typography,
            shapes = MedHomeShapes,
            content = content,
        )
    }
}

/** Every Material color is opaque, so stock components stay readable wherever they appear. */
internal fun GlassColors.toColorScheme(): ColorScheme = lightColorScheme(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = accent.copy(alpha = 0.16f).compositeOver(materialSurfaces.containerLowest),
    onPrimaryContainer = link,
    secondary = link,
    onSecondary = onAccent,
    secondaryContainer = accent.copy(alpha = 0.10f).compositeOver(materialSurfaces.containerLowest),
    onSecondaryContainer = link,
    tertiary = warning,
    onTertiary = onAccent,
    background = backgroundBase,
    onBackground = textPrimary,
    surface = materialSurfaces.surface,
    onSurface = textPrimary,
    surfaceVariant = materialSurfaces.containerHighest,
    onSurfaceVariant = textSecondary,
    surfaceTint = accent,
    surfaceBright = materialSurfaces.containerLowest,
    surfaceDim = materialSurfaces.containerHighest,
    surfaceContainerLowest = materialSurfaces.containerLowest,
    surfaceContainerLow = materialSurfaces.containerLow,
    surfaceContainer = materialSurfaces.container,
    surfaceContainerHigh = materialSurfaces.containerHigh,
    surfaceContainerHighest = materialSurfaces.containerHighest,
    inverseSurface = textPrimary,
    inverseOnSurface = materialSurfaces.containerLowest,
    inversePrimary = accent.copy(alpha = 0.45f).compositeOver(materialSurfaces.containerLowest),
    outline = materialSurfaces.outline,
    outlineVariant = materialSurfaces.outlineVariant,
    error = error,
    onError = onAccent,
    errorContainer = error.copy(alpha = 0.12f).compositeOver(materialSurfaces.containerLowest),
    onErrorContainer = error,
)

/** Shorthand for the glass tokens inside composables. */
object GlassTheme {
    val colors: GlassColors
        @Composable get() = LocalGlassColors.current
}
