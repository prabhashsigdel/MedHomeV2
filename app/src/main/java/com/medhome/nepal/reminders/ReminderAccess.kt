package com.medhome.nepal.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Phone makers whose battery savers need their own steps in the battery guide. */
enum class PhoneMaker { SAMSUNG, XIAOMI, OTHER }

/**
 * What the phone allows the reminders to do, and the system screens that change it. Read fresh
 * each time (the user can change any of it in Settings while the app is in the background).
 */
object ReminderAccess {

    /** Android 13+ asks before an app may post notifications. */
    val notificationPermissionNeeded: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** The notification permission is granted (always, below Android 13). */
    fun notificationPermissionGranted(context: Context): Boolean = !notificationPermissionNeeded ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Notifications can show: permission granted (13+) and not blocked in the app's settings. */
    fun notificationsAllowed(context: Context): Boolean =
        notificationPermissionGranted(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Below Android 12 there is no "Alarms & reminders" setting: exact alarms always work. */
    val exactAlarmsAlwaysAllowed: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    /** Whether one of the app's channels was turned off by the user. */
    fun channelBlocked(context: Context, channelId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val channel = NotificationManagerCompat.from(context).getNotificationChannel(channelId) ?: return false
        return channel.importance == NotificationManagerCompat.IMPORTANCE_NONE
    }

    /** Exact alarms: always below Android 12, otherwise only with "Alarms & reminders" allowed. */
    fun exactAlarmsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    /** True when the app is exempt from battery optimization (Doze still delivers its alarms). */
    fun ignoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    val phoneMaker: PhoneMaker
        get() = when (Build.MANUFACTURER.lowercase()) {
            "samsung" -> PhoneMaker.SAMSUNG
            "xiaomi", "redmi", "poco" -> PhoneMaker.XIAOMI
            else -> PhoneMaker.OTHER
        }

    // System screens, best first. Callers open them with [openFirst].

    /** "Alarms & reminders" for this app (Android 12+). */
    fun exactAlarmSettings(context: Context): List<Intent> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context)))
        }
        add(appDetails(context))
    }

    /** The app's notification settings. */
    fun notificationSettings(context: Context): List<Intent> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
        add(appDetails(context))
    }

    /** The list of apps and their battery optimization, where the user finds this app themselves. */
    fun batteryOptimizationSettings(context: Context): List<Intent> =
        listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), appDetails(context))

    /**
     * The system dialog that lets this app always run in the background (one tap), then the list
     * above if the phone has no such dialog.
     *
     * PLAY POLICY: this dialog needs the REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission, which
     * Google Play restricts to apps whose core function needs it and reviews. Revisit before any
     * Play release: justify it in the Play Console, or drop the permission and the first intent
     * (the list alone needs no permission). See AndroidManifest.xml.
     */
    @SuppressLint("BatteryLife")
    fun runInBackgroundRequest(context: Context): List<Intent> =
        listOf(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))) +
            batteryOptimizationSettings(context)

    /** Samsung: App info, where Battery > Unrestricted takes the app out of sleeping apps. */
    fun samsungBatterySettings(context: Context): List<Intent> = listOf(appDetails(context))

    /** Xiaomi: MIUI's Autostart list, when the phone has it; otherwise App info. */
    fun xiaomiAutostartSettings(context: Context): List<Intent> = listOf(
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        appDetails(context),
    )

    /** Opens the first of [intents] the phone has. False when none could be opened. */
    fun openFirst(context: Context, intents: List<Intent>): Boolean = intents.any { intent ->
        try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            // Another app's screen that isn't exported on this phone.
            false
        }
    }

    private fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

    private fun packageUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)
}
