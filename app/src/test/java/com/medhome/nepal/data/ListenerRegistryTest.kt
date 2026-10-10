package com.medhome.nepal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenerRegistryTest {

    private val listeners = ListenerRegistry()

    @Test
    fun `stopAll stops every open listener once and forgets them`() {
        var stopped = 0
        listeners.register { stopped++ }
        listeners.register { stopped++ }
        listeners.stopAll()
        listeners.stopAll()
        assertEquals(2, stopped)
        assertEquals(0, listeners.openCount)
    }

    @Test
    fun `a listener registered after stopAll is stopped at once and not kept`() {
        listeners.stopAll()
        var stopped = 0
        listeners.register { stopped++ }
        assertEquals(1, stopped)
        assertEquals(0, listeners.openCount)
        // Releasing it when its flow ends is harmless.
        listeners.register { stopped++ }.release()
        assertEquals(2, stopped)
    }

    @Test
    fun `after reopen listeners are kept until the next stopAll`() {
        listeners.stopAll()
        assertTrue(listeners.isClosed)
        listeners.reopen()
        assertFalse(listeners.isClosed)
        var stopped = 0
        listeners.register { stopped++ }
        assertEquals(0, stopped)
        assertEquals(1, listeners.openCount)
        listeners.stopAll()
        assertEquals(1, stopped)
    }

    @Test
    fun `a new registry is open`() {
        var stopped = 0
        listeners.register { stopped++ }
        assertFalse(listeners.isClosed)
        assertEquals(0, stopped)
        assertEquals(1, listeners.openCount)
    }
}
