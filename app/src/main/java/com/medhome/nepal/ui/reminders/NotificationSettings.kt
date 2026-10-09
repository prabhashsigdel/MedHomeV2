package com.medhome.nepal.ui.reminders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medhome.nepal.R
import com.medhome.nepal.reminders.ReminderAccess
import com.medhome.nepal.ui.components.MessageKind
import com.medhome.nepal.ui.components.SettingsDivider
import com.medhome.nepal.ui.components.SettingsRow
import com.medhome.nepal.ui.components.SettingsSection
import com.medhome.nepal.ui.components.SettingsSwitchRow
import com.medhome.nepal.ui.components.StatusMessage

/**
 * Profile's Notifications section: medicine and appointment reminders on or off (on sets every
 * reminder of that kind, off cancels them and removes their notifications), the phone's
 * notification switch when it is off, and the battery guide. Turning a kind on asks for the
 * notification permission in context.
 */
@Composable
fun NotificationSettingsSection(
    viewModel: ReminderSettingsViewModel,
    enabled: Boolean,
    onOpenBatteryGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val failed by viewModel.changeFailed.collectAsStateWithLifecycle()
    val access = rememberReminderAccess()
    val askNotifications = rememberNotificationPermissionRequest()
    val loaded = prefs != null && enabled

    SettingsSection(title = R.string.settings_notifications, modifier = modifier) {
        SettingsSwitchRow(
            title = R.string.settings_medicine_reminders,
            subtitle = R.string.settings_medicine_reminders_hint,
            checked = prefs?.medicineReminders == true,
            enabled = loaded,
            onCheckedChange = { on ->
                viewModel.setMedicineReminders(on)
                if (on) askNotifications()
            },
        )
        SettingsDivider()
        SettingsSwitchRow(
            title = R.string.settings_appointment_reminders,
            subtitle = R.string.settings_appointment_reminders_hint,
            checked = prefs?.appointmentReminders == true,
            enabled = loaded,
            onCheckedChange = { on ->
                viewModel.setAppointmentReminders(on)
                if (on) askNotifications()
            },
        )
        if (!access.notifications) {
            SettingsDivider()
            SettingsRow(
                title = R.string.settings_notifications_off,
                subtitle = R.string.settings_notifications_off_hint,
                onClick = { ReminderAccess.openFirst(context, ReminderAccess.notificationSettings(context)) },
            )
        }
        SettingsDivider()
        SettingsRow(
            title = R.string.settings_battery_guide,
            subtitle = R.string.settings_battery_guide_hint,
            enabled = enabled,
            onClick = onOpenBatteryGuide,
        )
    }
    if (failed) StatusMessage(message = R.string.settings_reminders_failed, kind = MessageKind.Error, onDismiss = viewModel::dismissError)
}
