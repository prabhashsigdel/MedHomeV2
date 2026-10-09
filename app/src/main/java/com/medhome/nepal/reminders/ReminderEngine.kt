package com.medhome.nepal.reminders

import com.medhome.nepal.data.BookingMapper
import com.medhome.nepal.data.MedicineEntity
import com.medhome.nepal.data.ReminderDao
import com.medhome.nepal.data.ReminderMapper
import com.medhome.nepal.data.ReminderPrefs
import com.medhome.nepal.data.ReminderSettings
import com.medhome.nepal.domain.AppointmentAlert
import com.medhome.nepal.domain.AppointmentReminder
import com.medhome.nepal.domain.AppointmentReminders
import com.medhome.nepal.domain.Booking
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.domain.Dose
import com.medhome.nepal.domain.DoseRecord
import com.medhome.nepal.domain.DoseSchedule
import com.medhome.nepal.domain.Medicine
import com.medhome.nepal.domain.NepalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the reminder screens read and change. */
interface ReminderRepository {
    /** Every medicine, by name. */
    val medicines: Flow<List<Medicine>>

    /** The upcoming appointments that have reminders, soonest first. */
    val appointments: Flow<List<AppointmentReminder>>

    val prefs: Flow<ReminderPrefs>

    /** What happened to the doses of [date] (taken, snoozed). */
    fun recordsOn(date: CalendarDate): Flow<List<DoseRecord>>

    suspend fun medicine(id: Long): Medicine?

    /** Adds ([Medicine.id] 0) or replaces a medicine and sets its next reminder. Returns its ID. */
    suspend fun saveMedicine(medicine: Medicine): Long

    suspend fun deleteMedicine(id: Long)

    /** Marks [dose] taken (or not, to undo), removing its notification and any snooze. */
    suspend fun setTaken(dose: Dose, taken: Boolean)

    /** On: every medicine reminder is set. Off: all are cancelled and their notifications go. */
    suspend fun setMedicineReminders(enabled: Boolean)

    /** On: every appointment reminder is set. Off: all are cancelled and their notifications go. */
    suspend fun setAppointmentReminders(enabled: Boolean)

    /** True the first time only: the battery guide opens by itself once. */
    suspend fun claimBatteryGuide(): Boolean
}

/**
 * The reminder logic, between the local database, the alarms and the notifications. Every change
 * takes one lock, so a sign-out wipe can't interleave with an alarm, a snooze or a bookings sync:
 * whatever runs after the wipe finds nothing left to remind of. Alarms are only set for rows
 * that exist, so nothing outlives its data.
 *
 * [currentPatientUid] is the signed-in patient (null otherwise); new data is only accepted for
 * them, so a sync or booking finishing after sign-out adds nothing. [hasAccount] says whether
 * Firebase still holds a signed-in account on this phone (known at once, even in a receiver
 * before the session has loaded): when it doesn't, an alarm or a reschedule wipes instead, so
 * reminders left by a sign-out that didn't finish (process killed) never ring for anyone.
 */
class ReminderEngine(
    private val dao: ReminderDao,
    private val settings: ReminderSettings,
    private val scheduler: AlarmScheduler,
    private val notifier: ReminderNotifier,
    private val currentPatientUid: () -> String?,
    private val hasAccount: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) : ReminderRepository {

    private val lock = Mutex()

    override val medicines: Flow<List<Medicine>> =
        dao.observeMedicines().map { rows -> rows.mapNotNull(ReminderMapper::toMedicine) }

    override val appointments: Flow<List<AppointmentReminder>> =
        dao.observeAppointments().map { rows -> rows.mapNotNull(ReminderMapper::toAppointment) }

    override val prefs: Flow<ReminderPrefs> = settings.prefs

    override fun recordsOn(date: CalendarDate): Flow<List<DoseRecord>> =
        dao.observeRecordsOn(date.epochDay).map { rows -> rows.mapNotNull(ReminderMapper::toRecord) }

    override suspend fun medicine(id: Long): Medicine? = dao.medicine(id)?.let(ReminderMapper::toMedicine)

    override suspend fun saveMedicine(medicine: Medicine): Long = locked {
        checkNotNull(currentPatientUid()) { "Not signed in" }
        val entity = ReminderMapper.toEntity(medicine)
        val id = if (medicine.id == 0L) {
            dao.insertMedicine(entity.copy(id = 0))
        } else {
            check(dao.updateMedicine(entity) == 1) { "No such medicine" }
            medicine.id
        }
        // Old times may be gone: drop their snoozes and notifications, then set the next dose.
        clearSnoozes(id)
        notifier.cancelMedicine(id)
        val saved = medicine.copy(id = id)
        if (settings.current().medicineReminders) scheduleNextDose(saved, after = clock()) else clearDoseAlarm(id)
        id
    }

    override suspend fun deleteMedicine(id: Long) = locked {
        clearSnoozes(id)
        scheduler.cancelKey(ReminderAlarm.medicineKey(id))
        notifier.cancelMedicine(id)
        dao.deleteMedicine(id)
    }

    override suspend fun setTaken(dose: Dose, taken: Boolean) = locked {
        dao.medicine(dose.medicineId) ?: return@locked
        val current = dao.record(dose.medicineId, dose.date.epochDay, dose.time.minutes)?.let(ReminderMapper::toRecord)
        if (current?.snoozedUntilMillis != null) scheduler.cancel(ReminderAlarm.Snoozed(dose))
        val record = DoseRecord(dose, takenAtMillis = if (taken) clock() else null, snoozedUntilMillis = null)
        dao.upsertRecord(ReminderMapper.toEntity(record))
        if (taken) notifier.cancelDose(dose)
    }

    /** The notification's Snooze: the reminder comes back in [DoseSchedule.SNOOZE_MINUTES]. */
    suspend fun snooze(dose: Dose) = locked {
        dao.medicine(dose.medicineId) ?: return@locked
        val current = dao.record(dose.medicineId, dose.date.epochDay, dose.time.minutes)?.let(ReminderMapper::toRecord)
        notifier.cancelDose(dose)
        if (current?.takenAtMillis != null || !settings.current().medicineReminders) return@locked
        val until = clock() + DoseSchedule.SNOOZE_MINUTES * NepalTime.MILLIS_PER_MINUTE
        dao.upsertRecord(ReminderMapper.toEntity(DoseRecord(dose, takenAtMillis = null, snoozedUntilMillis = until)))
        scheduler.schedule(ReminderAlarm.Snoozed(dose), until)
    }

    /** An alarm went off. Shows its reminder if it is still wanted, and sets the next one. */
    suspend fun onAlarm(alarm: ReminderAlarm) = locked {
        if (!hasAccount()) return@locked wipeLocked()
        val prefs = settings.current()
        when (alarm) {
            is ReminderAlarm.MedicineDue -> onDoseDue(alarm.dose, prefs)
            is ReminderAlarm.Snoozed -> onSnoozeDue(alarm.dose, prefs)
            is ReminderAlarm.Appointment -> onAppointmentDue(alarm.bookingId, alarm.alert, prefs)
        }
    }

    /**
     * Sets every alarm again from the database: after a reboot, an app update, a clock or time
     * zone change, when exact alarms become allowed, and at app start (a force stop cancels all
     * alarms). A dose alarm that hasn't fired yet keeps its dose, even if a little late.
     */
    suspend fun rescheduleAll() = locked {
        if (!hasAccount()) return@locked wipeLocked()
        val now = clock()
        val prefs = settings.current()
        dao.deleteRecordsBefore(NepalTime.dateOf(now).plusDays(-KEEP_RECORD_DAYS).epochDay)
        dao.medicines().forEach { entity ->
            if (prefs.medicineReminders) restoreDoseAlarm(entity, now) else clearDoseAlarm(entity.id)
        }
        restoreSnoozes(now, prefs)
        dao.appointments().forEach { entity ->
            val reminder = ReminderMapper.toAppointment(entity)
            if (reminder == null || reminder.startAtMillis <= now) {
                forgetAppointment(entity.bookingId)
            } else {
                scheduleAppointment(reminder, now, prefs.appointmentReminders)
            }
        }
    }

    override suspend fun setMedicineReminders(enabled: Boolean) = locked {
        settings.setMedicineReminders(enabled)
        val now = clock()
        if (enabled) {
            dao.medicines().forEach { restoreDoseAlarm(it, now) }
        } else {
            dao.medicines().forEach { clearDoseAlarm(it.id) }
            dao.snoozedRecords().forEach { row -> ReminderMapper.toRecord(row)?.let { clearSnooze(it) } }
            notifier.cancelAllMedicines()
        }
    }

    override suspend fun setAppointmentReminders(enabled: Boolean) = locked {
        settings.setAppointmentReminders(enabled)
        val now = clock()
        dao.appointments().mapNotNull(ReminderMapper::toAppointment).forEach { scheduleAppointment(it, now, enabled) }
        if (!enabled) notifier.cancelAllAppointments()
    }

    override suspend fun claimBatteryGuide(): Boolean = locked {
        if (settings.current().batteryGuideShown) return@locked false
        settings.setBatteryGuideShown()
        true
    }

    /**
     * Makes the stored appointments match [uid]'s bookings (the listener's whole list): new and
     * moved ones are (re)scheduled, cancelled and started ones dropped. Ignored when [uid] is no
     * longer the signed-in patient. An answer [fromCache] only adds: the cache can be missing
     * bookings (cleared, or never synced), and that must not cancel real reminders.
     */
    suspend fun syncAppointments(uid: String, bookings: List<Booking>, fromCache: Boolean) = locked {
        if (currentPatientUid() != uid) return@locked
        val now = clock()
        val stored = dao.appointments().mapNotNull(ReminderMapper::toAppointment)
        val plan = AppointmentReminders.plan(stored, bookings, now)
        if (!fromCache) plan.remove.forEach { forgetAppointment(it) }
        val enabled = settings.current().appointmentReminders
        plan.upsert.forEach { reminder ->
            dao.upsertAppointment(ReminderMapper.toEntity(reminder))
            scheduleAppointment(reminder, now, enabled)
        }
    }

    /** Just booked: remind at once, before the listener reports it (it fills in the doctor's name). */
    suspend fun addAppointment(bookingId: String, startAtMillis: Long, doctorName: String) = locked {
        if (currentPatientUid() == null) return@locked
        val known = dao.appointment(bookingId)?.let(ReminderMapper::toAppointment)
        val reminder = AppointmentReminder(bookingId, startAtMillis, doctorName.ifEmpty { known?.doctorName.orEmpty() })
        dao.upsertAppointment(ReminderMapper.toEntity(reminder))
        scheduleAppointment(reminder, clock(), settings.current().appointmentReminders)
    }

    /** Just cancelled here: its reminders go now, without waiting for the listener. */
    suspend fun removeAppointment(bookingId: String) = locked { forgetAppointment(bookingId) }

    /**
     * Sign-out and account deletion: every alarm cancelled, every notification removed, the
     * database emptied and the switches reset. Alarms are cancelled from the rows, so first.
     */
    suspend fun wipe() = locked { wipeLocked() }

    /** Every step runs even if an earlier one fails (the data must go); the first failure is rethrown. */
    private suspend fun wipeLocked() {
        var failure: Exception? = null
        suspend fun step(block: suspend () -> Unit) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (failure == null) failure = e
            }
        }
        step { dao.medicines().forEach { scheduler.cancelKey(ReminderAlarm.medicineKey(it.id)) } }
        step { dao.snoozedRecords().forEach { row -> ReminderMapper.toRecord(row)?.let { scheduler.cancel(ReminderAlarm.Snoozed(it.dose)) } } }
        step { dao.appointments().forEach { row -> cancelAppointmentAlarms(row.bookingId) } }
        step { notifier.cancelAll() }
        step { dao.deleteAll() }
        step { settings.clear() }
        failure?.let { throw it }
    }

    // Medicines

    private suspend fun onDoseDue(dose: Dose, prefs: ReminderPrefs) {
        val entity = dao.medicine(dose.medicineId) ?: return
        // Fired, so no alarm is set until the next one below.
        dao.setAlarmDose(entity.id, null)
        val medicine = ReminderMapper.toMedicine(entity) ?: return
        if (!prefs.medicineReminders) return
        // The next one first: a notification that fails to post must not end the chain.
        scheduleNextDose(medicine, after = maxOf(clock(), dose.atMillis))
        if (dose in DoseSchedule.dosesOn(medicine, dose.date) && !isTaken(dose)) notifier.showDose(medicine, dose)
    }

    private suspend fun onSnoozeDue(dose: Dose, prefs: ReminderPrefs) {
        val medicine = dao.medicine(dose.medicineId)?.let(ReminderMapper::toMedicine) ?: return
        if (!prefs.medicineReminders || isTaken(dose)) return
        if (dose in DoseSchedule.dosesOn(medicine, dose.date)) notifier.showDose(medicine, dose)
    }

    private suspend fun isTaken(dose: Dose): Boolean =
        dao.record(dose.medicineId, dose.date.epochDay, dose.time.minutes)?.takenAtMillis != null

    private suspend fun scheduleNextDose(medicine: Medicine, after: Long) {
        val next = DoseSchedule.nextDose(medicine, after)
        if (next == null) {
            clearDoseAlarm(medicine.id)
            return
        }
        scheduler.schedule(ReminderAlarm.MedicineDue(next), next.atMillis)
        dao.setAlarmDose(medicine.id, next.atMillis)
    }

    /**
     * Re-sets [entity]'s dose alarm. One that was set but hasn't fired (late, or the phone was
     * off) keeps its dose while that dose isn't missed yet, so the reminder still comes; else the
     * next dose from now.
     */
    private suspend fun restoreDoseAlarm(entity: MedicineEntity, now: Long) {
        val medicine = ReminderMapper.toMedicine(entity)
        if (medicine == null) {
            clearDoseAlarm(entity.id)
            return
        }
        val pending = entity.alarmDoseAtMillis?.let { at ->
            Dose(medicine.id, NepalTime.dateOf(at), NepalTime.timeOf(at))
                .takeIf { it.atMillis == at && it in DoseSchedule.dosesOn(medicine, it.date) }
        }
        val stillDue = pending != null &&
            now < pending.atMillis + DoseSchedule.MISSED_AFTER_MINUTES * NepalTime.MILLIS_PER_MINUTE &&
            !isTaken(pending)
        if (pending != null && stillDue) {
            scheduler.schedule(ReminderAlarm.MedicineDue(pending), pending.atMillis)
        } else {
            scheduleNextDose(medicine, after = now)
        }
    }

    private suspend fun clearDoseAlarm(medicineId: Long) {
        scheduler.cancelKey(ReminderAlarm.medicineKey(medicineId))
        dao.setAlarmDose(medicineId, null)
    }

    private suspend fun clearSnoozes(medicineId: Long) {
        dao.snoozedRecords()
            .filter { it.medicineId == medicineId }
            .forEach { row -> ReminderMapper.toRecord(row)?.let { clearSnooze(it) } }
    }

    private suspend fun clearSnooze(record: DoseRecord) {
        scheduler.cancel(ReminderAlarm.Snoozed(record.dose))
        dao.upsertRecord(ReminderMapper.toEntity(record.copy(snoozedUntilMillis = null)))
    }

    /** Pending snoozes are set again; ones that came due while the phone was off are dropped. */
    private suspend fun restoreSnoozes(now: Long, prefs: ReminderPrefs) {
        dao.snoozedRecords().mapNotNull(ReminderMapper::toRecord).forEach { record ->
            val until = record.snoozedUntilMillis ?: return@forEach
            if (prefs.medicineReminders && until > now && record.takenAtMillis == null) {
                scheduler.schedule(ReminderAlarm.Snoozed(record.dose), until)
            } else {
                clearSnooze(record)
            }
        }
    }

    // Appointments

    private suspend fun onAppointmentDue(bookingId: String, alert: AppointmentAlert, prefs: ReminderPrefs) {
        val reminder = dao.appointment(bookingId)?.let(ReminderMapper::toAppointment) ?: return
        if (prefs.appointmentReminders && reminder.startAtMillis > clock()) notifier.showAppointment(reminder, alert)
    }

    /** Sets the alerts still to come (all of them cancelled when [enabled] is false). */
    private fun scheduleAppointment(reminder: AppointmentReminder, now: Long, enabled: Boolean) {
        val pending = if (enabled) AppointmentReminders.pendingAlerts(reminder.startAtMillis, now) else emptyMap()
        AppointmentAlert.entries.forEach { alert ->
            val alarm = ReminderAlarm.Appointment(reminder.bookingId, alert)
            val at = pending[alert]
            if (at != null) scheduler.schedule(alarm, at) else scheduler.cancel(alarm)
        }
    }

    private suspend fun forgetAppointment(bookingId: String) {
        cancelAppointmentAlarms(bookingId)
        notifier.cancelAppointment(bookingId)
        dao.deleteAppointment(bookingId)
    }

    private fun cancelAppointmentAlarms(bookingId: String) {
        // A row with a bad ID never had alarms (alarm keys need a valid ID).
        if (!BookingMapper.isValidId(bookingId)) return
        AppointmentAlert.entries.forEach { scheduler.cancel(ReminderAlarm.Appointment(bookingId, it)) }
    }

    private suspend fun <T> locked(block: suspend () -> T): T = lock.withLock { block() }

    private companion object {
        /** Dose records older than this many days are deleted (only today's are shown). */
        const val KEEP_RECORD_DAYS = 7
    }
}
