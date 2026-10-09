package com.medhome.nepal.domain

import com.medhome.nepal.fakes.doctor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SlotsTest {

    private fun t(text: String) = requireNotNull(TimeOfDay.parse(text))

    /** [time] on 2026-10-[day], Nepal time, as an instant. */
    private fun nepal(day: Int, time: String) = NepalTime.epochMillis(CalendarDate(2026, 10, day), t(time))

    /** Thursday 8 October 2026, 09:00 in Kathmandu (03:15 UTC). */
    private val thursdayNine = nepal(8, "09:00")

    /** Thursdays 09:00-13:00 and Fridays 16:00-19:00, 30-minute slots. */
    private val doctor = doctor().copy(
        slotMinutes = 30,
        weeklySchedule = mapOf(
            Weekday.THURSDAY to listOf(TimeRange(t("09:00"), t("13:00"))),
            Weekday.FRIDAY to listOf(TimeRange(t("16:00"), t("19:00"))),
        ),
    )

    private fun startsOn(days: List<DaySlots>, day: Int) =
        days.first { it.date == CalendarDate(2026, 10, day) }.slots.map { it.start }

    @Test
    fun `slots fill each range and leftover minutes are not a slot`() {
        val ranges = listOf(TimeRange(t("10:00"), t("11:10")), TimeRange(t("15:00"), t("15:30")))
        assertEquals(listOf("10:00", "10:30", "15:00").map(::t), Slots.startTimes(ranges, slotMinutes = 30))
    }

    @Test
    fun `a range shorter than a slot has none`() {
        assertEquals(emptyList<TimeOfDay>(), Slots.startTimes(listOf(TimeRange(t("10:00"), t("10:10"))), slotMinutes = 15))
    }

    @Test
    fun `the window is 14 Nepal days from today`() {
        val days = Slots.upcoming(doctor, thursdayNine, taken = emptySet())
        assertEquals(Slots.BOOKING_DAYS, days.size)
        assertEquals(CalendarDate(2026, 10, 8), days.first().date)
        assertEquals(CalendarDate(2026, 10, 21), days.last().date)
    }

    @Test
    fun `today is the Nepal date, not the UTC one`() {
        // 20:00 UTC on 7 October is already 01:45 on 8 October in Kathmandu.
        val lateUtc = NepalTime.epochMillis(CalendarDate(2026, 10, 8), t("01:45"))
        assertEquals(CalendarDate(2026, 10, 8), Slots.upcoming(doctor, lateUtc, emptySet()).first().date)
        // And 18:14 UTC on 8 October is 23:59 there, still the 8th.
        assertEquals(CalendarDate(2026, 10, 8), NepalTime.dateOf(nepal(8, "23:59")))
        assertEquals(CalendarDate(2026, 10, 9), NepalTime.dateOf(nepal(8, "23:59") + 60_000))
    }

    @Test
    fun `slot times are Nepal wall-clock times on the doctor's weekdays`() {
        val days = Slots.upcoming(doctor, nepal(7, "12:00"), taken = emptySet())
        assertEquals(listOf("09:00", "09:30", "10:00", "10:30", "11:00", "11:30", "12:00", "12:30").map(::t), startsOn(days, 8))
        assertEquals(listOf("16:00", "16:30", "17:00", "17:30", "18:00", "18:30").map(::t), startsOn(days, 9))
        assertEquals(emptyList<TimeOfDay>(), startsOn(days, 10)) // Saturday: closed
        // Each slot's instant is its Nepal time: 09:00 NPT is 03:15 UTC.
        val first = days.first { it.date == CalendarDate(2026, 10, 8) }.slots.first()
        assertEquals(java.time.Instant.parse("2026-10-08T03:15:00Z").toEpochMilli(), first.startAtMillis)
    }

    @Test
    fun `slots starting within the hour or already started are hidden`() {
        val days = Slots.upcoming(doctor, thursdayNine, taken = emptySet())
        // 09:00 starts now and 09:30 within the hour.
        assertEquals(listOf("10:00", "10:30", "11:00", "11:30", "12:00", "12:30").map(::t), startsOn(days, 8))

        val justAfterNoon = Slots.upcoming(doctor, nepal(8, "12:01"), taken = emptySet())
        assertEquals(emptyList<TimeOfDay>(), startsOn(justAfterNoon, 8))
    }

    @Test
    fun `a slot exactly an hour away is still offered`() {
        val days = Slots.upcoming(doctor, nepal(8, "11:30"), taken = emptySet())
        assertEquals(listOf("12:30").map(::t), startsOn(days, 8))
    }

    @Test
    fun `locked slots are hidden`() {
        val taken = setOf(Slot(doctor.id, CalendarDate(2026, 10, 9), t("17:00")).id)
        val days = Slots.upcoming(doctor, thursdayNine, taken)
        assertEquals(listOf("16:00", "16:30", "17:30", "18:00", "18:30").map(::t), startsOn(days, 9))
        // The same time a week later is a different slot.
        assertTrue(t("17:00") in startsOn(days, 16))
    }

    @Test
    fun `only offered slots pass the booking check`() {
        val friday = CalendarDate(2026, 10, 9)
        assertTrue(Slots.isOffered(doctor, Slot(doctor.id, friday, t("16:00")), thursdayNine))
        assertFalse(Slots.isOffered(doctor, Slot(doctor.id, friday, t("16:15")), thursdayNine)) // off the grid
        assertFalse(Slots.isOffered(doctor, Slot(doctor.id, friday, t("19:00")), thursdayNine)) // after hours
        assertFalse(Slots.isOffered(doctor, Slot(doctor.id, CalendarDate(2026, 10, 8), t("09:30")), thursdayNine)) // too soon
        assertFalse(Slots.isOffered(doctor, Slot(doctor.id, CalendarDate(2026, 10, 22), t("09:00")), thursdayNine)) // day 15
        assertFalse(Slots.isOffered(doctor, Slot("doc-999", friday, t("16:00")), thursdayNine)) // someone else's
    }

    @Test
    fun `the window covers the whole last day`() {
        val window = Slots.windowMillis(thursdayNine)
        assertEquals(thursdayNine, window.first)
        assertEquals(nepal(22, "00:00") - 1, window.last)
    }

    @Test
    fun `the slot id is doctor, Nepal date and start time`() {
        assertEquals("doc-001_20261012_1030", Slots.slotId("doc-001", CalendarDate(2026, 10, 12), t("10:30")))
        assertEquals("doc-001_20260105_0905", Slots.slotId("doc-001", CalendarDate(2026, 1, 5), t("09:05")))
        assertEquals("doc-001_20261009_1600", Slot("doc-001", CalendarDate(2026, 10, 9), t("16:00")).id)
    }

    @Test
    fun `the slot id never takes the phone's digits`() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ne-NP"))
            assertEquals("doc-001_20261012_1030", Slots.slotId("doc-001", CalendarDate(2026, 10, 12), t("10:30")))
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun `doctor ids with a slash or an underscore are rejected`() {
        for (bad in listOf("doc/1", "doc_1", "", "x".repeat(65))) {
            assertThrows(bad, IllegalArgumentException::class.java) {
                Slots.slotId(bad, CalendarDate(2026, 10, 12), t("10:30"))
            }
        }
    }

    @Test
    fun `day periods split at noon and five`() {
        assertEquals(DayPeriod.MORNING, DayPeriod.of(t("11:59")))
        assertEquals(DayPeriod.AFTERNOON, DayPeriod.of(t("12:00")))
        assertEquals(DayPeriod.AFTERNOON, DayPeriod.of(t("16:59")))
        assertEquals(DayPeriod.EVENING, DayPeriod.of(t("17:00")))
    }

    @Test
    fun `times parse only as 24-hour HH colon mm`() {
        assertEquals(10 * 60 + 30, TimeOfDay.parse("10:30")?.minutes)
        assertEquals(23 * 60 + 59, TimeOfDay.parse("23:59")?.minutes)
        for (bad in listOf("24:00", "9:30", "10:60", "", "10.30", null)) assertNull(bad, TimeOfDay.parse(bad))
    }
}
