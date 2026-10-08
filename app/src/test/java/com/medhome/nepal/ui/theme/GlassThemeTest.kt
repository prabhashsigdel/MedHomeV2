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
        assertEquals(Color(0xFF225A54).toArgb(), link.toArgb())
    }

    @Test
    fun `changing the accent changes every derived token`() {
        val accent = Color(0xFF6A4FB3)
        val colors = lightGlassColors(accent)
        assertEquals(accent, colors.accent)
        assertEquals(accent.darken(LINK_DARKEN_FRACTION), colors.link)
        assertEquals(accent.copy(alpha = 0.55f), colors.blobAccent)
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
    fun `motion is reduced only when animations are turned off`() {
        assertTrue(isReducedMotion(0f))
        assertFalse(isReducedMotion(0.5f))
        assertFalse(isReducedMotion(1f))
    }
}
