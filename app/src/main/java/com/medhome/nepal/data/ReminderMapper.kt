package com.medhome.nepal.data

import com.medhome.nepal.domain.AppointmentReminder
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.domain.Dose
import com.medhome.nepal.domain.DoseRecord
import com.medhome.nepal.domain.Medicine
import com.medhome.nepal.domain.MedicineDays
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.domain.Weekday

/**
 * Between Room rows and domain types. Rows are only written by this app, but a row that doesn't
 * make sense (a bad time, an impossible date) is dropped rather than crashing a reminder.
 */
object ReminderMapper {
    private const val TIME_SEPARATOR = ","

    fun toEntity(medicine: Medicine): MedicineEntity = MedicineEntity(
        id = medicine.id,
        name = medicine.name,
        dose = medicine.dose,
        times = medicine.times.joinToString(TIME_SEPARATOR) { it.minutes.toString() },
        everyDay = medicine.days is MedicineDays.EveryDay,
        weekdays = when (val days = medicine.days) {
            MedicineDays.EveryDay -> 0
            is MedicineDays.Chosen -> days.days.fold(0) { bits, day -> bits or (1 shl day.ordinal) }
        },
        startEpochDay = medicine.startDate.epochDay,
        endEpochDay = medicine.endDate?.epochDay,
    )

    fun toMedicine(entity: MedicineEntity): Medicine? = runCatching {
        val times = entity.times.split(TIME_SEPARATOR).map { TimeOfDay(it.trim().toInt()) }
        val days = if (entity.everyDay) {
            MedicineDays.EveryDay
        } else {
            MedicineDays.Chosen(Weekday.entries.filter { entity.weekdays and (1 shl it.ordinal) != 0 }.toSet())
        }
        Medicine(
            id = entity.id,
            name = entity.name,
            dose = entity.dose,
            times = times,
            days = days,
            startDate = CalendarDate.ofEpochDay(entity.startEpochDay),
            endDate = entity.endEpochDay?.let(CalendarDate::ofEpochDay),
        )
    }.getOrNull()

    fun toRecord(entity: DoseRecordEntity): DoseRecord? = runCatching {
        DoseRecord(
            dose = Dose(entity.medicineId, CalendarDate.ofEpochDay(entity.epochDay), TimeOfDay(entity.minuteOfDay)),
            takenAtMillis = entity.takenAtMillis,
            snoozedUntilMillis = entity.snoozedUntilMillis,
        )
    }.getOrNull()

    fun toEntity(record: DoseRecord): DoseRecordEntity = DoseRecordEntity(
        medicineId = record.dose.medicineId,
        epochDay = record.dose.date.epochDay,
        minuteOfDay = record.dose.time.minutes,
        takenAtMillis = record.takenAtMillis,
        snoozedUntilMillis = record.snoozedUntilMillis,
    )

    fun toAppointment(entity: AppointmentReminderEntity): AppointmentReminder? =
        if (BookingMapper.isValidId(entity.bookingId)) {
            AppointmentReminder(entity.bookingId, entity.startAtMillis, entity.doctorName)
        } else {
            null
        }

    fun toEntity(reminder: AppointmentReminder): AppointmentReminderEntity =
        AppointmentReminderEntity(reminder.bookingId, reminder.startAtMillis, reminder.doctorName)
}
