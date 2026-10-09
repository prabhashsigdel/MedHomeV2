package com.medhome.nepal.ui.doctors

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medhome.nepal.R
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.Doctor
import com.medhome.nepal.domain.Specialty
import com.medhome.nepal.ui.common.feeText
import com.medhome.nepal.ui.components.ChoiceOption
import com.medhome.nepal.ui.components.FilterChipRow
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassLinkButton
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.GlassTextField
import com.medhome.nepal.ui.components.InitialsAvatar
import com.medhome.nepal.ui.components.MessageKind
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.components.StatusMessage
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.theme.GlassTheme

/** Test tag on each doctor's row, for navigation tests. */
const val DOCTOR_ROW_TAG = "doctor_row"

/**
 * Find a doctor (pushed on Home's stack): a name search, specialty chips (only those some doctor
 * has, plus All) and one glass card per doctor. Also shows loading, offline, empty and error
 * states, and says when the list is the copy saved on the phone.
 */
@Composable
fun FindDoctorScreen(
    onOpenDoctor: (String) -> Unit,
    viewModel: DoctorListViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    GlassScreen(showBack = true, drawBackground = false) {
        ScreenTitle(title = R.string.shortcut_find_doctor, modifier = Modifier.entrance(0))
        GlassTextField(
            value = viewModel.query,
            onValueChange = viewModel::onQueryChange,
            label = R.string.doctors_search,
            error = null,
            enabled = true,
            imeAction = ImeAction.Done,
            onImeDone = { focusManager.clearFocus() },
            capitalization = KeyboardCapitalization.Words,
            modifier = Modifier.entrance(1),
        )
        if (state.specialties.size > 1) {
            SpecialtyChips(
                specialties = state.specialties,
                selected = state.filter.specialty,
                onSelect = viewModel::onSpecialtySelected,
            )
        }
        if (state.showingSaved) StatusMessage(message = R.string.doctors_saved_notice, kind = MessageKind.Info)
        DoctorListBody(state = state, onOpenDoctor = onOpenDoctor, onRetry = viewModel::retry, onClear = {
            viewModel.onQueryChange("")
            viewModel.onSpecialtySelected(null)
        })
    }
}

@Composable
private fun SpecialtyChips(specialties: List<Specialty>, selected: Specialty?, onSelect: (Specialty?) -> Unit) {
    val options = listOf(ChoiceOption<Specialty?>(null, stringResource(R.string.doctors_filter_all))) +
        specialties.map { ChoiceOption<Specialty?>(it, stringResource(it.label)) }
    FilterChipRow(options = options, selected = selected, onSelect = onSelect)
}

@Composable
private fun DoctorListBody(
    state: DoctorListUiState,
    onOpenDoctor: (String) -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
) {
    when (state.status) {
        DoctorListStatus.LOADING -> LoadingCard()
        DoctorListStatus.READY -> state.doctors.forEach { doctor ->
            key(doctor.id) { DoctorRow(doctor = doctor, onClick = { onOpenDoctor(doctor.id) }) }
        }
        DoctorListStatus.OFFLINE -> MessageCard(R.string.doctors_offline_title, R.string.doctors_offline_body)
        DoctorListStatus.NO_DOCTORS -> MessageCard(R.string.doctors_none_title, R.string.doctors_none_body)
        DoctorListStatus.NO_MATCHES -> MessageCard(R.string.doctors_no_match_title, R.string.doctors_no_match_body) {
            GlassLinkButton(text = R.string.doctors_clear_filters, onClick = onClear)
        }
        DoctorListStatus.FAILED -> StatusMessage(
            message = (state.error ?: AuthError.UNKNOWN).messageRes,
            kind = MessageKind.Error,
            onRetry = onRetry,
        )
    }
}

/** One doctor: avatar, name, specialty, hospital and fee. TalkBack reads it as one button. */
@Composable
private fun DoctorRow(doctor: Doctor, onClick: () -> Unit) {
    val colors = GlassTheme.colors
    GlassCard(
        onClick = onClick,
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.testTag(DOCTOR_ROW_TAG),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialsAvatar(name = doctor.name)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = doctor.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = stringResource(doctor.specialty.label), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                Text(
                    text = doctor.hospital,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(text = feeText(doctor.feeNpr), style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
        }
    }
}

/** A titled message in a card, with an optional action under it. */
@Composable
internal fun MessageCard(
    @StringRes title: Int,
    @StringRes body: Int,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = GlassTheme.colors
    GlassCard(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = stringResource(title), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Text(text = stringResource(body), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        if (action != null) action()
    }
}

/** A spinner in a card while the first answer is on its way. Announced as "Loading". */
@Composable
internal fun LoadingCard() {
    val label = stringResource(R.string.state_loading)
    GlassCard {
        Box(modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = GlassTheme.colors.accentEmphasis, modifier = Modifier.size(32.dp))
        }
    }
}

/** The specialty's display name. */
@get:StringRes
val Specialty.label: Int
    get() = when (this) {
        Specialty.GENERAL_PHYSICIAN -> R.string.specialty_general_physician
        Specialty.CARDIOLOGY -> R.string.specialty_cardiology
        Specialty.DERMATOLOGY -> R.string.specialty_dermatology
        Specialty.PEDIATRICS -> R.string.specialty_pediatrics
        Specialty.GYNECOLOGY -> R.string.specialty_gynecology
        Specialty.ORTHOPEDICS -> R.string.specialty_orthopedics
        Specialty.ENT -> R.string.specialty_ent
        Specialty.NEUROLOGY -> R.string.specialty_neurology
        Specialty.PSYCHIATRY -> R.string.specialty_psychiatry
        Specialty.OPHTHALMOLOGY -> R.string.specialty_ophthalmology
        Specialty.DENTISTRY -> R.string.specialty_dentistry
        Specialty.GASTROENTEROLOGY -> R.string.specialty_gastroenterology
    }
