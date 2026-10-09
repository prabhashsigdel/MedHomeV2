package com.medhome.nepal.ui.components

import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import com.medhome.nepal.ui.motion.LocalReducedMotion
import com.medhome.nepal.ui.motion.MotionTokens
import com.medhome.nepal.ui.theme.GlassTheme
import com.medhome.nepal.ui.theme.GlowFalloff
import com.medhome.nepal.ui.theme.MeshPalette
import com.medhome.nepal.ui.theme.THEME_CROSSFADE_MS
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
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
 * Draws [palette] behind the content. The gradients are built once per palette and size and
 * reused on every redraw (recomposing the caller doesn't rebuild them). When the palette
 * changes (a theme switch), the new layer fades in over the old one: both stay cached, so only
 * their opacity changes per frame. Instant when animations are turned off.
 */
@Composable
fun Modifier.meshGradient(palette: MeshPalette): Modifier {
    val crossfadeMs = if (LocalReducedMotion.current) 0 else THEME_CROSSFADE_MS
    return this then MeshGradientElement(palette, crossfadeMs)
}

private data class MeshGradientElement(
    val palette: MeshPalette,
    val crossfadeMs: Int,
) : ModifierNodeElement<MeshGradientNode>() {
    override fun create() = MeshGradientNode(MeshLayer(palette), crossfadeMs)

    override fun update(node: MeshGradientNode) = node.update(palette, crossfadeMs)

    override fun InspectorInfo.inspectableProperties() {
        name = "meshGradient"
        properties["palette"] = palette
    }
}

private class MeshGradientNode(
    private var current: MeshLayer,
    private var crossfadeMs: Int,
) : Modifier.Node(), DrawModifierNode {
    /** The layer fading out under [current] during a palette change. */
    private var previous: MeshLayer? = null

    /** How far [current] has faded in over [previous]; read in draw, so frames only redraw. */
    private val fadeIn = Animatable(1f)

    fun update(palette: MeshPalette, crossfadeMs: Int) {
        this.crossfadeMs = crossfadeMs
        if (palette == current.palette) return
        previous = if (crossfadeMs > 0) current else null
        current = MeshLayer(palette)
        if (previous == null) {
            invalidateDraw()
            return
        }
        coroutineScope.launch {
            fadeIn.snapTo(0f)
            fadeIn.animateTo(1f, tween(crossfadeMs, easing = MotionTokens.EaseOut))
            previous = null
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val fading = previous
        if (fading != null) fading.draw(this, alpha = 1f)
        current.draw(this, alpha = if (fading == null) 1f else fadeIn.value)
        drawContent()
    }
}

/** One palette's drawing, with its paints cached for the last size it was drawn at. */
internal class MeshLayer(val palette: MeshPalette) {
    private var cachedSize = Size.Unspecified
    private var cachedPaints: List<Paint> = emptyList()
    private val basePaint = Paint().apply { color = palette.base.toArgb() }
    private val layerPaint = Paint()

    /** The glow paints for [size]; rebuilt only when the size changes. */
    fun paintsFor(size: Size): List<Paint> {
        if (size != cachedSize) {
            cachedPaints = buildPaints(size)
            cachedSize = size
        }
        return cachedPaints
    }

    fun draw(scope: DrawScope, alpha: Float) {
        val size = scope.size
        val paints = paintsFor(size)
        scope.drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            // While fading, base and glows go into one layer first, so the composite fades as one.
            val layered = alpha < 1f
            if (layered) {
                layerPaint.alpha = (alpha * OPAQUE).toInt().coerceIn(0, OPAQUE)
                native.saveLayer(0f, 0f, size.width, size.height, layerPaint)
            }
            native.drawRect(0f, 0f, size.width, size.height, basePaint)
            paints.forEach { native.drawRect(0f, 0f, size.width, size.height, it) }
            if (layered) native.restore()
        }
    }

    /** Dithered, so the dark glows don't band. */
    private fun buildPaints(size: Size): List<Paint> {
        val unit = max(size.width, size.height)
        return palette.glows.map { glow ->
            Paint().apply {
                isDither = true
                shader = RadialGradient(
                    glow.center.x * size.width,
                    glow.center.y * size.height,
                    max(glow.radius * unit, 1f),
                    GlowFalloff.map { (_, opacity) -> glow.color.copy(alpha = glow.color.alpha * opacity).toArgb() }.toIntArray(),
                    FalloffPositions,
                    Shader.TileMode.CLAMP,
                )
            }
        }
    }

    private companion object {
        const val OPAQUE = 255
        val FalloffPositions = GlowFalloff.map { it.first }.toFloatArray()
    }
}
