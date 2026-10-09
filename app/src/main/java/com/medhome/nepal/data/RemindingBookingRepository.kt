package com.medhome.nepal.data

import android.util.Log
import com.medhome.nepal.domain.Booking
import com.medhome.nepal.domain.Slot
import kotlinx.coroutines.CancellationException

/**
 * Booking and cancelling, with the appointment's reminders set or removed straight away rather
 * than when the bookings listener next answers. A reminder failure never fails the booking.
 */
class RemindingBookingRepository(
    private val delegate: BookingRepository,
    private val onBooked: suspend (bookingId: String, startAtMillis: Long) -> Unit,
    private val onCancelled: suspend (bookingId: String) -> Unit,
) : BookingRepository by delegate {

    override suspend fun book(slot: Slot): String {
        val id = delegate.book(slot)
        quietly { onBooked(id, slot.startAtMillis) }
        return id
    }

    override suspend fun cancel(booking: Booking) {
        delegate.cancel(booking)
        quietly { onCancelled(booking.id) }
    }

    private suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The listener's next answer sets things right; the type only, no data.
            Log.w(TAG, "Reminder update failed: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "RemindingBookings"
    }
}
