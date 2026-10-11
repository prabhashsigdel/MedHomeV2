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

    private fun logged(dose: Dose, name: String = "Paracetamol", taken: Long? = null, snoozedUntil: Long? = null) =
        DoseRecord(dose, name, "1 tablet", takenAtMillis = taken, snoozedUntilMillis = snoozedUntil)

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
        val record = logged(dose, taken = at(thursday, eight, plusMinutes = 90))
        assertEquals(DoseState.TAKEN, DoseSchedule.state(dose, record, at(thursday, twenty)))
    }

    @Test
    fun `a snoozed dose stays upcoming until the snooze fires`() {
        val dose = Dose(1, thursday, eight)
        val snoozedUntil = at(thursday, eight, plusMinutes = 65)
        val record = logged(dose, snoozedUntil = snoozedUntil)
        assertEquals(DoseState.UPCOMING, DoseSchedule.state(dose, record, at(thursday, eight, plusMinutes = 64)))
        assertEquals(DoseState.MISSED, DoseSchedule.state(dose, record, snoozedUntil))
    }

    @Test
    fun `a day's log is every due medicine's doses, not taken, with the name and dose of that day`() {
        val a = medicine(id = 1, name = "B", times = listOf(twenty))
        val b = medicine(id = 2, name = "A", dose = "5 ml", times = listOf(eight, twenty))
        val mondays = medicine(id = 3, days = MedicineDays.Chosen(setOf(Weekday.MONDAY)))
        val log = DoseSchedule.logFor(listOf(a, b, mondays), thursday)
        assertEquals(
            listOf(
                DoseRecord(Dose(1, thursday, twenty), "B", "1 tablet", null, null),
                DoseRecord(Dose(2, thursday, eight), "A", "5 ml", null, null),
                DoseRecord(Dose(2, thursday, twenty), "A", "5 ml", null, null),
            ),
            log,
        )
    }

    @Test
    fun `a day lists its logged doses in time order, then by name`() {
        val log = listOf(logged(Dose(1, thursday, twenty), name = "B"), logged(Dose(2, thursday, eight), name = "A"), logged(Dose(2, thursday, twenty), name = "A"))
        val today = DoseSchedule.listed(log, at(thursday, TimeOfDay(0)))
        assertEquals(listOf(2L to eight, 2L to twenty, 1L to twenty), today.map { it.dose.medicineId to it.dose.time })
        assertEquals(listOf("A", "A", "B"), today.map { it.name })
    }

    // Editing today

    @Test
    fun `an edit replaces today's upcoming doses and keeps the taken and missed ones`() {
        val six = TimeOfDay(6 * 60)
        val noon = TimeOfDay(12 * 60)
        val today = listOf(
            logged(Dose(1, thursday, six)),
            logged(Dose(1, thursday, eight), taken = 1L),
            logged(Dose(1, thursday, twenty)),
        )
        val edited = medicine(name = "New name", times = listOf(eight, noon, TimeOfDay(21 * 60)))
        val plan = DoseSchedule.replanToday(today, edited, thursday, at(thursday, TimeOfDay(9 * 60)))
        // 06:00 was missed and 08:00 taken: they stay. 20:00 was still to come: replaced.
        assertEquals(listOf(Dose(1, thursday, twenty)), plan.remove.map { it.dose })
        assertEquals(listOf(noon, TimeOfDay(21 * 60)), plan.add.map { it.dose.time })
        assertEquals(listOf("New name", "New name"), plan.add.map { it.name })
    }

    @Test
    fun `an edit doesn't add doses that are already missed`() {
        val edited = medicine(times = listOf(TimeOfDay(6 * 60), eight))
        val plan = DoseSchedule.replanToday(emptyList(), edited, thursday, at(thursday, eight, plusMinutes = 30))
        // 06:00 is over an hour ago; 08:00 can still be taken.
        assertEquals(listOf(eight), plan.add.map { it.dose.time })
    }

    @Test
    fun `deleting removes only today's upcoming doses`() {
        val today = listOf(logged(Dose(1, thursday, eight), taken = 1L), logged(Dose(1, thursday, twenty)))
        val plan = DoseSchedule.replanToday(today, null, thursday, at(thursday, TimeOfDay(9 * 60)))
        assertEquals(listOf(Dose(1, thursday, twenty)), plan.remove.map { it.dose })
        assertEquals(emptyList<DoseRecord>(), plan.add)
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

    // History

    @Test
    fun `history groups past days latest first, each in time order, taken or missed`() {
        val tuesday = thursday.plusDays(-2)
        val wednesday = thursday.plusDays(-1)
        val records = listOf(
            logged(Dose(1, wednesday, twenty)),
            logged(Dose(1, wednesday, eight), taken = 1L),
            logged(Dose(1, tuesday, eight)),
            logged(Dose(1, tuesday, twenty), taken = 1L),
            // Today's are the Today view's.
            logged(Dose(1, thursday, eight)),
        )
        val history = DoseSchedule.history(records, thursday.plusDays(-30), wednesday, at(thursday, eight))
        assertEquals(listOf(wednesday, tuesday), history.map { it.first })
        assertEquals(listOf(DoseState.TAKEN, DoseState.MISSED), history[0].second.map { it.state })
        assertEquals(listOf(DoseState.MISSED, DoseState.TAKEN), history[1].second.map { it.state })
        assertEquals(listOf(eight, twenty), history[0].second.map { it.dose.time })
    }

    @Test
    fun `history leaves out days with no doses and an empty range`() {
        val records = listOf(logged(Dose(1, CalendarDate(2026, 10, 5), eight)), logged(Dose(1, CalendarDate(2026, 9, 28), eight)))
        val history = DoseSchedule.history(records, thursday.plusDays(-14), thursday.plusDays(-1), at(thursday, eight))
        assertEquals(listOf(CalendarDate(2026, 10, 5), CalendarDate(2026, 9, 28)), history.map { it.first })
        assertEquals(emptyList<Any>(), DoseSchedule.history(records, thursday, thursday.plusDays(-1), at(thursday, eight)))
    }
}
