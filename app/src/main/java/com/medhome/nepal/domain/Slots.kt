package com.medhome.nepal.domain

import java.util.Locale

/**
 * Appointment slots, ahead of booking (part 2). Slots are never stored: they are generated
 * from a doctor's weekly schedule, and a booking's document ID is derived from its slot, so
 * the same slot can only ever be created once (a second create hits an existing document).
 */
object Slots {

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
     * The booking document ID for a slot: "{doctorId}_{yyyyMMdd}_{HHmm}", e.g.
     * "doc-001_20261012_1030". The date is the calendar date in Asia/Kathmandu.
     */
    fun bookingId(doctorId: String, year: Int, month: Int, day: Int, start: TimeOfDay): String {
        require(Doctor.isValidId(doctorId)) { "Not a doctor ID: $doctorId" }
        require(year in MIN_YEAR..MAX_YEAR && month in 1..MONTHS && day in 1..daysIn(year, month)) { "Not a date" }
        // Locale.ROOT: an ID never takes the phone's digits (Devanagari in Nepali).
        val date = String.format(Locale.ROOT, "%04d%02d%02d", year, month, day)
        val time = String.format(Locale.ROOT, "%02d%02d", start.hour, start.minute)
        return "${doctorId}_${date}_$time"
    }

    /** Days in [month] (1-12) of [year], Gregorian. */
    private fun daysIn(year: Int, month: Int): Int = when (month) {
        2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    private const val MIN_YEAR = 2000
    private const val MAX_YEAR = 2999
    private const val MONTHS = 12
}
