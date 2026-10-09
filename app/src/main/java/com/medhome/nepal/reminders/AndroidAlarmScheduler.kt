package com.medhome.nepal.reminders

import android.app.AlarmManager
import android.content.Context
import android.util.Log

/**
 * Sets reminders with AlarmManager. Exact (setExactAndAllowWhileIdle, so they fire in Doze)
 * when the app may; otherwise setAndAllowWhileIdle, which still fires in Doze but may come
 * several minutes late. The app asks for SCHEDULE_EXACT_ALARM, not USE_EXACT_ALARM: Play keeps
 * USE_EXACT_ALARM for apps whose core function is an alarm clock or calendar, and reminders are
 * one feature of MedHome. SCHEDULE_EXACT_ALARM is denied by default on Android 14+ for new
 * installs, so the medicines screen explains it and links to the setting
 * ([ReminderAccess.exactAlarmSettings]); when granted, everything is rescheduled as exact.
 */
class AndroidAlarmScheduler(context: Context) : AlarmScheduler {
    private val context = context.applicationContext
    private val alarmManager = checkNotNull(this.context.getSystemService(AlarmManager::class.java))

    override fun schedule(alarm: ReminderAlarm, atMillis: Long) {
        val operation = ReminderIntents.alarm(context, alarm)
        if (canScheduleExact()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
                return
            } catch (e: SecurityException) {
                // Revoked between the check and the call: fall through to an inexact alarm.
                Log.w(TAG, "Exact alarm refused", e)
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
    }

    override fun cancel(alarm: ReminderAlarm) = cancelKey(alarm.key)

    override fun cancelKey(key: String) {
        val operation = ReminderIntents.existingAlarm(context, key) ?: return
        alarmManager.cancel(operation)
        operation.cancel()
    }

    override fun canScheduleExact(): Boolean = ReminderAccess.exactAlarmsAllowed(context)

    private companion object {
        const val TAG = "AlarmScheduler"
    }
}
