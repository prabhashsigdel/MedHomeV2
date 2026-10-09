package com.medhome.nepal.ui.doctors

import com.medhome.nepal.data.DoctorsSnapshot
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.Specialty
import com.medhome.nepal.fakes.FakeDoctorRepository
import com.medhome.nepal.fakes.doctor
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DoctorViewModelsTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val asha = doctor(id = "1", name = "Asha Rai", specialty = Specialty.CARDIOLOGY)
    private val bikash = doctor(id = "2", name = "Bikash Thapa", specialty = Specialty.DERMATOLOGY)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** The states are only produced while someone collects (as the screen does). */
    private fun <T> TestScope.collecting(flow: StateFlow<T>): StateFlow<T> {
        backgroundScope.launch(dispatcher) { flow.collect {} }
        return flow
    }

    @Test
    fun `loading until the first answer, then the doctors`() = runTest(dispatcher) {
        val repository = FakeDoctorRepository().apply { snapshot.value = null }
        val state = collecting(DoctorListViewModel(repository).uiState)
        assertEquals(DoctorListStatus.LOADING, state.value.status)

        repository.snapshot.value = DoctorsSnapshot(listOf(asha, bikash), fromCache = false)
        assertEquals(DoctorListStatus.READY, state.value.status)
        assertEquals(listOf(asha, bikash), state.value.doctors)
        assertEquals(listOf(Specialty.CARDIOLOGY, Specialty.DERMATOLOGY), state.value.specialties)
        assertFalse(state.value.showingSaved)
    }

    @Test
    fun `offline with nothing saved is not the same as no doctors`() = runTest(dispatcher) {
        val repository = FakeDoctorRepository(fromCache = true)
        val state = collecting(DoctorListViewModel(repository).uiState)
        // An empty cache isn't "offline" yet: the server may still answer.
        assertEquals(DoctorListStatus.LOADING, state.value.status)
        advanceTimeBy(OFFLINE_GRACE_MS + 1)
        assertEquals(DoctorListStatus.OFFLINE, state.value.status)

        repository.snapshot.value = DoctorsSnapshot(emptyList(), fromCache = false)
        assertEquals(DoctorListStatus.NO_DOCTORS, state.value.status)
    }

    @Test
    fun `a saved list shows at once, with the offline notice only if the server doesn't answer`() = runTest(dispatcher) {
        val state = collecting(DoctorListViewModel(FakeDoctorRepository(listOf(asha), fromCache = true)).uiState)
        assertEquals(DoctorListStatus.READY, state.value.status)
        assertFalse(state.value.showingSaved)
        advanceTimeBy(OFFLINE_GRACE_MS + 1)
        assertTrue(state.value.showingSaved)
    }

    @Test
    fun `a server answer within the grace period means no offline notice`() = runTest(dispatcher) {
        val repository = FakeDoctorRepository(listOf(asha), fromCache = true)
        val state = collecting(DoctorListViewModel(repository).uiState)
        advanceTimeBy(OFFLINE_GRACE_MS / 2)
        repository.snapshot.value = DoctorsSnapshot(listOf(asha, bikash), fromCache = false)
        advanceTimeBy(OFFLINE_GRACE_MS)
        assertFalse(state.value.showingSaved)
        assertEquals(listOf(asha, bikash), state.value.doctors)
    }

    @Test
    fun `the search text updates synchronously for the text field`() = runTest(dispatcher) {
        val viewModel = DoctorListViewModel(FakeDoctorRepository(listOf(asha)))
        viewModel.onQueryChange("as")
        assertEquals("as", viewModel.query)
    }

    @Test
    fun `search and specialty filter the list, and picking a chip again clears it`() = runTest(dispatcher) {
        val viewModel = DoctorListViewModel(FakeDoctorRepository(listOf(asha, bikash)))
        val state = collecting(viewModel.uiState)

        viewModel.onSpecialtySelected(Specialty.DERMATOLOGY)
        assertEquals(listOf(bikash), state.value.doctors)
        viewModel.onSpecialtySelected(Specialty.DERMATOLOGY)
        assertEquals(listOf(asha, bikash), state.value.doctors)

        viewModel.onQueryChange("zzz")
        assertEquals(DoctorListStatus.NO_MATCHES, state.value.status)
        viewModel.onQueryChange("x".repeat(500))
        assertEquals(MAX_QUERY_LENGTH, state.value.filter.query.length)
    }

    @Test
    fun `a specialty that no doctor has any more stops filtering`() = runTest(dispatcher) {
        val repository = FakeDoctorRepository(listOf(asha, bikash))
        val viewModel = DoctorListViewModel(repository)
        val state = collecting(viewModel.uiState)
        viewModel.onSpecialtySelected(Specialty.DERMATOLOGY)

        repository.snapshot.value = DoctorsSnapshot(listOf(asha), fromCache = false)
        assertEquals(null, state.value.filter.specialty)
        assertEquals(listOf(asha), state.value.doctors)
    }

    @Test
    fun `a failed read shows the error and retry listens again`() = runTest(dispatcher) {
        val repository = FakeDoctorRepository(listOf(asha)).apply { failure = AuthError.PERMISSION_DENIED }
        val viewModel = DoctorListViewModel(repository)
        val state = collecting(viewModel.uiState)
        assertEquals(DoctorListStatus.FAILED, state.value.status)
        assertEquals(AuthError.PERMISSION_DENIED, state.value.error)

        repository.failure = null
        viewModel.retry()
        assertEquals(DoctorListStatus.READY, state.value.status)
        assertEquals(2, repository.listens)
    }

    @Test
    fun `detail shows the doctor, or says it is unavailable`() = runTest(dispatcher) {
        val repository = FakeDoctorRepository(listOf(asha))
        val found = collecting(DoctorDetailViewModel("1", repository).uiState)
        assertEquals(DoctorDetailUiState.Ready(asha, showingSaved = false), found.value)

        val missing = collecting(DoctorDetailViewModel("nope", repository).uiState)
        assertEquals(DoctorDetailUiState.Unavailable(offline = false), missing.value)

        repository.snapshot.value = DoctorsSnapshot(emptyList(), fromCache = true)
        advanceTimeBy(OFFLINE_GRACE_MS + 1)
        assertEquals(DoctorDetailUiState.Unavailable(offline = true), found.value)
    }
}
