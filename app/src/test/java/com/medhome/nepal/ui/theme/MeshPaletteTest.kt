package com.medhome.nepal.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshPaletteTest {

    private val area = Size(360f, 800f)

    @Test
    fun `palettes use the specified base and glow colors`() {
        assertPalette(WarmDusk, 0xFF120A1C, listOf(0xFFF2994A, 0xFFE5577A, 0xFF7B5CD6))
        assertPalette(SoftDaylight, 0xFFF7F2EC, listOf(0xFFFFB38A, 0xFFC9B6F2, 0xFFA8D4F5))
    }

    @Test
    fun `both palettes have three glows, so the theme crossfade animates them pairwise`() {
        for (palette in listOf(WarmDusk, SoftDaylight)) assertEquals(3, palette.glows.size)
    }

    @Test
    fun `glows sit where each palette places them`() {
        // Warm dusk: amber top-left, rose mid-right, violet bottom-left.
        assertPlaced(WarmDusk.glows[0], left = true, Band.TOP)
        assertPlaced(WarmDusk.glows[1], left = false, Band.MIDDLE)
        assertPlaced(WarmDusk.glows[2], left = true, Band.BOTTOM)
        // Soft daylight: the same layout, so a theme switch only recolors the glows.
        assertPlaced(SoftDaylight.glows[0], left = true, Band.TOP)
        assertPlaced(SoftDaylight.glows[1], left = false, Band.MIDDLE)
        assertPlaced(SoftDaylight.glows[2], left = true, Band.BOTTOM)
    }

    @Test
    fun `glow falloff is full at the center, fades steadily and is gone at the edge`() {
        assertEquals(1f, glowFalloff(0f), 0.0001f)
        assertEquals(0f, glowFalloff(1f), 0.0001f)
        assertEquals(0f, glowFalloff(1.5f), 0.0001f)
        val samples = (0..100).map { glowFalloff(it / 100f) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b <= a })
    }

    @Test
    fun `colorAt is the glow over the base at its center and the base past its edge`() {
        val glow = Glow(Color.Red.copy(alpha = 0.4f), Offset(0.5f, 0.5f), radius = 0.25f)
        val palette = MeshPalette(base = Color.Black, glows = listOf(glow))
        val center = Offset(area.width / 2, area.height / 2)
        assertEquals(glow.color.compositeOver(Color.Black).toArgb(), palette.colorAt(center, area).toArgb())
        // The radius is a fraction of the longer side (800): 200px.
        val pastEdge = center + Offset(0f, 201f)
        assertEquals(Color.Black.toArgb(), palette.colorAt(pastEdge, area).toArgb())
    }

    @Test
    fun `dark palettes glow above their base and the light one tints below it`() {
        // The glows are large enough to reach every point, so the bare base itself never shows.
        val (darkestDark, brightestDark) = WarmDusk.darkestAndBrightest()
        assertTrue(darkestDark.luminance() >= WarmDusk.base.luminance())
        assertTrue(brightestDark.luminance() > darkestDark.luminance())
        val (darkest, brightest) = SoftDaylight.darkestAndBrightest()
        assertTrue(brightest.luminance() <= SoftDaylight.base.luminance())
        assertTrue(darkest.luminance() < brightest.luminance())
    }

    @Test
    fun `every sampled color is opaque`() {
        for (palette in listOf(WarmDusk, SoftDaylight)) {
            val (darkest, brightest) = palette.darkestAndBrightest()
            assertEquals(1f, darkest.alpha, 0.0001f)
            assertEquals(1f, brightest.alpha, 0.0001f)
        }
    }

    private enum class Band { TOP, MIDDLE, BOTTOM }

    private fun assertPlaced(glow: Glow, left: Boolean, band: Band) {
        assertEquals("left/right of $glow", left, glow.center.x < 0.5f)
        val actual = when {
            glow.center.y < 1f / 3 -> Band.TOP
            glow.center.y > 2f / 3 -> Band.BOTTOM
            else -> Band.MIDDLE
        }
        assertEquals("band of $glow", band, actual)
    }

    private fun assertPalette(palette: MeshPalette, base: Long, glows: List<Long>) {
        assertEquals(Color(base), palette.base)
        assertEquals(glows.map { Color(it) }, palette.glows.map { it.color.copy(alpha = 1f) })
    }
}
