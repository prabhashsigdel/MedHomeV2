package com.medhome.nepal.ui.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medhome.nepal.appContainer
import com.medhome.nepal.data.AdminRepository
import com.medhome.nepal.data.AuthErrorMapper
import com.medhome.nepal.data.ManagedDoctorLookup
import com.medhome.nepal.data.ManagedDoctorsSnapshot
import com.medhome.nepal.domain.AdminError
import com.medhome.nepal.domain.AdminException
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.CancelReason
import com.medhome.nepal.domain.ClinicCancelReason
import com.medhome.nepal.domain.Doctor
import com.medhome.nepal.domain.DoctorAppointment
import com.medhome.nepal.domain.ManagedDoctor
import com.medhome.nepal.domain.Specialty
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.domain.Weekday
import com.medhome.nepal.ui.common.Load
import com.medhome.nepal.ui.common.STOP_TIMEOUT_MS
import com.medhome.nepal.ui.common.load
import com.medhome.nepal.ui.common.withOfflineGrace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

// Doctors list

enum class AdminListStatus {
    LOADING,
    READY,

    /** Offline and nothing saved yet. */
    OFFLINE,
    NO_DOCTORS,

    /** There are doctors, but none matches the search. */
    NO_MATCHES,
    FAILED,
}

data class AdminDoctorListUiState(
    val status: AdminListStatus = AdminListStatus.LOADING,
    /** Matching doctors, active and inactive, sorted by name. */
    val doctors: List<ManagedDoctor> = emptyList(),
    /** The list came from the on-device cache: say it may be out of date. */
    val showingSaved: Boolean = false,
)

class AdminDoctorListViewModel(repository: AdminRepository) : ViewModel() {

    private val attempts = MutableStateFlow(0)
    private val search = MutableStateFlow("")

    /** The search field's text, updated synchronously (an async flow can lose keystrokes). */
    var query by mutableStateOf("")
        private set

    val uiState: StateFlow<AdminDoctorListUiState> =
        combine(attempts.load { repository.allDoctorsWithGrace() }, search, ::stateOf)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AdminDoctorListUiState())

    fun onQueryChange(text: String) {
        val trimmed = text.take(MAX_SEARCH_LENGTH)
        query = trimmed
        search.value = trimmed
    }

    fun retry() = attempts.update { it + 1 }

    companion object {
        const val MAX_SEARCH_LENGTH = 60

        fun stateOf(load: Load<ManagedDoctorsSnapshot>, query: String): AdminDoctorListUiState = when (load) {
            Load.Loading -> AdminDoctorListUiState()
            is Load.Failed -> AdminDoctorListUiState(status = AdminListStatus.FAILED)
            is Load.Ready -> {
                val all = load.value.doctors
                val shown = all.matchingSearch(query)
                AdminDoctorListUiState(
                    status = when {
                        all.isEmpty() && load.value.fromCache -> AdminListStatus.OFFLINE
                        all.isEmpty() -> AdminListStatus.NO_DOCTORS
                        shown.isEmpty() -> AdminListStatus.NO_MATCHES
                        else -> AdminListStatus.READY
                    },
                    doctors = shown,
                    showingSaved = load.value.fromCache && all.isNotEmpty(),
                )
            }
        }
    }
}

private fun AdminRepository.allDoctorsWithGrace() =
    allDoctors().withOfflineGrace(
        isCached = { it.fromCache },
        provisional = { if (it.doctors.isNotEmpty()) it.copy(fromCache = false) else null },
    )

/**
 * Doctors whose name or hospital contains every word of [query] (any order, ignoring case and
 * extra spaces). An empty query matches everyone.
 */
fun List<ManagedDoctor>.matchingSearch(query: String): List<ManagedDoctor> {
    val words = normalized(query).split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return this
    return filter { managed ->
        val text = normalized(managed.doctor.name) + " " + normalized(managed.doctor.hospital)
        words.all { it in text }
    }
}

private fun normalized(text: String): String = text.trim().lowercase(Locale.ROOT).replace(SPACES, " ")

private val SPACES = Regex("\\s+")

// One doctor: details, show / hide, upcoming bookings

sealed interface AdminDoctorUiState {
    data object Loading : AdminDoctorUiState
    data class Ready(val doctor: ManagedDoctor, val showingSaved: Boolean) : AdminDoctorUiState

    /** Gone or malformed; [offline] when it may just not be saved on this phone. */
    data class Missing(val offline: Boolean) : AdminDoctorUiState
    data class Failed(val error: AuthError) : AdminDoctorUiState
}

sealed interface AppointmentsUiState {
    data object Loading : AppointmentsUiState
    data class Ready(val appointments: List<DoctorAppointment>) : AppointmentsUiState
    data class Failed(val error: AuthError) : AppointmentsUiState
}

/**
 * The confirm dialog for showing ([activate]) or hiding a doctor. [upcomingCount] is null while
 * it is being counted, and stays null if counting failed ([countFailed]). Hiding a doctor with
 * upcoming bookings can also cancel them ([cancellingBookings] while that is saving), all with
 * the same [reason] (required for that) and optional [note]; when some couldn't be cancelled,
 * the doctor is hidden and [cancelResult] says what is left, with Retry (same reason and note).
 */
data class ActiveDialogState(
    val activate: Boolean,
    val upcomingCount: Int? = null,
    val countFailed: Boolean = false,
    val isSaving: Boolean = false,
    val cancellingBookings: Boolean = false,
    val error: AdminError? = null,
    val cancelResult: BulkCancelResult? = null,
    val reason: CancelReason? = null,
    val note: String = "",
) {
    /**
     * Hiding waits for the count (or its failure) so "Hide and cancel" can't appear under a tap
     * meant for the button that was there before it.
     */
    val canConfirm: Boolean
        get() = !isSaving && cancelResult == null && (activate || upcomingCount != null || countFailed)

    /** Hiding, with bookings to cancel, and nothing done yet. */
    val canCancelBookings: Boolean
        get() = !activate && (upcomingCount ?: 0) > 0 && cancelResult == null

    /** "Hide and cancel" also needs a reason for the patients. */
    val canHideAndCancel: Boolean
        get() = canConfirm && canCancelBookings && reason != null

    val cancelReason: ClinicCancelReason?
        get() = reason?.let { ClinicCancelReason(it, note.ifBlank { null }) }
}

class AdminDoctorViewModel(
    private val doctorId: String,
    private val repository: AdminRepository,
    private val clock: () -> Long,
) : ViewModel() {

    private val doctorAttempts = MutableStateFlow(0)
    private val appointmentAttempts = MutableStateFlow(0)
    private val _dialog = MutableStateFlow<ActiveDialogState?>(null)
    private val cancel = ClinicCancelFlow(viewModelScope, repository)
    private var countJob: Job? = null

    val uiState: StateFlow<AdminDoctorUiState> =
        doctorAttempts.load { repository.doctorWithGrace(doctorId) }
            .map(::doctorStateOf)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AdminDoctorUiState.Loading)

    val appointments: StateFlow<AppointmentsUiState> =
        appointmentAttempts.load { repository.upcomingAppointments(doctorId) }
            .map(::appointmentsStateOf)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppointmentsUiState.Loading)

    val dialog: StateFlow<ActiveDialogState?> = _dialog.asStateFlow()

    val cancelDialog: StateFlow<CancelBookingDialogState?> = cancel.dialog

    fun retry() = doctorAttempts.update { it + 1 }

    fun retryAppointments() = appointmentAttempts.update { it + 1 }

    /** Opens the confirm dialog for the opposite of the doctor's current state, and counts their bookings. */
    fun requestToggle() {
        val ready = uiState.value as? AdminDoctorUiState.Ready ?: return
        if (_dialog.value != null || cancel.isOpen) return
        _dialog.value = ActiveDialogState(activate = !ready.doctor.active)
        countJob = viewModelScope.launch {
            val count = try {
                repository.upcomingCount(doctorId)
            } catch (_: AdminException) {
                null
            }
            _dialog.update { it?.copy(upcomingCount = count, countFailed = count == null) }
        }
    }

    /**
     * Shows or hides the doctor. Hiding with [cancelBookings] (offered only when they have
     * upcoming bookings) then cancels those too; if any are left, the dialog stays with the result.
     */
    fun confirmToggle(cancelBookings: Boolean = false) {
        val current = _dialog.value ?: return
        if (!current.canConfirm) return
        if (cancelBookings && !current.canHideAndCancel) return
        val alsoCancel = cancelBookings && current.canCancelBookings
        _dialog.value = current.copy(isSaving = true, cancellingBookings = alsoCancel, error = null)
        viewModelScope.launch {
            try {
                repository.setActive(doctorId, current.activate)
                countJob?.cancel()
            } catch (e: AdminException) {
                _dialog.update { it?.copy(isSaving = false, cancellingBookings = false, error = e.error) }
                return@launch
            }
            if (alsoCancel) cancelUpcoming() else _dialog.value = null
        }
    }

    /** After a bulk cancel left bookings behind: tries every booking still upcoming again. */
    fun retryCancelBookings() {
        val current = _dialog.value ?: return
        if (current.isSaving || current.cancelResult == null) return
        _dialog.value = current.copy(isSaving = true, cancellingBookings = true)
        viewModelScope.launch { cancelUpcoming() }
    }

    /** The reason given to patients when hiding also cancels their bookings. */
    fun selectHideReason(reason: CancelReason) =
        _dialog.update { if (it == null || it.isSaving || it.cancelResult != null) it else it.copy(reason = reason) }

    fun editHideNote(note: String) = _dialog.update {
        if (it == null || it.isSaving || it.cancelResult != null) it else it.copy(note = note.take(ClinicCancelReason.MAX_NOTE_LENGTH))
    }

    /** Closes the dialog, unless the change is being saved. */
    fun dismissDialog() {
        if (_dialog.value?.isSaving == true) return
        countJob?.cancel()
        _dialog.value = null
    }

    private suspend fun cancelUpcoming() {
        // Never without a reason (confirmToggle checks it); if it ever were, stop saving instead of hanging.
        val reason = _dialog.value?.cancelReason ?: run {
            _dialog.update { it?.copy(isSaving = false, cancellingBookings = false) }
            return
        }
        val result = BulkCancel.cancelUpcoming(repository, doctorId, reason)
        if (result.isComplete) {
            _dialog.value = null
        } else {
            _dialog.update { it?.copy(isSaving = false, cancellingBookings = false, cancelResult = result) }
        }
    }

    /** Asks before cancelling [appointment] for the clinic (only while it is booked). */
    fun requestCancel(appointment: DoctorAppointment) {
        if (!appointment.isBooked || _dialog.value != null) return
        cancel.request(appointment)
    }

    fun selectCancelReason(reason: CancelReason) = cancel.selectReason(reason)

    fun editCancelNote(note: String) = cancel.editNote(note)

    /** The live list then shows it as cancelled by this admin. */
    fun confirmCancel() = cancel.confirm()

    /** Closes the cancel dialog, unless the booking is being cancelled. */
    fun dismissCancel() = cancel.dismiss()

    private fun doctorStateOf(load: Load<ManagedDoctorLookup>): AdminDoctorUiState = when (load) {
        Load.Loading -> AdminDoctorUiState.Loading
        is Load.Failed -> AdminDoctorUiState.Failed(load.error)
        is Load.Ready -> when (val lookup = load.value) {
            is ManagedDoctorLookup.Found -> AdminDoctorUiState.Ready(lookup.doctor, showingSaved = lookup.fromCache)
            is ManagedDoctorLookup.Missing -> AdminDoctorUiState.Missing(offline = lookup.fromCache)
        }
    }

    /** Only those still ahead: the list's "now" is when it was opened. */
    private fun appointmentsStateOf(load: Load<List<DoctorAppointment>>): AppointmentsUiState = when (load) {
        Load.Loading -> AppointmentsUiState.Loading
        is Load.Failed -> AppointmentsUiState.Failed(load.error)
        is Load.Ready -> AppointmentsUiState.Ready(load.value.filter { it.startAtMillis > clock() })
    }
}

private fun AdminRepository.doctorWithGrace(id: String) =
    doctor(id).withOfflineGrace(
        isCached = ManagedDoctorLookup::fromCache,
        provisional = { if (it is ManagedDoctorLookup.Found) it.copy(fromCache = false) else null },
    )

// Add / edit doctor

data class DoctorFormUiState(
    val isNew: Boolean,
    /** Editing: the doctor's current details are still loading. */
    val isLoading: Boolean = false,
    /** Editing: the doctor couldn't be loaded ([AuthError.NETWORK] offline, or gone). */
    val loadError: AuthError? = null,
    val fields: DoctorFormFields = DoctorFormFields(),
    /** Shown once Save was pressed, and kept up to date as fields change after that. */
    val errors: DoctorFormErrors = DoctorFormErrors(),
    val isSaving: Boolean = false,
    val saveError: AdminError? = null,
    /** Saved: the screen closes. */
    val saved: Boolean = false,
) {
    val canEdit: Boolean get() = !isLoading && loadError == null && !isSaving && !saved
}

/**
 * Adds a doctor ([doctorId] null; the new ID comes from [newId], once per form, so a retry
 * can't add the doctor twice) or edits one.
 */
class DoctorFormViewModel(
    private val doctorId: String?,
    private val repository: AdminRepository,
    newId: () -> String,
) : ViewModel() {

    private val id: String = doctorId ?: newId()
    private var submitted = false

    private val _uiState = MutableStateFlow(DoctorFormUiState(isNew = doctorId == null, isLoading = doctorId != null))
    val uiState: StateFlow<DoctorFormUiState> = _uiState.asStateFlow()

    init {
        if (doctorId != null) load()
    }

    fun retryLoad() {
        if (doctorId == null || _uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        load()
    }

    fun onNameChange(value: String) = edit { it.copy(name = value) }
    fun onSpecialtyChange(value: Specialty) = edit { it.copy(specialty = value) }
    fun onHospitalChange(value: String) = edit { it.copy(hospital = value) }
    fun onFeeChange(value: String) = edit { it.copy(fee = DoctorForm.numberInput(value)) }
    fun onExperienceChange(value: String) = edit { it.copy(experience = DoctorForm.numberInput(value)) }
    fun onBioChange(value: String) = edit { it.copy(bio = value) }
    fun onSlotMinutesChange(value: String) = edit { it.copy(slotMinutes = DoctorForm.numberInput(value)) }

    fun addRange(day: Weekday) = edit { it.copy(schedule = ScheduleEditor.add(it.schedule, day)) }
    fun removeRange(day: Weekday, index: Int) = edit { it.copy(schedule = ScheduleEditor.remove(it.schedule, day, index)) }
    fun setRangeStart(day: Weekday, index: Int, time: TimeOfDay) =
        edit { it.copy(schedule = ScheduleEditor.setStart(it.schedule, day, index, time)) }
    fun setRangeEnd(day: Weekday, index: Int, time: TimeOfDay) =
        edit { it.copy(schedule = ScheduleEditor.setEnd(it.schedule, day, index, time)) }

    fun dismissSaveError() = _uiState.update { it.copy(saveError = null) }

    fun save() {
        val current = _uiState.value
        if (!current.canEdit) return
        submitted = true
        val errors = DoctorForm.validate(current.fields)
        val doctor = DoctorForm.toDoctor(id, current.fields)
        if (doctor == null) {
            _uiState.update { it.copy(errors = errors) }
            return
        }
        _uiState.update { it.copy(errors = errors, isSaving = true, saveError = null) }
        viewModelScope.launch {
            try {
                if (current.isNew) repository.createDoctor(doctor) else repository.updateDoctor(doctor)
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: AdminException) {
                _uiState.update { it.copy(isSaving = false, saveError = e.error) }
            }
        }
    }

    private fun edit(change: (DoctorFormFields) -> DoctorFormFields) {
        if (!_uiState.value.canEdit) return
        _uiState.update { state ->
            val fields = change(state.fields)
            state.copy(fields = fields, errors = if (submitted) DoctorForm.validate(fields) else state.errors)
        }
    }

    /**
     * The doctor as the server has them: saving needs the server anyway, and a cached copy could
     * be older than another admin's edit (which this form would then undo). A cache-only answer
     * that lasts past the offline grace counts as offline.
     */
    private fun load() {
        viewModelScope.launch {
            val lookup = try {
                repository.doctor(id)
                    .withOfflineGrace(isCached = ManagedDoctorLookup::fromCache, provisional = { null })
                    .firstOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, loadError = AuthErrorMapper.map(e)) }
                return@launch
            }
            // The listener was stopped (signing out): nothing to show.
            if (lookup == null) return@launch
            _uiState.update { state ->
                when {
                    lookup.fromCache -> state.copy(isLoading = false, loadError = AuthError.NETWORK)
                    lookup is ManagedDoctorLookup.Found ->
                        state.copy(isLoading = false, fields = DoctorForm.fieldsOf(lookup.doctor.doctor))
                    else -> state.copy(isLoading = false, loadError = AuthError.UNKNOWN)
                }
            }
        }
    }
}

private val ManagedDoctorLookup.fromCache: Boolean
    get() = when (this) {
        is ManagedDoctorLookup.Found -> fromCache
        is ManagedDoctorLookup.Missing -> fromCache
    }

/**
 * Builds the admin ViewModels. A doctor's screens read the doctor's ID from their navigation
 * arguments ([ARG_DOCTOR_ID]; absent on the add form), a booking's screen its ID
 * ([ARG_BOOKING_ID]). Tests pass fakes; the app uses [Factory].
 */
object AdminViewModels {
    const val ARG_DOCTOR_ID = "doctorId"
    const val ARG_BOOKING_ID = "bookingId"

    class Dependencies(
        val repository: AdminRepository,
        val clock: () -> Long = System::currentTimeMillis,
        val newId: () -> String = { Doctor.newId() },
    )

    fun factory(dependencies: (CreationExtras) -> Dependencies): ViewModelProvider.Factory = viewModelFactory {
        initializer { AdminDoctorListViewModel(dependencies(this).repository) }
        initializer {
            val deps = dependencies(this)
            AdminDoctorViewModel(createSavedStateHandle().requireDoctorId(), deps.repository, deps.clock)
        }
        initializer {
            val deps = dependencies(this)
            DoctorFormViewModel(createSavedStateHandle().get<String>(ARG_DOCTOR_ID), deps.repository, deps.newId)
        }
        initializer {
            val deps = dependencies(this)
            AdminBookingsViewModel(deps.repository, deps.clock)
        }
        initializer {
            val deps = dependencies(this)
            val bookingId = requireNotNull(createSavedStateHandle().get<String>(ARG_BOOKING_ID)) { "Booking screen opened without a booking" }
            AdminBookingViewModel(bookingId, deps.repository, deps.clock)
        }
    }

    val Factory: ViewModelProvider.Factory = factory { Dependencies(it.appContainer.adminRepository) }

    private fun SavedStateHandle.requireDoctorId(): String =
        requireNotNull(get<String>(ARG_DOCTOR_ID)) { "Doctor screen opened without a doctor" }
}
