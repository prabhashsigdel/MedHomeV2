package com.medhome.nepal.ui.theme

import android.provider.Settings
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
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

private fun GlassColors.toColorScheme(): ColorScheme = lightColorScheme(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = accent.copy(alpha = 0.16f),
    onPrimaryContainer = link,
    secondary = link,
    onSecondary = onAccent,
    tertiary = warning,
    background = backgroundBase,
    onBackground = textPrimary,
    surface = Color.Transparent,
    onSurface = textPrimary,
    surfaceVariant = fieldFill,
    onSurfaceVariant = textSecondary,
    surfaceContainer = glassFallback,
    surfaceContainerHigh = glassFallback,
    surfaceContainerHighest = glassFallback,
    outline = glassBorder,
    outlineVariant = glassBorder,
    error = error,
    onError = onAccent,
    errorContainer = error.copy(alpha = 0.12f),
    onErrorContainer = error,
)

/** Shorthand for the glass tokens inside composables. */
object GlassTheme {
    val colors: GlassColors
        @Composable get() = LocalGlassColors.current
}
