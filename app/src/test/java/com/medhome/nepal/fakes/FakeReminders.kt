package com.medhome.nepal.fakes

import com.medhome.nepal.reminders.ReminderSetupItem
import com.medhome.nepal.data.ReminderPrefs
import com.medhome.nepal.data.ReminderSettings
import com.medhome.nepal.domain.AppointmentAlert
import com.medhome.nepal.domain.AppointmentReminder
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.domain.Dose
import com.medhome.nepal.domain.DoseRecord
import com.medhome.nepal.domain.Medicine
import com.medhome.nepal.domain.MedicineDays
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.reminders.AlarmScheduler
import com.medhome.nepal.reminders.ReminderAlarm
import com.medhome.nepal.reminders.ReminderNotifier
import com.medhome.nepal.reminders.ReminderRepository
import com.medhome.nepal.ui.reminders.ReminderViewModels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

fun medicine(
    id: Long = 1,
    name: String = "Paracetamol",
    dose: String = "1 tablet",
    times: List<TimeOfDay> = listOf(TimeOfDay(8 * 60)),
    days: MedicineDays = MedicineDays.EveryDay,
    startDate: CalendarDate = CalendarDate(2026, 10, 1),
    endDate: CalendarDate? = null,
) = Medicine(id, name, dose, times, days, startDate, endDate)

/** Records alarms like AlarmManager would hold them: one per key. */
class FakeAlarmScheduler : AlarmScheduler {
    /** Key to (alarm, time). */
    val alarms = linkedMapOf<String, Pair<ReminderAlarm, Long>>()
    var exact = true

    override fun schedule(alarm: ReminderAlarm, atMillis: Long) {
        alarms[alarm.key] = alarm to atMillis
    }

    override fun cancel(alarm: ReminderAlarm) = cancelKey(alarm.key)

    override fun cancelKey(key: String) {
        alarms.remove(key)
    }

    override fun canScheduleExact(): Boolean = exact

    /** What a reboot does: every alarm is gone. */
    fun reboot() = alarms.clear()

    fun at(key: String): Long? = alarms[key]?.second
}

/** Notifications showing, by a readable tag. */
class FakeReminderNotifier : ReminderNotifier {
    val shown = linkedSetOf<String>()
    val history = mutableListOf<String>()

    override fun showDose(medicine: Medicine, dose: Dose) {
        val tag = doseTag(dose)
        shown += tag
        history += tag
    }

    override fun showAppointment(reminder: AppointmentReminder, alert: AppointmentAlert) {
        val tag = "appointment/${reminder.bookingId}"
        shown += tag
        history += "$tag/${alert.key}"
    }

    override fun cancelDose(dose: Dose) {
        shown -= doseTag(dose)
    }

    override fun cancelMedicine(medicineId: Long) {
        shown.removeAll { it.startsWith("dose/$medicineId/") }
    }

    override fun cancelAppointment(bookingId: String) {
        shown -= "appointment/$bookingId"
    }

    override fun cancelAll() = shown.clear()

    override fun cancelAllMedicines() {
        shown.removeAll { it.startsWith("dose/") }
    }

    override fun cancelAllAppointments() {
        shown.removeAll { it.startsWith("appointment/") }
    }

    companion object {
        fun doseTag(dose: Dose) = "dose/${dose.medicineId}/${dose.date.epochDay}/${dose.time.minutes}"
    }
}

class InMemoryReminderSettings(initial: ReminderPrefs = ReminderPrefs()) : ReminderSettings {
    val state = MutableStateFlow(initial)
    var clearCount = 0

    override val prefs: Flow<ReminderPrefs> = state

    override suspend fun setMedicineReminders(enabled: Boolean) = state.update { it.copy(medicineReminders = enabled) }

    override suspend fun setAppointmentReminders(enabled: Boolean) = state.update { it.copy(appointmentReminders = enabled) }

    override suspend fun addSetupItemsShown(keys: Set<String>) = state.update { it.copy(setupItemsShown = it.setupItemsShown + keys) }

    override suspend fun clear() {
        clearCount++
        state.value = ReminderPrefs()
    }

    val ownerState = MutableStateFlow<String?>(null)

    override val owner: Flow<String?> = ownerState

    override suspend fun setOwner(uid: String?) {
        ownerState.value = uid
    }
}

/** The reminder screens' repository, in memory (no alarms). */
class FakeReminderRepository(
    medicines: List<Medicine> = emptyList(),
    appointments: List<AppointmentReminder> = emptyList(),
    prefs: ReminderPrefs = ReminderPrefs(),
) : ReminderRepository {
    val medicineState = MutableStateFlow(medicines)
    val records = MutableStateFlow<List<DoseRecord>>(emptyList())
    val prefsState = MutableStateFlow(prefs)
    private val appointmentState = MutableStateFlow(appointments)
    var failNext = false
    val calls = mutableListOf<String>()

    override val medicines: Flow<List<Medicine>> = medicineState
    override val appointments: Flow<List<AppointmentReminder>> = appointmentState
    override val prefs: Flow<ReminderPrefs> = prefsState

    override fun recordsOn(date: CalendarDate): Flow<List<DoseRecord>> = records.map { all -> all.filter { it.dose.date == date } }

    override fun recordsFrom(date: CalendarDate): Flow<List<DoseRecord>> = records.map { all -> all.filter { it.dose.date >= date } }

    override suspend fun medicine(id: Long): Medicine? = medicineState.value.firstOrNull { it.id == id }

    override suspend fun saveMedicine(medicine: Medicine): Long {
        check()
        val id = if (medicine.id == 0L) (medicineState.value.maxOfOrNull { it.id } ?: 0) + 1 else medicine.id
        medicineState.update { list -> list.filter { it.id != id } + medicine.copy(id = id) }
        calls += "save:$id"
        return id
    }

    override suspend fun deleteMedicine(id: Long) {
        check()
        medicineState.update { list -> list.filter { it.id != id } }
        calls += "delete:$id"
    }

    override suspend fun setTaken(dose: Dose, taken: Boolean) {
        check()
        records.update { list -> list.filter { it.dose != dose } + DoseRecord(dose, if (taken) 1L else null, null) }
        calls += "taken:$taken"
    }

    override suspend fun setMedicineReminders(enabled: Boolean) {
        check()
        prefsState.update { it.copy(medicineReminders = enabled) }
        calls += "medicineReminders:$enabled"
    }

    override suspend fun setAppointmentReminders(enabled: Boolean) {
        check()
        prefsState.update { it.copy(appointmentReminders = enabled) }
        calls += "appointmentReminders:$enabled"
    }

    override suspend fun claimSetupItems(missing: Set<ReminderSetupItem>): Set<ReminderSetupItem> {
        check()
        val shown = prefsState.value.setupItemsShown
        val offered = missing.filterTo(mutableSetOf()) { it.key !in shown }
        prefsState.update { it.copy(setupItemsShown = shown + offered.map { item -> item.key }) }
        return offered
    }

    private fun check() {
        if (failNext) {
            failNext = false
            error("Disk full")
        }
    }
}

/** Reminder ViewModels over a fake, for shell tests (no app container in JVM tests). */
fun fakeReminderViewModels(
    reminders: ReminderRepository = FakeReminderRepository(),
    clock: () -> Long = System::currentTimeMillis,
) = ReminderViewModels.factory { ReminderViewModels.Dependencies(reminders, clock) }
