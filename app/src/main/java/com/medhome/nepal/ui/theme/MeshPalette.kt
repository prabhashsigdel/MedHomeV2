package com.medhome.nepal.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
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
val SampledAreas = listOf(Size(360f, 800f), Size(800f, 360f), Size(600f, 960f), Size(400f, 300f))

/** Points along each line between two glow centres, and along each edge of the area. */
private const val LINE_STEPS = 64

/** How many of the most extreme starting points are refined, per direction. */
private const val REFINED_SEEDS = 8

/** Refinement stops once its step is this small (px): far below one pixel. */
private const val MIN_REFINE_STEP = 0.05f

/**
 * The darkest and the brightest color this palette shows, found exactly rather than on a grid.
 * The search starts from the glow centers (where each glow peaks), the lines between them
 * (where overlaps add up) and the area's edges and center (where glows are weakest), then
 * climbs from the most extreme of those to the true local extreme. MeshPaletteTest checks the
 * result against a dense grid.
 */
fun MeshPalette.darkestAndBrightest(): Pair<Color, Color> {
    val darkest = SampledAreas.map { size -> extreme(size, brighter = false) }.minBy { it.luminance() }
    val brightest = SampledAreas.map { size -> extreme(size, brighter = true) }.maxBy { it.luminance() }
    return darkest to brightest
}

private fun MeshPalette.extreme(size: Size, brighter: Boolean): Color {
    val sign = if (brighter) 1f else -1f
    fun score(point: Offset) = sign * colorAt(point, size).luminance()
    val seeds = extremeCandidates(size).sortedByDescending(::score).take(REFINED_SEEDS)
    val best = seeds.map { refine(it, size, ::score) }.maxBy(::score)
    return colorAt(best, size)
}

/** Pattern search: step toward whichever neighbor scores higher, halving the step when none does. */
private fun refine(start: Offset, size: Size, score: (Offset) -> Float): Offset {
    var point = start
    var step = max(size.width, size.height) / LINE_STEPS
    while (step > MIN_REFINE_STEP) {
        val next = listOf(Offset(step, 0f), Offset(-step, 0f), Offset(0f, step), Offset(0f, -step))
            .map { (point + it).within(size) }
            .maxBy(score)
        if (score(next) > score(point)) point = next else step /= 2
    }
    return point
}

private fun Offset.within(size: Size) = Offset(x.coerceIn(0f, size.width), y.coerceIn(0f, size.height))

/** Where [darkestAndBrightest] starts its search on an area of [size]: exact glow centers included. */
fun MeshPalette.extremeCandidates(size: Size): List<Offset> {
    val centers = glows.map { Offset(it.center.x * size.width, it.center.y * size.height) }
    val betweenCenters = centers.indices.flatMap { i ->
        (i + 1 until centers.size).flatMap { j -> pointsBetween(centers[i], centers[j]) }
    }
    val corners = listOf(Offset.Zero, Offset(size.width, 0f), Offset(size.width, size.height), Offset(0f, size.height))
    val edges = corners.indices.flatMap { pointsBetween(corners[it], corners[(it + 1) % corners.size]) }
    return centers + betweenCenters + edges + Offset(size.width / 2, size.height / 2)
}

private fun pointsBetween(start: Offset, end: Offset): List<Offset> =
    (0..LINE_STEPS).map { step -> lerp(start, end, step.toFloat() / LINE_STEPS) }

/** Dark: amber top-left, rose mid-right, violet bottom-left over a deep plum. */
val WarmDusk = MeshPalette(
    base = Color(0xFF120A1C),
    glows = listOf(
        Glow(Color(0xFFF2994A).copy(alpha = 0.20f), Offset(0.08f, 0.06f), radius = 0.62f),
        Glow(Color(0xFFE5577A).copy(alpha = 0.27f), Offset(1.00f, 0.45f), radius = 0.55f),
        Glow(Color(0xFF7B5CD6).copy(alpha = 0.32f), Offset(0.05f, 0.95f), radius = 0.65f),
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
