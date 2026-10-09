package com.medhome.nepal.ui.booking

import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.BookingError
import com.medhome.nepal.domain.BookingStatus
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.domain.CancelledBy
import com.medhome.nepal.domain.NepalTime
import com.medhome.nepal.domain.Slot
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.domain.TimeRange
import com.medhome.nepal.domain.Weekday
import com.medhome.nepal.fakes.FakeBookingRepository
import com.medhome.nepal.fakes.FakeDoctorRepository
import com.medhome.nepal.fakes.booking
import com.medhome.nepal.fakes.doctor
import com.medhome.nepal.ui.common.TICK_MS
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
class BookingViewModelsTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private fun t(text: String) = requireNotNull(TimeOfDay.parse(text))
    private fun nepal(day: Int, time: String) = NepalTime.epochMillis(CalendarDate(2026, 10, day), t(time))

    /** Thursday 8 October 2026, 09:00 in Kathmandu. Tests move it forward. */
    private var now = nepal(8, "09:00")
    private val clock = { now }

    /** Thursdays 09:00-11:00 and Fridays 16:00-17:00, 30-minute slots. */
    private val asha = doctor(id = "doc-001").copy(
        slotMinutes = 30,
        weeklySchedule = mapOf(
            Weekday.THURSDAY to listOf(TimeRange(t("09:00"), t("11:00"))),
            Weekday.FRIDAY to listOf(TimeRange(t("16:00"), t("17:00"))),
        ),
    )
    private val thursday = CalendarDate(2026, 10, 8)
    private val friday = CalendarDate(2026, 10, 9)

    private val doctors = FakeDoctorRepository(listOf(asha))
    private val bookings = FakeBookingRepository(clock = clock)
    private var verificationRequests = 0

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun <T> TestScope.collecting(flow: StateFlow<T>): StateFlow<T> {
        backgroundScope.launch(dispatcher) { flow.collect {} }
        return flow
    }

    private fun bookViewModel() = BookAppointmentViewModel(
        doctorId = asha.id,
        bookings = bookings,
        doctors = doctors,
        clock = clock,
        requestEmailVerification = { verificationRequests++ },
    )

    private fun BookAppointmentUiState.day(date: CalendarDate) =
        (slots as SlotsState.Ready).days.first { it.date == date }.slots.map { it.start }

    // Booking

    @Test
    fun `slots are ready with the first day that has free times selected`() = runTest(dispatcher) {
        val state = collecting(bookViewModel().uiState)
        assertTrue(state.value.slots is SlotsState.Ready)
        // 09:00 and 09:30 are within the hour.
        assertEquals(listOf(t("10:00"), t("10:30")), state.value.day(thursday))
        assertEquals(thursday, state.value.selectedDate)
    }

    @Test
    fun `a day without free times is skipped when choosing the default day`() = runTest(dispatcher) {
        now = nepal(8, "10:01")
        val viewModel = bookViewModel()
        val state = collecting(viewModel.uiState)
        assertEquals(friday, state.value.selectedDate)

        // Choosing such a day explicitly keeps it (the screen says it has no free times).
        viewModel.selectDate(thursday)
        assertEquals(thursday, state.value.selectedDate)
        assertTrue(state.value.day(thursday).isEmpty())
    }

    @Test
    fun `the lead time moves on as the clock does`() = runTest(dispatcher) {
        val state = collecting(bookViewModel().uiState)
        assertEquals(listOf(t("10:00"), t("10:30")), state.value.day(thursday))
        now = nepal(8, "09:01")
        advanceTimeBy(TICK_MS + 1)
        assertEquals(listOf(t("10:30")), state.value.day(thursday))
    }

    @Test
    fun `locked slots are hidden as soon as the listener reports them`() = runTest(dispatcher) {
        val state = collecting(bookViewModel().uiState)
        bookings.taken.value = setOf(Slot(asha.id, friday, t("16:00")).id)
        assertEquals(listOf(t("16:30")), state.value.day(friday))
    }

    @Test
    fun `picking a slot and confirming books it once`() = runTest(dispatcher) {
        val viewModel = bookViewModel()
        val state = collecting(viewModel.uiState)
        val slot = Slot(asha.id, friday, t("16:00"))
        viewModel.selectSlot(slot)
        assertEquals(slot, state.value.pendingSlot)

        val gate = CompletableDeferred<Unit>().also { bookings.gate = it }
        viewModel.confirm()
        assertTrue(state.value.submitting)
        viewModel.confirm()
        viewModel.dismissConfirm() // ignored while sending
        assertEquals(slot, state.value.pendingSlot)
        gate.complete(Unit)

        assertEquals(listOf(slot), bookings.booked)
        assertEquals(slot, state.value.booked)
        assertNull(state.value.pendingSlot)
        assertFalse(state.value.submitting)
    }

    @Test
    fun `a slot taken meanwhile closes the sheet, explains, and disappears`() = runTest(dispatcher) {
        val viewModel = bookViewModel()
        val state = collecting(viewModel.uiState)
        val slot = Slot(asha.id, friday, t("16:00"))
        bookings.bookFailure = BookingError.SLOT_TAKEN
        viewModel.selectSlot(slot)
        viewModel.confirm()

        assertNull(state.value.pendingSlot)
        assertNull(state.value.booked)
        assertEquals(BookingError.SLOT_TAKEN, state.value.error)
        assertEquals(listOf(t("16:30")), state.value.day(friday))

        viewModel.dismissError()
        assertNull(state.value.error)
    }

    @Test
    fun `a refused slot closes the sheet but stays offered (the refusal may not mean taken)`() = runTest(dispatcher) {
        val viewModel = bookViewModel()
        val state = collecting(viewModel.uiState)
        bookings.bookFailure = BookingError.SLOT_UNAVAILABLE
        viewModel.selectSlot(Slot(asha.id, friday, t("16:00")))
        viewModel.confirm()
        assertNull(state.value.pendingSlot)
        assertEquals(BookingError.SLOT_UNAVAILABLE, state.value.error)
        assertEquals(listOf(t("16:00"), t("16:30")), state.value.day(friday))
    }

    @Test
    fun `the success state keeps its doctor if the slots stop being ready`() = runTest(dispatcher) {
        val viewModel = bookViewModel()
        val state = collecting(viewModel.uiState)
        viewModel.selectSlot(Slot(asha.id, friday, t("16:00")))
        viewModel.confirm()
        doctors.snapshot.value = doctors.snapshot.value?.copy(doctors = emptyList())
        assertTrue(state.value.slots is SlotsState.DoctorUnavailable)
        assertEquals(asha, state.value.bookedDoctor)
    }

    @Test
    fun `a network failure keeps the sheet open to try again`() = runTest(dispatcher) {
        val viewModel = bookViewModel()
        val state = collecting(viewModel.uiState)
        val slot = Slot(asha.id, friday, t("16:00"))
        bookings.bookFailure = BookingError.NETWORK
        viewModel.selectSlot(slot)
        viewModel.confirm()
        assertEquals(slot, state.value.pendingSlot)
        assertEquals(BookingError.NETWORK, state.value.error)

        bookings.bookFailure = null
        viewModel.confirm()
        assertEquals(slot, state.value.booked)
        assertNull(state.value.error)
    }

    @Test
    fun `the limit of upcoming bookings keeps the sheet open with the reason`() = runTest(dispatcher) {
        val viewModel = bookViewModel()
        val state = collecting(viewModel.uiState)
        bookings.bookFailure = BookingError.LIMIT_REACHED
        viewModel.selectSlot(Slot(asha.id, friday, t("16:00")))
        viewModel.confirm()
        assertEquals(BookingError.LIMIT_REACHED, state.value.error)
        assertTrue(state.value.pendingSlot != null)
    }

    @Test
    fun `an unverified email shows the explanation and links to verification`() = runTest(dispatcher) {
        bookings.verified = false
        val viewModel = bookViewModel()
        val state = collecting(viewModel.uiState)
        assertEquals(SlotsState.NeedsVerification, state.value.slots)
        viewModel.verifyEmail()
        assertEquals(1, verificationRequests)
    }

    @Test
    fun `a booking refused for verification switches to the explanation`() = runTest(dispatcher) {
        val viewModel = bookViewModel()
        val state = collecting(viewModel.uiState)
        bookings.bookFailure = BookingError.EMAIL_NOT_VERIFIED
        viewModel.selectSlot(Slot(asha.id, friday, t("16:00")))
        viewModel.confirm()
        assertEquals(SlotsState.NeedsVerification, state.value.slots)
        assertNull(state.value.pendingSlot)
    }

    @Test
    fun `an unavailable doctor and a failed read have their own states`() = runTest(dispatcher) {
        doctors.snapshot.value = doctors.snapshot.value?.copy(doctors = emptyList())
        val state = collecting(bookViewModel().uiState)
        assertEquals(SlotsState.DoctorUnavailable(offline = false), state.value.slots)

        doctors.failure = AuthError.PERMISSION_DENIED
        val failed = collecting(bookViewModel().uiState)
        assertEquals(SlotsState.Failed, failed.value.slots)
    }

    // Bookings tab

    @Test
    fun `upcoming soonest first, past and cancelled latest first`() = runTest(dispatcher) {
        bookings.bookings.value = listOf(
            booking(id = "late", startAtMillis = nepal(15, "10:00")),
            booking(id = "soon", startAtMillis = nepal(9, "16:00")),
            booking(id = "old", startAtMillis = nepal(1, "10:00")),
            booking(id = "older", startAtMillis = nepal(1, "09:00")),
            booking(id = "cancelled", startAtMillis = nepal(10, "10:00"), status = BookingStatus.CANCELLED),
        )
        val state = collecting(BookingsViewModel(bookings, clock).uiState)
        val ready = state.value as BookingsUiState.Ready
        assertEquals(listOf("soon", "late"), ready.upcoming.map { it.id })
        assertEquals(listOf("cancelled", "old", "older"), ready.past.map { it.id })
    }

    @Test
    fun `a booking moves to Past once it starts`() = runTest(dispatcher) {
        bookings.bookings.value = listOf(booking(id = "b", startAtMillis = nepal(8, "09:30")))
        val state = collecting(BookingsViewModel(bookings, clock).uiState)
        assertEquals(1, (state.value as BookingsUiState.Ready).upcoming.size)
        now = nepal(8, "09:30")
        advanceTimeBy(TICK_MS + 1)
        assertEquals(listOf("b"), (state.value as BookingsUiState.Ready).past.map { it.id })
    }

    @Test
    fun `a failed read shows its own error and retry recovers`() = runTest(dispatcher) {
        bookings.listFailure = AuthError.PERMISSION_DENIED
        val viewModel = BookingsViewModel(bookings, clock)
        val state = collecting(viewModel.uiState)
        assertEquals(BookingsUiState.Failed, state.value)
        bookings.listFailure = null
        viewModel.retry()
        assertTrue(state.value is BookingsUiState.Ready)
    }

    // Booking detail and cancelling

    @Test
    fun `cancelling asks first, then cancels and shows the booking as cancelled`() = runTest(dispatcher) {
        bookings.bookings.value = listOf(booking(id = "b", startAtMillis = nepal(9, "16:00")))
        val viewModel = BookingDetailViewModel("b", bookings, clock)
        val state = collecting(viewModel.uiState)
        assertTrue((state.value as BookingDetailUiState.Ready).canCancel)

        viewModel.askToCancel()
        assertTrue((state.value as BookingDetailUiState.Ready).confirmingCancel)
        viewModel.dismissCancel()
        assertFalse((state.value as BookingDetailUiState.Ready).confirmingCancel)
        assertTrue(bookings.cancelled.isEmpty())

        viewModel.askToCancel()
        viewModel.confirmCancel()
        val after = state.value as BookingDetailUiState.Ready
        assertEquals(listOf("b"), bookings.cancelled)
        assertEquals(BookingStatus.CANCELLED, after.booking.status)
        assertFalse(after.canCancel)
        // Hidden even before the listener reports the cancel.
        viewModel.askToCancel()
        assertFalse((state.value as BookingDetailUiState.Ready).confirmingCancel)
        assertFalse(after.confirmingCancel)
        assertNull(after.cancelError)
    }

    @Test
    fun `a booking that has started cannot be cancelled`() = runTest(dispatcher) {
        bookings.bookings.value = listOf(booking(id = "b", startAtMillis = nepal(8, "09:30")))
        val viewModel = BookingDetailViewModel("b", bookings, clock)
        val state = collecting(viewModel.uiState)
        viewModel.askToCancel()
        now = nepal(8, "09:30")
        advanceTimeBy(TICK_MS + 1)
        val ready = state.value as BookingDetailUiState.Ready
        assertFalse(ready.canCancel)
        assertFalse(ready.confirmingCancel)
    }

    @Test
    fun `a refused cancel shows why and the booking stays`() = runTest(dispatcher) {
        bookings.bookings.value = listOf(booking(id = "b", startAtMillis = nepal(9, "16:00")))
        bookings.cancelFailure = BookingError.ALREADY_STARTED
        val viewModel = BookingDetailViewModel("b", bookings, clock)
        val state = collecting(viewModel.uiState)
        viewModel.askToCancel()
        viewModel.confirmCancel()
        val ready = state.value as BookingDetailUiState.Ready
        assertEquals(BookingError.ALREADY_STARTED, ready.cancelError)
        assertEquals(BookingStatus.BOOKED, ready.booking.status)
    }

    @Test
    fun `the patient's own cancel is recorded as theirs`() = runTest(dispatcher) {
        bookings.bookings.value = listOf(booking(id = "b", startAtMillis = nepal(9, "16:00")))
        val viewModel = BookingDetailViewModel("b", bookings, clock)
        val state = collecting(viewModel.uiState)
        viewModel.askToCancel()
        viewModel.confirmCancel()
        assertEquals(CancelledBy.PATIENT, (state.value as BookingDetailUiState.Ready).booking.cancelledBy)
    }

    @Test
    fun `a booking the clinic cancels shows as cancelled by the clinic, live, without Cancel`() = runTest(dispatcher) {
        bookings.bookings.value = listOf(booking(id = "b", startAtMillis = nepal(9, "16:00")))
        val viewModel = BookingDetailViewModel("b", bookings, clock)
        val state = collecting(viewModel.uiState)
        viewModel.askToCancel()

        bookings.cancelByClinic("b")
        val ready = state.value as BookingDetailUiState.Ready
        assertEquals(BookingStatus.CANCELLED, ready.booking.status)
        assertEquals(CancelledBy.CLINIC, ready.booking.cancelledBy)
        assertFalse(ready.canCancel)
        // The open confirm dialog closes: there is nothing left to cancel.
        assertFalse(ready.confirmingCancel)
        assertTrue(bookings.cancelled.isEmpty())
    }

    @Test
    fun `a clinic cancel moves the booking from Upcoming to Past`() = runTest(dispatcher) {
        bookings.bookings.value = listOf(booking(id = "b", startAtMillis = nepal(9, "16:00")))
        val state = collecting(BookingsViewModel(bookings, clock).uiState)
        bookings.cancelByClinic("b")
        val ready = state.value as BookingsUiState.Ready
        assertTrue(ready.upcoming.isEmpty())
        assertEquals(listOf(CancelledBy.CLINIC), ready.past.map { it.cancelledBy })
    }

    @Test
    fun `an unknown booking is not found`() = runTest(dispatcher) {
        val state = collecting(BookingDetailViewModel("nope", bookings, clock).uiState)
        assertEquals(BookingDetailUiState.NotFound, state.value)
    }

    // Home

    @Test
    fun `Home shows the soonest upcoming booking, or none`() = runTest(dispatcher) {
        bookings.bookings.value = listOf(
            booking(id = "late", startAtMillis = nepal(15, "10:00")),
            booking(id = "soon", startAtMillis = nepal(9, "16:00")),
            booking(id = "cancelled", startAtMillis = nepal(8, "12:00"), status = BookingStatus.CANCELLED),
        )
        val state = collecting(NextAppointmentViewModel(bookings, clock).state)
        assertEquals("soon", (state.value as NextAppointment.Upcoming).booking.id)

        bookings.bookings.value = listOf(booking(id = "old", startAtMillis = nepal(1, "10:00")))
        assertEquals(NextAppointment.None, state.value)
    }

    @Test
    fun `Home's next appointment moves on when the clinic cancels it`() = runTest(dispatcher) {
        bookings.bookings.value = listOf(
            booking(id = "late", startAtMillis = nepal(15, "10:00")),
            booking(id = "soon", startAtMillis = nepal(9, "16:00")),
        )
        val state = collecting(NextAppointmentViewModel(bookings, clock).state)
        bookings.cancelByClinic("soon")
        assertEquals("late", (state.value as NextAppointment.Upcoming).booking.id)
        bookings.cancelByClinic("late")
        assertEquals(NextAppointment.None, state.value)
    }
}
