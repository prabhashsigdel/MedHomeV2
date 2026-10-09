package com.medhome.nepal.ui.reminders

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medhome.nepal.R
import com.medhome.nepal.domain.AppointmentReminder
import com.medhome.nepal.domain.NepalTime
import com.medhome.nepal.domain.TodayDose
import com.medhome.nepal.ui.common.LocaleFormat
import com.medhome.nepal.ui.common.currentLocale
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassButtonStyle
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.MessageKind
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.components.SectionTitle
import com.medhome.nepal.ui.components.SettingsDivider
import com.medhome.nepal.ui.components.StatusMessage
import com.medhome.nepal.ui.doctors.LoadingCard
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.theme.GlassTheme

/**
 * The bell's screen: today's missed doses (still tappable, to mark them taken late), what is
 * still to come today (doses and appointments), and how many were taken.
 */
@Composable
fun TodayRemindersScreen(viewModel: TodayRemindersViewModel, onOpenMedicines: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val access = rememberReminderAccess()
    GlassScreen(showBack = true, drawBackground = false) {
        ScreenTitle(title = R.string.today_reminders_title, modifier = Modifier.entrance(0))
        if (state.loading) {
            LoadingCard()
            return@GlassScreen
        }
        if (state.doses.isNotEmpty() || state.appointments.isNotEmpty()) {
            ReminderAccessNotices(access = access, channelAllowed = access.medicineChannel, needsExact = state.doses.isNotEmpty())
        }
        if (state.changeFailed) {
            StatusMessage(message = R.string.dose_change_failed, kind = MessageKind.Error, onDismiss = viewModel::dismissError)
        }
        val nothingLeft = state.missed.isEmpty() && state.upcoming.isEmpty() && state.appointments.isEmpty()
        if (nothingLeft) {
            GlassCard(modifier = Modifier.entrance(1), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.today_nothing_left), style = MaterialTheme.typography.titleMedium, color = GlassTheme.colors.textPrimary)
                Text(
                    stringResource(if (state.hasMedicines) R.string.today_nothing_left_body else R.string.home_no_medicines_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTheme.colors.textSecondary,
                )
            }
        }
        if (state.missed.isNotEmpty()) DoseSection(R.string.today_missed, state.missed, viewModel::toggleTaken, 1)
        if (state.upcoming.isNotEmpty()) DoseSection(R.string.today_upcoming, state.upcoming, viewModel::toggleTaken, 2)
        if (state.appointments.isNotEmpty()) AppointmentSection(state.appointments)
        if (state.takenCount > 0) {
            Text(
                text = pluralStringResource(R.plurals.today_taken_count, state.takenCount, LocaleFormat.number(state.takenCount, currentLocale())),
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTheme.colors.textSecondary,
            )
        }
        GlassButton(text = R.string.today_manage_medicines, onClick = onOpenMedicines, style = GlassButtonStyle.Secondary)
    }
}

@Composable
private fun DoseSection(@StringRes title: Int, doses: List<TodayDose>, onToggle: (TodayDose) -> Unit, order: Int) {
    SectionTitle(text = title, modifier = Modifier.entrance(order))
    GlassCard(
        modifier = Modifier.entrance(order),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        doses.forEachIndexed { index, dose ->
            if (index > 0) SettingsDivider()
            DoseRow(dose = dose, onToggle = onToggle)
        }
    }
}

@Composable
private fun AppointmentSection(appointments: List<AppointmentReminder>) {
    val colors = GlassTheme.colors
    val locale = currentLocale()
    SectionTitle(text = R.string.today_appointments, modifier = Modifier.entrance(3))
    appointments.forEach { appointment ->
        GlassCard(
            modifier = Modifier.entrance(3).semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = LocaleFormat.timeOfDay(NepalTime.timeOf(appointment.startAtMillis), locale),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(
                text = appointment.doctorName.ifEmpty { stringResource(R.string.today_appointment_no_doctor) },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}
