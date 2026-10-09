package com.medhome.nepal.ui.reminders

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medhome.nepal.appContainer
import com.medhome.nepal.data.ReminderPrefs
import com.medhome.nepal.domain.AppointmentReminder
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.domain.DoseRecord
import com.medhome.nepal.domain.DoseSchedule
import com.medhome.nepal.domain.DoseState
import com.medhome.nepal.domain.Medicine
import com.medhome.nepal.domain.NepalTime
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.domain.TodayDose
import com.medhome.nepal.domain.Weekday
import com.medhome.nepal.reminders.ReminderRepository
import com.medhome.nepal.ui.common.STOP_TIMEOUT_MS
import com.medhome.nepal.ui.common.ticker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Runs a local reminder change; false (logged by type only, no data) when it failed. */
private suspend fun attempt(block: suspend () -> Unit): Boolean = try {
    block()
    true
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.w("Reminders", "Reminder change failed: ${e.javaClass.simpleName}")
    false
}

// Today's reminders: Home's medicines card, the bell and its screen

data class TodayReminders(
    /** Today's doses in time order, each with its state now. */
    val doses: List<TodayDose> = emptyList(),
    /** Today's appointments that haven't started. */
    val appointments: List<AppointmentReminder> = emptyList(),
    val hasMedicines: Boolean = false,
    val loading: Boolean = true,
    val changeFailed: Boolean = false,
) {
    val missed: List<TodayDose> get() = doses.filter { it.state == DoseState.MISSED }
    val upcoming: List<TodayDose> get() = doses.filter { it.state == DoseState.UPCOMING }
    val takenCount: Int get() = doses.count { it.state == DoseState.TAKEN }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayRemindersViewModel(private val reminders: ReminderRepository, clock: () -> Long) : ViewModel() {
    private val changeFailed = MutableStateFlow(false)

    private val now: Flow<Long> = ticker(clock)

    /** Today's records, following the date when midnight passes. */
    private val records: Flow<Pair<CalendarDate, List<DoseRecord>>> = ticker(clock)
        .map { NepalTime.dateOf(it) }
        .distinctUntilChanged()
        .flatMapLatest { date -> reminders.recordsOn(date).map { date to it } }

    val state: StateFlow<TodayReminders> =
        combine(reminders.medicines, records, reminders.appointments, now, changeFailed) { medicines, (date, dayRecords), appointments, nowMillis, failed ->
            TodayReminders(
                doses = DoseSchedule.today(medicines, dayRecords, date, nowMillis),
                appointments = appointments.filter { NepalTime.dateOf(it.startAtMillis) == date && it.startAtMillis > nowMillis },
                hasMedicines = medicines.isNotEmpty(),
                loading = false,
                changeFailed = failed,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TodayReminders())

    /** Marks a dose taken, or not taken when it already is (a tap by mistake can be undone). */
    fun toggleTaken(dose: TodayDose) {
        viewModelScope.launch {
            val ok = attempt { reminders.setTaken(dose.dose, taken = dose.state != DoseState.TAKEN) }
            changeFailed.value = !ok
        }
    }

    fun dismissError() = changeFailed.update { false }
}

// Medicines list

sealed interface MedicinesUiState {
    data object Loading : MedicinesUiState
    data class Ready(val medicines: List<Medicine>, val prefs: ReminderPrefs) : MedicinesUiState
}

class MedicinesViewModel(reminders: ReminderRepository) : ViewModel() {
    val uiState: StateFlow<MedicinesUiState> =
        combine(reminders.medicines, reminders.prefs) { medicines, prefs -> MedicinesUiState.Ready(medicines, prefs) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MedicinesUiState.Loading)
}

// Add or edit a medicine

/** How the form closed. */
sealed interface FormResult {
    /** Saved; [showBatteryGuide] the first time a reminder is set. */
    data class Saved(val showBatteryGuide: Boolean) : FormResult
    data object Deleted : FormResult
}

data class MedicineFormUiState(
    val isNew: Boolean,
    val loading: Boolean,
    val fields: MedicineFields,
    /** Shown once Save was pressed (field problems as they are fixed). */
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val confirmingDelete: Boolean = false,
    val changeFailed: Boolean = false,
    /** The medicine was deleted elsewhere (or the ID is wrong). */
    val notFound: Boolean = false,
    val result: FormResult? = null,
) {
    val errors: MedicineErrors get() = if (showErrors) MedicineForm.errors(fields) else MedicineErrors()
    val canEdit: Boolean get() = !loading && !saving && !notFound && result == null
}

class MedicineFormViewModel(
    private val medicineId: Long,
    private val reminders: ReminderRepository,
    clock: () -> Long,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        MedicineFormUiState(
            isNew = medicineId == NEW,
            loading = medicineId != NEW,
            fields = MedicineForm.newFields(NepalTime.dateOf(clock())),
        ),
    )
    val uiState: StateFlow<MedicineFormUiState> = _uiState.asStateFlow()

    init {
        if (medicineId != NEW) {
            viewModelScope.launch {
                var found: Medicine? = null
                val ok = attempt { found = reminders.medicine(medicineId) }
                _uiState.update { state ->
                    val medicine = found
                    if (ok && medicine != null) {
                        state.copy(loading = false, fields = MedicineForm.fieldsOf(medicine))
                    } else {
                        state.copy(loading = false, notFound = true)
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) = edit { it.copy(name = value) }

    fun onDoseChange(value: String) = edit { it.copy(dose = value) }

    fun onTimesPerDayChange(count: Int) = edit { MedicineForm.withTimesPerDay(it, count) }

    fun onTimeChange(index: Int, time: TimeOfDay) = edit { MedicineForm.withTime(it, index, time) }

    fun onEveryDayChange(everyDay: Boolean) = edit { it.copy(everyDay = everyDay) }

    fun onDayToggle(day: Weekday) = edit { MedicineForm.withDayToggled(it, day) }

    fun onStartDateChange(date: CalendarDate) = edit { it.copy(startDate = date) }

    fun onHasEndDateChange(hasEnd: Boolean) = edit { MedicineForm.withEndDate(it, hasEnd) }

    fun onEndDateChange(date: CalendarDate) = edit { it.copy(endDate = date) }

    fun save() {
        val state = _uiState.value
        if (!state.canEdit) return
        val medicine = MedicineForm.toMedicine(if (state.isNew) 0 else medicineId, state.fields)
        if (medicine == null) {
            _uiState.update { it.copy(showErrors = true) }
            return
        }
        _uiState.update { it.copy(saving = true, showErrors = true, changeFailed = false) }
        viewModelScope.launch {
            val ok = attempt { reminders.saveMedicine(medicine) }
            // Separate: if only this fails, the medicine is saved and Save must not add it again.
            var showGuide = false
            if (ok) attempt { showGuide = reminders.claimBatteryGuide() }
            _uiState.update {
                if (ok) it.copy(saving = false, result = FormResult.Saved(showGuide)) else it.copy(saving = false, changeFailed = true)
            }
        }
    }

    fun askToDelete() = _uiState.update { if (it.canEdit && !it.isNew) it.copy(confirmingDelete = true) else it }

    fun dismissDelete() = _uiState.update { it.copy(confirmingDelete = false) }

    fun confirmDelete() {
        if (!_uiState.value.canEdit) return
        _uiState.update { it.copy(confirmingDelete = false, saving = true, changeFailed = false) }
        viewModelScope.launch {
            val ok = attempt { reminders.deleteMedicine(medicineId) }
            _uiState.update { if (ok) it.copy(saving = false, result = FormResult.Deleted) else it.copy(saving = false, changeFailed = true) }
        }
    }

    fun dismissError() = _uiState.update { it.copy(changeFailed = false) }

    private fun edit(change: (MedicineFields) -> MedicineFields) =
        _uiState.update { if (it.canEdit) it.copy(fields = change(it.fields)) else it }

    companion object {
        /** The route's ID for a new medicine. */
        const val NEW = 0L
    }
}

// Settings: the Notifications section

class ReminderSettingsViewModel(private val reminders: ReminderRepository) : ViewModel() {
    val prefs: StateFlow<ReminderPrefs?> = reminders.prefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _changeFailed = MutableStateFlow(false)
    val changeFailed: StateFlow<Boolean> = _changeFailed.asStateFlow()

    fun setMedicineReminders(enabled: Boolean) = change { reminders.setMedicineReminders(enabled) }

    fun setAppointmentReminders(enabled: Boolean) = change { reminders.setAppointmentReminders(enabled) }

    fun dismissError() = _changeFailed.update { false }

    private fun change(block: suspend () -> Unit) {
        viewModelScope.launch { _changeFailed.value = !attempt(block) }
    }
}

/** Builds every reminder ViewModel. The app uses [Factory]; tests pass a fake repository. */
object ReminderViewModels {
    /** Navigation argument (the form route's property name). */
    const val ARG_MEDICINE_ID = "medicineId"

    class Dependencies(
        val reminders: ReminderRepository,
        val clock: () -> Long = System::currentTimeMillis,
    )

    fun factory(dependencies: (CreationExtras) -> Dependencies): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val deps = dependencies(this)
            TodayRemindersViewModel(deps.reminders, deps.clock)
        }
        initializer { MedicinesViewModel(dependencies(this).reminders) }
        initializer {
            val deps = dependencies(this)
            val id = createSavedStateHandle().get<Long>(ARG_MEDICINE_ID) ?: MedicineFormViewModel.NEW
            MedicineFormViewModel(id, deps.reminders, deps.clock)
        }
        initializer { ReminderSettingsViewModel(dependencies(this).reminders) }
    }

    val Factory: ViewModelProvider.Factory = factory { extras -> Dependencies(extras.appContainer.reminderEngine) }
}
