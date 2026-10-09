package com.medhome.nepal.ui.motion

import com.medhome.nepal.ui.common.OFFLINE_GRACE_MS
import com.medhome.nepal.ui.theme.THEME_CROSSFADE_MS
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keeps the app feeling quick: no animation or loading delay creeps back up unnoticed. */
class MotionBudgetTest {

    @Test
    fun `crossfades and entrances stay around 150 to 200ms`() {
        assertTrue(MotionTokens.CROSSFADE_MS in 150..200)
        assertTrue(MotionTokens.ENTRANCE_MS in 150..200)
        assertTrue(THEME_CROSSFADE_MS <= 200)
    }

    @Test
    fun `a whole screen is in within a quarter second`() {
        val lastItemDone = MotionTokens.MAX_STAGGER_STEPS * MotionTokens.STAGGER_MS + MotionTokens.ENTRANCE_MS
        assertTrue("Last item settles at ${lastItemDone}ms", lastItemDone <= 250)
    }

    @Test
    fun `screen slides stay short`() {
        assertTrue(MotionTokens.SCREEN_MS <= 250)
    }

    @Test
    fun `the offline notice waits under a second`() {
        assertTrue(OFFLINE_GRACE_MS < 1_000)
    }
}
