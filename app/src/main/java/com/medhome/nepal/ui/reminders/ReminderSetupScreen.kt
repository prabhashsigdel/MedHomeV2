package com.medhome.nepal.ui.reminders

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.medhome.nepal.R
import com.medhome.nepal.reminders.PhoneMaker
import com.medhome.nepal.reminders.ReminderAccess
import com.medhome.nepal.reminders.ReminderSetupItem
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassButtonStyle
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.motion.MotionTokens
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.motion.motionSpec
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassTheme

/** Test tag on the "Still late on some phones?" toggle. */
const val STILL_LATE_TOGGLE_TAG = "still_late_toggle"

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
 * Settings > Reminder setup: the same checklist as the dialog after a medicine is saved, but
 * every item, on or off. Below it, collapsed, the extra steps some phone makers need ("Still
 * late on some phones?", this phone's maker first).
 */
@Composable
fun ReminderSetupScreen() {
    val access = rememberReminderAccess()
    var expanded by rememberSaveable { mutableStateOf(false) }
    GlassScreen(showBack = true, drawBackground = false) {
        ScreenTitle(title = R.string.settings_reminder_setup, subtitle = R.string.reminder_setup_intro, modifier = Modifier.entrance(0))
        GlassCard(modifier = Modifier.entrance(1), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReminderChecklist(items = ReminderSetupItem.entries.filter { it.applies }, access = access)
        }
        StillLateToggle(expanded = expanded, onToggle = { expanded = it }, modifier = Modifier.entrance(2))
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(motionSpec(tween(MotionTokens.CROSSFADE_MS))) + fadeIn(motionSpec(tween(MotionTokens.CROSSFADE_MS))),
            exit = shrinkVertically(motionSpec(tween(MotionTokens.FEEDBACK_MS))) + fadeOut(motionSpec(tween(MotionTokens.FEEDBACK_MS))),
        ) {
            MakerStepsList()
        }
    }
}

/** A small row that opens and closes the phone makers' steps. TalkBack hears it as expanded or not. */
@Composable
private fun StillLateToggle(expanded: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = GlassTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .heightIn(min = GlassDimens.MinTouchTarget)
            .toggleable(
                value = expanded,
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Switch,
                onValueChange = onToggle,
            )
            .testTag(STILL_LATE_TOGGLE_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(R.string.setup_still_late), style = MaterialTheme.typography.titleSmall, color = colors.link)
        Spacer(Modifier.width(4.dp))
        Icon(
            painter = painterResource(R.drawable.ic_sym_chevron_right),
            contentDescription = null,
            tint = colors.link,
            modifier = Modifier.size(20.dp).rotate(if (expanded) 270f else 90f),
        )
    }
}

@Composable
private fun MakerStepsList() {
    val context = LocalContext.current
    val ordered = remember { Steps.sortedBy { if (it.maker == ReminderAccess.phoneMaker) 0 else 1 } }
    Column(verticalArrangement = Arrangement.spacedBy(GlassDimens.ItemSpacing)) {
        ordered.forEach { steps ->
            GlassCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
