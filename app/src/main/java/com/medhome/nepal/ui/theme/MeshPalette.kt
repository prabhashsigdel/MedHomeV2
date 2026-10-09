package com.medhome.nepal.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.medhome.nepal.data.DarkPalette
import kotlin.math.max

/**
 * One large soft radial light. [center] is a fraction of the width and height, [radius] a
 * fraction of the longer side, and [color]'s alpha is the opacity at the center.
 */
@Immutable
data class Glow(val color: Color, val center: Offset, val radius: Float)

/** The app background: an opaque [base] with [glows] drawn over it in order. */
@Immutable
data class MeshPalette(val base: Color, val glows: List<Glow>)

/**
 * A glow's opacity from its center (0) to its edge (1), as a fraction of its peak. Drawn as
 * gradient stops and used by [colorAt], so the contrast tests check what is actually drawn.
 */
val GlowFalloff: List<Pair<Float, Float>> = listOf(0f to 1f, 0.35f to 0.7f, 0.7f to 0.25f, 1f to 0f)

/** [GlowFalloff] at [distance] (0 = center, 1 = edge); 0 beyond the edge. */
fun glowFalloff(distance: Float): Float {
    if (distance >= 1f) return 0f
    val (startStop, endStop) = GlowFalloff.zipWithNext().first { (_, end) -> distance <= end.first }
    val fraction = (distance - startStop.first) / (endStop.first - startStop.first)
    return startStop.second + (endStop.second - startStop.second) * fraction
}

/** The color drawn at [position] on an area of [size]: the base with every glow composited over it. */
fun MeshPalette.colorAt(position: Offset, size: Size): Color {
    val unit = max(size.width, size.height)
    return glows.fold(base) { behind, glow ->
        val center = Offset(glow.center.x * size.width, glow.center.y * size.height)
        val distance = (position - center).getDistance() / (glow.radius * unit)
        glow.color.copy(alpha = glow.color.alpha * glowFalloff(distance)).compositeOver(behind)
    }
}

/**
 * Shapes the background is drawn in: phones in portrait and landscape, a tablet, and the
 * wide, short panels of sheets and dialogs (they draw the mesh too).
 */
private val SampledAreas = listOf(Size(360f, 800f), Size(800f, 360f), Size(600f, 960f), Size(400f, 300f))
private const val SAMPLES_PER_SIDE = 41

/** The darkest and the brightest color this palette shows, sampled over [SampledAreas]. */
fun MeshPalette.darkestAndBrightest(): Pair<Color, Color> {
    val colors = SampledAreas.flatMap { size ->
        val steps = SAMPLES_PER_SIDE - 1
        (0..steps).flatMap { i ->
            (0..steps).map { j -> colorAt(Offset(size.width * i / steps, size.height * j / steps), size) }
        }
    }
    return colors.minBy { it.luminance() } to colors.maxBy { it.luminance() }
}

/** Dark: amber top-left, rose mid-right, violet bottom-left over a deep plum. The dark default. */
val WarmDusk = MeshPalette(
    base = Color(0xFF120A1C),
    glows = listOf(
        Glow(Color(0xFFF2994A).copy(alpha = 0.20f), Offset(0.08f, 0.06f), radius = 0.62f),
        Glow(Color(0xFFE5577A).copy(alpha = 0.27f), Offset(1.00f, 0.45f), radius = 0.55f),
        Glow(Color(0xFF7B5CD6).copy(alpha = 0.32f), Offset(0.05f, 0.95f), radius = 0.65f),
    ),
)

/** Dark: violet top-right, sky blue mid-left, magenta bottom-right over a deep navy. */
val MidnightAurora = MeshPalette(
    base = Color(0xFF070B1A),
    glows = listOf(
        Glow(Color(0xFF6E56CF).copy(alpha = 0.36f), Offset(0.92f, 0.06f), radius = 0.62f),
        Glow(Color(0xFF3BA7E0).copy(alpha = 0.24f), Offset(0.00f, 0.50f), radius = 0.55f),
        Glow(Color(0xFFB04BC9).copy(alpha = 0.32f), Offset(0.95f, 0.95f), radius = 0.65f),
    ),
)

/** Light: peach top-left, lilac mid-right, sky bottom-left over a warm off-white. */
val SoftDaylight = MeshPalette(
    base = Color(0xFFF7F2EC),
    glows = listOf(
        Glow(Color(0xFFFFB38A).copy(alpha = 0.60f), Offset(0.08f, 0.06f), radius = 0.62f),
        Glow(Color(0xFFC9B6F2).copy(alpha = 0.60f), Offset(1.00f, 0.45f), radius = 0.55f),
        Glow(Color(0xFFA8D4F5).copy(alpha = 0.65f), Offset(0.05f, 0.95f), radius = 0.65f),
    ),
)

fun DarkPalette.mesh(): MeshPalette = when (this) {
    DarkPalette.WARM_DUSK -> WarmDusk
    DarkPalette.MIDNIGHT_AURORA -> MidnightAurora
}
