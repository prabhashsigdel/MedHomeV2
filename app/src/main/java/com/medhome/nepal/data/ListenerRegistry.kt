package com.medhome.nepal.data

/**
 * Every open Firestore snapshot listener, so sign-out can stop them all before it shuts
 * Firestore down. A screen's listener normally stops when the screen stops collecting, but that
 * happens a frame or more after sign-out is published; shutting Firestore down under a live
 * listener makes it fail, and its screen would flash an error while it fades out.
 *
 * [stopAll] also closes the registry: a listener that registers afterwards (a screen that started
 * collecting just as sign-out ran) is stopped at once instead of being left running. The next
 * sign-in [reopen]s it.
 */
class ListenerRegistry {
    private val stops = mutableSetOf<Stop>()
    private var closed = false

    /** A registered stop action; [release] when the listener ends on its own. */
    inner class Stop internal constructor(private val action: () -> Unit) {
        fun release() {
            synchronized(stops) { stops.remove(this) }
        }

        internal fun run() = action()
    }

    /**
     * Registers [stop] (remove the Firestore registration and end the flow normally: screens
     * keep what they showed). It must be safe to call twice. While the registry is closed (signed
     * out), [stop] runs at once and nothing is kept.
     */
    fun register(stop: () -> Unit): Stop {
        val entry = Stop(stop)
        val accepted = synchronized(stops) {
            if (!closed) stops += entry
            !closed
        }
        if (!accepted) entry.run()
        return entry
    }

    /** Stops every open listener now, synchronously, and closes the registry until [reopen]. */
    fun stopAll() {
        val open = synchronized(stops) {
            closed = true
            stops.toList().also { stops.clear() }
        }
        open.forEach { it.run() }
    }

    /** Accepts listeners again: called when a session starts (sign-in, or a restored session). */
    fun reopen() {
        synchronized(stops) { closed = false }
    }

    val isClosed: Boolean get() = synchronized(stops) { closed }

    val openCount: Int get() = synchronized(stops) { stops.size }
}
