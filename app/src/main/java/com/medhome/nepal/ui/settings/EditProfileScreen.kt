package com.medhome.nepal.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medhome.nepal.R
import com.medhome.nepal.appContainer
import com.medhome.nepal.domain.Gender
import com.medhome.nepal.domain.UserProfile
import com.medhome.nepal.ui.common.BirthDate
import com.medhome.nepal.ui.components.ChoiceOption
import com.medhome.nepal.ui.components.ErrorMessage
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassLinkButton
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.GlassTextField
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.components.SegmentedChoice
import com.medhome.nepal.ui.components.glassControl
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassShapes
import com.medhome.nepal.ui.theme.GlassTheme

@Composable
fun EditProfileScreen(
    profile: UserProfile,
    onDone: () -> Unit,
    viewModel: EditProfileViewModel = viewModel(
        factory = viewModelFactory {
            initializer { EditProfileViewModel(appContainer.sessionManager, profile) }
        },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val enabled = !state.isSaving

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    GlassScreen(showBack = true, drawBackground = false) {
        ScreenTitle(title = R.string.edit_profile_title, modifier = Modifier.entrance(0))
        GlassCard(modifier = Modifier.entrance(1)) {
            state.error?.let { ErrorMessage(error = it, onDismiss = viewModel::dismissError) }
            GlassTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = R.string.field_name,
                error = state.nameError,
                enabled = enabled,
                capitalization = KeyboardCapitalization.Words,
                contentType = ContentType.PersonFullName,
            )
            GlassTextField(
                value = state.phone,
                onValueChange = viewModel::onPhoneChange,
                label = R.string.field_phone,
                error = state.phoneError,
                hint = R.string.field_phone_hint,
                enabled = enabled,
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done,
                contentType = ContentType.PhoneNumber,
            )
            DateOfBirthField(
                isoDate = state.dateOfBirth,
                error = state.dateOfBirthError,
                enabled = enabled,
                onOpen = viewModel::openDatePicker,
                onClear = viewModel::clearDateOfBirth,
            )
            GenderField(selected = state.gender, enabled = enabled, onChange = viewModel::onGenderChange)
            GlassButton(
                text = R.string.action_save,
                onClick = viewModel::save,
                loading = state.isSaving,
                enabled = enabled,
            )
        }
    }

    if (state.showDatePicker) {
        BirthDatePickerDialog(
            initialIso = state.dateOfBirth,
            onConfirm = viewModel::onDateSelected,
            onDismiss = viewModel::dismissDatePicker,
        )
    }
}

/** Looks like a field; opens the date picker. Shows the date in the app's language. */
@Composable
private fun DateOfBirthField(
    isoDate: String?,
    @StringRes error: Int?,
    enabled: Boolean,
    onOpen: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = GlassTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val shown = isoDate?.let { BirthDate.display(it, locale) } ?: stringResource(R.string.date_of_birth_not_set)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(GlassDimens.FieldHeight)
                .glassControl(GlassShapes.Input)
                .then(
                    if (error != null) Modifier.border(GlassDimens.BorderWidth, colors.error, GlassShapes.Input) else Modifier,
                )
                .clickable(enabled = enabled, role = Role.Button, onClick = onOpen)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.field_date_of_birth),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
                Text(text = shown, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            }
            if (isoDate != null) GlassLinkButton(text = R.string.action_clear, onClick = onClear, enabled = enabled)
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
}

@Composable
private fun GenderField(selected: Gender?, enabled: Boolean, onChange: (Gender?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.field_gender),
                style = MaterialTheme.typography.labelMedium,
                color = GlassTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            if (selected != null) GlassLinkButton(text = R.string.action_clear, onClick = { onChange(null) }, enabled = enabled)
        }
        SegmentedChoice(
            options = Gender.entries.map { ChoiceOption(it, stringResource(it.labelRes)) },
            selected = selected,
            onSelect = onChange,
            enabled = enabled,
        )
    }
}

@get:StringRes
internal val Gender.labelRes: Int
    get() = when (this) {
        Gender.MALE -> R.string.gender_male
        Gender.FEMALE -> R.string.gender_female
        Gender.OTHER -> R.string.gender_other
    }

/** Material's date picker (solid surfaces), limited to dates up to today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDatePickerDialog(
    initialIso: String?,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = System.currentTimeMillis()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialIso?.let(BirthDate::toUtcMillis),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { pickerState.selectedDateMillis?.let(onConfirm) ?: onDismiss() },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = pickerState)
    }
}
