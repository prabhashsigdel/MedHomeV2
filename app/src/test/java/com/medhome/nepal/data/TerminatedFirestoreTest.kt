package com.medhome.nepal.data

import com.google.firebase.firestore.FirebaseFirestore
import com.medhome.nepal.domain.AdminError
import com.medhome.nepal.domain.AdminException
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.AuthException
import com.medhome.nepal.domain.BookingError
import com.medhome.nepal.domain.BookingException
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.domain.Slot
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.fakes.booking
import com.medhome.nepal.fakes.doctor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

/**
 * After sign-out the Firestore instance is terminated: asking for it, or building a reference
 * from it, throws IllegalStateException. The repositories must report that as their own error
 * (never let it escape and crash a ViewModel's scope), and their flows must only touch Firestore
 * once collected.
 */
class TerminatedFirestoreTest {

    private var firestoreCalls = 0
    private val terminated: () -> FirebaseFirestore = {
        firestoreCalls++
        throw IllegalStateException("The client has already been terminated")
    }

    private val bookings = FirestoreBookingRepository(
        firestore = terminated,
        currentUid = { "uid" },
        verifiedClaim = { true },
        listeners = ListenerRegistry(),
    )

    private val admin = FirestoreAdminRepository(
        firestore = terminated,
        currentUid = { "admin" },
        listeners = ListenerRegistry(),
    )

    private val slot = Slot("doc-001", CalendarDate(2030, 1, 7), TimeOfDay(10 * 60))

    private suspend fun expectBooking(expected: BookingError, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected $expected")
        } catch (e: BookingException) {
            assertEquals(expected, e.error)
        }
    }

    private suspend fun expectAdmin(expected: AdminError, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected $expected")
        } catch (e: AdminException) {
            assertEquals(expected, e.error)
        }
    }

    private suspend fun expectFlowFailure(flow: Flow<*>) {
        try {
            flow.collect()
            fail("Expected the flow to fail")
        } catch (e: AuthException) {
            assertEquals(AuthError.UNKNOWN, e.error)
        }
    }

    // Booking

    @Test
    fun `booking with a terminated instance fails as UNKNOWN`() = runTest {
        expectBooking(BookingError.UNKNOWN) { bookings.book(slot) }
    }

    @Test
    fun `cancelling with a terminated instance fails as UNKNOWN`() = runTest {
        expectBooking(BookingError.UNKNOWN) { bookings.cancel(booking(id = "b1", startAtMillis = slot.startAtMillis)) }
    }

    @Test
    fun `cancelling every upcoming booking with a terminated instance fails as UNKNOWN`() = runTest {
        expectBooking(BookingError.UNKNOWN) { bookings.cancelAllUpcoming() }
    }

    @Test
    fun `a failing auth lookup fails a booking or a cancel as UNKNOWN`() = runTest {
        val broken = FirestoreBookingRepository(
            firestore = terminated,
            currentUid = { throw IllegalStateException("No Firebase app") },
            verifiedClaim = { true },
            listeners = ListenerRegistry(),
        )
        expectBooking(BookingError.UNKNOWN) { broken.book(slot) }
        expectBooking(BookingError.UNKNOWN) { broken.cancel(booking(id = "b1", startAtMillis = slot.startAtMillis)) }
        expectBooking(BookingError.UNKNOWN) { broken.cancelAllUpcoming() }
    }

    @Test
    fun `an invalid booking ID is still not found`() = runTest {
        expectBooking(BookingError.NOT_FOUND) { bookings.cancel(booking(id = "bad/id", startAtMillis = slot.startAtMillis)) }
    }

    @Test
    fun `booking flows touch Firestore only when collected, and then fail as an AuthException`() = runTest {
        val mine = bookings.myBookings()
        val taken = bookings.takenSlotIds("doc-001", 0L..1L)
        assertEquals(0, firestoreCalls)
        expectFlowFailure(mine)
        expectFlowFailure(taken)
        assertEquals(2, firestoreCalls)
    }

    // Admin

    @Test
    fun `admin writes with a terminated instance fail as UNKNOWN`() = runTest {
        val asha = doctor(id = "doc-001")
        expectAdmin(AdminError.UNKNOWN) { admin.cancelBooking("b1") }
        expectAdmin(AdminError.UNKNOWN) { admin.createDoctor(asha) }
        expectAdmin(AdminError.UNKNOWN) { admin.updateDoctor(asha) }
        expectAdmin(AdminError.UNKNOWN) { admin.setActive("doc-001", active = false) }
        expectAdmin(AdminError.UNKNOWN) { admin.upcomingBookingPage("doc-001", afterMillis = null) }
        expectAdmin(AdminError.UNKNOWN) { admin.upcomingCount("doc-001") }
    }

    @Test
    fun `admin input checks still come first`() = runTest {
        expectAdmin(AdminError.NOT_FOUND) { admin.cancelBooking("bad/id") }
        expectAdmin(AdminError.NOT_FOUND) { admin.setActive("doc_001", active = false) }
        val signedOut = FirestoreAdminRepository(terminated, currentUid = { null }, listeners = ListenerRegistry())
        expectAdmin(AdminError.PERMISSION_DENIED) { signedOut.cancelBooking("b1") }
    }

    @Test
    fun `admin flows touch Firestore only when collected, and then fail as an AuthException`() = runTest {
        val all = admin.allDoctors()
        val one = admin.doctor("doc-001")
        val upcoming = admin.upcomingAppointments("doc-001")
        assertEquals(0, firestoreCalls)
        expectFlowFailure(all)
        expectFlowFailure(one)
        expectFlowFailure(upcoming)
        assertEquals(3, firestoreCalls)
    }

    // A listener that can't be added

    @Test
    fun `a listener that fails to be added ends its flow with an AuthException, not an IllegalStateException`() = runTest {
        val listeners = ListenerRegistry()
        val flow = snapshotFlow<Any, Unit>(
            listeners = listeners,
            // The query or reference built fine; adding the listener to a terminated instance throws.
            attach = { throw IllegalStateException("The client has already been terminated") },
        ) { _, _ -> }
        expectFlowFailure(flow)
        assertEquals(0, listeners.openCount)
    }

    // Cancellation is never mapped to an error

    private val cancelled = CancellationException("Screen left")

    private suspend fun expectCancellation(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected the cancellation to propagate")
        } catch (e: CancellationException) {
            assertSame(cancelled, e)
        }
    }

    @Test
    fun `a cancellation from getting Firestore or the uid propagates through book, cancel and admin writes`() = runTest {
        val cancelledFirestore = FirestoreBookingRepository(
            firestore = { throw cancelled },
            currentUid = { "uid" },
            verifiedClaim = { true },
            listeners = ListenerRegistry(),
        )
        val cancelledUid = FirestoreBookingRepository(
            firestore = terminated,
            currentUid = { throw cancelled },
            verifiedClaim = { true },
            listeners = ListenerRegistry(),
        )
        val booked = booking(id = "b1", startAtMillis = slot.startAtMillis)
        expectCancellation { cancelledFirestore.book(slot) }
        expectCancellation { cancelledFirestore.cancel(booked) }
        expectCancellation { cancelledUid.book(slot) }
        expectCancellation { cancelledUid.cancel(booked) }
        expectCancellation { cancelledUid.cancelAllUpcoming() }

        val adminCancelled = FirestoreAdminRepository({ throw cancelled }, currentUid = { "admin" }, listeners = ListenerRegistry())
        val adminUidCancelled = FirestoreAdminRepository(terminated, currentUid = { throw cancelled }, listeners = ListenerRegistry())
        expectCancellation { adminCancelled.cancelBooking("b1") }
        expectCancellation { adminCancelled.setActive("doc-001", active = false) }
        expectCancellation { adminCancelled.upcomingCount("doc-001") }
        expectCancellation { adminUidCancelled.createDoctor(doctor(id = "doc-001")) }
    }

    @Test
    fun `mapErrors lets a cancellation through unchanged`() = runTest {
        expectCancellation { mapErrors { throw cancelled } }
    }
}
