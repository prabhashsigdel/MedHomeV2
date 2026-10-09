package com.medhome.nepal.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Locale

class SlotsTest {

    private fun t(text: String) = requireNotNull(TimeOfDay.parse(text))

    @Test
    fun `slots fill each range and leftover minutes are not a slot`() {
        val ranges = listOf(TimeRange(t("10:00"), t("11:10")), TimeRange(t("15:00"), t("15:30")))
        assertEquals(
            listOf("10:00", "10:30", "15:00").map(::t),
            Slots.startTimes(ranges, slotMinutes = 30),
        )
    }

    @Test
    fun `a range shorter than a slot has none`() {
        assertEquals(emptyList<TimeOfDay>(), Slots.startTimes(listOf(TimeRange(t("10:00"), t("10:10"))), slotMinutes = 15))
    }

    @Test
    fun `the booking id is doctor, date and start time`() {
        assertEquals("doc-001_20261012_1030", Slots.bookingId("doc-001", 2026, 10, 12, t("10:30")))
        assertEquals("doc-001_20260105_0905", Slots.bookingId("doc-001", 2026, 1, 5, t("09:05")))
    }

    @Test
    fun `the booking id never takes the phone's digits`() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ne-NP"))
            assertEquals("doc-001_20261012_1030", Slots.bookingId("doc-001", 2026, 10, 12, t("10:30")))
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun `doctor ids with a slash or an underscore are rejected`() {
        for (bad in listOf("doc/1", "doc_1", "", "x".repeat(65))) {
            assertThrows(bad, IllegalArgumentException::class.java) { Slots.bookingId(bad, 2026, 10, 12, t("10:30")) }
        }
    }

    @Test
    fun `only real calendar dates make a booking id`() {
        assertEquals("doc-001_20280229_1000", Slots.bookingId("doc-001", 2028, 2, 29, t("10:00")))
        for ((month, day) in listOf(2 to 29, 2 to 30, 4 to 31, 13 to 1, 0 to 1, 1 to 0)) {
            assertThrows("$month/$day", IllegalArgumentException::class.java) {
                Slots.bookingId("doc-001", 2026, month, day, t("10:00"))
            }
        }
    }

    @Test
    fun `times parse only as 24-hour HH colon mm`() {
        assertEquals(10 * 60 + 30, TimeOfDay.parse("10:30")?.minutes)
        assertEquals(23 * 60 + 59, TimeOfDay.parse("23:59")?.minutes)
        for (bad in listOf("24:00", "9:30", "10:60", "", "10.30", null)) assertNull(bad, TimeOfDay.parse(bad))
    }
}
