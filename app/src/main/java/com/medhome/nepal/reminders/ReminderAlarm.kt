package com.medhome.nepal.reminders

import com.medhome.nepal.data.BookingMapper
import com.medhome.nepal.domain.AppointmentAlert
import com.medhome.nepal.domain.AppointmentReminder
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.domain.Dose
import com.medhome.nepal.domain.Medicine
import com.medhome.nepal.domain.TimeOfDay

/**
 * One alarm the app can have set. [key] is its identity: setting an alarm with the same key
 * replaces the earlier one, and cancelling needs only the key. Keys hold IDs only, never names.
 */
sealed interface ReminderAlarm {
    val key: String

    /** A medicine's next dose. One per medicine: when it fires, the following dose is set. */
    data class MedicineDue(val dose: Dose) : ReminderAlarm {
        override val key: String get() = medicineKey(dose.medicineId)
    }

    /** A snoozed dose coming back. */
    data class Snoozed(val dose: Dose) : ReminderAlarm {
        override val key: String get() = "$SNOOZE/${dose.medicineId}/${dose.date.epochDay}/${dose.time.minutes}"
    }

    data class Appointment(val bookingId: String, val alert: AppointmentAlert) : ReminderAlarm {
        init {
            require(BookingMapper.isValidId(bookingId)) { "Not a booking ID" }
        }

        override val key: String get() = "$APPOINTMENT/$bookingId/${alert.key}"
    }

    companion object {
        private const val MEDICINE = "medicine"
        private const val SNOOZE = "snooze"
        private const val APPOINTMENT = "appointment"

        /** The key of [medicineId]'s dose alarm, whichever dose it is set for. */
        fun medicineKey(medicineId: Long): String = "$MEDICINE/$medicineId"

        /** Reads a key back (the alarm's own intent), or null when it isn't one of ours. */
        fun parse(key: String?, doseDay: Long? = null, doseMinute: Int? = null): ReminderAlarm? = runCatching {
            val parts = key?.split('/') ?: return null
            when (parts.firstOrNull()) {
                MEDICINE -> {
                    if (parts.size != 2 || doseDay == null || doseMinute == null) return null
                    MedicineDue(Dose(parts[1].toLong(), CalendarDate.ofEpochDay(doseDay), TimeOfDay(doseMinute)))
                }
                SNOOZE -> {
                    if (parts.size != 4) return null
                    Snoozed(Dose(parts[1].toLong(), CalendarDate.ofEpochDay(parts[2].toLong()), TimeOfDay(parts[3].toInt())))
                }
                APPOINTMENT -> {
                    if (parts.size != 3) return null
                    val alert = AppointmentAlert.fromKey(parts[2]) ?: return null
                    Appointment(parts[1], alert)
                }
                else -> null
            }
        }.getOrNull()
    }
}

/** Sets and cancels alarms. Android's is [AndroidAlarmScheduler]; tests record them. */
interface AlarmScheduler {
    /** Sets [alarm] for [atMillis], replacing one with the same key. Exact when allowed. */
    fun schedule(alarm: ReminderAlarm, atMillis: Long)

    fun cancel(alarm: ReminderAlarm)

    /** Cancels whatever alarm has [key] (a medicine's dose alarm, whichever dose it is for). */
    fun cancelKey(key: String)

    /** False when the phone only allows inexact alarms (Android 12+ without "Alarms & reminders"). */
    fun canScheduleExact(): Boolean
}

/** Shows and removes reminder notifications. Android's is [AndroidReminderNotifier]. */
interface ReminderNotifier {
    fun showDose(medicine: Medicine, dose: Dose)

    fun showAppointment(reminder: AppointmentReminder, alert: AppointmentAlert)

    fun cancelDose(dose: Dose)

    /** Every notification of [medicineId] (deleted or edited). */
    fun cancelMedicine(medicineId: Long)

    fun cancelAppointment(bookingId: String)

    /** Every notification of the app (sign-out, or a kind of reminder turned off). */
    fun cancelAll()

    fun cancelAllMedicines()

    fun cancelAllAppointments()
}
