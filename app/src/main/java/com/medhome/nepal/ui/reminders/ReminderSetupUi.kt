package com.medhome.nepal.ui.reminders

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.medhome.nepal.R
import com.medhome.nepal.reminders.ReminderAccess
import com.medhome.nepal.reminders.ReminderSetupItem
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassButtonStyle
import com.medhome.nepal.ui.components.GlassDialog
import com.medhome.nepal.ui.components.SettingsDivider
import com.medhome.nepal.ui.components.StatusChip
import com.medhome.nepal.ui.theme.GlassTheme

/** Test tag on each checklist item. */
const val SETUP_ITEM_TAG = "reminder_setup_item"

/** The setup items this phone can have at all ("Alarms & reminders" exists from Android 12). */
val ReminderSetupItem.applies: Boolean
    get() = this != ReminderSetupItem.EXACT_ALARMS || !ReminderAccess.exactAlarmsAlwaysAllowed

/** Whether [item] is allowed now. */
fun ReminderAccessState.allows(item: ReminderSetupItem): Boolean = when (item) {
    ReminderSetupItem.NOTIFICATIONS -> notifications && medicineChannel
    ReminderSetupItem.EXACT_ALARMS -> exactAlarms
    ReminderSetupItem.BACKGROUND -> batteryUnrestricted
}

/** What reminders still need on this phone. */
fun ReminderAccessState.missing(): Set<ReminderSetupItem> =
    ReminderSetupItem.entries.filterTo(mutableSetOf()) { it.applies && !allows(it) }

@get:StringRes
private val ReminderSetupItem.title: Int
    get() = when (this) {
        ReminderSetupItem.NOTIFICATIONS -> R.string.setup_notifications
        ReminderSetupItem.EXACT_ALARMS -> R.string.setup_alarms
        ReminderSetupItem.BACKGROUND -> R.string.setup_background
    }

@get:StringRes
private val ReminderSetupItem.body: Int
    get() = when (this) {
        ReminderSetupItem.NOTIFICATIONS -> R.string.setup_notifications_body
        ReminderSetupItem.EXACT_ALARMS -> R.string.setup_alarms_body
        ReminderSetupItem.BACKGROUND -> R.string.setup_background_body
    }

/**
 * [items], each with what it is for and, while it is off, the button that turns it on; on ones
 * say so. [access] is read again whenever the screen resumes, so coming back from Settings
 * updates the list.
 */
@Composable
fun ReminderChecklist(items: List<ReminderSetupItem>, access: ReminderAccessState) {
    val fixes = rememberSetupFixes()
    items.forEachIndexed { index, item ->
        if (index > 0) SettingsDivider()
        SetupItemRow(item = item, on = access.allows(item), onFix = { fixes(item) })
    }
}

@Composable
private fun SetupItemRow(item: ReminderSetupItem, on: Boolean, onFix: () -> Unit) {
    val colors = GlassTheme.colors
    Column(
        modifier = Modifier.testTag(SETUP_ITEM_TAG),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = stringResource(item.title), style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                Text(text = stringResource(item.body), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(text = if (on) R.string.battery_status_ok else R.string.battery_status_off, emphasized = on)
        }
        if (!on) {
            GlassButton(text = R.string.reminders_turn_on, onClick = onFix, style = GlassButtonStyle.Secondary, compact = true)
        }
    }
}

/**
 * What each item's button does: notifications ask for the permission (Android 13+), or open the
 * app's notification settings when Android won't ask again or they are off another way;
 * "Alarms & reminders" opens its setting for the app; background opens the system's "always run
 * in the background" dialog.
 */
@Composable
private fun rememberSetupFixes(): (ReminderSetupItem) -> Unit {
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Refused with Android no longer asking (twice refused): the app's settings are the only way.
        val activity = context.findActivity()
        if (!granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            ReminderAccess.openFirst(context, ReminderAccess.notificationSettings(context))
        }
    }
    return { item ->
        when (item) {
            ReminderSetupItem.NOTIFICATIONS ->
                if (ReminderAccess.notificationPermissionNeeded && !ReminderAccess.notificationPermissionGranted(context)) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // Permission granted (or not needed) but notifications or the channel are off.
                    ReminderAccess.openFirst(context, ReminderAccess.notificationSettings(context))
                }
            ReminderSetupItem.EXACT_ALARMS -> ReminderAccess.openFirst(context, ReminderAccess.exactAlarmSettings(context))
            ReminderSetupItem.BACKGROUND -> ReminderAccess.openFirst(context, ReminderAccess.runInBackgroundRequest(context))
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * "Make sure reminders arrive", after a medicine is saved: only the [items] that were off then,
 * each with its button, updating as the user comes back from each setting. Done closes it.
 */
@Composable
fun ReminderSetupDialog(items: Set<ReminderSetupItem>, onDismiss: () -> Unit) {
    val access = rememberReminderAccess()
    GlassDialog(onDismissRequest = onDismiss, title = R.string.reminder_setup_title) {
        Text(
            text = stringResource(R.string.reminder_setup_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = GlassTheme.colors.textSecondary,
        )
        // Fixed order, whatever order they were offered in.
        ReminderChecklist(items = ReminderSetupItem.entries.filter { it in items }, access = access)
        GlassButton(text = R.string.action_done, onClick = onDismiss)
    }
}
