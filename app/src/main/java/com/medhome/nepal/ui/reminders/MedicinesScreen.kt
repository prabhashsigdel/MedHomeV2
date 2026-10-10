package com.medhome.nepal.ui.reminders

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medhome.nepal.R
import com.medhome.nepal.domain.Medicine
import com.medhome.nepal.ui.common.DateStyle
import com.medhome.nepal.ui.common.LocaleFormat
import com.medhome.nepal.ui.common.currentLocale
import com.medhome.nepal.ui.components.ChoiceOption
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.MessageKind
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.components.SegmentedChoice
import com.medhome.nepal.ui.components.SettingsDivider
import com.medhome.nepal.ui.components.StatusMessage
import com.medhome.nepal.ui.doctors.LoadingCard
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.theme.GlassTheme

/** Test tags. */
const val MEDICINE_CARD_TAG = "medicine_card"
const val HISTORY_DAY_TAG = "history_day"

/** The Medicines tab's three views. */
enum class MedicinesSegment { TODAY, HISTORY, MEDICINES }

/**
 * The Medicines tab: Today (each of today's doses, taken / upcoming / missed, tap to mark
 * taken), History (the last 30 days by day, taken or missed per dose) and the medicines
 * themselves (add, tap to edit), with what may stop reminders arriving. [checkSetup] is set when
 * a medicine was just saved: what the phone still blocks is offered once in the setup dialog
 * ([onSetupChecked] clears it).
 */
@Composable
fun MedicinesScreen(
    viewModel: MedicinesViewModel,
    today: TodayRemindersViewModel,
    history: DoseHistoryViewModel,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    checkSetup: Boolean = false,
    onSetupChecked: () -> Unit = {},
) {
    val setupItems by viewModel.setupItems.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var segment by rememberSaveable { mutableStateOf(MedicinesSegment.TODAY) }
    LaunchedEffect(checkSetup) {
        if (checkSetup) {
            onSetupChecked()
            viewModel.checkSetup(ReminderAccessState.read(context).missing())
        }
    }
    if (setupItems.isNotEmpty()) ReminderSetupDialog(items = setupItems, onDismiss = viewModel::dismissSetup)

    GlassScreen(drawBackground = false) {
        ScreenTitle(title = R.string.nav_medicines, modifier = Modifier.entrance(0))
        SegmentedChoice(
            options = listOf(
                ChoiceOption(MedicinesSegment.TODAY, stringResource(R.string.medicines_today)),
                ChoiceOption(MedicinesSegment.HISTORY, stringResource(R.string.medicines_history)),
                ChoiceOption(MedicinesSegment.MEDICINES, stringResource(R.string.nav_medicines)),
            ),
            selected = segment,
            onSelect = { segment = it },
            modifier = Modifier.entrance(1),
        )
        when (segment) {
            MedicinesSegment.TODAY -> TodayView(viewModel = today, onAdd = onAdd)
            MedicinesSegment.HISTORY -> HistoryView(viewModel = history)
            MedicinesSegment.MEDICINES -> MedicinesList(viewModel = viewModel, onAdd = onAdd, onOpen = onOpen)
        }
    }
}

/** Today's doses in time order; a tap marks one taken (or not, to undo). */
@Composable
private fun TodayView(viewModel: TodayRemindersViewModel, onAdd: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val access = rememberReminderAccess()
    when {
        state.loading -> LoadingCard()
        !state.hasMedicines -> EmptyCard(R.string.medicines_empty, R.string.medicines_empty_body) {
            GlassButton(text = R.string.medicines_add, onClick = onAdd)
        }
        state.doses.isEmpty() -> EmptyCard(R.string.home_no_medicines, R.string.home_no_doses_today_body)
        else -> {
            ReminderAccessNotices(access = access, channelAllowed = access.medicineChannel, needsExact = true)
            if (state.changeFailed) {
                StatusMessage(message = R.string.dose_change_failed, kind = MessageKind.Error, onDismiss = viewModel::dismissError)
            }
            GlassCard(
                modifier = Modifier.entrance(2),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                state.doses.forEachIndexed { index, dose ->
                    if (index > 0) SettingsDivider()
                    DoseRow(dose = dose, onToggle = viewModel::toggleTaken)
                }
            }
            if (state.takenCount > 0) {
                Text(
                    text = pluralStringResource(R.plurals.today_taken_count, state.takenCount, LocaleFormat.number(state.takenCount, currentLocale())),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTheme.colors.textSecondary,
                )
            }
        }
    }
}

/** The last 30 days, latest first: a date, then that day's doses, each taken or missed. */
@Composable
private fun HistoryView(viewModel: DoseHistoryViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = currentLocale()
    when {
        state.loading -> LoadingCard()
        state.days.isEmpty() -> EmptyCard(R.string.medicines_history_empty, R.string.medicines_history_empty_body)
        else -> state.days.forEach { (date, doses) ->
            key(date) {
                Text(
                    text = LocaleFormat.date(date, DateStyle.FULL, locale),
                    style = MaterialTheme.typography.titleSmall,
                    color = GlassTheme.colors.textSecondary,
                    modifier = Modifier.semantics { heading() },
                )
                GlassCard(
                    modifier = Modifier.testTag(HISTORY_DAY_TAG),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    doses.forEachIndexed { index, dose ->
                        if (index > 0) SettingsDivider()
                        DoseHistoryRow(dose)
                    }
                }
            }
        }
    }
}

/** What may stop reminders arriving (each with its fix), Add medicine, and every medicine (tap to edit). */
@Composable
private fun MedicinesList(viewModel: MedicinesViewModel, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val access = rememberReminderAccess()
    when (val current = state) {
        MedicinesUiState.Loading -> LoadingCard()
        is MedicinesUiState.Ready -> {
            Text(
                text = stringResource(R.string.medicines_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTheme.colors.textSecondary,
            )
            if (!current.prefs.medicineReminders) {
                StatusMessage(message = R.string.medicines_reminders_off, kind = MessageKind.Warning)
            } else if (current.medicines.isNotEmpty()) {
                ReminderAccessNotices(access = access, channelAllowed = access.medicineChannel, needsExact = true)
            }
            GlassButton(text = R.string.medicines_add, onClick = onAdd, modifier = Modifier.entrance(2))
            if (current.medicines.isEmpty()) {
                EmptyCard(R.string.medicines_empty, R.string.medicines_empty_body)
            } else {
                current.medicines.forEachIndexed { index, medicine ->
                    key(medicine.id) {
                        MedicineCard(
                            medicine = medicine,
                            onClick = { onOpen(medicine.id) },
                            // Only the first few animate in (Column, so nothing re-animates on scroll).
                            modifier = if (index < ENTRANCE_ITEMS) Modifier.entrance(3 + index) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCard(@StringRes title: Int, @StringRes body: Int, action: (@Composable () -> Unit)? = null) {
    GlassCard(modifier = Modifier.entrance(2), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = GlassTheme.colors.textPrimary)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = GlassTheme.colors.textSecondary)
        action?.invoke()
    }
}

@Composable
private fun MedicineCard(medicine: Medicine, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GlassTheme.colors
    GlassCard(
        onClick = onClick,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .testTag(MEDICINE_CARD_TAG)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(text = medicine.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Text(text = medicine.dose, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        Text(text = scheduleText(medicine), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        Text(text = periodText(medicine.startDate, medicine.endDate), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

private const val ENTRANCE_ITEMS = 4
