package com.medhome.nepal.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.medhome.nepal.MainActivity
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.domain.Dose
import com.medhome.nepal.domain.TimeOfDay

/**
 * Every intent and PendingIntent of the reminders. All PendingIntents are immutable and explicit
 * (they name [ReminderReceiver] or [MainActivity]); each one's identity is its data URI, built
 * from IDs only, so cancelling rebuilds the same URI.
 */
object ReminderIntents {
    const val ACTION_ALARM = "com.medhome.nepal.reminders.ALARM"
    const val ACTION_TAKEN = "com.medhome.nepal.reminders.TAKEN"
    const val ACTION_SNOOZE = "com.medhome.nepal.reminders.SNOOZE"

    const val EXTRA_KEY = "key"
    const val EXTRA_MEDICINE_ID = "medicineId"
    const val EXTRA_EPOCH_DAY = "epochDay"
    const val EXTRA_MINUTE = "minute"

    private const val SCHEME = "medhome-reminder"

    private const val IMMUTABLE = PendingIntent.FLAG_IMMUTABLE

    /** The alarm's broadcast; extras carry the dose of a medicine's alarm (not part of its identity). */
    fun alarm(context: Context, alarm: ReminderAlarm): PendingIntent {
        val intent = alarmIntent(context, alarm.key)
        if (alarm is ReminderAlarm.MedicineDue) intent.putDose(alarm.dose)
        return PendingIntent.getBroadcast(context, 0, intent, IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** The alarm with [key] if one is set, for cancelling. */
    fun existingAlarm(context: Context, key: String): PendingIntent? =
        PendingIntent.getBroadcast(context, 0, alarmIntent(context, key), IMMUTABLE or PendingIntent.FLAG_NO_CREATE)

    /** The notification's "Taken" or "Snooze 10 min" button. */
    fun doseAction(context: Context, action: String, dose: Dose): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .setData(uri("$action/${dose.medicineId}/${dose.date.epochDay}/${dose.time.minutes}"))
            .putDose(dose)
        return PendingIntent.getBroadcast(context, 0, intent, IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Tapping a reminder opens the app where it was. */
    fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        return PendingIntent.getActivity(context, 0, intent, IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** The dose an intent's extras name, or null when they don't name a real one. */
    fun doseOf(intent: Intent): Dose? = runCatching {
        if (!intent.hasExtra(EXTRA_MEDICINE_ID) || !intent.hasExtra(EXTRA_EPOCH_DAY) || !intent.hasExtra(EXTRA_MINUTE)) {
            return null
        }
        Dose(
            medicineId = intent.getLongExtra(EXTRA_MEDICINE_ID, 0),
            date = CalendarDate.ofEpochDay(intent.getLongExtra(EXTRA_EPOCH_DAY, 0)),
            time = TimeOfDay(intent.getIntExtra(EXTRA_MINUTE, -1)),
        )
    }.getOrNull()

    /** The alarm an [ACTION_ALARM] intent stands for. */
    fun alarmOf(intent: Intent): ReminderAlarm? {
        val dose = doseOf(intent)
        return ReminderAlarm.parse(intent.getStringExtra(EXTRA_KEY), dose?.date?.epochDay, dose?.time?.minutes)
            ?.takeIf { it !is ReminderAlarm.MedicineDue || it.dose == dose }
    }

    private fun alarmIntent(context: Context, key: String): Intent =
        Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_ALARM)
            .setData(uri("alarm/$key"))
            .putExtra(EXTRA_KEY, key)

    private fun Intent.putDose(dose: Dose): Intent = putExtra(EXTRA_MEDICINE_ID, dose.medicineId)
        .putExtra(EXTRA_EPOCH_DAY, dose.date.epochDay)
        .putExtra(EXTRA_MINUTE, dose.time.minutes)

    private fun uri(path: String): Uri = "$SCHEME://$path".toUri()
}
