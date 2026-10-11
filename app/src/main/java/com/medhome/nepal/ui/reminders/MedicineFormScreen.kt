package com.medhome.nepal.ui.reminders

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medhome.nepal.R
import com.medhome.nepal.domain.Medicine
import com.medhome.nepal.domain.Weekday
import com.medhome.nepal.ui.common.DateStyle
import com.medhome.nepal.ui.common.LocaleFormat
import com.medhome.nepal.ui.common.currentLocale
import com.medhome.nepal.ui.components.CalendarDatePickerDialog
import com.medhome.nepal.ui.components.ChoiceOption
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassButtonStyle
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassDialog
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.GlassTextField
import com.medhome.nepal.ui.components.MessageKind
import com.medhome.nepal.ui.components.PickerField
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.components.SectionTitle
import com.medhome.nepal.ui.components.SegmentedChoice
import com.medhome.nepal.ui.components.StatusMessage
import com.medhome.nepal.ui.components.TimePickerDialog
import com.medhome.nepal.ui.components.controlBorder
import com.medhome.nepal.ui.doctors.LoadingCard
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.motion.pressScale
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassShapes
import com.medhome.nepal.ui.theme.GlassTheme

/** Which date picker is open. */
private enum class DateTarget { START, END }

/**
 * Add or edit a medicine: name, dose, how many times a day and when, which days, and from when
 * until when. [onDone] gets how it closed; after a save the medicines list checks the
 * reminder setup (notifications, exact alarms, background).
 */
@Composable
fun MedicineFormScreen(viewModel: MedicineFormViewModel, onDone: (FormResult) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The medicines list asks for what reminders still need (the setup dialog) once this closes.
    LaunchedEffect(state.result) {
        state.result?.let(onDone)
    }
    // A save in progress still commits if the screen goes, so Back waits for it.
    BackHandler(enabled = state.saving) {}

    GlassScreen(showBack = true, drawBackground = false) {
        ScreenTitle(
            title = if (state.isNew) R.string.medicine_add_title else R.string.medicine_edit_title,
            modifier = Modifier.entrance(0),
        )
        when {
            state.loading -> LoadingCard()
            state.notFound -> StatusMessage(message = R.string.medicine_not_found, kind = MessageKind.Error)
            else -> FormContent(state = state, viewModel = viewModel)
        }
    }

    if (state.confirmingDelete) DeleteDialog(onConfirm = viewModel::confirmDelete, onDismiss = viewModel::dismissDelete)
}

@Composable
private fun FormContent(state: MedicineFormUiState, viewModel: MedicineFormViewModel) {
    val fields = state.fields
    val errors = state.errors
    val enabled = state.canEdit
    var pickingTime by rememberSaveable { mutableStateOf<Int?>(null) }
    var pickingDate by rememberSaveable { mutableStateOf<DateTarget?>(null) }
    val locale = currentLocale()

    GlassCard(modifier = Modifier.entrance(1)) {
        GlassTextField(
            value = fields.name,
            onValueChange = viewModel::onNameChange,
            label = R.string.medicine_field_name,
            error = errors.name,
            enabled = enabled,
            capitalization = KeyboardCapitalization.Words,
        )
        GlassTextField(
            value = fields.dose,
            onValueChange = viewModel::onDoseChange,
            label = R.string.medicine_field_dose,
            hint = R.string.medicine_field_dose_hint,
            error = errors.dose,
            enabled = enabled,
        )
    }

    SectionTitle(text = R.string.medicine_times_title, modifier = Modifier.entrance(2))
    GlassCard(modifier = Modifier.entrance(2)) {
        Text(stringResource(R.string.medicine_times_per_day), style = MaterialTheme.typography.bodyMedium, color = GlassTheme.colors.textSecondary)
        SegmentedChoice(
            options = (Medicine.MIN_TIMES..Medicine.MAX_TIMES).map { ChoiceOption(it, LocaleFormat.number(it, locale)) },
            selected = fields.times.size,
            onSelect = viewModel::onTimesPerDayChange,
            enabled = enabled,
        )
        val timeError = errors.times?.let { stringResource(it) }
        fields.times.forEachIndexed { index, time ->
            PickerField(
                label = stringResource(R.string.medicine_time_n, LocaleFormat.number(index + 1, locale)),
                value = LocaleFormat.timeOfDay(time, locale),
                onClick = { pickingTime = index },
                enabled = enabled,
                // The message once, under the last time; every time gets the red border.
                error = timeError?.takeIf { index == fields.times.lastIndex },
            )
        }
    }

    SectionTitle(text = R.string.medicine_days_title, modifier = Modifier.entrance(3))
    GlassCard(modifier = Modifier.entrance(3)) {
        SegmentedChoice(
            options = listOf(
                ChoiceOption(true, stringResource(R.string.medicine_every_day)),
                ChoiceOption(false, stringResource(R.string.medicine_chosen_days)),
            ),
            selected = fields.everyDay,
            onSelect = viewModel::onEveryDayChange,
            enabled = enabled,
        )
        if (!fields.everyDay) {
            WeekdayChips(selected = fields.days, enabled = enabled, onToggle = viewModel::onDayToggle)
            errors.days?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.error) }
        }
    }

    SectionTitle(text = R.string.medicine_dates_title, modifier = Modifier.entrance(4))
    GlassCard(modifier = Modifier.entrance(4)) {
        PickerField(
            label = stringResource(R.string.medicine_start_date),
            value = LocaleFormat.date(fields.startDate, DateStyle.SHORT, locale),
            onClick = { pickingDate = DateTarget.START },
            enabled = enabled,
        )
        SegmentedChoice(
            options = listOf(
                ChoiceOption(false, stringResource(R.string.medicine_no_end)),
                ChoiceOption(true, stringResource(R.string.medicine_has_end)),
            ),
            selected = fields.endDate != null,
            onSelect = viewModel::onHasEndDateChange,
            enabled = enabled,
        )
        fields.endDate?.let { end ->
            PickerField(
                label = stringResource(R.string.medicine_end_date),
                value = LocaleFormat.date(end, DateStyle.SHORT, locale),
                onClick = { pickingDate = DateTarget.END },
                enabled = enabled,
                error = errors.endDate?.let { stringResource(it) },
            )
        }
    }

    if (!errors.isEmpty) StatusMessage(message = R.string.medicine_error_form, kind = MessageKind.Error)
    if (state.changeFailed) StatusMessage(message = R.string.medicine_save_failed, kind = MessageKind.Error, onDismiss = viewModel::dismissError)
    GlassButton(text = R.string.action_save, onClick = viewModel::save, loading = state.saving, enabled = enabled)
    if (!state.isNew) {
        GlassButton(
            text = R.string.medicine_delete,
            onClick = viewModel::askToDelete,
            style = GlassButtonStyle.Danger,
            enabled = enabled,
            compact = true,
        )
    }

    pickingTime?.let { index ->
        val time = fields.times.getOrNull(index)
        if (time != null) {
            TimePickerDialog(
                title = R.string.medicine_pick_time,
                initial = time,
                onConfirm = {
                    viewModel.onTimeChange(index, it)
                    pickingTime = null
                },
                onDismiss = { pickingTime = null },
            )
        }
    }
    pickingDate?.let { target ->
        CalendarDatePickerDialog(
            initial = if (target == DateTarget.START) fields.startDate else fields.endDate ?: fields.startDate,
            earliest = if (target == DateTarget.END) fields.startDate else null,
            onConfirm = {
                if (target == DateTarget.START) viewModel.onStartDateChange(it) else viewModel.onEndDateChange(it)
                pickingDate = null
            },
            onDismiss = { pickingDate = null },
        )
    }
}

/** Sunday to Saturday as chips that each turn on and off (checkboxes for TalkBack). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayChips(selected: Set<Weekday>, enabled: Boolean, onToggle: (Weekday) -> Unit) {
    val colors = GlassTheme.colors
    val locale = currentLocale()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Weekday.entries.forEach { day ->
            val on = day in selected
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .heightIn(min = GlassDimens.MinTouchTarget)
                    .widthIn(min = GlassDimens.MinTouchTarget)
                    .pressScale(interactionSource)
                    .clip(GlassShapes.Chip)
                    .background(if (on) colors.accent else colors.controlFill)
                    .controlBorder(GlassShapes.Chip)
                    .toggleable(
                        value = on,
                        interactionSource = interactionSource,
                        indication = ripple(),
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = { onToggle(day) },
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = LocaleFormat.weekdayShort(day, locale),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) colors.onAccent else colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun DeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    GlassDialog(onDismissRequest = onDismiss, title = R.string.medicine_delete_title) {
        Text(stringResource(R.string.medicine_delete_body), style = MaterialTheme.typography.bodyMedium, color = GlassTheme.colors.textSecondary)
        GlassButton(text = R.string.medicine_delete, onClick = onConfirm, style = GlassButtonStyle.Danger)
        GlassButton(text = R.string.action_cancel, onClick = onDismiss, style = GlassButtonStyle.Secondary)
    }
}
