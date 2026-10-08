package com.medhome.nepal.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medhome.nepal.R
import com.medhome.nepal.domain.Role
import com.medhome.nepal.domain.UserProfile
import com.medhome.nepal.ui.common.rememberGoogleIdTokenRequest
import com.medhome.nepal.ui.components.ErrorMessage
import com.medhome.nepal.ui.components.FloatingActionBar
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassButtonStyle
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassDialog
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.GlassTextField
import com.medhome.nepal.ui.components.MessageKind
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.components.StatusMessage
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.theme.GlassTheme

@Composable
fun HomeScreen(
    profile: UserProfile,
    usesPassword: Boolean,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    GlassScreen(
        bottomBar = {
            FloatingActionBar {
                GlassButton(
                    text = R.string.home_delete_account,
                    onClick = viewModel::openDeleteDialog,
                    style = GlassButtonStyle.Secondary,
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f),
                )
                GlassButton(
                    text = R.string.home_sign_out,
                    onClick = viewModel::signOut,
                    loading = state.isSigningOut,
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) {
        ScreenTitle(title = R.string.home_title, modifier = Modifier.entrance(0))
        ProfileCard(profile = profile, modifier = Modifier.entrance(1))
        state.signOutError?.let {
            ErrorMessage(error = it, onDismiss = viewModel::dismissSignOutError, onRetry = viewModel::signOut)
        }
    }

    if (state.showDeleteDialog) {
        DeleteAccountDialog(state = state, usesPassword = usesPassword, viewModel = viewModel)
    }
}

@Composable
private fun ProfileCard(profile: UserProfile, modifier: Modifier = Modifier) {
    val colors = GlassTheme.colors
    GlassCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = profile.name, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        Text(text = profile.email, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        Text(
            text = stringResource(R.string.home_role, stringResource(profile.role.labelRes)),
            style = MaterialTheme.typography.labelMedium,
            color = colors.link,
        )
    }
}

@Composable
private fun DeleteAccountDialog(
    state: HomeUiState,
    usesPassword: Boolean,
    viewModel: HomeViewModel,
) {
    val colors = GlassTheme.colors
    val confirmWithGoogle = rememberGoogleIdTokenRequest(
        onStart = viewModel::beginGoogleDelete,
        onResult = viewModel::onGoogleDeleteResult,
    )
    // Back and tapping outside go through dismissDeleteDialog, which ignores them mid-delete.
    GlassDialog(onDismissRequest = viewModel::dismissDeleteDialog) {
        Text(
            text = stringResource(R.string.delete_title),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(if (usesPassword) R.string.delete_body_password else R.string.delete_body_google),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        if (usesPassword) {
            GlassTextField(
                value = state.deletePassword,
                onValueChange = viewModel::onDeletePasswordChange,
                label = R.string.field_password,
                error = state.deletePasswordError,
                enabled = !state.isDeleting,
                isPassword = true,
                imeAction = ImeAction.Done,
                onImeDone = viewModel::confirmDeleteWithPassword,
            )
        }
        state.deleteError?.let { StatusMessage(message = it.messageRes, kind = MessageKind.Error) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassButton(
                text = R.string.action_cancel,
                onClick = viewModel::dismissDeleteDialog,
                style = GlassButtonStyle.Secondary,
                enabled = !state.isDeleting,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                text = R.string.delete_confirm,
                onClick = if (usesPassword) viewModel::confirmDeleteWithPassword else confirmWithGoogle,
                style = GlassButtonStyle.Danger,
                loading = state.isDeleting,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@get:StringRes
private val Role.labelRes: Int
    get() = when (this) {
        Role.PATIENT -> R.string.role_patient
        Role.DOCTOR -> R.string.role_doctor
        Role.ADMIN -> R.string.role_admin
    }
