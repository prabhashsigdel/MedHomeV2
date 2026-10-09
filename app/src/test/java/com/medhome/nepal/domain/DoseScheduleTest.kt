package com.medhome.nepal.domain

import com.medhome.nepal.fakes.medicine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.TimeZone

class DoseScheduleTest {

    private val thursday = CalendarDate(2026, 10, 8)
    private val eight = TimeOfDay(8 * 60)
    private val twenty = TimeOfDay(20 * 60)

    private fun at(date: CalendarDate, time: TimeOfDay, plusMinutes: Int = 0) =
        NepalTime.epochMillis(date, time) + plusMinutes * NepalTime.MILLIS_PER_MINUTE

    // Next dose

    @Test
    fun `next dose is later today when one is left`() {
        val med = medicine(times = listOf(eight, twenty))
        assertEquals(Dose(1, thursday, twenty), DoseSchedule.nextDose(med, at(thursday, eight)))
    }

    @Test
    fun `next dose is strictly after the given instant`() {
        val med = medicine(times = listOf(eight))
        assertEquals(Dose(1, thursday.plusDays(1), eight), DoseSchedule.nextDose(med, at(thursday, eight)))
        assertEquals(Dose(1, thursday, eight), DoseSchedule.nextDose(med, at(thursday, eight) - 1))
    }

    @Test
    fun `chosen weekdays skip other days`() {
        // Thursday evening: Monday and Wednesday only, so next is Monday 12 October.
        val med = medicine(times = listOf(eight), days = MedicineDays.Chosen(setOf(Weekday.MONDAY, Weekday.WEDNESDAY)))
        assertEquals(Dose(1, CalendarDate(2026, 10, 12), eight), DoseSchedule.nextDose(med, at(thursday, twenty)))
    }

    @Test
    fun `a single weekday whose time has passed comes back a week later`() {
        val med = medicine(times = listOf(eight), days = MedicineDays.Chosen(setOf(Weekday.THURSDAY)))
        assertEquals(Dose(1, thursday.plusDays(7), eight), DoseSchedule.nextDose(med, at(thursday, eight, plusMinutes = 1)))
    }

    @Test
    fun `nothing after the end date`() {
        val med = medicine(times = listOf(eight), endDate = thursday)
        assertEquals(Dose(1, thursday, eight), DoseSchedule.nextDose(med, at(thursday, TimeOfDay(0))))
        assertNull(DoseSchedule.nextDose(med, at(thursday, eight)))
    }

    @Test
    fun `the end date day itself is included`() {
        val med = medicine(times = listOf(eight, twenty), endDate = thursday)
        assertEquals(Dose(1, thursday, twenty), DoseSchedule.nextDose(med, at(thursday, eight)))
    }

    @Test
    fun `chosen days with none left before the end date give nothing`() {
        // Ends Saturday, taken on Mondays: no Monday left.
        val med = medicine(times = listOf(eight), days = MedicineDays.Chosen(setOf(Weekday.MONDAY)), endDate = thursday.plusDays(2))
        assertNull(DoseSchedule.nextDose(med, at(thursday, TimeOfDay(0))))
    }

    @Test
    fun `a future start waits for the start date`() {
        val med = medicine(times = listOf(eight), startDate = thursday.plusDays(10))
        assertEquals(Dose(1, thursday.plusDays(10), eight), DoseSchedule.nextDose(med, at(thursday, eight)))
    }

    @Test
    fun `a dose is not due before the start or after the end`() {
        val med = medicine(startDate = thursday, endDate = thursday.plusDays(1))
        assertEquals(emptyList<Dose>(), DoseSchedule.dosesOn(med, thursday.plusDays(-1)))
        assertEquals(listOf(Dose(1, thursday, eight)), DoseSchedule.dosesOn(med, thursday))
        assertEquals(emptyList<Dose>(), DoseSchedule.dosesOn(med, thursday.plusDays(2)))
    }

    // Time zones

    @Test
    fun `doses are Nepal wall-clock times whatever the phone's time zone`() {
        val med = medicine(times = listOf(eight))
        val original = TimeZone.getDefault()
        val results = listOf("Asia/Kathmandu", "America/New_York", "Pacific/Kiritimati", "UTC").map { zone ->
            try {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                DoseSchedule.nextDose(med, at(thursday, TimeOfDay(0)))
            } finally {
                TimeZone.setDefault(original)
            }
        }
        assertEquals(List(4) { Dose(1, thursday, eight) }, results)
        // 08:00 Nepal (UTC+05:45) is 02:15 UTC.
        assertEquals(2 * 60 + 15, ((results.first()!!.atMillis / NepalTime.MILLIS_PER_MINUTE) % (24 * 60)).toInt())
    }

    @Test
    fun `no daylight saving shift across a DST change elsewhere`() {
        // The US leaves DST on 1 November 2026; Nepal time has none, so doses stay 24 hours apart.
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val med = medicine(times = listOf(eight))
            var dose = DoseSchedule.nextDose(med, at(CalendarDate(2026, 10, 30), TimeOfDay(0)))!!
            repeat(4) {
                val next = DoseSchedule.nextDose(med, dose.atMillis)!!
                assertEquals(NepalTime.MILLIS_PER_DAY, next.atMillis - dose.atMillis)
                assertEquals(eight, next.time)
                dose = next
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    // States

    @Test
    fun `a dose is upcoming until 60 minutes after its time, then missed`() {
        val dose = Dose(1, thursday, eight)
        assertEquals(DoseState.UPCOMING, DoseSchedule.state(dose, null, at(thursday, eight, plusMinutes = -5)))
        assertEquals(DoseState.UPCOMING, DoseSchedule.state(dose, null, at(thursday, eight, plusMinutes = 59)))
        assertEquals(DoseState.MISSED, DoseSchedule.state(dose, null, at(thursday, eight, plusMinutes = 60)))
    }

    @Test
    fun `a taken dose stays taken`() {
        val dose = Dose(1, thursday, eight)
        val record = DoseRecord(dose, takenAtMillis = at(thursday, eight, plusMinutes = 90), snoozedUntilMillis = null)
        assertEquals(DoseState.TAKEN, DoseSchedule.state(dose, record, at(thursday, twenty)))
    }

    @Test
    fun `a snoozed dose stays upcoming until the snooze fires`() {
        val dose = Dose(1, thursday, eight)
        val snoozedUntil = at(thursday, eight, plusMinutes = 65)
        val record = DoseRecord(dose, takenAtMillis = null, snoozedUntilMillis = snoozedUntil)
        assertEquals(DoseState.UPCOMING, DoseSchedule.state(dose, record, at(thursday, eight, plusMinutes = 64)))
        assertEquals(DoseState.MISSED, DoseSchedule.state(dose, record, snoozedUntil))
    }

    @Test
    fun `states start afresh every day`() {
        val med = medicine(times = listOf(eight))
        val yesterday = DoseRecord(Dose(1, thursday.plusDays(-1), eight), takenAtMillis = 1, snoozedUntilMillis = null)
        val today = DoseSchedule.today(listOf(med), listOf(yesterday), thursday, at(thursday, TimeOfDay(7 * 60)))
        assertEquals(listOf(DoseState.UPCOMING), today.map { it.state })
    }

    @Test
    fun `today lists every medicine's doses in time order`() {
        val a = medicine(id = 1, name = "B", times = listOf(twenty))
        val b = medicine(id = 2, name = "A", times = listOf(eight, twenty))
        val today = DoseSchedule.today(listOf(a, b), emptyList(), thursday, at(thursday, TimeOfDay(0)))
        assertEquals(listOf(2L to eight, 2L to twenty, 1L to twenty), today.map { it.dose.medicineId to it.dose.time })
    }

    // The medicine itself

    @Test
    fun `a medicine needs 1 to 6 distinct sorted times and an end not before the start`() {
        assertThrows(IllegalArgumentException::class.java) { medicine(times = emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { medicine(times = (0 until 7).map { TimeOfDay(it * 60) }) }
        assertThrows(IllegalArgumentException::class.java) { medicine(times = listOf(twenty, eight)) }
        assertThrows(IllegalArgumentException::class.java) { medicine(times = listOf(eight, eight)) }
        assertThrows(IllegalArgumentException::class.java) { medicine(startDate = thursday, endDate = thursday.plusDays(-1)) }
        assertThrows(IllegalArgumentException::class.java) { MedicineDays.Chosen(emptySet()) }
    }

    @Test
    fun `suggested times are distinct and sorted for every count`() {
        (1..6).forEach { count ->
            val times = DoseSchedule.defaultTimes(count)
            assertEquals(count, times.size)
            assertEquals(times.distinct().sorted(), times)
        }
    }
}
