package com.medhome.nepal.domain

import com.medhome.nepal.fakes.booking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppointmentRemindersTest {

    private val friday = CalendarDate(2026, 10, 9)
    private val tenThirty = NepalTime.epochMillis(friday, TimeOfDay(10 * 60 + 30))

    private fun at(date: CalendarDate, hour: Int, minute: Int = 0) = NepalTime.epochMillis(date, TimeOfDay(hour * 60 + minute))

    @Test
    fun `the evening reminder is at 18 00 Nepal time the day before`() {
        assertEquals(at(friday.plusDays(-1), 18), AppointmentReminders.alertAt(tenThirty, AppointmentAlert.EVENING_BEFORE))
    }

    @Test
    fun `the other reminder is one hour before`() {
        assertEquals(at(friday, 9, 30), AppointmentReminders.alertAt(tenThirty, AppointmentAlert.HOUR_BEFORE))
    }

    @Test
    fun `both are pending when booked days ahead`() {
        val pending = AppointmentReminders.pendingAlerts(tenThirty, at(friday.plusDays(-3), 12))
        assertEquals(setOf(AppointmentAlert.EVENING_BEFORE, AppointmentAlert.HOUR_BEFORE), pending.keys)
    }

    @Test
    fun `an alert whose time has passed is skipped`() {
        val pending = AppointmentReminders.pendingAlerts(tenThirty, at(friday.plusDays(-1), 20))
        assertEquals(mapOf(AppointmentAlert.HOUR_BEFORE to at(friday, 9, 30)), pending)
        assertEquals(emptyMap<AppointmentAlert, Long>(), AppointmentReminders.pendingAlerts(tenThirty, at(friday, 9, 30)))
    }

    @Test
    fun `booked tonight for early tomorrow gets only the hour-before reminder`() {
        // 00:30 Saturday, booked 20:00 Friday: the 18:00 alert has passed, 23:30 is still to come.
        val start = at(friday.plusDays(1), 0, 30)
        assertEquals(mapOf(AppointmentAlert.HOUR_BEFORE to at(friday, 23, 30)), AppointmentReminders.pendingAlerts(start, at(friday, 20)))
    }

    @Test
    fun `the evening alert always comes before the hour-before one`() {
        (0 until 24 * 60 step 15).forEach { minute ->
            val start = NepalTime.epochMillis(friday, TimeOfDay(minute))
            val evening = AppointmentReminders.alertAt(start, AppointmentAlert.EVENING_BEFORE)
            val hourBefore = AppointmentReminders.alertAt(start, AppointmentAlert.HOUR_BEFORE)
            assertTrue("At minute $minute", evening < hourBefore)
        }
    }

    @Test
    fun `plan adds new bookings, drops cancelled and started ones and fills in names`() {
        val now = at(friday, 8)
        val upcoming = booking(id = "b1", startAtMillis = tenThirty)
        val cancelled = booking(id = "b2", startAtMillis = tenThirty + 3_600_000).copy(status = BookingStatus.CANCELLED)
        val started = booking(id = "b3", startAtMillis = now - 1)
        val stored = listOf(
            AppointmentReminder("b1", tenThirty, ""),
            AppointmentReminder("b2", tenThirty + 3_600_000, "Dr B"),
            AppointmentReminder("b3", now - 1, "Dr C"),
            AppointmentReminder("gone", tenThirty, "Dr D"),
        )
        val plan = AppointmentReminders.plan(stored, listOf(upcoming, cancelled, started), now)
        assertEquals(listOf(AppointmentReminder("b1", tenThirty, upcoming.doctor.name)), plan.upsert)
        assertEquals(setOf("b2", "b3", "gone"), plan.remove.toSet())
    }

    @Test
    fun `plan reschedules a booking whose time changed and leaves matching ones alone`() {
        val now = at(friday, 8)
        val moved = booking(id = "b1", startAtMillis = tenThirty + 3_600_000)
        val same = booking(id = "b2", startAtMillis = tenThirty)
        val stored = listOf(
            AppointmentReminder("b1", tenThirty, moved.doctor.name),
            AppointmentReminder("b2", tenThirty, same.doctor.name),
        )
        val plan = AppointmentReminders.plan(stored, listOf(moved, same), now)
        assertEquals(listOf("b1"), plan.upsert.map { it.bookingId })
        assertEquals(emptyList<String>(), plan.remove)
    }
}
