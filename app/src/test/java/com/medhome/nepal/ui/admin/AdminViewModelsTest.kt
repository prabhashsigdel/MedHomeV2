package com.medhome.nepal.ui.admin

import com.medhome.nepal.R
import com.medhome.nepal.domain.AdminError
import com.medhome.nepal.domain.AuthError
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
