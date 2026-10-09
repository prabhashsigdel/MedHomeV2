package com.medhome.nepal.reminders

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medhome.nepal.MainActivity
import com.medhome.nepal.domain.AppointmentAlert
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.domain.Dose
import com.medhome.nepal.domain.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * The Android edges of the reminders: every PendingIntent is immutable and explicit, alarms
 * round-trip through their intents (and tampered extras are refused), cancelling really cancels,
 * and neither receiver is exported.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class ReminderIntentsTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val dose = Dose(7, CalendarDate(2026, 10, 9), TimeOfDay(8 * 60 + 30))
    private val alarmsToTest = listOf(
        ReminderAlarm.MedicineDue(dose),
        ReminderAlarm.Snoozed(dose),
        ReminderAlarm.Appointment("AbC123xyz", AppointmentAlert.EVENING_BEFORE),
        ReminderAlarm.Appointment("AbC123xyz", AppointmentAlert.HOUR_BEFORE),
    )

    private fun PendingIntent.isImmutableFlag() = shadowOf(this).flags and PendingIntent.FLAG_IMMUTABLE != 0

    @Test
    fun `every PendingIntent is immutable and names its component`() {
        val intents = alarmsToTest.map { ReminderIntents.alarm(context, it) } +
            ReminderIntents.doseAction(context, ReminderIntents.ACTION_TAKEN, dose) +
            ReminderIntents.doseAction(context, ReminderIntents.ACTION_SNOOZE, dose)
        intents.forEach { pending ->
            assertTrue(pending.isImmutableFlag())
            assertEquals(ComponentName(context, ReminderReceiver::class.java), shadowOf(pending).savedIntent.component)
        }
        val open = ReminderIntents.openApp(context)
        assertTrue(open.isImmutableFlag())
        assertEquals(ComponentName(context, MainActivity::class.java), shadowOf(open).savedIntent.component)
    }

    @Test
    fun `alarms come back from their own intents`() {
        alarmsToTest.forEach { alarm ->
            val intent = shadowOf(ReminderIntents.alarm(context, alarm)).savedIntent
            assertEquals(alarm, ReminderIntents.alarmOf(intent))
        }
    }

    @Test
    fun `a dose alarm whose extras don't match its key, or bad keys, are refused`() {
        val intent = Intent(shadowOf(ReminderIntents.alarm(context, ReminderAlarm.MedicineDue(dose))).savedIntent)
        intent.putExtra(ReminderIntents.EXTRA_MINUTE, 99_999)
        assertNull(ReminderIntents.alarmOf(intent))
        listOf(null, "", "medicine", "medicine/x", "snooze/1/2", "appointment/bad_id!/hour", "appointment/a/never", "other/1")
            .forEach { assertNull(it, ReminderAlarm.parse(it, doseDay = 1, doseMinute = 1)) }
    }

    @Test
    fun `notification buttons carry their dose`() {
        val intent = shadowOf(ReminderIntents.doseAction(context, ReminderIntents.ACTION_TAKEN, dose)).savedIntent
        assertEquals(ReminderIntents.ACTION_TAKEN, intent.action)
        assertEquals(dose, ReminderIntents.doseOf(intent))
        assertNull(ReminderIntents.doseOf(Intent(ReminderIntents.ACTION_TAKEN)))
    }

    @Test
    fun `scheduling sets an alarm and cancelling removes it, exact or not`() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val scheduler = AndroidAlarmScheduler(context)
        listOf(true, false).forEach { exact ->
            ShadowAlarmManager.setCanScheduleExactAlarms(exact)
            val alarm = ReminderAlarm.Snoozed(dose)
            scheduler.schedule(alarm, 1_000_000L)
            assertEquals(1_000_000L, shadowOf(alarmManager).peekNextScheduledAlarm()?.triggerAtMs)
            assertNotNull(ReminderIntents.existingAlarm(context, alarm.key))
            scheduler.cancel(alarm)
            assertNull(shadowOf(alarmManager).peekNextScheduledAlarm())
        }
    }

    @Test
    fun `receivers are not exported`() {
        listOf(ReminderReceiver::class.java, RescheduleReceiver::class.java).forEach { receiver ->
            val info = context.packageManager.getReceiverInfo(ComponentName(context, receiver), 0)
            assertFalse("${receiver.simpleName} is exported", info.exported)
        }
    }
}
