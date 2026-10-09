package com.medhome.nepal.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.medhome.nepal.ui.motion.isReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassThemeTest {

    /** Every contrast rule must hold over both backgrounds. */
    private val palettes = listOf(
        "light" to glassColorsFor(dark = false),
        "dark" to glassColorsFor(dark = true),
    )

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
    fun `glass fills are neutral white at the specified strength`() {
        val light = lightGlassColors()
        val dark = darkGlassColors()
        assertEquals(Color.White.copy(alpha = 0.45f), light.glassFill)
        assertEquals(Color.White.copy(alpha = 0.12f), dark.glassFill)
        assertEquals(Color.White.copy(alpha = 0.90f), light.glassBorderTop)
        assertEquals(Color.White.copy(alpha = 0.30f), light.glassBorderBottom)
        assertEquals(Color.White.copy(alpha = 0.40f), dark.glassBorderTop)
        assertEquals(Color.White.copy(alpha = 0.06f), dark.glassBorderBottom)
        for ((name, colors) in palettes) {
            assertTrue("$name controls are fainter than cards", colors.controlFill.alpha < colors.glassFill.alpha)
            assertTrue("$name fallback is denser than the bar", colors.glassFallback.alpha > colors.glassFill.alpha)
        }
    }

    @Test
    fun `the selected tab is indigo in light and white in dark`() {
        assertEquals(lightGlassColors().link, lightGlassColors().selectedTabContent)
        assertEquals(Color.White, darkGlassColors().selectedTabContent)
    }

    @Test
    fun `dark text is white and light text stays dark`() {
        val dark = darkGlassColors()
        assertEquals(Color.White, dark.textPrimary)
        assertEquals(Color.White.copy(alpha = 0.75f), dark.textSecondary)
        assertTrue(lightGlassColors().textPrimary.luminance() < 0.05f)
    }

    @Test
    fun `dark uses warm dusk and light uses soft daylight`() {
        assertEquals(WarmDusk, glassColorsFor(dark = true).background)
        assertEquals(SoftDaylight, glassColorsFor(dark = false).background)
    }

    @Test
    fun `text on filled buttons passes text contrast in every palette`() {
        for ((name, colors) in palettes) {
            assertTrue("$name on accent", contrastRatio(colors.onAccent, colors.accent) >= MIN_TEXT_CONTRAST)
            assertTrue("$name on error", contrastRatio(colors.onError, colors.error) >= MIN_TEXT_CONTRAST)
        }
    }

    @Test
    fun `backdrops cover the darkest and brightest region bare, on a card and on a control`() {
        for ((name, colors) in palettes) {
            val backdrops = colors.glassBackdrops()
            val (darkest, brightest) = colors.background.darkestAndBrightest()
            assertEquals(name, 6, backdrops.size)
            assertTrue(name, darkest in backdrops && brightest in backdrops)
            assertTrue("$name glows are visible", brightest.luminance() - darkest.luminance() > 0.02f)
            assertTrue(name, backdrops.all { it.alpha == 1f })
        }
    }

    @Test
    fun `all text passes text contrast on every glass backdrop in every palette`() {
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
    fun `tab bar labels pass text contrast on the bar and the selected pill in every palette`() {
        for ((name, colors) in palettes) {
            val (darkest, brightest) = colors.background.darkestAndBrightest()
            for (region in listOf(darkest, brightest)) {
                val bar = colors.glassFill.compositeOver(colors.floatingScrim.compositeOver(region))
                val pill = colors.selectedPill.compositeOver(bar)
                val fallbackBar = colors.glassFallback.compositeOver(region)
                for (backdrop in listOf(bar, fallbackBar)) {
                    assertTrue("$name unselected on $backdrop", contrastRatio(colors.textSecondary, backdrop) >= MIN_TEXT_CONTRAST)
                }
                // The selected label sits on the pill, which slides under the others too.
                for (backdrop in listOf(pill, colors.selectedPill.compositeOver(fallbackBar))) {
                    assertTrue("$name selected on $backdrop", contrastRatio(colors.selectedTabContent, backdrop) >= MIN_TEXT_CONTRAST)
                    assertTrue("$name unselected on $backdrop", contrastRatio(colors.textSecondary, backdrop) >= MIN_TEXT_CONTRAST)
                }
            }
        }
    }

    @Test
    fun `status message text passes inside its tinted box in every palette`() {
        for ((name, colors) in palettes) {
            for (tone in listOf(colors.error, colors.warning, colors.link)) {
                for (backdrop in colors.glassBackdrops()) {
                    val tinted = tone.copy(alpha = STATUS_TINT_ALPHA).compositeOver(colors.statusUnderlay.compositeOver(backdrop))
                    assertTrue("$name $tone on $tinted", contrastRatio(tone, tinted) >= MIN_TEXT_CONTRAST)
                }
            }
        }
    }

    @Test
    fun `accent borders and indicators pass non-text contrast on glass in every palette`() {
        for ((name, colors) in palettes) {
            for (backdrop in colors.glassBackdrops()) {
                assertTrue("$name emphasis on $backdrop", contrastRatio(colors.accentEmphasis, backdrop) >= MIN_UI_CONTRAST)
            }
        }
    }

    @Test
    fun `stock Material surfaces are solid and readable in every palette`() {
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
    fun `contrast ratio composites translucent text over the background first`() {
        // Fully transparent text shows the background itself: no contrast at all.
        assertEquals(1f, contrastRatio(Color.White.copy(alpha = 0f), Color.Black), 0.001f)
        val halfWhite = Color.White.copy(alpha = 0.5f)
        val expected = contrastRatio(halfWhite.compositeOver(Color.Black), Color.Black)
        assertEquals(expected, contrastRatio(halfWhite, Color.Black), 0.001f)
    }

    @Test
    fun `motion is reduced only when animations are turned off`() {
        assertTrue(isReducedMotion(0f))
        assertFalse(isReducedMotion(0.5f))
        assertFalse(isReducedMotion(1f))
    }
}
