package com.medhome.nepal.data

/**
 * Every open Firestore snapshot listener, so sign-out can stop them all before it shuts
 * Firestore down. A screen's listener normally stops when the screen stops collecting, but that
 * happens a frame or more after sign-out is published; shutting Firestore down under a live
 * listener makes it fail, and its screen would flash an error while it fades out.
 */
class ListenerRegistry {
    private val stops = mutableSetOf<Stop>()

    /** A registered stop action; [release] when the listener ends on its own. */
    inner class Stop internal constructor(private val action: () -> Unit) {
        fun release() {
            synchronized(stops) { stops.remove(this) }
        }

        internal fun run() = action()
    }

    /**
     * Registers [stop] (remove the Firestore registration and end the flow normally: screens
     * keep what they showed). It must be safe to call twice.
     */
    fun register(stop: () -> Unit): Stop {
        val entry = Stop(stop)
        synchronized(stops) { stops += entry }
        return entry
    }

    /** Stops every open listener now, synchronously. */
    fun stopAll() {
        val open = synchronized(stops) { stops.toList().also { stops.clear() } }
        open.forEach { it.run() }
    }

    val openCount: Int get() = synchronized(stops) { stops.size }
}
