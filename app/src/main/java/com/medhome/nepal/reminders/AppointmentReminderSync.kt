package com.medhome.nepal.reminders

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
 */
class AppointmentReminderSync(
    private val session: StateFlow<SessionState>,
    private val bookings: BookingRepository,
    private val apply: suspend (uid: String, snapshot: BookingsSnapshot) -> Unit,
    private val scope: CoroutineScope,
) {
    /** Call once, on the main thread (it follows the app's foreground lifecycle). */
    fun start() {
        scope.launch {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                patientUid().collectLatest { uid -> if (uid != null) follow(uid) }
            }
        }
    }

    internal fun patientUid() = session
        .map { state -> (state as? SessionState.SignedIn)?.profile?.takeIf { it.role == Role.PATIENT }?.uid }
        .distinctUntilChanged()

    /** Applies every answer until the listener ends (sign-out) or fails (retried next start). */
    internal suspend fun follow(uid: String) {
        try {
            bookings.myBookings().collect { snapshot -> apply(uid, snapshot) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Bookings listener failed: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "AppointmentSync"
    }
}
