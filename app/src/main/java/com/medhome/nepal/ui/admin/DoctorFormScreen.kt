package com.medhome.nepal.ui.admin

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medhome.nepal.R
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.Specialty
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.domain.Weekday
import com.medhome.nepal.ui.common.LocaleFormat
import com.medhome.nepal.ui.common.currentLocale
import com.medhome.nepal.ui.components.ChoiceOption
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassChoiceSheet
import com.medhome.nepal.ui.components.GlassLinkButton
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.GlassTextArea
import com.medhome.nepal.ui.components.GlassTextField
import com.medhome.nepal.ui.components.MessageKind
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.components.SectionTitle
import com.medhome.nepal.ui.components.SettingsDivider
import com.medhome.nepal.ui.components.StatusMessage
import com.medhome.nepal.ui.components.glassControl
import com.medhome.nepal.ui.doctors.LoadingCard
import com.medhome.nepal.ui.doctors.label
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassShapes
import com.medhome.nepal.ui.theme.GlassTheme

/** Which time of which range the time picker is changing. */
private data class TimeTarget(val day: Weekday, val index: Int, val isStart: Boolean)

/**
 * Add a doctor, or edit one: details, specialty from the fixed list, and the weekly hours (up
 * to 3 ranges a day). Problems show under each field once Save was pressed; range problems
 * show as soon as they exist. Closes itself once saved.
 */
@Composable
fun DoctorFormScreen(viewModel: DoctorFormViewModel, onSaved: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    // A save in progress still commits if the screen goes, so Back (and the back arrow, which
    // goes through the same dispatcher) waits for it to finish.
    BackHandler(enabled = state.isSaving) {}

    GlassScreen(showBack = true, drawBackground = false) {
        ScreenTitle(title = if (state.isNew) R.string.admin_add_title else R.string.admin_edit_title, modifier = Modifier.entrance(0))
        when {
            state.isLoading -> LoadingCard()
            state.loadError != null -> StatusMessage(
                message = if (state.loadError == AuthError.UNKNOWN) R.string.admin_doctor_missing_body else R.string.admin_doctor_load_failed,
                kind = MessageKind.Error,
                onRetry = viewModel::retryLoad,
            )
            else -> FormContent(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun FormContent(state: DoctorFormUiState, viewModel: DoctorFormViewModel) {
    val fields = state.fields
    val errors = state.errors
    val enabled = state.canEdit
    GlassCard(modifier = Modifier.entrance(1)) {
        GlassTextField(
            value = fields.name,
            onValueChange = viewModel::onNameChange,
            label = R.string.admin_field_name,
            error = errors.name,
            enabled = enabled,
            capitalization = KeyboardCapitalization.Words,
        )
        SpecialtyField(selected = fields.specialty, error = errors.specialty, enabled = enabled, onSelect = viewModel::onSpecialtyChange)
        GlassTextField(
            value = fields.hospital,
            onValueChange = viewModel::onHospitalChange,
            label = R.string.admin_field_hospital,
            error = errors.hospital,
            enabled = enabled,
            capitalization = KeyboardCapitalization.Words,
        )
        NumberField(fields.fee, viewModel::onFeeChange, R.string.admin_field_fee, errors.fee, enabled)
        NumberField(fields.experience, viewModel::onExperienceChange, R.string.admin_field_experience, errors.experience, enabled)
        NumberField(fields.slotMinutes, viewModel::onSlotMinutesChange, R.string.admin_field_slot_minutes, errors.slotMinutes, enabled)
        GlassTextArea(
            value = fields.bio,
            onValueChange = viewModel::onBioChange,
            label = R.string.admin_field_bio,
            error = errors.bio,
            enabled = enabled,
        )
    }

    SectionTitle(text = R.string.admin_schedule_title, modifier = Modifier.entrance(2))
    Text(
        text = stringResource(R.string.admin_schedule_hint),
        style = MaterialTheme.typography.bodySmall,
        color = GlassTheme.colors.textSecondary,
    )
    ScheduleEditorCard(schedule = fields.schedule, enabled = enabled, viewModel = viewModel)

    if (!errors.isEmpty) StatusMessage(message = R.string.admin_error_form, kind = MessageKind.Error)
    state.saveError?.let {
        StatusMessage(message = adminErrorText(it), kind = MessageKind.Error, onDismiss = viewModel::dismissSaveError)
    }
    GlassButton(text = R.string.action_save, onClick = viewModel::save, loading = state.isSaving, enabled = enabled)
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, @StringRes label: Int, @StringRes error: Int?, enabled: Boolean) {
    GlassTextField(
        value = value,
        onValueChange = onChange,
        label = label,
        error = error,
        enabled = enabled,
        keyboardType = KeyboardType.Number,
    )
}

/** Looks like a field; opens a sheet with every specialty. */
@Composable
private fun SpecialtyField(selected: Specialty?, @StringRes error: Int?, enabled: Boolean, onSelect: (Specialty) -> Unit) {
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val colors = GlassTheme.colors
    val label = stringResource(R.string.admin_field_specialty)
    val value = selected?.let { stringResource(it.label) } ?: stringResource(R.string.admin_specialty_none)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(GlassDimens.FieldHeight)
                .glassControl(GlassShapes.Input)
                .then(if (error != null) Modifier.border(GlassDimens.BorderWidth, colors.error, GlassShapes.Input) else Modifier)
                .clickable(enabled = enabled, role = Role.Button) { showSheet = true }
                .semantics { contentDescription = "$label, $value" }
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = if (error != null) colors.error else colors.textSecondary)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected != null) colors.textPrimary else colors.textSecondary,
            )
        }
        if (error != null) {
            Text(
                text = stringResource(error),
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
    if (showSheet) {
        GlassChoiceSheet(
            title = R.string.admin_field_specialty,
            options = Specialty.entries.map { ChoiceOption(it, stringResource(it.label)) },
            selected = selected,
            onSelect = onSelect,
            onDismissRequest = { showSheet = false },
        )
    }
}

/** Sunday to Saturday: each day's ranges (start, end, Remove) or "Closed", and Add hours. */
@Composable
private fun ScheduleEditorCard(schedule: ScheduleInput, enabled: Boolean, viewModel: DoctorFormViewModel) {
    var picking by remember { mutableStateOf<TimeTarget?>(null) }
    val locale = currentLocale()
    GlassCard(modifier = Modifier.entrance(2), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Weekday.entries.forEachIndexed { dayIndex, day ->
            if (dayIndex > 0) SettingsDivider()
            val ranges = ScheduleEditor.ranges(schedule, day)
            val problems = ScheduleEditor.problems(ranges)
            val dayName = LocaleFormat.weekdayLong(day, locale)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (ranges.isEmpty()) {
                    Text(
                        text = stringResource(R.string.admin_day_closed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTheme.colors.textSecondary,
                    )
                }
            }
            ranges.forEachIndexed { index, range ->
                RangeRow(
                    dayName = dayName,
                    range = range,
                    problem = problems[index],
                    enabled = enabled,
                    onPickStart = { picking = TimeTarget(day, index, isStart = true) },
                    onPickEnd = { picking = TimeTarget(day, index, isStart = false) },
                    onRemove = { viewModel.removeRange(day, index) },
                )
            }
            if (ScheduleEditor.canAdd(schedule, day)) {
                val addLabel = stringResource(R.string.admin_add_hours_on, dayName)
                GlassLinkButton(
                    text = R.string.admin_add_hours,
                    onClick = { viewModel.addRange(day) },
                    enabled = enabled,
                    modifier = Modifier.labelled(addLabel, enabled) { viewModel.addRange(day) },
                )
            }
        }
    }

    picking?.let { target ->
        // The range can be gone (removed meanwhile): then there is nothing to pick for.
        val range = ScheduleEditor.ranges(schedule, target.day).getOrNull(target.index)
        if (range != null) {
            TimePickerDialog(
                title = if (target.isStart) R.string.admin_range_start else R.string.admin_range_end,
                initial = if (target.isStart) range.start else range.end,
                onConfirm = { time ->
                    if (target.isStart) {
                        viewModel.setRangeStart(target.day, target.index, time)
                    } else {
                        viewModel.setRangeEnd(target.day, target.index, time)
                    }
                    picking = null
                },
                onDismiss = { picking = null },
            )
        }
    }
}

@Composable
private fun RangeRow(
    dayName: String,
    range: RangeInput,
    problem: RangeProblem?,
    enabled: Boolean,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = GlassTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeButton(label = R.string.admin_range_start, time = range.start, hasProblem = problem != null, enabled = enabled, onClick = onPickStart, modifier = Modifier.weight(1f))
            // Decorative: each time is announced with its own label.
            Text(text = "–", style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary, modifier = Modifier.clearAndSetSemantics {})
            TimeButton(label = R.string.admin_range_end, time = range.end, hasProblem = problem != null, enabled = enabled, onClick = onPickEnd, modifier = Modifier.weight(1f))
            val locale = currentLocale()
            val removeLabel = stringResource(
                R.string.admin_remove_hours_of,
                stringResource(R.string.doctor_hours_range, LocaleFormat.timeOfDay(range.start, locale), LocaleFormat.timeOfDay(range.end, locale)),
                dayName,
            )
            GlassLinkButton(
                text = R.string.admin_remove_hours,
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.labelled(removeLabel, enabled, onRemove),
            )
        }
        if (problem != null) {
            Text(
                text = stringResource(
                    when (problem) {
                        RangeProblem.END_NOT_AFTER_START -> R.string.admin_range_end_before_start
                        RangeProblem.OVERLAPS -> R.string.admin_range_overlaps
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** A time in the app's language, in a control; opens the time picker. Read as "Starts, 9:00 AM". */
@Composable
private fun TimeButton(
    @StringRes label: Int,
    time: TimeOfDay,
    hasProblem: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GlassTheme.colors
    val text = LocaleFormat.timeOfDay(time, currentLocale())
    val description = "${stringResource(label)}, $text"
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = colors.textPrimary,
        modifier = modifier
            .heightIn(min = GlassDimens.MinTouchTarget)
            .glassControl(GlassShapes.Input)
            .then(if (hasProblem) Modifier.border(GlassDimens.BorderWidth, colors.error, GlassShapes.Input) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 12.dp),
    )
}

/** Material's time input (solid surfaces), in the phone's 12 / 24-hour setting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    @StringRes title: Int,
    initial: TimeOfDay,
    onConfirm: (TimeOfDay) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { TimeInput(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(TimeOfDay(state.hour * TimeOfDay.MINUTES_PER_HOUR + state.minute)) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * A button announced by [label] instead of its short visible text ("Remove" on every row would
 * say nothing about which hours), still a button TalkBack can press.
 */
private fun Modifier.labelled(label: String, enabled: Boolean, onClick: () -> Unit): Modifier =
    clearAndSetSemantics {
        contentDescription = label
        role = Role.Button
        if (!enabled) disabled()
        onClick { onClick(); true }
    }
