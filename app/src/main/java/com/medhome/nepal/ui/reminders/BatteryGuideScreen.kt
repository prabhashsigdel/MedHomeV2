package com.medhome.nepal.ui.reminders

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.medhome.nepal.R
import com.medhome.nepal.reminders.PhoneMaker
import com.medhome.nepal.reminders.ReminderAccess
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassButtonStyle
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.components.SectionTitle
import com.medhome.nepal.ui.components.SettingsDivider
import com.medhome.nepal.ui.components.StatusChip
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.theme.GlassTheme

/** One phone maker's steps. */
private class MakerSteps(
    val maker: PhoneMaker,
    @param:StringRes val title: Int,
    @param:StringRes val steps: Int,
    @param:StringRes val button: Int,
    val open: (Context) -> List<Intent>,
)

private val Steps = listOf(
    MakerSteps(PhoneMaker.SAMSUNG, R.string.battery_samsung_title, R.string.battery_samsung_steps, R.string.battery_open_app_info, ReminderAccess::samsungBatterySettings),
    MakerSteps(PhoneMaker.XIAOMI, R.string.battery_xiaomi_title, R.string.battery_xiaomi_steps, R.string.battery_open_autostart, ReminderAccess::xiaomiAutostartSettings),
    MakerSteps(PhoneMaker.OTHER, R.string.battery_other_title, R.string.battery_other_steps, R.string.battery_open_battery, ReminderAccess::batteryOptimizationSettings),
)

/**
 * How to stop the phone from killing reminders: what is set now (notifications, exact alarms,
 * battery optimization), each with a button to its system screen, then the steps for Samsung,
 * Xiaomi and other phones (this phone's maker first). Opens by itself once after the first
 * reminder is set, and stays in Settings.
 */
@Composable
fun BatteryGuideScreen() {
    val context = LocalContext.current
    val access = rememberReminderAccess()
    GlassScreen(showBack = true, drawBackground = false) {
        ScreenTitle(title = R.string.battery_title, subtitle = R.string.battery_intro, modifier = Modifier.entrance(0))

        SectionTitle(text = R.string.battery_status_title, modifier = Modifier.entrance(1))
        GlassCard(modifier = Modifier.entrance(1), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusLine(R.string.battery_status_notifications, access.notifications, R.string.reminders_turn_on) {
                ReminderAccess.openFirst(context, ReminderAccess.notificationSettings(context))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingsDivider()
                StatusLine(R.string.battery_status_exact, access.exactAlarms, R.string.reminders_allow) {
                    ReminderAccess.openFirst(context, ReminderAccess.exactAlarmSettings(context))
                }
            }
            SettingsDivider()
            StatusLine(R.string.battery_status_battery, access.batteryUnrestricted, R.string.battery_open_battery) {
                ReminderAccess.openFirst(context, ReminderAccess.batteryOptimizationSettings(context))
            }
        }

        val ordered = remember { Steps.sortedBy { if (it.maker == ReminderAccess.phoneMaker) 0 else 1 } }
        ordered.forEachIndexed { index, steps ->
            GlassCard(modifier = Modifier.entrance(2 + index), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(steps.title),
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassTheme.colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(text = stringResource(steps.steps), style = MaterialTheme.typography.bodyMedium, color = GlassTheme.colors.textSecondary)
                GlassButton(
                    text = steps.button,
                    onClick = { ReminderAccess.openFirst(context, steps.open(context)) },
                    style = GlassButtonStyle.Secondary,
                    compact = true,
                )
            }
        }
    }
}

/** "Notifications   [On]" with a fix button while it is off. */
@Composable
private fun StatusLine(@StringRes label: Int, ok: Boolean, @StringRes fix: Int, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyLarge,
            color = GlassTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        StatusChip(text = if (ok) R.string.battery_status_ok else R.string.battery_status_off, emphasized = ok)
    }
    if (!ok) GlassButton(text = fix, onClick = onFix, style = GlassButtonStyle.Secondary, compact = true)
}
