package com.medhome.nepal.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.medhome.nepal.MedHomeApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Alarms going off and the notification buttons ("Taken", "Snooze 10 min"). Not exported: only
 * the app's own PendingIntents reach it, and their extras are still validated.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as MedHomeApplication).container
        val engine = container.reminderEngine
        val work: (suspend () -> Unit) = when (intent.action) {
            ReminderIntents.ACTION_ALARM -> ReminderIntents.alarmOf(intent)?.let { alarm -> { engine.onAlarm(alarm) } }
            ReminderIntents.ACTION_TAKEN -> ReminderIntents.doseOf(intent)?.let { dose -> { engine.setTaken(dose, taken = true) } }
            ReminderIntents.ACTION_SNOOZE -> ReminderIntents.doseOf(intent)?.let { dose -> { engine.snooze(dose) } }
            else -> null
        } ?: return
        runAsync(container.backgroundScope, work)
    }
}

/**
 * Sets every reminder again when the system has dropped or shifted alarms: after a reboot, an app
 * update, a clock or time zone change, and when exact alarms become allowed. Not exported: these
 * are system broadcasts, which reach it anyway, and no other app can send it anything.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val container = (context.applicationContext as MedHomeApplication).container
        runAsync(container.backgroundScope) { container.reminderEngine.rescheduleAll() }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            // Some phones (HTC, some Xiaomi) send this after a fast boot instead.
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            // AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (Android 12+): sent
            // when the user allows exact alarms, so everything is set again as exact.
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}

/** Keeps the broadcast alive until [work] is done (well within the 10 seconds allowed). */
private fun BroadcastReceiver.runAsync(scope: CoroutineScope, work: suspend () -> Unit) {
    val pending = goAsync()
    scope.launch {
        try {
            work()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The type only: messages could carry data.
            Log.w(TAG, "Reminder work failed: ${e.javaClass.simpleName}")
        } finally {
            pending.finish()
        }
    }
}

private const val TAG = "Reminders"
