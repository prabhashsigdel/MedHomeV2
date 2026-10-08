package com.medhome.nepal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.max

/** The blur source for glass surfaces on the current screen; null means draw the fallback fill. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Light grey base with large soft color shapes. The shapes are radial gradients that fade out
 * over their edge, which looks like a heavy blur on every Android version without the cost of
 * a real blur (nothing here re-renders while scrolling). It is the blur source for [GlassCard].
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = GlassTheme.colors
    val hazeState = rememberHazeState()
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) {
            drawRect(colors.backgroundBase)
            val unit = max(size.width, size.height)
            val softEdge = GlassDimens.BackgroundBlobBlur.toPx()
            drawBlob(colors.blobAccent, Offset(size.width * 0.05f, size.height * 0.10f), unit * 0.42f, softEdge)
            drawBlob(colors.blobPeach, Offset(size.width * 1.00f, size.height * 0.42f), unit * 0.36f, softEdge)
            drawBlob(colors.blobBlue, Offset(size.width * 0.15f, size.height * 0.92f), unit * 0.40f, softEdge)
        }
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            content()
        }
    }
}

/** A filled circle whose outer [softEdge] fades to transparent, plus a gentle inner falloff. */
private fun DrawScope.drawBlob(color: Color, center: Offset, radius: Float, softEdge: Float) {
    val outer = radius + softEdge
    val solidStop = (radius / outer) * 0.55f
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to color,
                solidStop to color.copy(alpha = color.alpha * 0.85f),
                1f to color.copy(alpha = 0f),
            ),
            center = center,
            radius = outer,
        ),
        radius = outer,
        center = center,
    )
}
