package com.medhome.nepal.reminders

import com.medhome.nepal.data.RemindingBookingRepository
import com.medhome.nepal.domain.BookingStatus
import com.medhome.nepal.domain.NepalTime
import com.medhome.nepal.domain.Role
import com.medhome.nepal.domain.Slot
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.domain.UserProfile
import com.medhome.nepal.fakes.FakeBookingRepository
import com.medhome.nepal.fakes.booking
import com.medhome.nepal.fakes.doctor
import com.medhome.nepal.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppointmentReminderSyncTest {

    private val start = System.currentTimeMillis() + 2 * 24 * 60 * 60 * 1000L

    private fun signedIn(role: Role) = SessionState.SignedIn(UserProfile("uid", "Asha Rai", "a@example.com", role), usesPassword = true)

    @Test
    fun `every answer of the bookings listener is applied for the patient`() = runTest {
        val bookings = FakeBookingRepository(listOf(booking(id = "b1", startAtMillis = start)))
        val applied = mutableListOf<Pair<String, List<BookingStatus>>>()
        val sync = AppointmentReminderSync(
            session = MutableStateFlow(signedIn(Role.PATIENT)),
            bookings = bookings,
            apply = { uid, snapshot -> applied += uid to snapshot.bookings.map { it.status } },
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sync.follow("uid") }
        bookings.cancelByClinic("b1")
        job.cancel()
        assertEquals(
            listOf("uid" to listOf(BookingStatus.BOOKED), "uid" to listOf(BookingStatus.CANCELLED)),
            applied,
        )
    }

    @Test
    fun `only a signed-in patient is followed`() = runTest {
        val session = MutableStateFlow<SessionState>(SessionState.Loading)
        val sync = AppointmentReminderSync(session, FakeBookingRepository(), { _, _ -> }, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val seen = mutableListOf<String?>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sync.patientUid().toList(seen) }
        session.value = signedIn(Role.ADMIN)
        session.value = signedIn(Role.PATIENT)
        session.value = SessionState.SignedOut()
        job.cancel()
        assertEquals(listOf(null, "uid", null), seen)
    }

    @Test
    fun `booking and cancelling here update the reminders at once, and their failure is ignored`() = runTest {
        val bookings = FakeBookingRepository()
        val events = mutableListOf<String>()
        var fail = false
        val repo = RemindingBookingRepository(
            delegate = bookings,
            onBooked = { id, at -> if (fail) error("Disk full") else events += "booked:$id:$at" },
            onCancelled = { id -> events += "cancelled:$id" },
        )
        val asha = doctor()
        val slot = Slot(asha.id, NepalTime.dateOf(start), TimeOfDay(10 * 60))
        val id = repo.book(slot)
        assertEquals(listOf("booked:$id:${slot.startAtMillis}"), events)

        fail = true
        repo.book(slot.copy(start = TimeOfDay(11 * 60)))
        assertEquals(2, bookings.booked.size)
    }
}
