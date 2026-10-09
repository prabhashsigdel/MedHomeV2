package com.medhome.nepal.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DarkPaletteTest {

    @Test
    fun `stored keys map back to their palette`() {
        for (palette in DarkPalette.entries) {
            assertEquals(palette, DarkPalette.fromKey(palette.key))
        }
    }

    @Test
    fun `missing or unknown keys fall back to warm dusk`() {
        assertEquals(DarkPalette.WARM_DUSK, DarkPalette.fromKey(null))
        assertEquals(DarkPalette.WARM_DUSK, DarkPalette.fromKey("neon"))
    }

    @Test
    fun `keys are stable`() {
        assertEquals("warm_dusk", DarkPalette.WARM_DUSK.key)
        assertEquals("midnight_aurora", DarkPalette.MIDNIGHT_AURORA.key)
    }
}
