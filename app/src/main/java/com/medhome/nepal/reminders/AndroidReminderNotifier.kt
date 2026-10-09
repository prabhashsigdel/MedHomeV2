package com.medhome.nepal.reminders

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.medhome.nepal.R
import com.medhome.nepal.domain.AppointmentAlert
import com.medhome.nepal.domain.AppointmentReminder
import com.medhome.nepal.domain.Dose
import com.medhome.nepal.domain.Medicine
import com.medhome.nepal.domain.NepalTime
import com.medhome.nepal.ui.common.LocaleFormat

/** The app's notification channels. Names follow the app's language. */
object ReminderChannels {
    const val MEDICINE = "medicine"
    const val APPOINTMENTS = "appointments"
    const val GENERAL = "general"

    /** Creates or renames the channels (safe to repeat; the user's own channel settings stay). */
    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val localized = ReminderLocale.context(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                channel(localized, MEDICINE, R.string.channel_medicine, R.string.channel_medicine_description, NotificationManager.IMPORTANCE_HIGH),
                channel(localized, APPOINTMENTS, R.string.channel_appointments, R.string.channel_appointments_description, NotificationManager.IMPORTANCE_HIGH),
                channel(localized, GENERAL, R.string.channel_general, R.string.channel_general_description, NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    @SuppressLint("NewApi") // Only called from ensure(), which returns early below Android 8.
    private fun channel(context: Context, id: String, @StringRes name: Int, @StringRes description: Int, importance: Int) =
        NotificationChannel(id, context.getString(name), importance).apply {
            this.description = context.getString(description)
            // The private notification shows on the lock screen only in its public version.
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
}

/** Notifications are built outside any activity, so they apply the app's language themselves. */
internal object ReminderLocale {
    fun context(context: Context): Context {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (appLocales.isEmpty) return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(appLocales.toLanguageTags()))
        return context.createConfigurationContext(configuration)
    }
}

/**
 * Posts reminders. Each is private: on a locked screen only its public version shows ("Time for
 * your medicine", "Upcoming appointment"), with no medicine or doctor name. Tags hold IDs only.
 */
class AndroidReminderNotifier(context: Context) : ReminderNotifier {
    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    @SuppressLint("MissingPermission") // Checked by notificationsAllowed().
    override fun showDose(medicine: Medicine, dose: Dose) {
        if (!ReminderAccess.notificationsAllowed(context)) return
        ReminderChannels.ensure(context)
        val text = ReminderLocale.context(context)
        val locale = text.resources.configuration.locales[0]
        val public = NotificationCompat.Builder(context, ReminderChannels.MEDICINE)
            .setSmallIcon(R.drawable.ic_sym_alarm)
            .setContentTitle(text.getString(R.string.reminder_medicine_public_title))
            .setContentText(text.getString(R.string.reminder_public_text))
            .build()
        val notification = NotificationCompat.Builder(context, ReminderChannels.MEDICINE)
            .setSmallIcon(R.drawable.ic_sym_alarm)
            .setContentTitle(text.getString(R.string.reminder_medicine_title, medicine.name))
            .setContentText(text.getString(R.string.reminder_medicine_text, medicine.dose, LocaleFormat.timeOfDay(dose.time, locale)))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setWhen(dose.atMillis)
            .setShowWhen(true)
            .setAutoCancel(true)
            // Re-posting the same reminder (an alarm restored at app start) doesn't ring twice.
            .setOnlyAlertOnce(true)
            .setContentIntent(ReminderIntents.openApp(context))
            .addAction(0, text.getString(R.string.reminder_action_taken), ReminderIntents.doseAction(context, ReminderIntents.ACTION_TAKEN, dose))
            .addAction(0, text.getString(R.string.reminder_action_snooze), ReminderIntents.doseAction(context, ReminderIntents.ACTION_SNOOZE, dose))
            .build()
        manager.notify(doseTag(dose), NOTIFICATION_ID, notification)
    }

    @SuppressLint("MissingPermission") // Checked by notificationsAllowed().
    override fun showAppointment(reminder: AppointmentReminder, alert: AppointmentAlert) {
        if (!ReminderAccess.notificationsAllowed(context)) return
        ReminderChannels.ensure(context)
        val text = ReminderLocale.context(context)
        val locale = text.resources.configuration.locales[0]
        val time = LocaleFormat.timeOfDay(NepalTime.timeOf(reminder.startAtMillis), locale)
        val title = text.getString(
            when (alert) {
                AppointmentAlert.EVENING_BEFORE -> R.string.reminder_appointment_tomorrow
                AppointmentAlert.HOUR_BEFORE -> R.string.reminder_appointment_hour
            },
        )
        val body = if (reminder.doctorName.isNotEmpty()) {
            text.getString(R.string.reminder_appointment_text, reminder.doctorName, time)
        } else {
            text.getString(R.string.reminder_appointment_text_no_doctor, time)
        }
        val public = NotificationCompat.Builder(context, ReminderChannels.APPOINTMENTS)
            .setSmallIcon(R.drawable.ic_sym_calendar_month)
            .setContentTitle(text.getString(R.string.reminder_appointment_public_title))
            .setContentText(text.getString(R.string.reminder_public_text))
            .build()
        val notification = NotificationCompat.Builder(context, ReminderChannels.APPOINTMENTS)
            .setSmallIcon(R.drawable.ic_sym_calendar_month)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setAutoCancel(true)
            .setContentIntent(ReminderIntents.openApp(context))
            .build()
        // One per booking: the hour-before reminder replaces the evening one.
        manager.notify(appointmentTag(reminder.bookingId), NOTIFICATION_ID, notification)
    }

    override fun cancelDose(dose: Dose) = manager.cancel(doseTag(dose), NOTIFICATION_ID)

    override fun cancelMedicine(medicineId: Long) = cancelTagged("$DOSE_PREFIX$medicineId/")

    override fun cancelAppointment(bookingId: String) = manager.cancel(appointmentTag(bookingId), NOTIFICATION_ID)

    override fun cancelAll() = manager.cancelAll()

    override fun cancelAllMedicines() = cancelTagged(DOSE_PREFIX)

    override fun cancelAllAppointments() = cancelTagged(APPOINTMENT_PREFIX)

    private fun cancelTagged(prefix: String) {
        manager.activeNotifications
            .filter { it.tag?.startsWith(prefix) == true }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    private fun doseTag(dose: Dose) = "$DOSE_PREFIX${dose.medicineId}/${dose.date.epochDay}/${dose.time.minutes}"

    private fun appointmentTag(bookingId: String) = "$APPOINTMENT_PREFIX$bookingId"

    private companion object {
        const val NOTIFICATION_ID = 1
        const val DOSE_PREFIX = "dose/"
        const val APPOINTMENT_PREFIX = "appointment/"
    }
}
