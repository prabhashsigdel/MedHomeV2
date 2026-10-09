package com.medhome.nepal.domain

import java.util.Locale

/** One bookable appointment time. [id] is its slot lock's document ID. */
data class Slot(val doctorId: String, val date: CalendarDate, val start: TimeOfDay) {
    val id: String get() = Slots.slotId(doctorId, date, start)
    val startAtMillis: Long get() = NepalTime.epochMillis(date, start)
}

/** A day in the booking window and its free slots (empty when closed or fully booked). */
data class DaySlots(val date: CalendarDate, val slots: List<Slot>)

/** How the slot grid groups a day. Boundaries are Nepal time. */
enum class DayPeriod {
    MORNING,
    AFTERNOON,
    EVENING,
    ;

    companion object {
        private val NOON = TimeOfDay(12 * TimeOfDay.MINUTES_PER_HOUR)
        private val FIVE_PM = TimeOfDay(17 * TimeOfDay.MINUTES_PER_HOUR)

        fun of(time: TimeOfDay): DayPeriod = when {
            time < NOON -> MORNING
            time < FIVE_PM -> AFTERNOON
            else -> EVENING
        }
    }
}

/**
 * Appointment slots. Slots are never stored: they are generated from a doctor's weekly
 * schedule. A booked slot has a lock document whose ID is [slotId], so the same slot can only
 * be booked once (a second create hits an existing document). firestore.rules checks the same
 * things: the ID matches the time, and the time is on the doctor's schedule.
 */
object Slots {

    /** Days offered, today included. */
    const val BOOKING_DAYS = 14

    /** Slots starting sooner than this are not offered (time to get there). */
    const val LEAD_MINUTES = 60

    /**
     * Start times of the [slotMinutes]-long slots in [ranges], in order. A slot must end within
     * its range; leftover minutes at the end of a range are not a slot.
     */
    fun startTimes(ranges: List<TimeRange>, slotMinutes: Int): List<TimeOfDay> {
        require(slotMinutes > 0) { "slotMinutes must be positive" }
        return ranges.flatMap { range ->
            generateSequence(range.start.minutes) { it + slotMinutes }
                .takeWhile { it + slotMinutes <= range.end.minutes }
                .map(::TimeOfDay)
                .toList()
        }
    }

    /**
     * The next [BOOKING_DAYS] days in Nepal, today first, each with its free slots: on the
     * doctor's schedule, starting at least [LEAD_MINUTES] after [nowMillis], and not [taken].
     */
    fun upcoming(doctor: Doctor, nowMillis: Long, taken: Set<String>): List<DaySlots> {
        val today = NepalTime.dateOf(nowMillis)
        return (0 until BOOKING_DAYS).map { offset ->
            val date = today.plusDays(offset)
            val slots = slotsOn(doctor, date)
                .filter { !isTooSoon(it, nowMillis) && it.id !in taken }
            DaySlots(date, slots)
        }
    }

    /** Whether [slot] is still offered at [nowMillis] (ignoring whether it is taken). */
    fun isOffered(doctor: Doctor, slot: Slot, nowMillis: Long): Boolean =
        slot.doctorId == doctor.id &&
            slot.date in windowDates(nowMillis) &&
            !isTooSoon(slot, nowMillis) &&
            slot.start in startTimes(doctor.weeklySchedule[slot.date.weekday].orEmpty(), doctor.slotMinutes)

    /** Instants covering the whole booking window: from now to the end of its last day. */
    fun windowMillis(nowMillis: Long): LongRange {
        val lastDay = NepalTime.dateOf(nowMillis).plusDays(BOOKING_DAYS)
        return nowMillis until NepalTime.epochMillis(lastDay, TimeOfDay(0))
    }

    /**
     * The slot lock ID: "{doctorId}_{yyyyMMdd}_{HHmm}", e.g. "doc-001_20261012_1030". The date
     * and time are Nepal time.
     */
    fun slotId(doctorId: String, date: CalendarDate, start: TimeOfDay): String {
        require(Doctor.isValidId(doctorId)) { "Not a doctor ID: $doctorId" }
        // Locale.ROOT: an ID never takes the phone's digits (Devanagari in Nepali).
        val day = String.format(Locale.ROOT, "%04d%02d%02d", date.year, date.month, date.day)
        val time = String.format(Locale.ROOT, "%02d%02d", start.hour, start.minute)
        return "${doctorId}_${day}_$time"
    }

    private fun slotsOn(doctor: Doctor, date: CalendarDate): List<Slot> =
        startTimes(doctor.weeklySchedule[date.weekday].orEmpty(), doctor.slotMinutes)
            .map { Slot(doctor.id, date, it) }

    /** True when [slot] starts within the lead time (or has started). */
    private fun isTooSoon(slot: Slot, nowMillis: Long): Boolean =
        slot.startAtMillis < nowMillis + LEAD_MINUTES * NepalTime.MILLIS_PER_MINUTE

    private fun windowDates(nowMillis: Long): ClosedRange<CalendarDate> {
        val today = NepalTime.dateOf(nowMillis)
        return today..today.plusDays(BOOKING_DAYS - 1)
    }
}
