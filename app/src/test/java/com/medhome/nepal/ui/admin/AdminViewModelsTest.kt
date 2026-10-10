package com.medhome.nepal.ui.admin

import com.medhome.nepal.R
import com.medhome.nepal.data.AdminRepository
import com.medhome.nepal.data.UpcomingPage
import com.medhome.nepal.domain.AdminError
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.ClinicCancel
import com.medhome.nepal.domain.DoctorAppointment
import com.medhome.nepal.domain.ManagedDoctor
import com.medhome.nepal.domain.Specialty
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.domain.Weekday
import com.medhome.nepal.fakes.FakeAdminRepository
import com.medhome.nepal.fakes.doctor
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
class AdminViewModelsTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val asha = ManagedDoctor(doctor(id = "doc-001", name = "Asha Rai", hospital = "Valley Care Hospital"), active = true)
    private val bikash = ManagedDoctor(
        doctor(id = "doc-002", name = "Bikash Thapa", specialty = Specialty.DERMATOLOGY, hospital = "Blue Pine Clinic"),
        active = false,
    )
    private val now = 1_000_000_000_000L

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun <T> TestScope.collecting(flow: StateFlow<T>): StateFlow<T> {
        backgroundScope.launch(dispatcher) { flow.collect {} }
        return flow
    }

    // Doctors list

    @Test
    fun `the list shows active and inactive doctors`() = runTest(dispatcher) {
        val state = collecting(AdminDoctorListViewModel(FakeAdminRepository(listOf(asha, bikash))).uiState)
        assertEquals(AdminListStatus.READY, state.value.status)
        assertEquals(listOf(asha, bikash), state.value.doctors)
    }

    @Test
    fun `search matches words of the name or hospital in any order`() = runTest(dispatcher) {
        val viewModel = AdminDoctorListViewModel(FakeAdminRepository(listOf(asha, bikash)))
        val state = collecting(viewModel.uiState)
        viewModel.onQueryChange("  rai  ASHA ")
        assertEquals(listOf(asha), state.value.doctors)
        viewModel.onQueryChange("pine")
        assertEquals(listOf(bikash), state.value.doctors)
        viewModel.onQueryChange("nobody")
        assertEquals(AdminListStatus.NO_MATCHES, state.value.status)
        viewModel.onQueryChange("")
        assertEquals(2, state.value.doctors.size)
    }

    @Test
    fun `the search text is capped`() {
        val viewModel = AdminDoctorListViewModel(FakeAdminRepository())
        viewModel.onQueryChange("x".repeat(100))
        assertEquals(AdminDoctorListViewModel.MAX_SEARCH_LENGTH, viewModel.query.length)
    }

    @Test
    fun `no doctors, offline and failures are told apart`() = runTest(dispatcher) {
        assertEquals(AdminListStatus.NO_DOCTORS, collecting(AdminDoctorListViewModel(FakeAdminRepository()).uiState).value.status)

        val offline = collecting(AdminDoctorListViewModel(FakeAdminRepository().apply { fromCache = true }).uiState)
        advanceTimeBy(OFFLINE_GRACE_MS + 1)
        assertEquals(AdminListStatus.OFFLINE, offline.value.status)

        val repository = FakeAdminRepository(listOf(asha)).apply { listFailure = AuthError.PERMISSION_DENIED }
        val viewModel = AdminDoctorListViewModel(repository)
        val failed = collecting(viewModel.uiState)
        assertEquals(AdminListStatus.FAILED, failed.value.status)
        repository.listFailure = null
        viewModel.retry()
        assertEquals(AdminListStatus.READY, failed.value.status)
    }

    // One doctor

    private fun appointment(id: String, inMinutes: Long, name: String?) =
        DoctorAppointment(id, now + inMinutes * 60_000, name)

    @Test
    fun `a doctor shows with their upcoming bookings only`() = runTest(dispatcher) {
        val repository = FakeAdminRepository(listOf(asha)).apply {
            appointments.value = mapOf("doc-001" to listOf(appointment("past", -5, "Old"), appointment("b1", 60, "Sita"), appointment("b2", 120, null)))
        }
        val viewModel = AdminDoctorViewModel("doc-001", repository) { now }
        assertEquals(AdminDoctorUiState.Ready(asha, showingSaved = false), collecting(viewModel.uiState).value)
        val appointments = collecting(viewModel.appointments).value as AppointmentsUiState.Ready
        assertEquals(listOf("b1", "b2"), appointments.appointments.map { it.bookingId })
        assertEquals(listOf("Sita", null), appointments.appointments.map { it.patientFirstName })
    }

    @Test
    fun `a missing doctor is not shown`() = runTest(dispatcher) {
        val state = collecting(AdminDoctorViewModel("doc-404", FakeAdminRepository(listOf(asha))) { now }.uiState)
        assertEquals(AdminDoctorUiState.Missing(offline = false), state.value)
    }

    @Test
    fun `hiding a doctor asks first, counting their upcoming bookings`() = runTest(dispatcher) {
        val repository = FakeAdminRepository(listOf(asha)).apply {
            appointments.value = mapOf("doc-001" to listOf(appointment("b1", 60, "Sita"), appointment("b2", 90, "Ram")))
        }
        val viewModel = AdminDoctorViewModel("doc-001", repository) { now }
        val state = collecting(viewModel.uiState)
        val dialog = collecting(viewModel.dialog)

        viewModel.requestToggle()
        assertEquals(ActiveDialogState(activate = false, upcomingCount = 2), dialog.value)
        assertTrue(repository.activeChanges.isEmpty())

        viewModel.confirmToggle()
        assertEquals(listOf("doc-001" to false), repository.activeChanges)
        assertNull(dialog.value)
        assertFalse((state.value as AdminDoctorUiState.Ready).doctor.active)
        // Bookings are left alone.
        assertEquals(2, repository.appointments.value.getValue("doc-001").size)
    }

    @Test
    fun `showing an inactive doctor again works the same way`() = runTest(dispatcher) {
        val repository = FakeAdminRepository(listOf(bikash))
        val viewModel = AdminDoctorViewModel("doc-002", repository) { now }
        collecting(viewModel.uiState)
        viewModel.requestToggle()
        assertTrue(requireNotNull(viewModel.dialog.value).activate)
        viewModel.confirmToggle()
        assertEquals(listOf("doc-002" to true), repository.activeChanges)
    }

    @Test
    fun `a failed count still lets the admin decide`() = runTest(dispatcher) {
        val repository = FakeAdminRepository(listOf(asha)).apply { countFailure = AdminError.NETWORK }
        val viewModel = AdminDoctorViewModel("doc-001", repository) { now }
        collecting(viewModel.uiState)
        viewModel.requestToggle()
        assertEquals(ActiveDialogState(activate = false, upcomingCount = null, countFailed = true), viewModel.dialog.value)
    }

    @Test
    fun `a failed change keeps the dialog open with the error, and it can't be closed while saving`() = runTest(dispatcher) {
        val repository = FakeAdminRepository(listOf(asha)).apply { writeFailure = AdminError.NETWORK }
        val viewModel = AdminDoctorViewModel("doc-001", repository) { now }
        collecting(viewModel.uiState)
        viewModel.requestToggle()
        viewModel.confirmToggle()
        assertEquals(AdminError.NETWORK, viewModel.dialog.value?.error)
        assertFalse(requireNotNull(viewModel.dialog.value).isSaving)

        repository.writeFailure = null
        val gate = CompletableDeferred<Unit>().also { repository.gate = it }
        viewModel.confirmToggle()
        assertTrue(requireNotNull(viewModel.dialog.value).isSaving)
        viewModel.dismissDialog()
        assertTrue(viewModel.dialog.value != null)
        gate.complete(Unit)
        assertNull(viewModel.dialog.value)
    }

    @Test
    fun `cancel closes the dialog without a change`() = runTest(dispatcher) {
        val repository = FakeAdminRepository(listOf(asha))
        val viewModel = AdminDoctorViewModel("doc-001", repository) { now }
        collecting(viewModel.uiState)
        viewModel.requestToggle()
        viewModel.dismissDialog()
        assertNull(viewModel.dialog.value)
        assertTrue(repository.activeChanges.isEmpty())
    }

    // Cancelling bookings for the clinic

    private fun withBookings(vararg ids: String) = FakeAdminRepository(listOf(asha)).apply {
        appointments.value = mapOf("doc-001" to ids.mapIndexed { i, id -> appointment(id, 60L + i * 15, "P$i") })
    }

    /** The bookings still booked (cancelled ones stay listed, as cancelled). */
    private fun FakeAdminRepository.upcomingIds() = booked("doc-001").map { it.bookingId }

    @Test
    fun `cancelling one booking asks first, then cancels it`() = runTest(dispatcher) {
        val repository = withBookings("b1", "b2")
        val viewModel = AdminDoctorViewModel("doc-001", repository) { now }
        collecting(viewModel.uiState)
        val appointments = collecting(viewModel.appointments)
        val target = (appointments.value as AppointmentsUiState.Ready).appointments.first()

        viewModel.requestCancel(target)
        assertEquals(CancelBookingDialogState(target), viewModel.cancelDialog.value)
        viewModel.dismissCancel()
        assertNull(viewModel.cancelDialog.value)
        assertTrue(repository.cancelledBookings.isEmpty())

        viewModel.requestCancel(target)
        viewModel.confirmCancel()
        assertEquals(listOf("b1"), repository.cancelledBookings)
        assertNull(viewModel.cancelDialog.value)
        // Still listed, now as cancelled by this admin; the other stays booked.
        val after = (appointments.value as AppointmentsUiState.Ready).appointments
        assertEquals(listOf("b1" to ClinicCancel.BY_YOU, "b2" to null), after.map { it.bookingId to it.clinicCancel })
    }

    @Test
    fun `a booking the clinic already cancelled can't be cancelled again`() = runTest(dispatcher) {
        val repository = FakeAdminRepository(listOf(asha)).apply {
            appointments.value = mapOf(
                "doc-001" to listOf(
                    appointment("b1", 60, "Sita").copy(clinicCancel = ClinicCancel.BY_YOU),
                    appointment("b2", 90, "Ram").copy(clinicCancel = ClinicCancel.BY_CLINIC),
                ),
            )
        }
        val viewModel = AdminDoctorViewModel("doc-001", repository) { now }
        collecting(viewModel.uiState)
        val listed = (collecting(viewModel.appointments).value as AppointmentsUiState.Ready).appointments
        assertEquals(listOf(ClinicCancel.BY_YOU, ClinicCancel.BY_CLINIC), listed.map { it.clinicCancel })
        listed.forEach(viewModel::requestCancel)
        assertNull(viewModel.cancelDialog.value)
        // Nor are they counted or offered for cancelling when the doctor is hidden.
        viewModel.requestToggle()
        assertEquals(0, viewModel.dialog.value?.upcomingCount)
        assertFalse(requireNotNull(viewModel.dialog.value).canCancelBookings)
    }

    @Test
    fun `a deleted patient's booking is listed without a name`() = runTest(dispatcher) {
        val repository = FakeAdminRepository(listOf(asha)).apply {
            appointments.value = mapOf("doc-001" to listOf(appointment("b1", 60, null).copy(patientDeleted = true)))
        }
        val viewModel = AdminDoctorViewModel("doc-001", repository) { now }
        val listed = (collecting(viewModel.appointments).value as AppointmentsUiState.Ready).appointments.single()
        assertTrue(listed.patientDeleted)
        assertNull(listed.patientFirstName)
        viewModel.requestCancel(listed)
        assertEquals(CancelBookingDialogState(listed), viewModel.cancelDialog.value)
    }

    @Test
    fun `a refused cancel keeps the dialog with the reason, and it can't be closed while cancelling`() = runTest(dispatcher) {
        val repository = withBookings("b1").apply { cancelFailures["b1"] = AdminError.BOOKING_STARTED }
        val viewModel = AdminDoctorViewModel("doc-001", repository) { now }
        val target = (collecting(viewModel.appointments).value as AppointmentsUiState.Ready).appointments.single()
        viewModel.requestCancel(target)
        viewModel.confirmCancel()
        assertEquals(CancelBookingDialogState(target, error = AdminError.BOOKING_STARTED), viewModel.cancelDialog.value)

        repository.cancelFailures.clear()
        val gate = CompletableDeferred<Unit>().also { repository.gate = it }
        viewModel.confirmCancel()
        assertEquals(CancelBookingDialogState(target, isCancelling = true), viewModel.cancelDialog.value)
        viewModel.dismissCancel()
        viewModel.confirmCancel()
        assertTrue(requireNotNull(viewModel.cancelDialog.value).isCancelling)
        gate.complete(Unit)
        assertNull(viewModel.cancelDialog.value)
        assertEquals(listOf("b1"), repository.cancelledBookings)
    }

    @Test
    fun `only one dialog opens at a time`() = runTest(dispatcher) {
        val viewModel = AdminDoctorViewModel("doc-001", withBookings("b1")) { now }
        collecting(viewModel.uiState)
        val target = (collecting(viewModel.appointments).value as AppointmentsUiState.Ready).appointments.single()
        viewModel.requestToggle()
        viewModel.requestCancel(target)
        assertNull(viewModel.cancelDialog.value)
        viewModel.dismissDialog()
        viewModel.requestCancel(target)
        viewModel.requestToggle()
        assertNull(viewModel.dialog.value)
    }

    @Test
    fun `hiding offers to cancel the upcoming bookings only when there are some`() = runTest(dispatcher) {
        val viewModel = AdminDoctorViewModel("doc-001", withBookings("b1")) { now }
        collecting(viewModel.uiState)
        viewModel.requestToggle()
        assertTrue(requireNotNull(viewModel.dialog.value).canCancelBookings)

        val none = AdminDoctorViewModel("doc-001", withBookings()) { now }
        collecting(none.uiState)
        none.requestToggle()
        assertFalse(requireNotNull(none.dialog.value).canCancelBookings)

        val uncounted = AdminDoctorViewModel("doc-001", withBookings("b1").apply { countFailure = AdminError.NETWORK }) { now }
        collecting(uncounted.uiState)
        uncounted.requestToggle()
        assertFalse(requireNotNull(uncounted.dialog.value).canCancelBookings)

        assertFalse(ActiveDialogState(activate = true, upcomingCount = 2).canCancelBookings)
    }

    @Test
    fun `hide and cancel hides the doctor, then cancels every upcoming booking`() = runTest(dispatcher) {
        val repository = withBookings("b1", "b2", "b3")
        val viewModel = AdminDoctorViewModel("doc-001", repository) { now }
        val state = collecting(viewModel.uiState)
        viewModel.requestToggle()
        viewModel.confirmToggle(cancelBookings = true)

        assertEquals(listOf("doc-001" to false), repository.activeChanges)
        assertFalse((state.value as AdminDoctorUiState.Ready).doctor.active)
        assertEquals(listOf("b1", "b2", "b3"), repository.cancelledBookings.sorted())
        assertTrue(repository.upcomingIds().isEmpty())
        assertNull(viewModel.dialog.value)
    }

    @Test
    fun `hide only leaves the bookings booked`() = runTest(dispatcher) {
        val repository = withBookings("b1", "b2")
        val viewModel = AdminDoctorViewModel("doc-001", repository) { now }
        collecting(viewModel.uiState)
        viewModel.requestToggle()
        viewModel.confirmToggle(cancelBookings = false)
        assertEquals(listOf("doc-001" to false), repository.activeChanges)
        assertTrue(repository.cancelledBookings.isEmpty())
        assertNull(viewModel.dialog.value)
    }

    @Test
    fun `a failed hide cancels nothing`() = runTest(dispatcher) {
        val repository = withBookings("b1").apply { writeFailure = AdminError.NETWORK }
        val viewModel = AdminDoctorViewModel("doc-001", repository) { now }
        collecting(viewModel.uiState)
        viewModel.requestToggle()
        viewModel.confirmToggle(cancelBookings = true)
        val dialog = requireNotNull(viewModel.dialog.value)
        assertEquals(AdminError.NETWORK, dialog.error)
        assertFalse(dialog.isSaving)
        assertNull(dialog.cancelResult)
        assertTrue(repository.cancelledBookings.isEmpty())
        assertEquals(listOf("b1"), repository.upcomingIds())
    }

    @Test
    fun `bookings that couldn't be cancelled are reported, and Retry cancels just those`() = runTest(dispatcher) {
        val repository = withBookings("b1", "b2", "b3", "b4").apply {
            cancelFailures["b2"] = AdminError.NETWORK
            cancelFailures["b4"] = AdminError.PERMISSION_DENIED
        }
        val viewModel = AdminDoctorViewModel("doc-001", repository) { now }
        val state = collecting(viewModel.uiState)
        viewModel.requestToggle()
        viewModel.confirmToggle(cancelBookings = true)

        // Hidden either way; the dialog stays with what is left.
        assertFalse((state.value as AdminDoctorUiState.Ready).doctor.active)
        val partial = requireNotNull(viewModel.dialog.value)
        assertEquals(BulkCancelResult(cancelled = 2, failed = 2, error = AdminError.NETWORK), partial.cancelResult)
        assertFalse(partial.isSaving)
        assertFalse(partial.canCancelBookings)
        assertEquals(listOf("b2", "b4"), repository.upcomingIds())
        // Confirming again can't hide twice or start over.
        viewModel.confirmToggle(cancelBookings = true)
        assertEquals(1, repository.activeChanges.size)

        repository.cancelFailures.remove("b2")
        viewModel.retryCancelBookings()
        assertEquals(
            BulkCancelResult(cancelled = 1, failed = 1, error = AdminError.PERMISSION_DENIED),
            viewModel.dialog.value?.cancelResult,
        )

        repository.cancelFailures.clear()
        viewModel.retryCancelBookings()
        assertNull(viewModel.dialog.value)
        assertTrue(repository.upcomingIds().isEmpty())
        // Retrying never touches the doctor again.
        assertEquals(listOf("doc-001" to false), repository.activeChanges)
    }

    @Test
    fun `a bulk cancel can't be closed while running`() = runTest(dispatcher) {
        val repository = withBookings("b1")
        val viewModel = AdminDoctorViewModel("doc-001", repository) { now }
        collecting(viewModel.uiState)
        viewModel.requestToggle()
        val gate = CompletableDeferred<Unit>().also { repository.gate = it }
        viewModel.confirmToggle(cancelBookings = true)
        val saving = requireNotNull(viewModel.dialog.value)
        assertTrue(saving.isSaving)
        assertTrue(saving.cancellingBookings)
        viewModel.dismissDialog()
        assertTrue(viewModel.dialog.value != null)
        gate.complete(Unit)
        assertNull(viewModel.dialog.value)
    }

    @Test
    fun `when the bookings can't be listed after hiding, the dialog says so and Retry recovers`() = runTest(dispatcher) {
        val repository = withBookings("b1", "b2").apply { idsFailure = AdminError.NETWORK }
        val viewModel = AdminDoctorViewModel("doc-001", repository) { now }
        collecting(viewModel.uiState)
        viewModel.requestToggle()
        viewModel.confirmToggle(cancelBookings = true)
        assertEquals(BulkCancelResult(cancelled = 0, failed = 0, error = AdminError.NETWORK), viewModel.dialog.value?.cancelResult)
        assertEquals(listOf("doc-001" to false), repository.activeChanges)

        repository.idsFailure = null
        viewModel.retryCancelBookings()
        assertNull(viewModel.dialog.value)
        assertEquals(listOf("b1", "b2"), repository.cancelledBookings.sorted())
    }

    @Test
    fun `a bulk cancel goes page by page past failures, and skips started ones`() = runTest(dispatcher) {
        val repository = withBookings("b1", "b2", "b3", "b4", "b5").apply {
            idsPageSize = 2
            cancelFailures["b1"] = AdminError.UNKNOWN
            cancelFailures["b3"] = AdminError.BOOKING_STARTED
        }
        val result = BulkCancel.cancelUpcoming(repository, "doc-001")
        assertEquals(BulkCancelResult(cancelled = 3, failed = 1, error = AdminError.UNKNOWN), result)
        assertEquals(listOf("b2", "b4", "b5"), repository.cancelledBookings.sorted())
    }

    @Test
    fun `bookings the patient cancelled meanwhile aren't counted as cancelled by the clinic`() = runTest(dispatcher) {
        val repository = withBookings("b1", "b2").apply { alreadyCancelled += "b2" }
        assertEquals(BulkCancelResult(cancelled = 1, failed = 0, error = null), BulkCancel.cancelUpcoming(repository, "doc-001"))
    }

    @Test
    fun `hiding can't be confirmed until the bookings are counted`() = runTest(dispatcher) {
        val repository = withBookings("b1")
        val viewModel = AdminDoctorViewModel("doc-001", repository) { now }
        collecting(viewModel.uiState)
        // Still counting, as when the count is slow.
        val counting = ActiveDialogState(activate = false)
        assertFalse(counting.canConfirm)
        assertTrue(counting.copy(upcomingCount = 1).canConfirm)
        assertTrue(counting.copy(countFailed = true).canConfirm)
        assertTrue(ActiveDialogState(activate = true).canConfirm)

        val gate = CompletableDeferred<Unit>()
        val slowCount = object : AdminRepository by repository {
            override suspend fun upcomingCount(doctorId: String): Int {
                gate.await()
                return repository.upcomingCount(doctorId)
            }
        }
        val slow = AdminDoctorViewModel("doc-001", slowCount) { now }
        collecting(slow.uiState)
        slow.requestToggle()
        slow.confirmToggle(cancelBookings = true)
        slow.confirmToggle(cancelBookings = false)
        assertTrue(repository.activeChanges.isEmpty())
        assertFalse(requireNotNull(slow.dialog.value).isSaving)
        gate.complete(Unit)
        assertTrue(requireNotNull(slow.dialog.value).canCancelBookings)
    }

    @Test
    fun `a bulk cancel reports unexpected exceptions as UNKNOWN instead of crashing`() = runTest(dispatcher) {
        val fake = withBookings("b1", "b2")
        val brokenCancel = object : AdminRepository by fake {
            override suspend fun cancelBooking(bookingId: String): Boolean =
                if (bookingId == "b1") throw IllegalStateException("terminated") else fake.cancelBooking(bookingId)
        }
        assertEquals(BulkCancelResult(cancelled = 1, failed = 1, error = AdminError.UNKNOWN), BulkCancel.cancelUpcoming(brokenCancel, "doc-001"))

        val brokenPage = object : AdminRepository by fake {
            override suspend fun upcomingBookingPage(doctorId: String, afterMillis: Long?): UpcomingPage =
                throw IllegalStateException("terminated")
        }
        assertEquals(BulkCancelResult(cancelled = 0, failed = 0, error = AdminError.UNKNOWN), BulkCancel.cancelUpcoming(brokenPage, "doc-001"))
    }

    @Test
    fun `a bulk cancel with only started bookings left is complete`() = runTest(dispatcher) {
        val repository = withBookings("b1").apply { cancelFailures["b1"] = AdminError.BOOKING_STARTED }
        assertTrue(BulkCancel.cancelUpcoming(repository, "doc-001").isComplete)
    }

    @Test
    fun `a bulk cancel that keeps finding bookings stops and says it isn't done`() = runTest(dispatcher) {
        // Always another page: the page guard stops it rather than loop forever.
        var page = 0L
        val repository = object : AdminRepository by FakeAdminRepository(listOf(asha)) {
            override suspend fun upcomingBookingPage(doctorId: String, afterMillis: Long?) =
                UpcomingPage(listOf("p$page"), nextAfterMillis = page++)

            override suspend fun cancelBooking(bookingId: String) = true
        }
        val result = BulkCancel.cancelUpcoming(repository, "doc-001")
        assertEquals(BulkCancel.MAX_PAGES, result.cancelled)
        assertFalse(result.isComplete)
    }

    // Add / edit

    private fun DoctorFormViewModel.fillIn() {
        onNameChange("Sita Shrestha")
        onSpecialtyChange(Specialty.PEDIATRICS)
        onHospitalChange("Riverside Clinic")
        onFeeChange("600")
        onExperienceChange("5")
        onBioChange("Children's health.")
        addRange(Weekday.MONDAY)
    }

    @Test
    fun `adding a doctor saves them under a new id, active`() = runTest(dispatcher) {
        val repository = FakeAdminRepository()
        val viewModel = DoctorFormViewModel(null, repository) { "doc-new1" }
        assertTrue(viewModel.uiState.value.isNew)
        viewModel.fillIn()
        viewModel.save()
        assertTrue(viewModel.uiState.value.saved)
        val saved = repository.created.single()
        assertEquals("doc-new1", saved.id)
        assertEquals("Sita Shrestha", saved.name)
        assertEquals(mapOf(Weekday.MONDAY to 1), saved.weeklySchedule.mapValues { it.value.size })
        assertTrue(repository.doctors.value!!.single().active)
    }

    @Test
    fun `errors show only after Save, then follow the edits`() = runTest(dispatcher) {
        val viewModel = DoctorFormViewModel(null, FakeAdminRepository()) { "doc-new1" }
        viewModel.onNameChange("")
        assertTrue(viewModel.uiState.value.errors.isEmpty)
        viewModel.save()
        assertEquals(R.string.admin_error_required, viewModel.uiState.value.errors.name)
        assertFalse(viewModel.uiState.value.saved)
        viewModel.onNameChange("Sita")
        assertNull(viewModel.uiState.value.errors.name)
    }

    @Test
    fun `editing loads the doctor, saves the changes and keeps the id`() = runTest(dispatcher) {
        val repository = FakeAdminRepository(listOf(asha))
        val viewModel = DoctorFormViewModel("doc-001", repository) { error("no new id when editing") }
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Asha Rai", viewModel.uiState.value.fields.name)
        viewModel.onFeeChange("950")
        viewModel.setRangeEnd(Weekday.SUNDAY, 0, TimeOfDay(14 * 60))
        viewModel.save()
        val saved = repository.updated.single()
        assertEquals("doc-001", saved.id)
        assertEquals(950, saved.feeNpr)
        assertEquals(TimeOfDay(14 * 60), saved.weeklySchedule.getValue(Weekday.SUNDAY).single().end)
        assertTrue(repository.created.isEmpty())
        // Editing never shows or hides the doctor.
        assertTrue(repository.doctors.value!!.single().active)
    }

    @Test
    fun `an invalid schedule blocks saving`() = runTest(dispatcher) {
        val repository = FakeAdminRepository()
        val viewModel = DoctorFormViewModel(null, repository) { "doc-new1" }
        viewModel.fillIn()
        viewModel.addRange(Weekday.MONDAY)
        viewModel.setRangeStart(Weekday.MONDAY, 1, TimeOfDay(10 * 60))
        viewModel.save()
        assertTrue(viewModel.uiState.value.errors.schedule)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun `a failed save keeps the form, and the same id is used on retry`() = runTest(dispatcher) {
        val repository = FakeAdminRepository().apply { writeFailure = AdminError.NETWORK }
        var ids = 0
        val viewModel = DoctorFormViewModel(null, repository) { "doc-new${++ids}" }
        viewModel.fillIn()
        viewModel.save()
        assertEquals(AdminError.NETWORK, viewModel.uiState.value.saveError)
        assertFalse(viewModel.uiState.value.saved)
        repository.writeFailure = null
        viewModel.save()
        assertEquals("doc-new1", repository.created.single().id)
        assertEquals(1, ids)
    }

    @Test
    fun `fields can't change while saving`() = runTest(dispatcher) {
        val repository = FakeAdminRepository()
        val gate = CompletableDeferred<Unit>().also { repository.gate = it }
        val viewModel = DoctorFormViewModel(null, repository) { "doc-new1" }
        viewModel.fillIn()
        viewModel.save()
        assertTrue(viewModel.uiState.value.isSaving)
        viewModel.onNameChange("Changed")
        viewModel.save()
        gate.complete(Unit)
        assertEquals("Sita Shrestha", repository.created.single().name)
    }

    @Test
    fun `editing a doctor that is gone says so`() = runTest(dispatcher) {
        val viewModel = DoctorFormViewModel("doc-404", FakeAdminRepository(listOf(asha))) { "x" }
        assertEquals(AuthError.UNKNOWN, viewModel.uiState.value.loadError)
        assertFalse(viewModel.uiState.value.canEdit)
    }

    @Test
    fun `editing offline with nothing saved can be retried`() = runTest(dispatcher) {
        val repository = FakeAdminRepository(emptyList()).apply { fromCache = true }
        val viewModel = DoctorFormViewModel("doc-001", repository) { "x" }
        advanceTimeBy(OFFLINE_GRACE_MS + 1)
        assertEquals(AuthError.NETWORK, viewModel.uiState.value.loadError)
        repository.fromCache = false
        repository.doctors.value = listOf(asha)
        viewModel.retryLoad()
        assertNull(viewModel.uiState.value.loadError)
        assertEquals("Asha Rai", viewModel.uiState.value.fields.name)
    }
}
