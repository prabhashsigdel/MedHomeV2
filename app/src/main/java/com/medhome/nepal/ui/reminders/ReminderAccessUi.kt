package com.medhome.nepal.ui.reminders

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.medhome.nepal.R
import com.medhome.nepal.reminders.ReminderAccess
import com.medhome.nepal.reminders.ReminderChannels
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassButtonStyle
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.theme.GlassTheme

/** What the phone currently lets the reminders do. */
data class ReminderAccessState(
    val notifications: Boolean,
    val medicineChannel: Boolean,
    val appointmentChannel: Boolean,
    val exactAlarms: Boolean,
    val batteryUnrestricted: Boolean,
) {
    companion object {
        fun read(context: Context) = ReminderAccessState(
            notifications = ReminderAccess.notificationsAllowed(context),
            medicineChannel = !ReminderAccess.channelBlocked(context, ReminderChannels.MEDICINE),
            appointmentChannel = !ReminderAccess.channelBlocked(context, ReminderChannels.APPOINTMENTS),
            exactAlarms = ReminderAccess.exactAlarmsAllowed(context),
            batteryUnrestricted = ReminderAccess.ignoringBatteryOptimizations(context),
        )
    }
}

/** Read again every time the screen resumes: the user may have just changed it in Settings. */
@Composable
fun rememberReminderAccess(): ReminderAccessState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(ReminderAccessState.read(context)) }
    LifecycleResumeEffect(context) {
        state = ReminderAccessState.read(context)
        onPauseOrDispose {}
    }
    return state
}

/**
 * Asks for the notification permission (Android 13+) in context: when a reminder is set or
 * turned on, never at launch. Calls [onDone] once answered, or at once when there is nothing to
 * ask (older Android, already allowed). After two refusals Android stops showing the prompt; the
 * screens then offer the app's notification settings instead.
 */
@Composable
fun rememberNotificationPermissionRequest(onDone: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val latestOnDone by rememberUpdatedState(onDone)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { latestOnDone() }
    return {
        if (ReminderAccess.notificationPermissionNeeded && !ReminderAccess.notificationsAllowed(context)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            latestOnDone()
        }
    }
}

/**
 * Why reminders may not arrive, each with the button that fixes it: notifications off (all, or
 * the [channel] alone) and, for [needsExact], exact alarms not allowed.
 */
@Composable
fun ReminderAccessNotices(
    access: ReminderAccessState,
    channelAllowed: Boolean,
    needsExact: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    if (!access.notifications || !channelAllowed) {
        NoticeCard(
            text = R.string.reminders_notifications_off,
            action = R.string.reminders_turn_on,
            onAction = { ReminderAccess.openFirst(context, ReminderAccess.notificationSettings(context)) },
            modifier = modifier,
        )
    }
    if (needsExact && !access.exactAlarms) {
        NoticeCard(
            text = R.string.reminders_exact_off,
            action = R.string.reminders_allow,
            onAction = { ReminderAccess.openFirst(context, ReminderAccess.exactAlarmSettings(context)) },
            modifier = modifier,
        )
    }
}

/** A short explanation with the one button that acts on it. */
@Composable
fun NoticeCard(
    @StringRes text: Int,
    @StringRes action: Int,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = stringResource(text), style = MaterialTheme.typography.bodyMedium, color = GlassTheme.colors.textPrimary)
        GlassButton(text = action, onClick = onAction, style = GlassButtonStyle.Secondary, compact = true)
    }
}
