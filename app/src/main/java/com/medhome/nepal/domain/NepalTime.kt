package com.medhome.nepal.domain

/**
 * A Gregorian calendar date with no time zone. Its own type because java.time needs API 26
 * (minSdk is 24). Only real dates can be built.
 */
data class CalendarDate(val year: Int, val month: Int, val day: Int) : Comparable<CalendarDate> {
    init {
        require(year in MIN_YEAR..MAX_YEAR && month in 1..MONTHS && day in 1..daysIn(year, month)) {
            "Not a date: $year-$month-$day"
        }
    }

    /** Days since 1970-01-01. */
    val epochDay: Long get() = daysFromCivil(year, month, day)

    val weekday: Weekday get() = Weekday.entries[(epochDay + THURSDAY_ORDINAL).mod(DAYS_PER_WEEK)]

    fun plusDays(days: Int): CalendarDate = ofEpochDay(epochDay + days)

    override fun compareTo(other: CalendarDate): Int = epochDay.compareTo(other.epochDay)

    companion object {
        const val MIN_YEAR = 2000
        const val MAX_YEAR = 2999
        private const val MONTHS = 12
        private const val DAYS_PER_WEEK = 7

        /** 1970-01-01 was a Thursday; Weekday is Sunday first. */
        private const val THURSDAY_ORDINAL = 4

        fun ofEpochDay(epochDay: Long): CalendarDate = civilFromDays(epochDay)

        /** Days in [month] (1-12) of [year]. */
        fun daysIn(year: Int, month: Int): Int = when (month) {
            2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        // Howard Hinnant's civil-date algorithms (proleptic Gregorian, exact for any date).
        private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
            val y = (if (month <= 2) year - 1 else year).toLong()
            val era = y.floorDiv(400L)
            val yearOfEra = y - era * 400
            val shiftedMonth = (month + 9) % 12
            val dayOfYear = (153 * shiftedMonth + 2) / 5 + day - 1
            val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
            return era * 146_097 + dayOfEra - 719_468
        }

        private fun civilFromDays(epochDay: Long): CalendarDate {
            val z = epochDay + 719_468
            val era = z.floorDiv(146_097L)
            val dayOfEra = z - era * 146_097
            val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
            val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
            val shiftedMonth = (5 * dayOfYear + 2) / 153
            val day = (dayOfYear - (153 * shiftedMonth + 2) / 5 + 1).toInt()
            val month = (if (shiftedMonth < 10) shiftedMonth + 3 else shiftedMonth - 9).toInt()
            val year = (yearOfEra + era * 400 + if (month <= 2) 1 else 0).toInt()
            return CalendarDate(year, month, day)
        }
    }
}

/**
 * Nepal time (Asia/Kathmandu): a fixed UTC+05:45, with no daylight saving since 1986. Fixed
 * rather than read from the phone's time zone database, so the app and firestore.rules (which
 * has no time zones) always agree on a slot's date and time.
 */
object NepalTime {
    const val OFFSET_MINUTES = 5 * 60 + 45
    const val MILLIS_PER_MINUTE = 60_000L
    const val MILLIS_PER_DAY = 24 * 60 * MILLIS_PER_MINUTE
    private const val OFFSET_MILLIS = OFFSET_MINUTES * MILLIS_PER_MINUTE

    /** The date in Nepal at [epochMillis]. */
    fun dateOf(epochMillis: Long): CalendarDate =
        CalendarDate.ofEpochDay((epochMillis + OFFSET_MILLIS).floorDiv(MILLIS_PER_DAY))

    /** The wall-clock minute in Nepal at [epochMillis] (seconds dropped). */
    fun timeOf(epochMillis: Long): TimeOfDay =
        TimeOfDay(((epochMillis + OFFSET_MILLIS).mod(MILLIS_PER_DAY) / MILLIS_PER_MINUTE).toInt())

    /** The instant [time] on [date] in Nepal. */
    fun epochMillis(date: CalendarDate, time: TimeOfDay): Long =
        date.epochDay * MILLIS_PER_DAY + time.minutes * MILLIS_PER_MINUTE - OFFSET_MILLIS
}
