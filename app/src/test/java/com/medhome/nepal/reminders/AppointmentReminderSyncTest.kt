package com.medhome.nepal.reminders

import com.medhome.nepal.data.BookingRepository
import com.medhome.nepal.data.BookingsSnapshot
import com.medhome.nepal.data.RemindingBookingRepository
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.AuthException
import com.medhome.nepal.reminders.AppointmentReminderSync.Companion.FIRST_RETRY_MS
import com.medhome.nepal.reminders.AppointmentReminderSync.Companion.HEALTHY_MS
import com.medhome.nepal.reminders.AppointmentReminderSync.Companion.MAX_RETRY_MS
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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

    /**
     * Bookings whose listener fails its first [failures] times. Otherwise it answers once (from
     * the cache if [fromCache]), stays up [upMillis], then fails if [failAfterAnswer] or ends.
     */
    private fun TestScope.listener(
        attempts: MutableList<Long>,
        failures: Int = 0,
        fromCache: Boolean = false,
        upMillis: Long = 0,
        failAfterAnswer: Boolean = false,
    ): BookingRepository =
        object : BookingRepository by FakeBookingRepository() {
            override fun myBookings(): Flow<BookingsSnapshot> = flow {
                attempts += testScheduler.currentTime
                if (attempts.size <= failures) throw AuthException(AuthError.NETWORK)
                emit(BookingsSnapshot(emptyList(), fromCache))
                delay(upMillis)
                if (failAfterAnswer) throw AuthException(AuthError.NETWORK)
            }
        }

    private fun TestScope.sync(bookings: BookingRepository, applied: MutableList<String> = mutableListOf()) =
        AppointmentReminderSync(
            session = MutableStateFlow(signedIn(Role.PATIENT)),
            bookings = bookings,
            apply = { uid, _ -> applied += uid },
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            elapsedMillis = { testScheduler.currentTime },
        )

    private fun TestScope.follow(sync: AppointmentReminderSync) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sync.follow("uid") }

    @Test
    fun `a failed listener is retried after growing delays, which start over after a server answer`() = runTest {
        val attempts = mutableListOf<Long>()
        val applied = mutableListOf<String>()
        follow(sync(listener(attempts, failures = 2), applied))
        advanceTimeBy(20_001)
        // Fails at 0 and 5s, answers from the server at 15s and ends; started again 5s later.
        assertEquals(listOf(0L, FIRST_RETRY_MS, FIRST_RETRY_MS * 3, FIRST_RETRY_MS * 4), attempts)
        assertEquals(listOf("uid", "uid"), applied)
    }

    @Test
    fun `answers from the cache don't start the delays over`() = runTest {
        val attempts = mutableListOf<Long>()
        follow(sync(listener(attempts, fromCache = true, failAfterAnswer = true)))
        advanceTimeBy(80_000)
        assertEquals(listOf(5_000L, 10_000L, 20_000L, 40_000L), attempts.zipWithNext { a, b -> b - a })
    }

    @Test
    fun `a listener that stayed up a minute starts the delays over`() = runTest {
        val attempts = mutableListOf<Long>()
        follow(sync(listener(attempts, fromCache = true, upMillis = HEALTHY_MS, failAfterAnswer = true)))
        advanceTimeBy(2 * (HEALTHY_MS + FIRST_RETRY_MS) + 1)
        assertEquals(listOf(0L, HEALTHY_MS + FIRST_RETRY_MS, 2 * (HEALTHY_MS + FIRST_RETRY_MS)), attempts)
    }

    @Test
    fun `the retry delay stops growing at its cap`() = runTest {
        val attempts = mutableListOf<Long>()
        follow(sync(listener(attempts, failures = Int.MAX_VALUE)))
        advanceTimeBy(60 * 60_000L)
        val gaps = attempts.zipWithNext { a, b -> b - a }
        assertEquals(listOf(5_000L, 10_000L, 20_000L, 40_000L, 80_000L, 160_000L, MAX_RETRY_MS), gaps.take(7))
        assertTrue(gaps.drop(6).all { it == MAX_RETRY_MS })
    }

    @Test
    fun `retrying stops once the patient is no longer followed`() = runTest {
        val attempts = mutableListOf<Long>()
        val job = follow(sync(listener(attempts, failures = Int.MAX_VALUE)))
        advanceTimeBy(FIRST_RETRY_MS + 1)
        job.cancel()
        advanceTimeBy(60 * 60_000L)
        assertEquals(2, attempts.size)
    }

    @Test
    fun `the reminders are claimed for the patient before their bookings are followed, retrying a failed claim`() = runTest {
        val events = mutableListOf<String>()
        var claimFailures = 1
        val sync = AppointmentReminderSync(
            session = MutableStateFlow(signedIn(Role.PATIENT)),
            bookings = FakeBookingRepository(listOf(booking(id = "b1", startAtMillis = start))),
            apply = { uid, _ -> events += "apply:$uid" },
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            claimOwner = { uid ->
                events += "claim:$uid"
                if (claimFailures-- > 0) error("Disk full")
            },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sync.claimAndFollow("uid") }
        assertEquals(listOf("claim:uid"), events)
        advanceTimeBy(FIRST_RETRY_MS + 1)
        assertEquals(listOf("claim:uid", "claim:uid", "apply:uid"), events)
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
