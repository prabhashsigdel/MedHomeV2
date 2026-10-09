package com.medhome.nepal.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medhome.nepal.R
import com.medhome.nepal.domain.ManagedDoctor
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.GlassTextField
import com.medhome.nepal.ui.components.InitialsAvatar
import com.medhome.nepal.ui.components.MessageKind
import com.medhome.nepal.ui.components.StatusChip
import com.medhome.nepal.ui.components.StatusMessage
import com.medhome.nepal.ui.doctors.LoadingCard
import com.medhome.nepal.ui.doctors.MessageCard
import com.medhome.nepal.ui.doctors.label
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.motion.pressScale
import com.medhome.nepal.ui.theme.GlassTheme

/** Test tags, for navigation tests. */
const val ADMIN_DOCTOR_ROW_TAG = "admin_doctor_row"
const val ADMIN_SETTINGS_BUTTON_TAG = "admin_settings_button"

private val SettingsButtonSize = 48.dp

/**
 * The admin's home: every doctor (active and inactive, with a badge), a search by name or
 * hospital, Add doctor, and the avatar that opens Settings.
 */
@Composable
fun AdminDoctorsScreen(
    adminName: String,
    viewModel: AdminDoctorListViewModel,
    onOpenDoctor: (String) -> Unit,
    onAddDoctor: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    GlassScreen(drawBackground = false) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.entrance(0)) {
            Text(
                text = stringResource(R.string.admin_doctors_title),
                style = MaterialTheme.typography.headlineLarge,
                color = GlassTheme.colors.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Spacer(Modifier.width(16.dp))
            SettingsButton(name = adminName, onClick = onOpenSettings)
        }
        GlassTextField(
            value = viewModel.query,
            onValueChange = viewModel::onQueryChange,
            label = R.string.admin_search,
            error = null,
            enabled = true,
            imeAction = ImeAction.Done,
            onImeDone = { focusManager.clearFocus() },
            capitalization = KeyboardCapitalization.Words,
            modifier = Modifier.entrance(1),
        )
        GlassButton(text = R.string.admin_add_doctor, onClick = onAddDoctor, modifier = Modifier.entrance(1))
        if (state.showingSaved) StatusMessage(message = R.string.doctors_saved_notice, kind = MessageKind.Info)
        when (state.status) {
            AdminListStatus.LOADING -> LoadingCard()
            AdminListStatus.READY -> state.doctors.forEach { managed ->
                key(managed.doctor.id) { AdminDoctorRow(managed = managed, onClick = { onOpenDoctor(managed.doctor.id) }) }
            }
            AdminListStatus.OFFLINE -> MessageCard(R.string.doctors_offline_title, R.string.doctors_offline_body)
            AdminListStatus.NO_DOCTORS -> MessageCard(R.string.admin_doctors_none_title, R.string.admin_doctors_none_body)
            AdminListStatus.NO_MATCHES -> MessageCard(R.string.admin_no_match_title, R.string.admin_no_match_body)
            AdminListStatus.FAILED -> StatusMessage(
                message = R.string.doctors_load_failed,
                kind = MessageKind.Error,
                onRetry = viewModel::retry,
            )
        }
    }
}

/** The admin's initials in a glass circle; opens Settings. Announced as "Settings". */
@Composable
private fun SettingsButton(name: String, onClick: () -> Unit) {
    val description = stringResource(R.string.admin_open_settings)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(SettingsButtonSize)
            .pressScale(interactionSource)
            .clip(CircleShape)
            .clickable(interactionSource = interactionSource, indication = ripple(), role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(ADMIN_SETTINGS_BUTTON_TAG),
        contentAlignment = Alignment.Center,
    ) {
        InitialsAvatar(name = name, size = SettingsButtonSize)
    }
}

/** One doctor: avatar, name, specialty, hospital and an Active / Inactive badge. One button for TalkBack. */
@Composable
private fun AdminDoctorRow(managed: ManagedDoctor, onClick: () -> Unit) {
    val colors = GlassTheme.colors
    val doctor = managed.doctor
    GlassCard(
        onClick = onClick,
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.testTag(ADMIN_DOCTOR_ROW_TAG),
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
            StatusChip(
                text = if (managed.active) R.string.admin_status_active else R.string.admin_status_inactive,
                emphasized = managed.active,
            )
        }
    }
}
