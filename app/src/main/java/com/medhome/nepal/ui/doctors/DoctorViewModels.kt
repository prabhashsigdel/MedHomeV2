package com.medhome.nepal.ui.doctors

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medhome.nepal.appContainer
import com.medhome.nepal.data.DoctorLookup
import com.medhome.nepal.data.DoctorRepository
import com.medhome.nepal.data.DoctorsSnapshot
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.Doctor
import com.medhome.nepal.domain.Specialty
import com.medhome.nepal.ui.common.Load
import com.medhome.nepal.ui.common.STOP_TIMEOUT_MS
import com.medhome.nepal.ui.common.load
import com.medhome.nepal.ui.common.withOfflineGrace
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

private fun DoctorRepository.activeDoctorsWithGrace(): Flow<DoctorsSnapshot> =
    activeDoctors().withOfflineGrace(
        isCached = { it.fromCache },
        provisional = { if (it.doctors.isNotEmpty()) it.copy(fromCache = false) else null },
    )

private fun DoctorRepository.doctorWithGrace(id: String): Flow<DoctorLookup> =
    doctor(id).withOfflineGrace(
        isCached = { it is DoctorLookup.Found && it.fromCache || it is DoctorLookup.Unavailable && it.fromCache },
        provisional = { if (it is DoctorLookup.Found) it.copy(fromCache = false) else null },
    )

// Find a doctor

enum class DoctorListStatus {
    LOADING,
    /** Doctors to show (possibly the saved list, while offline). */
    READY,
    /** Offline and nothing saved yet. */
    OFFLINE,
    /** The server has no active doctors. */
    NO_DOCTORS,
    /** There are doctors, but none matches the search and filter. */
    NO_MATCHES,
    FAILED,
}

data class DoctorListUiState(
    val status: DoctorListStatus = DoctorListStatus.LOADING,
    val doctors: List<Doctor> = emptyList(),
    val specialties: List<Specialty> = emptyList(),
    val filter: DoctorFilter = DoctorFilter(),
    /** The list shown came from the on-device cache: say it may be out of date. */
    val showingSaved: Boolean = false,
    val error: AuthError? = null,
)

class DoctorListViewModel(repository: DoctorRepository) : ViewModel() {

    private val filter = MutableStateFlow(DoctorFilter())
    private val attempts = MutableStateFlow(0)

    /**
     * The search field's text, read by the field directly and updated synchronously: a text
     * field fed through an asynchronous flow can lose keystrokes or IME composition.
     */
    var query by mutableStateOf("")
        private set

    val uiState: StateFlow<DoctorListUiState> =
        combine(attempts.load(repository::activeDoctorsWithGrace), filter, ::stateOf)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DoctorListUiState())

    fun onQueryChange(text: String) {
        val trimmed = text.take(MAX_QUERY_LENGTH)
        query = trimmed
        filter.update { it.copy(query = trimmed) }
    }

    /** Picking the selected specialty again clears it (back to all). */
    fun onSpecialtySelected(specialty: Specialty?) =
        filter.update { it.copy(specialty = if (specialty == it.specialty) null else specialty) }

    fun retry() = attempts.update { it + 1 }

    companion object {
        fun stateOf(load: Load<DoctorsSnapshot>, filter: DoctorFilter): DoctorListUiState = when (load) {
            Load.Loading -> DoctorListUiState(status = DoctorListStatus.LOADING, filter = filter)
            is Load.Failed -> DoctorListUiState(status = DoctorListStatus.FAILED, filter = filter, error = load.error)
            is Load.Ready -> {
                val all = load.value.doctors
                val specialties = all.specialties()
                // A specialty no doctor has any more (the list changed) no longer filters.
                val effective = if (filter.specialty in specialties) filter else filter.copy(specialty = null)
                val shown = all.matching(effective)
                DoctorListUiState(
                    status = when {
                        all.isEmpty() && load.value.fromCache -> DoctorListStatus.OFFLINE
                        all.isEmpty() -> DoctorListStatus.NO_DOCTORS
                        shown.isEmpty() -> DoctorListStatus.NO_MATCHES
                        else -> DoctorListStatus.READY
                    },
                    doctors = shown,
                    specialties = specialties,
                    filter = effective,
                    showingSaved = load.value.fromCache && all.isNotEmpty(),
                )
            }
        }
    }
}

// Doctor detail

sealed interface DoctorDetailUiState {
    data object Loading : DoctorDetailUiState
    data class Ready(val doctor: Doctor, val showingSaved: Boolean) : DoctorDetailUiState

    /** Gone, inactive or unusable; [offline] when it may just not be saved on this phone. */
    data class Unavailable(val offline: Boolean) : DoctorDetailUiState
    data class Failed(val error: AuthError) : DoctorDetailUiState
}

class DoctorDetailViewModel(doctorId: String, repository: DoctorRepository) : ViewModel() {

    private val attempts = MutableStateFlow(0)

    val uiState: StateFlow<DoctorDetailUiState> =
        attempts.load { repository.doctorWithGrace(doctorId) }
            .map(::stateOf)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DoctorDetailUiState.Loading)

    fun retry() = attempts.update { it + 1 }

    private fun stateOf(load: Load<DoctorLookup>): DoctorDetailUiState = when (load) {
        Load.Loading -> DoctorDetailUiState.Loading
        is Load.Failed -> DoctorDetailUiState.Failed(load.error)
        is Load.Ready -> when (val lookup = load.value) {
            is DoctorLookup.Found -> DoctorDetailUiState.Ready(lookup.doctor, showingSaved = lookup.fromCache)
            is DoctorLookup.Unavailable -> DoctorDetailUiState.Unavailable(offline = lookup.fromCache)
        }
    }
}

/**
 * Builds both doctor ViewModels over [repository]. The detail screen's doctor ID comes from its
 * navigation arguments. Tests pass a fake repository; the app uses [Factory].
 */
object DoctorViewModels {
    /** The navigation argument holding the doctor's ID (the route's property name). */
    const val ARG_DOCTOR_ID = "doctorId"

    fun factory(repository: (CreationExtras) -> DoctorRepository): ViewModelProvider.Factory = viewModelFactory {
        initializer { DoctorListViewModel(repository(this)) }
        initializer {
            val doctorId = createSavedStateHandle().doctorId()
            DoctorDetailViewModel(doctorId, repository(this))
        }
    }

    val Factory: ViewModelProvider.Factory = factory { it.appContainer.doctorRepository }

    private fun SavedStateHandle.doctorId(): String =
        requireNotNull(get<String>(ARG_DOCTOR_ID)) { "Doctor detail opened without a doctor" }
}
