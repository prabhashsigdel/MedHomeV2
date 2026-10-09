package com.medhome.nepal.ui.components

import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.medhome.nepal.ui.theme.GlassTheme
import com.medhome.nepal.ui.theme.GlowFalloff
import com.medhome.nepal.ui.theme.MeshPalette
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.max

/** The blur source for the floating bar on the current screen; null means draw its fallback fill. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * The app background: the theme's mesh gradient (an opaque base under three large soft radial
 * glows), behind [content]. Static: nothing animates or blurs, and scrolling never redraws it.
 * It is also the blur source of the floating bar, the only surface that blurs.
 */
@Composable
fun MeshBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val hazeState = rememberHazeState()
    Box(modifier = modifier.fillMaxSize()) {
        Spacer(
            modifier = Modifier
                .matchParentSize()
                // Inside hazeSource, so the mesh is part of what the floating bar blurs.
                .hazeSource(hazeState)
                .meshGradient(GlassTheme.colors.background),
        )
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            content()
        }
    }
}

/**
 * Draws [palette] behind the content, in the draw phase only. The gradients are built once per
 * size and palette (drawWithCache) and dithered, so the dark glows don't band.
 */
fun Modifier.meshGradient(palette: MeshPalette): Modifier = drawWithCache {
    val unit = max(size.width, size.height)
    val positions = GlowFalloff.map { it.first }.toFloatArray()
    val paints = palette.glows.map { glow ->
        Paint().apply {
            isDither = true
            shader = RadialGradient(
                glow.center.x * size.width,
                glow.center.y * size.height,
                max(glow.radius * unit, 1f),
                GlowFalloff.map { (_, opacity) -> glow.color.copy(alpha = glow.color.alpha * opacity).toArgb() }.toIntArray(),
                positions,
                Shader.TileMode.CLAMP,
            )
        }
    }
    onDrawBehind {
        drawRect(palette.base)
        drawIntoCanvas { canvas ->
            paints.forEach { canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, it) }
        }
    }
}
