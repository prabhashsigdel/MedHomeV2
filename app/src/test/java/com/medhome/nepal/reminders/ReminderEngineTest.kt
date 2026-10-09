package com.medhome.nepal.reminders

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medhome.nepal.data.ReminderDatabase
import com.medhome.nepal.data.ReminderPrefs
import com.medhome.nepal.domain.AppointmentAlert
import com.medhome.nepal.domain.AppointmentReminders
import com.medhome.nepal.domain.BookingStatus
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.domain.Dose
import com.medhome.nepal.domain.DoseSchedule
import com.medhome.nepal.domain.DoseState
import com.medhome.nepal.domain.MedicineDays
import com.medhome.nepal.domain.NepalTime
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.domain.Weekday
import com.medhome.nepal.fakes.FakeAlarmScheduler
import com.medhome.nepal.fakes.FakeReminderNotifier
import com.medhome.nepal.fakes.InMemoryReminderSettings
import com.medhome.nepal.fakes.booking
import com.medhome.nepal.fakes.medicine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.TimeZone

/**
 * The reminder engine over a real (in-memory) Room database, with alarms and notifications
 * recorded by fakes: what is set after each change, after a reboot, a clock or time zone change,
 * the Settings switches, the bookings sync and the sign-out wipe.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class ReminderEngineTest {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), ReminderDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = db.reminderDao()
    private val settings = InMemoryReminderSettings()
    private val alarms = FakeAlarmScheduler()
    private val notifications = FakeReminderNotifier()

    private val thursday = CalendarDate(2026, 10, 8)
    private val eight = TimeOfDay(8 * 60)
    private val twenty = TimeOfDay(20 * 60)
    private var now = at(thursday, TimeOfDay(7 * 60))
    private var uid: String? = "uid"
    private var hasAccount = true

    private val engine = ReminderEngine(dao, settings, alarms, notifications, currentPatientUid = { uid }, hasAccount = { hasAccount }, clock = { now })

    @After
    fun close() = db.close()

    private fun at(date: CalendarDate, time: TimeOfDay, plusMinutes: Int = 0) =
        NepalTime.epochMillis(date, time) + plusMinutes * NepalTime.MILLIS_PER_MINUTE

    private fun medicineKey(id: Long) = ReminderAlarm.medicineKey(id)

    private suspend fun addTwiceDaily(): Long =
        engine.saveMedicine(medicine(id = 0, times = listOf(eight, twenty), startDate = thursday))

    /** The dose alarm of [id] going off as AlarmManager would deliver it. */
    private suspend fun fire(id: Long) {
        val (alarm, at) = checkNotNull(alarms.alarms[medicineKey(id)])
        now = at
        engine.onAlarm(alarm)
    }

    // Medicines

    @Test
    fun `saving a medicine sets an alarm for its next dose`() = runTest {
        val id = addTwiceDaily()
        assertEquals(at(thursday, eight), alarms.at(medicineKey(id)))
    }

    @Test
    fun `an alarm shows the reminder and sets the following dose`() = runTest {
        val id = addTwiceDaily()
        fire(id)
        assertEquals(setOf(FakeReminderNotifier.doseTag(Dose(id, thursday, eight))), notifications.shown)
        assertEquals(at(thursday, twenty), alarms.at(medicineKey(id)))
        fire(id)
        assertEquals(at(thursday.plusDays(1), eight), alarms.at(medicineKey(id)))
    }

    @Test
    fun `a dose taken before its alarm rings silently and the next one is still set`() = runTest {
        val id = addTwiceDaily()
        engine.setTaken(Dose(id, thursday, eight), taken = true)
        fire(id)
        assertTrue(notifications.shown.isEmpty())
        assertEquals(at(thursday, twenty), alarms.at(medicineKey(id)))
    }

    @Test
    fun `taken from the notification removes it and its snooze`() = runTest {
        val id = addTwiceDaily()
        fire(id)
        val dose = Dose(id, thursday, eight)
        engine.snooze(dose)
        engine.setTaken(dose, taken = true)
        assertTrue(notifications.shown.isEmpty())
        assertFalse(alarms.alarms.keys.any { it.startsWith("snooze/") })
        val record = engine.recordsOn(thursday).first().single()
        assertEquals(DoseState.TAKEN, DoseSchedule.state(dose, record, now))
    }

    @Test
    fun `snooze brings the reminder back in 10 minutes and the dose stays upcoming`() = runTest {
        val id = addTwiceDaily()
        fire(id)
        val dose = Dose(id, thursday, eight)
        now = at(thursday, eight, plusMinutes = 55)
        engine.snooze(dose)
        assertTrue(notifications.shown.isEmpty())
        val snooze = ReminderAlarm.Snoozed(dose)
        assertEquals(now + 10 * NepalTime.MILLIS_PER_MINUTE, alarms.at(snooze.key))
        // Past the 60-minute mark, but the snooze hasn't fired: still upcoming.
        val record = engine.recordsOn(thursday).first().single()
        assertEquals(DoseState.UPCOMING, DoseSchedule.state(dose, record, at(thursday, eight, plusMinutes = 62)))

        now = alarms.at(snooze.key)!!
        engine.onAlarm(snooze)
        assertEquals(setOf(FakeReminderNotifier.doseTag(dose)), notifications.shown)
    }

    @Test
    fun `deleting a medicine cancels its alarms and notifications`() = runTest {
        val id = addTwiceDaily()
        fire(id)
        engine.snooze(Dose(id, thursday, eight))
        engine.deleteMedicine(id)
        assertTrue(alarms.alarms.isEmpty())
        assertTrue(notifications.shown.isEmpty())
        assertTrue(engine.medicines.first().isEmpty())
    }

    @Test
    fun `editing the times moves the alarm and drops old snoozes`() = runTest {
        val id = addTwiceDaily()
        fire(id)
        engine.snooze(Dose(id, thursday, eight))
        engine.saveMedicine(medicine(id = id, times = listOf(TimeOfDay(21 * 60)), startDate = thursday))
        assertEquals(mapOf(medicineKey(id) to at(thursday, TimeOfDay(21 * 60))), alarms.alarms.mapValues { it.value.second })
    }

    @Test
    fun `a medicine on chosen days with an end date stops after its last dose`() = runTest {
        val id = engine.saveMedicine(
            medicine(id = 0, days = MedicineDays.Chosen(setOf(Weekday.THURSDAY)), startDate = thursday, endDate = thursday.plusDays(3)),
        )
        fire(id)
        assertNull(alarms.at(medicineKey(id)))
    }

    // Rescheduling: reboot, update, clock and time zone changes

    @Test
    fun `after a reboot every alarm is set again`() = runTest {
        val id = addTwiceDaily()
        fire(id)
        engine.snooze(Dose(id, thursday, eight))
        engine.syncAppointments("uid", listOf(booking(id = "b1", startAtMillis = at(thursday.plusDays(2), TimeOfDay(10 * 60)))), fromCache = false)
        val before = alarms.alarms.toMap()
        assertEquals(4, before.size)

        alarms.reboot()
        engine.rescheduleAll()
        assertEquals(before, alarms.alarms)
    }

    @Test
    fun `a dose alarm that hasn't fired yet keeps its dose while it isn't missed`() = runTest {
        val id = addTwiceDaily()
        // An inexact alarm still waiting at 08:30, then the app restarts.
        now = at(thursday, eight, plusMinutes = 30)
        alarms.reboot()
        engine.rescheduleAll()
        assertEquals(at(thursday, eight), alarms.at(medicineKey(id)))

        // Phone off until 09:30: that dose is missed, so the next one is set instead.
        now = at(thursday, eight, plusMinutes = 90)
        alarms.reboot()
        engine.rescheduleAll()
        assertEquals(at(thursday, twenty), alarms.at(medicineKey(id)))
    }

    @Test
    fun `a snooze that came due while the phone was off is dropped`() = runTest {
        val id = addTwiceDaily()
        fire(id)
        engine.snooze(Dose(id, thursday, eight))
        now += 30 * NepalTime.MILLIS_PER_MINUTE
        alarms.reboot()
        engine.rescheduleAll()
        assertFalse(alarms.alarms.keys.any { it.startsWith("snooze/") })
    }

    @Test
    fun `a time zone change sets the same Nepal times`() = runTest {
        val id = addTwiceDaily()
        val before = alarms.at(medicineKey(id))
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            engine.rescheduleAll()
        } finally {
            TimeZone.setDefault(original)
        }
        assertEquals(before, alarms.at(medicineKey(id)))
    }

    @Test
    fun `a clock moved forward past a dose sets the next one`() = runTest {
        val id = addTwiceDaily()
        now = at(thursday, TimeOfDay(15 * 60))
        engine.rescheduleAll()
        assertEquals(at(thursday, twenty), alarms.at(medicineKey(id)))
    }

    // Settings switches

    @Test
    fun `medicine reminders off cancels every medicine alarm and notification, on sets them again`() = runTest {
        val id = addTwiceDaily()
        fire(id)
        engine.snooze(Dose(id, thursday, eight))
        engine.syncAppointments("uid", listOf(booking(id = "b1", startAtMillis = at(thursday.plusDays(2), eight))), fromCache = false)

        engine.setMedicineReminders(false)
        assertFalse(settings.state.value.medicineReminders)
        assertFalse(alarms.alarms.keys.any { it.startsWith("medicine/") || it.startsWith("snooze/") })
        assertTrue(notifications.shown.none { it.startsWith("dose/") })
        // Appointments are untouched.
        assertEquals(2, alarms.alarms.keys.count { it.startsWith("appointment/") })

        engine.setMedicineReminders(true)
        assertEquals(at(thursday, twenty), alarms.at(medicineKey(id)))
    }

    @Test
    fun `nothing is set or shown while medicine reminders are off`() = runTest {
        engine.setMedicineReminders(false)
        val id = addTwiceDaily()
        assertNull(alarms.at(medicineKey(id)))
        engine.onAlarm(ReminderAlarm.MedicineDue(Dose(id, thursday, eight)))
        engine.rescheduleAll()
        assertTrue(alarms.alarms.isEmpty())
        assertTrue(notifications.history.isEmpty())
    }

    @Test
    fun `appointment reminders off cancels them, on sets them again`() = runTest {
        val start = at(thursday.plusDays(2), TimeOfDay(10 * 60))
        engine.syncAppointments("uid", listOf(booking(id = "b1", startAtMillis = start)), fromCache = false)
        engine.setAppointmentReminders(false)
        assertTrue(alarms.alarms.isEmpty())
        engine.onAlarm(ReminderAlarm.Appointment("b1", AppointmentAlert.HOUR_BEFORE))
        assertTrue(notifications.history.isEmpty())

        engine.setAppointmentReminders(true)
        assertEquals(
            AppointmentReminders.pendingAlerts(start, now).mapKeys { ReminderAlarm.Appointment("b1", it.key).key },
            alarms.alarms.mapValues { it.value.second },
        )
    }

    // Appointments

    @Test
    fun `sync schedules new bookings and forgets ones cancelled by the clinic`() = runTest {
        val start = at(thursday.plusDays(2), TimeOfDay(10 * 60))
        val b1 = booking(id = "b1", startAtMillis = start)
        val b2 = booking(id = "b2", startAtMillis = start + 3_600_000)
        engine.syncAppointments("uid", listOf(b1, b2), fromCache = false)
        assertEquals(4, alarms.alarms.size)

        engine.syncAppointments("uid", listOf(b1, b2.copy(status = BookingStatus.CANCELLED)), fromCache = false)
        assertEquals(setOf("appointment/b1/evening", "appointment/b1/hour"), alarms.alarms.keys)
        assertEquals(listOf("b1"), engine.appointments.first().map { it.bookingId })
    }

    @Test
    fun `an answer from the cache adds but never removes`() = runTest {
        val start = at(thursday.plusDays(2), TimeOfDay(10 * 60))
        engine.syncAppointments("uid", listOf(booking(id = "b1", startAtMillis = start)), fromCache = false)
        engine.syncAppointments("uid", emptyList(), fromCache = true)
        assertEquals(listOf("b1"), engine.appointments.first().map { it.bookingId })
        engine.syncAppointments("uid", emptyList(), fromCache = false)
        assertTrue(engine.appointments.first().isEmpty())
        assertTrue(alarms.alarms.isEmpty())
    }

    @Test
    fun `a booking made here is reminded at once and cancelling it here removes it`() = runTest {
        val start = at(thursday, TimeOfDay(10 * 60))
        engine.addAppointment("b1", start, doctorName = "")
        assertEquals(setOf("appointment/b1/hour"), alarms.alarms.keys)

        now = at(thursday, TimeOfDay(9 * 60))
        engine.onAlarm(ReminderAlarm.Appointment("b1", AppointmentAlert.HOUR_BEFORE))
        assertEquals(setOf("appointment/b1"), notifications.shown)

        engine.removeAppointment("b1")
        assertTrue(alarms.alarms.isEmpty())
        assertTrue(notifications.shown.isEmpty())
    }

    @Test
    fun `the listener fills in the doctor's name of a booking made here`() = runTest {
        val b1 = booking(id = "b1", startAtMillis = at(thursday.plusDays(1), eight))
        engine.addAppointment("b1", b1.startAtMillis, doctorName = "")
        engine.syncAppointments("uid", listOf(b1), fromCache = false)
        assertEquals(b1.doctor.name, engine.appointments.first().single().doctorName)
    }

    @Test
    fun `a sync for someone else, or after sign-out, changes nothing`() = runTest {
        val b1 = booking(id = "b1", startAtMillis = at(thursday.plusDays(1), eight))
        engine.syncAppointments("other", listOf(b1), fromCache = false)
        uid = null
        engine.syncAppointments("uid", listOf(b1), fromCache = false)
        engine.addAppointment("b2", b1.startAtMillis, doctorName = "")
        assertTrue(engine.appointments.first().isEmpty())
        assertTrue(alarms.alarms.isEmpty())
    }

    @Test
    fun `a started appointment is forgotten on reschedule`() = runTest {
        engine.syncAppointments("uid", listOf(booking(id = "b1", startAtMillis = at(thursday, TimeOfDay(10 * 60)))), fromCache = false)
        now = at(thursday, TimeOfDay(11 * 60))
        engine.rescheduleAll()
        assertTrue(engine.appointments.first().isEmpty())
    }

    // Sign-out

    @Test
    fun `the wipe cancels every alarm and notification, empties the database and resets the switches`() = runTest {
        val id = addTwiceDaily()
        fire(id)
        engine.snooze(Dose(id, thursday, eight))
        fire(id)
        engine.syncAppointments("uid", listOf(booking(id = "b1", startAtMillis = at(thursday.plusDays(2), eight))), fromCache = false)
        engine.setAppointmentReminders(false)
        engine.setAppointmentReminders(true)
        settings.setBatteryGuideShown()

        uid = null
        engine.wipe()

        assertTrue(alarms.alarms.isEmpty())
        assertTrue(notifications.shown.isEmpty())
        assertTrue(engine.medicines.first().isEmpty())
        assertTrue(engine.appointments.first().isEmpty())
        assertTrue(engine.recordsOn(thursday).first().isEmpty())
        assertEquals(ReminderPrefs(), settings.state.value)

        // Late arrivals find nothing to act on.
        engine.onAlarm(ReminderAlarm.MedicineDue(Dose(id, thursday.plusDays(1), eight)))
        engine.snooze(Dose(id, thursday, eight))
        engine.rescheduleAll()
        assertTrue(alarms.alarms.isEmpty())
    }

    @Test
    fun `left-over reminders from an unfinished sign-out are wiped, not rung, at the next start or alarm`() = runTest {
        val id = addTwiceDaily()
        val alarm = alarms.alarms.getValue(medicineKey(id)).first
        // The process died after Firebase signed out but before the wipe.
        uid = null
        hasAccount = false
        now = at(thursday, eight)
        engine.onAlarm(alarm)
        assertTrue(notifications.history.isEmpty())
        assertTrue(engine.medicines.first().isEmpty())
        assertTrue(alarms.alarms.isEmpty())
    }

    @Test
    fun `a failing step doesn't stop the rest of the wipe`() = runTest {
        addTwiceDaily()
        val failing = ReminderEngine(dao, settings, object : AlarmScheduler by alarms {
            override fun cancelKey(key: String) = error("Binder died")
        }, notifications, { uid }, { hasAccount }, { now })
        try {
            failing.wipe()
        } catch (_: IllegalStateException) {
            // Rethrown after every step ran.
        }
        assertTrue(engine.medicines.first().isEmpty())
    }

    @Test(expected = IllegalStateException::class)
    fun `medicines can't be added when nobody is signed in`() = runTest {
        uid = null
        addTwiceDaily()
    }

    @Test
    fun `the battery guide is claimed once`() = runTest {
        assertTrue(engine.claimBatteryGuide())
        assertFalse(engine.claimBatteryGuide())
    }
}
