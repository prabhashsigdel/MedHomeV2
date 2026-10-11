package com.medhome.nepal.domain

/** Which days a medicine is taken on. */
sealed interface MedicineDays {
    fun includes(day: Weekday): Boolean

    data object EveryDay : MedicineDays {
        override fun includes(day: Weekday): Boolean = true
    }

    /** Only on [days] (never empty). */
    data class Chosen(val days: Set<Weekday>) : MedicineDays {
        init {
            require(days.isNotEmpty()) { "Choose at least one day" }
        }

        override fun includes(day: Weekday): Boolean = day in days
    }
}

/**
 * A medicine the patient takes, with its reminder times. Stored on this phone only, never in
 * Firestore: the name is health data. Times and dates are Nepal time, like every other time in
 * the app, so a reminder means the same wall-clock time whatever the phone's time zone.
 */
data class Medicine(
    /** 0 until saved. */
    val id: Long,
    val name: String,
    /** Free text: "1 tablet", "5 ml". */
    val dose: String,
    /** Distinct and sorted, [MIN_TIMES] to [MAX_TIMES] of them. */
    val times: List<TimeOfDay>,
    val days: MedicineDays,
    val startDate: CalendarDate,
    /** The last day it is taken, or null to go on until deleted. Never before [startDate]. */
    val endDate: CalendarDate?,
) {
    init {
        require(times.size in MIN_TIMES..MAX_TIMES) { "1 to 6 times a day" }
        require(times == times.distinct().sorted()) { "Times must be distinct and sorted" }
        require(endDate == null || endDate >= startDate) { "Ends before it starts" }
    }

    /** Whether it is taken on [date] at all. */
    fun isDueOn(date: CalendarDate): Boolean =
        date >= startDate && (endDate == null || date <= endDate) && days.includes(date.weekday)

    companion object {
        const val MIN_TIMES = 1
        const val MAX_TIMES = 6
        const val MAX_NAME_LENGTH = 60
        const val MAX_DOSE_LENGTH = 40
    }
}

/** One scheduled intake: [medicineId] at [time] on [date] (Nepal time). */
data class Dose(val medicineId: Long, val date: CalendarDate, val time: TimeOfDay) {
    val atMillis: Long get() = NepalTime.epochMillis(date, time)
}

/**
 * One dose as scheduled on its day, from the day's log: the medicine's [name] and [amount] (its
 * dose text) as they were then, and what happened to it. Editing or deleting the medicine later
 * doesn't change it, so the history shows what was actually scheduled and taken.
 */
data class DoseRecord(
    val dose: Dose,
    val name: String,
    val amount: String,
    val takenAtMillis: Long?,
    /** When a pending snooze fires; at or before now means it has fired. */
    val snoozedUntilMillis: Long?,
)

enum class DoseState { TAKEN, UPCOMING, MISSED }

/** A logged dose shown in a list, with its state now. */
data class TodayDose(val dose: Dose, val name: String, val amount: String, val state: DoseState)

object DoseSchedule {
    /** A dose not marked taken this long after its time is missed. */
    const val MISSED_AFTER_MINUTES = 60

    /** Snoozing pushes the reminder back this long. */
    const val SNOOZE_MINUTES = 10

    /**
     * Taken once marked; otherwise missed from [MISSED_AFTER_MINUTES] after its time, except
     * while a snooze is pending (a snoozed dose stays upcoming until the snooze fires). Each
     * day's doses are separate, so the states start afresh every day.
     */
    fun state(dose: Dose, record: DoseRecord?, nowMillis: Long): DoseState {
        if (record?.takenAtMillis != null) return DoseState.TAKEN
        val snoozePending = record?.snoozedUntilMillis?.let { it > nowMillis } == true
        val pastGrace = nowMillis >= dose.atMillis + MISSED_AFTER_MINUTES * NepalTime.MILLIS_PER_MINUTE
        return if (pastGrace && !snoozePending) DoseState.MISSED else DoseState.UPCOMING
    }

    /** [medicine]'s doses on [date], in time order (none on days it isn't taken). */
    fun dosesOn(medicine: Medicine, date: CalendarDate): List<Dose> =
        if (medicine.isDueOn(date)) medicine.times.map { Dose(medicine.id, date, it) } else emptyList()

    /** [medicines]' doses on [date] as that day's log first records them: scheduled, not taken. */
    fun logFor(medicines: List<Medicine>, date: CalendarDate): List<DoseRecord> =
        medicines.flatMap { medicine ->
            dosesOn(medicine, date).map { DoseRecord(it, medicine.name, medicine.dose, takenAtMillis = null, snoozedUntilMillis = null) }
        }

    /**
     * What an edit of [medicine] (null: deleted) changes in its logged doses of today, [today]:
     * only the ones still upcoming at [nowMillis] go, replaced by the new schedule's doses that
     * aren't missed yet; taken and missed ones stay as they were (a new dose at the same time as
     * one of those is left out). Days before today are never changed.
     */
    fun replanToday(
        today: List<DoseRecord>,
        medicine: Medicine?,
        date: CalendarDate,
        nowMillis: Long,
    ): Replan {
        val (upcoming, settled) = today.partition { state(it.dose, it, nowMillis) == DoseState.UPCOMING }
        val settledTimes = settled.mapTo(mutableSetOf()) { it.dose.time }
        val added = medicine?.let { logFor(listOf(it), date) }.orEmpty()
            .filter { it.dose.time !in settledTimes && state(it.dose, it, nowMillis) == DoseState.UPCOMING }
        return Replan(remove = upcoming, add = added)
    }

    /** See [replanToday]. */
    data class Replan(val remove: List<DoseRecord>, val add: List<DoseRecord>)

    /** [records] with their state at [nowMillis], in time order (then by name). */
    fun listed(records: List<DoseRecord>, nowMillis: Long): List<TodayDose> =
        records
            .sortedWith(compareBy({ it.dose.time }, { it.name.lowercase() }, { it.dose.medicineId }))
            .map { TodayDose(it.dose, it.name, it.amount, state(it.dose, it, nowMillis)) }

    /**
     * The logged doses from [from] to [to] (inclusive, Nepal dates) with their state at
     * [nowMillis], grouped by day, latest day first and each day in time order. Days with no
     * doses are left out. Read from the log, so later edits and deletions don't change them.
     */
    fun history(
        records: List<DoseRecord>,
        from: CalendarDate,
        to: CalendarDate,
        nowMillis: Long,
    ): List<Pair<CalendarDate, List<TodayDose>>> =
        records
            .filter { it.dose.date in from..to }
            .groupBy { it.dose.date }
            .toSortedMap(compareByDescending { it })
            .map { (day, dayRecords) -> day to listed(dayRecords, nowMillis) }

    /**
     * The first dose of [medicine] strictly after [afterMillis], or null when there is none
     * (it has ended). Looks at most a week past the later of today and the start date, which
     * always holds a due day when one exists.
     */
    fun nextDose(medicine: Medicine, afterMillis: Long): Dose? {
        val today = NepalTime.dateOf(afterMillis)
        val first = maxOf(today, medicine.startDate)
        for (offset in 0..DAYS_PER_WEEK) {
            val date = first.plusDays(offset)
            if (medicine.endDate != null && date > medicine.endDate) return null
            dosesOn(medicine, date).firstOrNull { it.atMillis > afterMillis }?.let { return it }
        }
        return null
    }

    /** Suggested times for [count] doses a day, spread over waking hours. */
    fun defaultTimes(count: Int): List<TimeOfDay> {
        require(count in Medicine.MIN_TIMES..Medicine.MAX_TIMES) { "1 to 6 times a day" }
        val hours = when (count) {
            1 -> listOf(8)
            2 -> listOf(8, 20)
            3 -> listOf(8, 14, 20)
            4 -> listOf(8, 12, 16, 20)
            5 -> listOf(7, 10, 13, 16, 19)
            else -> listOf(6, 9, 12, 15, 18, 21)
        }
        return hours.map { TimeOfDay(it * TimeOfDay.MINUTES_PER_HOUR) }
    }

    private const val DAYS_PER_WEEK = 7
}
