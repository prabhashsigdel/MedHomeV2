package com.medhome.nepal.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.medhome.nepal.ui.motion.isReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassThemeTest {

    @Test
    fun `link color is the accent 28 percent darker`() {
        val link = lightGlassColors().link
        assertEquals(Color(0xFF394299).toArgb(), link.toArgb())
    }

    @Test
    fun `changing the accent changes every derived token`() {
        val accent = Color(0xFF6A4FB3)
        val colors = lightGlassColors(accent)
        assertEquals(accent, colors.accent)
        assertEquals(accent.darken(LINK_DARKEN_FRACTION), colors.link)
    }

    @Test
    fun `darken keeps alpha and clamps the fraction`() {
        val translucent = Color.White.copy(alpha = 0.4f)
        assertEquals(0.4f, translucent.darken(0.5f).alpha, 0.01f)
        assertEquals(Color.Black.toArgb(), Color.White.darken(2f).toArgb())
        assertEquals(Color.White.toArgb(), Color.White.darken(-1f).toArgb())
    }

    @Test
    fun `floating glass is more transparent than cards`() {
        val colors = lightGlassColors()
        assertTrue(colors.glassFillFloating.alpha < colors.glassFill.alpha)
        assertTrue(colors.glassFallback.alpha > colors.glassFill.alpha)
    }

    @Test
    fun `white text on the accent passes text contrast`() {
        val colors = lightGlassColors()
        assertTrue(contrastRatio(colors.onAccent, colors.accent) >= MIN_TEXT_CONTRAST)
    }

    @Test
    fun `link and body text pass text contrast on every glass backdrop`() {
        val colors = lightGlassColors()
        for (backdrop in colors.glassBackdrops()) {
            assertTrue("link on $backdrop", contrastRatio(colors.link, backdrop) >= MIN_TEXT_CONTRAST)
            assertTrue("primary on $backdrop", contrastRatio(colors.textPrimary, backdrop) >= MIN_TEXT_CONTRAST)
            assertTrue("secondary on $backdrop", contrastRatio(colors.textSecondary, backdrop) >= MIN_TEXT_CONTRAST)
            assertTrue("error on $backdrop", contrastRatio(colors.error, backdrop) >= MIN_TEXT_CONTRAST)
        }
    }

    @Test
    fun `accent borders and indicators pass non-text contrast on glass`() {
        val colors = lightGlassColors()
        for (backdrop in colors.glassBackdrops()) {
            assertTrue("accent on $backdrop", contrastRatio(colors.accent, backdrop) >= MIN_UI_CONTRAST)
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
