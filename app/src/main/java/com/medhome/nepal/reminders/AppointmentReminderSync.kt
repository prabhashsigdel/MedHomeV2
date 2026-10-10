package com.medhome.nepal.reminders

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.medhome.nepal.data.BookingRepository
import com.medhome.nepal.data.BookingsSnapshot
import com.medhome.nepal.domain.Role
import com.medhome.nepal.session.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps appointment reminders in step with the patient's bookings in Firestore while the app is
 * in use: each answer of the bookings listener (bookings made on another phone, cancelled by the
 * clinic) is applied, which also resyncs at every app start. The listener is registered with the
 * ListenerRegistry like any other, so sign-out stops it before Firestore shuts down.
 *
 * Before following a patient, the reminders on this phone are claimed for them ([claimOwner]):
 * if they belong to another account (a sign-out whose wipe failed), they are wiped first.
 */
class AppointmentReminderSync(
    private val session: StateFlow<SessionState>,
    private val bookings: BookingRepository,
    private val apply: suspend (uid: String, snapshot: BookingsSnapshot) -> Unit,
    private val scope: CoroutineScope,
    private val claimOwner: suspend (uid: String) -> Unit = {},
    /** A monotonic clock, to tell a listener that stayed up from one that keeps failing. */
    private val elapsedMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    /** Call once, on the main thread (it follows the app's foreground lifecycle). */
    fun start() {
        scope.launch {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                patientUid().collectLatest { uid -> if (uid != null) claimAndFollow(uid) }
            }
        }
    }

    internal fun patientUid() = session
        .map { state -> (state as? SessionState.SignedIn)?.profile?.takeIf { it.role == Role.PATIENT }?.uid }
        .distinctUntilChanged()

    /** Claims the reminders for [uid] (retrying until it works), then follows their bookings. */
    internal suspend fun claimAndFollow(uid: String) {
        retrying("Reminder owner claim") { claimOwner(uid) }
        follow(uid)
    }

    /**
     * Applies every answer for as long as [uid] stays the signed-in patient: collectLatest
     * cancels this when the patient changes or the app goes to the background. When the listener
     * fails, or ends while still running here (sign-out ends it, and then this is cancelled), it
     * is started again after a delay that doubles up to [MAX_RETRY_MS]. The delay only starts over
     * after the server answered or the listener stayed up [HEALTHY_MS]: a listener that answers
     * from the cache and then fails would otherwise retry every [FIRST_RETRY_MS] forever.
     */
    internal suspend fun follow(uid: String) {
        var delayMs = FIRST_RETRY_MS
        while (true) {
            var serverAnswered = false
            val startedAt = elapsedMillis()
            attempt("Bookings listener") {
                bookings.myBookings().collect { snapshot ->
                    if (!snapshot.fromCache) serverAnswered = true
                    apply(uid, snapshot)
                }
            }
            if (serverAnswered || elapsedMillis() - startedAt >= HEALTHY_MS) delayMs = FIRST_RETRY_MS
            delay(delayMs)
            delayMs = nextDelay(delayMs)
        }
    }

    private suspend fun retrying(what: String, block: suspend () -> Unit) {
        var delayMs = FIRST_RETRY_MS
        while (attempt(what, block)) {
            delay(delayMs)
            delayMs = nextDelay(delayMs)
        }
    }

    /** Runs [block]; returns true if it failed (logged by type only: no personal data). */
    private suspend fun attempt(what: String, block: suspend () -> Unit): Boolean = try {
        block()
        false
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$what failed: ${e.javaClass.simpleName}")
        true
    }

    private fun nextDelay(current: Long) = minOf(current * 2, MAX_RETRY_MS)

    internal companion object {
        const val TAG = "AppointmentSync"

        /** First wait before retrying a failed listener or claim; doubles up to [MAX_RETRY_MS]. */
        const val FIRST_RETRY_MS = 5_000L
        const val MAX_RETRY_MS = 5 * 60_000L

        /** A listener up at least this long counts as healthy: the next retry delay starts over. */
        const val HEALTHY_MS = 60_000L
    }
}
