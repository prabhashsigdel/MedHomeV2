package com.medhome.nepal.ui.admin

import com.medhome.nepal.domain.AdminBooking
import com.medhome.nepal.domain.AdminBookingFilter
import com.medhome.nepal.domain.AdminCancelledBy
import com.medhome.nepal.domain.AdminError
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.BookedDoctor
import com.medhome.nepal.domain.BookingStatus
import com.medhome.nepal.domain.Specialty
import com.medhome.nepal.fakes.FakeAdminRepository
import com.medhome.nepal.ui.common.OFFLINE_GRACE_MS
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminBookingsViewModelsTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val now = 1_000_000_000_000L
    private val clock = { now }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun <T> TestScope.collecting(flow: StateFlow<T>): StateFlow<T> {
        backgroundScope.launch(dispatcher) { flow.collect {} }
        return flow
    }

    private fun booking(
        id: String,
        hoursFromNow: Int,
        status: BookingStatus = BookingStatus.BOOKED,
        cancelledBy: AdminCancelledBy? = null,
        cancelledAtMillis: Long? = null,
        doctorName: String = "Asha Rai",
    ) = AdminBooking(
        bookingId = id,
        doctorId = "doc-001",
        doctor = BookedDoctor(doctorName, Specialty.CARDIOLOGY, "Valley Care Hospital", 800),
        startAtMillis = now + hoursFromNow * HOUR_MS,
        patientFirstName = "Sita",
        status = status,
        cancelledBy = cancelledBy,
        cancelledAtMillis = cancelledAtMillis,
    )

    private fun repository(vararg bookings: AdminBooking) = FakeAdminRepository().apply {
        allBookings.value = bookings.toList()
        now = clock
    }

    // Bookings tab

    @Test
    fun `upcoming is the default, soonest first, across doctors`() = runTest(dispatcher) {
        val later = booking("b2", 48, doctorName = "Bikash Thapa")
        val sooner = booking("b1", 2)
        val past = booking("b3", -2)
        val state = collecting(AdminBookingsViewModel(repository(later, sooner, past), clock).uiState)
        assertEquals(AdminBookingFilter.UPCOMING, state.value.filter)
        assertEquals(AdminBookingsStatus.READY, state.value.status)
        assertEquals(listOf(sooner, later), state.value.bookings)
        assertFalse(state.value.hasMore)
    }

    @Test
    fun `past and cancelled are latest first, and cancelled ones keep who and when`() = runTest(dispatcher) {
        val oldPast = booking("p1", -48)
        val newPast = booking("p2", -2)
        val byPatient = booking("c1", 24, BookingStatus.CANCELLED, AdminCancelledBy.PATIENT, cancelledAtMillis = now - HOUR_MS)
        val byOther = booking("c2", 48, BookingStatus.CANCELLED, AdminCancelledBy.ANOTHER_ADMIN, cancelledAtMillis = now)
        val legacy = booking("c3", -24, BookingStatus.CANCELLED, AdminCancelledBy.PATIENT, cancelledAtMillis = null)
        val viewModel = AdminBookingsViewModel(repository(oldPast, newPast, byPatient, byOther, legacy), clock)
        val state = collecting(viewModel.uiState)

        viewModel.selectFilter(AdminBookingFilter.PAST)
        assertEquals(listOf(newPast, oldPast), state.value.bookings)

        viewModel.selectFilter(AdminBookingFilter.CANCELLED)
        assertEquals(listOf(byOther, byPatient, legacy), state.value.bookings)
        assertEquals(AdminCancelledBy.ANOTHER_ADMIN, state.value.bookings[0].cancelledBy)
        assertNull(state.value.bookings[2].cancelledAtMillis)
    }

    @Test
    fun `each filter says when it is empty`() = runTest(dispatcher) {
        val viewModel = AdminBookingsViewModel(repository(booking("b1", 2)), clock)
        val state = collecting(viewModel.uiState)
        assertEquals(AdminBookingsStatus.READY, state.value.status)
        viewModel.selectFilter(AdminBookingFilter.CANCELLED)
        assertEquals(AdminBookingsStatus.EMPTY, state.value.status)
        assertEquals(AdminBookingFilter.CANCELLED, state.value.filter)
    }

    @Test
    fun `Show more asks for one more page, until there are no more`() = runTest(dispatcher) {
        val page = AdminBookingsViewModel.PAGE_SIZE
        val repository = repository(*Array(page + 5) { booking("b$it", it + 1) })
        val viewModel = AdminBookingsViewModel(repository, clock)
        val state = collecting(viewModel.uiState)
        assertEquals(page, state.value.bookings.size)
        assertTrue(state.value.hasMore)

        viewModel.showMore()
        assertEquals(page + 5, state.value.bookings.size)
        assertFalse(state.value.hasMore)
        assertFalse(state.value.loadingMore)
        assertEquals(listOf(page, 2 * page), repository.bookingLimits)

        // Nothing more to ask for.
        viewModel.showMore()
        assertEquals(2, repository.bookingLimits.size)
    }

    @Test
    fun `changing the filter starts again from one page`() = runTest(dispatcher) {
        val page = AdminBookingsViewModel.PAGE_SIZE
        val repository = repository(*Array(page + 1) { booking("b$it", it + 1) })
        val viewModel = AdminBookingsViewModel(repository, clock)
        collecting(viewModel.uiState)
        viewModel.showMore()
        viewModel.selectFilter(AdminBookingFilter.PAST)
        assertEquals(page, repository.bookingLimits.last())
    }

    @Test
    fun `a booking that has started since the list was read leaves Upcoming`() = runTest(dispatcher) {
        var time = now
        val soon = booking("b1", 1)
        val viewModel = AdminBookingsViewModel(repository(soon, booking("b2", 5)), { time })
        val state = collecting(viewModel.uiState)
        assertEquals(2, state.value.bookings.size)
        time = now + 2 * HOUR_MS
        viewModel.retry()
        assertEquals(listOf("b2"), state.value.bookings.map { it.bookingId })
    }

    @Test
    fun `a failure offers Retry, which reads again`() = runTest(dispatcher) {
        val repository = repository(booking("b1", 2)).apply { bookingsFailure = AuthError.NETWORK }
        val viewModel = AdminBookingsViewModel(repository, clock)
        val state = collecting(viewModel.uiState)
        assertEquals(AdminBookingsStatus.FAILED, state.value.status)
        repository.bookingsFailure = null
        viewModel.retry()
        assertEquals(AdminBookingsStatus.READY, state.value.status)
    }

    @Test
    fun `a list only from the cache says so after the grace period`() = runTest(dispatcher) {
        val repository = repository(booking("b1", 2)).apply { fromCache = true }
        val state = collecting(AdminBookingsViewModel(repository, clock).uiState)
        assertFalse(state.value.showingSaved)
        advanceTimeBy(OFFLINE_GRACE_MS + 1)
        assertTrue(state.value.showingSaved)
    }

    // One booking

    @Test
    fun `an upcoming booking can be cancelled, and then shows it was you`() = runTest(dispatcher) {
        val repository = repository(booking("b1", 2))
        val viewModel = AdminBookingViewModel("b1", repository, clock)
        val state = collecting(viewModel.uiState)
        val dialog = collecting(viewModel.cancelDialog)
        assertTrue((state.value as AdminBookingUiState.Ready).canCancel)

        viewModel.requestCancel()
        assertEquals("b1", dialog.value?.appointment?.bookingId)
        viewModel.confirmCancel()
        assertNull(dialog.value)
        assertEquals(listOf("b1"), repository.cancelledBookings)
        val after = (state.value as AdminBookingUiState.Ready)
        assertEquals(AdminCancelledBy.YOU, after.booking.cancelledBy)
        assertEquals(now, after.booking.cancelledAtMillis)
        assertFalse(after.canCancel)
    }

    @Test
    fun `past and cancelled bookings offer no cancel`() = runTest(dispatcher) {
        val repository = repository(
            booking("p1", -1),
            booking("c1", 5, BookingStatus.CANCELLED, AdminCancelledBy.PATIENT),
        )
        for (id in listOf("p1", "c1")) {
            val viewModel = AdminBookingViewModel(id, repository, clock)
            val state = collecting(viewModel.uiState)
            val dialog = collecting(viewModel.cancelDialog)
            assertFalse((state.value as AdminBookingUiState.Ready).canCancel)
            viewModel.requestCancel()
            assertNull(dialog.value)
        }
    }

    @Test
    fun `a failed cancel keeps the dialog with the reason, and it can't close while cancelling`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = repository(booking("b1", 2)).apply { this.gate = gate }
        val viewModel = AdminBookingViewModel("b1", repository, clock)
        collecting(viewModel.uiState)
        val dialog = collecting(viewModel.cancelDialog)
        viewModel.requestCancel()
        viewModel.confirmCancel()
        assertTrue(dialog.value?.isCancelling == true)
        viewModel.dismissCancel()
        assertTrue(dialog.value != null)

        repository.writeFailure = AdminError.BOOKING_STARTED
        gate.complete(Unit)
        assertEquals(AdminError.BOOKING_STARTED, dialog.value?.error)
        assertFalse(dialog.value?.isCancelling == true)
        viewModel.dismissCancel()
        assertNull(dialog.value)
    }

    @Test
    fun `a missing booking and a failed read are told apart`() = runTest(dispatcher) {
        val repository = repository()
        assertEquals(AdminBookingUiState.Missing, collecting(AdminBookingViewModel("nope", repository, clock).uiState).value)
        repository.bookingsFailure = AuthError.PERMISSION_DENIED
        assertEquals(
            AdminBookingUiState.Failed(AuthError.PERMISSION_DENIED),
            collecting(AdminBookingViewModel("b1", repository, clock).uiState).value,
        )
    }

    private companion object {
        const val HOUR_MS = 60 * 60 * 1000L
    }
}
