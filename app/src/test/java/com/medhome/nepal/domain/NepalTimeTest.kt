package com.medhome.nepal.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

class NepalTimeTest {

    @Test
    fun `epoch days round-trip across leap years and centuries`() {
        val start = CalendarDate(2000, 1, 1).epochDay
        for (day in start until start + 365 * 30) {
            assertEquals(day, CalendarDate.ofEpochDay(day).epochDay)
        }
        assertEquals(10_957L, CalendarDate(2000, 1, 1).epochDay)
        assertEquals(CalendarDate(2028, 2, 29), CalendarDate(2028, 2, 28).plusDays(1))
        assertEquals(CalendarDate(2027, 3, 1), CalendarDate(2027, 2, 28).plusDays(1))
        assertEquals(CalendarDate(2027, 1, 1), CalendarDate(2026, 12, 31).plusDays(1))
    }

    @Test
    fun `weekdays are right`() {
        assertEquals(Weekday.THURSDAY, CalendarDate(2026, 10, 8).weekday)
        assertEquals(Weekday.SUNDAY, CalendarDate(2026, 10, 11).weekday)
        assertEquals(Weekday.SATURDAY, CalendarDate(2000, 1, 1).weekday)
    }

    @Test
    fun `only real dates can be built`() {
        for ((month, day) in listOf(2 to 29, 2 to 30, 4 to 31, 13 to 1, 0 to 1, 1 to 0)) {
            assertThrows("$month/$day", IllegalArgumentException::class.java) { CalendarDate(2026, month, day) }
        }
        CalendarDate(2028, 2, 29)
    }

    /** The fixed +05:45 agrees with the time zone database for every hour of a year. */
    @Test
    fun `the fixed offset matches Asia Kathmandu`() {
        val zone = TimeZone.getTimeZone("Asia/Kathmandu")
        val calendar = GregorianCalendar(zone)
        val start = NepalTime.epochMillis(CalendarDate(2026, 1, 1), TimeOfDay(0))
        val hour = 60 * NepalTime.MILLIS_PER_MINUTE
        for (step in 0 until 366 * 24) {
            val instant = start + step * hour + 7 * NepalTime.MILLIS_PER_MINUTE
            calendar.timeInMillis = instant
            val date = NepalTime.dateOf(instant)
            val time = NepalTime.timeOf(instant)
            assertEquals(calendar.get(Calendar.YEAR), date.year)
            assertEquals(calendar.get(Calendar.MONTH) + 1, date.month)
            assertEquals(calendar.get(Calendar.DAY_OF_MONTH), date.day)
            assertEquals(calendar.get(Calendar.HOUR_OF_DAY), time.hour)
            assertEquals(calendar.get(Calendar.MINUTE), time.minute)
            assertEquals(instant - instant % NepalTime.MILLIS_PER_MINUTE, NepalTime.epochMillis(date, time))
        }
    }
}
