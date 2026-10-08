package com.medhome.nepal.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import com.medhome.nepal.ui.motion.isReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassThemeTest {

    /** Every contrast rule must hold in both themes. */
    private val palettes = listOf("light" to lightGlassColors(), "dark" to darkGlassColors())

    @Test
    fun `light link color is the accent 28 percent darker`() {
        assertEquals(Color(0xFF394299).toArgb(), lightGlassColors().link.toArgb())
    }

    @Test
    fun `dark link and emphasis are lighter tints of the accent`() {
        val dark = darkGlassColors()
        assertEquals(DefaultAccent.lighten(DARK_LINK_LIGHTEN_FRACTION), dark.link)
        assertEquals(DefaultAccent.lighten(DARK_EMPHASIS_LIGHTEN_FRACTION), dark.accentEmphasis)
        assertEquals(DefaultAccent, dark.accent)
    }

    @Test
    fun `changing the accent changes every derived token`() {
        val accent = Color(0xFF6A4FB3)
        val light = lightGlassColors(accent)
        assertEquals(accent, light.accent)
        assertEquals(accent.darken(LINK_DARKEN_FRACTION), light.link)
        assertEquals(accent.lighten(DARK_LINK_LIGHTEN_FRACTION), darkGlassColors(accent).link)
    }

    @Test
    fun `darken and lighten keep alpha and clamp the fraction`() {
        val translucent = Color.White.copy(alpha = 0.4f)
        assertEquals(0.4f, translucent.darken(0.5f).alpha, 0.01f)
        assertEquals(0.4f, Color.Black.copy(alpha = 0.4f).lighten(0.5f).alpha, 0.01f)
        assertEquals(Color.Black.toArgb(), Color.White.darken(2f).toArgb())
        assertEquals(Color.White.toArgb(), Color.Black.lighten(2f).toArgb())
    }

    @Test
    fun `floating glass is more transparent than cards in both themes`() {
        for ((name, colors) in palettes) {
            assertTrue(name, colors.glassFillFloating.alpha < colors.glassFill.alpha)
            assertTrue(name, colors.glassFallback.alpha > colors.glassFill.alpha)
        }
    }

    @Test
    fun `white text on the accent passes text contrast in both themes`() {
        for ((name, colors) in palettes) {
            assertTrue(name, contrastRatio(colors.onAccent, colors.accent) >= MIN_TEXT_CONTRAST)
        }
    }

    @Test
    fun `all text passes text contrast on every glass backdrop in both themes`() {
        for ((name, colors) in palettes) {
            for (backdrop in colors.glassBackdrops()) {
                assertTrue("$name link on $backdrop", contrastRatio(colors.link, backdrop) >= MIN_TEXT_CONTRAST)
                assertTrue("$name primary on $backdrop", contrastRatio(colors.textPrimary, backdrop) >= MIN_TEXT_CONTRAST)
                assertTrue("$name secondary on $backdrop", contrastRatio(colors.textSecondary, backdrop) >= MIN_TEXT_CONTRAST)
                assertTrue("$name error on $backdrop", contrastRatio(colors.error, backdrop) >= MIN_TEXT_CONTRAST)
                assertTrue("$name warning on $backdrop", contrastRatio(colors.warning, backdrop) >= MIN_TEXT_CONTRAST)
            }
        }
    }

    @Test
    fun `status message text passes inside its tinted box in both themes`() {
        for ((name, colors) in palettes) {
            for (tone in listOf(colors.error, colors.warning, colors.link)) {
                for (backdrop in colors.glassBackdrops()) {
                    val tinted = tone.copy(alpha = STATUS_TINT_ALPHA).compositeOver(backdrop)
                    assertTrue("$name $tone on $tinted", contrastRatio(tone, tinted) >= MIN_TEXT_CONTRAST)
                }
            }
        }
    }

    @Test
    fun `accent borders and indicators pass non-text contrast on glass in both themes`() {
        for ((name, colors) in palettes) {
            for (backdrop in colors.glassBackdrops()) {
                assertTrue("$name emphasis on $backdrop", contrastRatio(colors.accentEmphasis, backdrop) >= MIN_UI_CONTRAST)
            }
        }
    }

    @Test
    fun `stock Material surfaces are solid and readable in both themes`() {
        for ((name, colors) in palettes) {
            val s = colors.materialSurfaces
            val surfaces = listOf(s.surface, s.containerLowest, s.containerLow, s.container, s.containerHigh, s.containerHighest)
            for (surface in surfaces) {
                assertEquals("$name surface must be opaque", 1f, surface.alpha, 0.001f)
                assertTrue("$name text on $surface", contrastRatio(colors.textPrimary, surface) >= MIN_TEXT_CONTRAST)
                assertTrue("$name secondary on $surface", contrastRatio(colors.textSecondary, surface) >= MIN_TEXT_CONTRAST)
                assertTrue("$name outline on $surface", contrastRatio(s.outline, surface) >= MIN_UI_CONTRAST)
            }
        }
    }

    @Test
    fun `contrast ratio matches known WCAG values`() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(1f, contrastRatio(Color.White, Color.White), 0.001f)
    }

    @Test
    fun `motion is reduced only when animations are turned off`() {
        assertTrue(isReducedMotion(0f))
        assertFalse(isReducedMotion(0.5f))
        assertFalse(isReducedMotion(1f))
    }
}
