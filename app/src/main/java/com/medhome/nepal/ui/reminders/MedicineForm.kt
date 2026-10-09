package com.medhome.nepal.ui.reminders

import androidx.annotation.StringRes
import com.medhome.nepal.R
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.domain.DoseSchedule
import com.medhome.nepal.domain.Medicine
import com.medhome.nepal.domain.MedicineDays
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.domain.Weekday

/** What the medicine form holds while it is being filled in. Times keep the order they were set. */
data class MedicineFields(
    val name: String = "",
    val dose: String = "",
    val times: List<TimeOfDay> = DoseSchedule.defaultTimes(1),
    val everyDay: Boolean = true,
    val days: Set<Weekday> = emptySet(),
    val startDate: CalendarDate,
    /** Null: no end date. */
    val endDate: CalendarDate? = null,
)

/** One message per field, or null where the field is fine. */
data class MedicineErrors(
    @param:StringRes val name: Int? = null,
    @param:StringRes val dose: Int? = null,
    @param:StringRes val times: Int? = null,
    @param:StringRes val days: Int? = null,
    @param:StringRes val endDate: Int? = null,
) {
    val isEmpty: Boolean get() = listOf(name, dose, times, days, endDate).all { it == null }
}

/** Validation and edits of [MedicineFields], kept apart from the screen so they can be tested. */
object MedicineForm {
    /** Days a new end date is set after the start (a week's course). */
    private const val DEFAULT_COURSE_DAYS = 6

    private val CONTROL = Regex("\\p{Cntrl}")
    private val SPACES = Regex("\\s+")

    fun newFields(today: CalendarDate) = MedicineFields(startDate = today)

    fun fieldsOf(medicine: Medicine) = MedicineFields(
        name = medicine.name,
        dose = medicine.dose,
        times = medicine.times,
        everyDay = medicine.days is MedicineDays.EveryDay,
        days = (medicine.days as? MedicineDays.Chosen)?.days.orEmpty(),
        startDate = medicine.startDate,
        endDate = medicine.endDate,
    )

    /** Text as stored: control characters removed, runs of spaces made one, ends trimmed. */
    fun clean(text: String): String = text.replace(CONTROL, " ").replace(SPACES, " ").trim()

    fun errors(fields: MedicineFields): MedicineErrors {
        val name = clean(fields.name)
        val dose = clean(fields.dose)
        return MedicineErrors(
            name = when {
                name.isEmpty() -> R.string.medicine_error_name_required
                name.length > Medicine.MAX_NAME_LENGTH -> R.string.medicine_error_name_long
                else -> null
            },
            dose = when {
                dose.isEmpty() -> R.string.medicine_error_dose_required
                dose.length > Medicine.MAX_DOSE_LENGTH -> R.string.medicine_error_dose_long
                else -> null
            },
            times = if (fields.times.distinct().size != fields.times.size) R.string.medicine_error_times_same else null,
            days = if (!fields.everyDay && fields.days.isEmpty()) R.string.medicine_error_days else null,
            endDate = if (fields.endDate != null && fields.endDate < fields.startDate) R.string.medicine_error_end_before_start else null,
        )
    }

    /** The medicine to save, or null while [errors] finds a problem. */
    fun toMedicine(id: Long, fields: MedicineFields): Medicine? {
        if (!errors(fields).isEmpty) return null
        return Medicine(
            id = id,
            name = clean(fields.name),
            dose = clean(fields.dose),
            times = fields.times.sorted(),
            days = if (fields.everyDay) MedicineDays.EveryDay else MedicineDays.Chosen(fields.days),
            startDate = fields.startDate,
            endDate = fields.endDate,
        )
    }

    /**
     * Changes how many times a day. Times already set are kept (the earliest ones, when there are
     * fewer now); new ones are taken from the suggested times not in use yet.
     */
    fun withTimesPerDay(fields: MedicineFields, count: Int): MedicineFields {
        require(count in Medicine.MIN_TIMES..Medicine.MAX_TIMES) { "1 to 6 times a day" }
        val kept = fields.times.sorted().take(count)
        val extra = (DoseSchedule.defaultTimes(count) + DoseSchedule.defaultTimes(Medicine.MAX_TIMES))
            .distinct()
            .filter { it !in kept }
            .take(count - kept.size)
        return fields.copy(times = (kept + extra).sorted())
    }

    fun withTime(fields: MedicineFields, index: Int, time: TimeOfDay): MedicineFields {
        if (index !in fields.times.indices) return fields
        return fields.copy(times = fields.times.toMutableList().also { it[index] = time })
    }

    fun withDayToggled(fields: MedicineFields, day: Weekday): MedicineFields =
        fields.copy(days = if (day in fields.days) fields.days - day else fields.days + day)

    /** Turns the end date on (a week after the start) or off. */
    fun withEndDate(fields: MedicineFields, hasEnd: Boolean): MedicineFields =
        fields.copy(endDate = if (hasEnd) fields.endDate ?: fields.startDate.plusDays(DEFAULT_COURSE_DAYS) else null)
}
