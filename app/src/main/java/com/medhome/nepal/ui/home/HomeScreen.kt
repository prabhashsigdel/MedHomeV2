package com.medhome.nepal.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medhome.nepal.R
import com.medhome.nepal.domain.Role
import com.medhome.nepal.domain.UserProfile
import com.medhome.nepal.ui.common.ErrorCard
import com.medhome.nepal.ui.common.FormScreen
import com.medhome.nepal.ui.common.LoadingButton
import com.medhome.nepal.ui.common.PasswordTextField
import com.medhome.nepal.ui.common.ScreenTitle
import com.medhome.nepal.ui.common.rememberGoogleIdTokenRequest

@Composable
fun HomeScreen(
    profile: UserProfile,
    usesPassword: Boolean,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    FormScreen {
        ScreenTitle(title = R.string.home_title)
        ProfileCard(profile)

        state.signOutError?.let {
            ErrorCard(error = it, onDismiss = viewModel::dismissSignOutError, onRetry = viewModel::signOut)
        }

        LoadingButton(
            text = R.string.home_sign_out,
            loading = state.isSigningOut,
            enabled = !state.isBusy,
            onClick = viewModel::signOut,
        )
        OutlinedButton(
            onClick = viewModel::openDeleteDialog,
            enabled = !state.isBusy,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_delete_account))
        }
    }

    if (state.showDeleteDialog) {
        DeleteAccountDialog(state = state, usesPassword = usesPassword, viewModel = viewModel)
    }
}

@Composable
private fun ProfileCard(profile: UserProfile) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = profile.name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = profile.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.home_role, stringResource(profile.role.labelRes)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DeleteAccountDialog(
    state: HomeUiState,
    usesPassword: Boolean,
    viewModel: HomeViewModel,
) {
    val confirmWithGoogle = rememberGoogleIdTokenRequest(
        onStart = viewModel::beginGoogleDelete,
        onResult = viewModel::onGoogleDeleteResult,
    )
    AlertDialog(
        onDismissRequest = viewModel::dismissDeleteDialog,
        title = { Text(stringResource(R.string.delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        if (usesPassword) R.string.delete_body_password else R.string.delete_body_google,
                    ),
                )
                if (usesPassword) {
                    PasswordTextField(
                        value = state.deletePassword,
                        onValueChange = viewModel::onDeletePasswordChange,
                        label = R.string.field_password,
                        error = state.deletePasswordError,
                        enabled = !state.isDeleting,
                    )
                }
                state.deleteError?.let { Text(stringResource(it.messageRes), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = if (usesPassword) viewModel::confirmDeleteWithPassword else confirmWithGoogle,
                enabled = !state.isDeleting,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(if (state.isDeleting) R.string.delete_in_progress else R.string.delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissDeleteDialog, enabled = !state.isDeleting) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@get:StringRes
private val Role.labelRes: Int
    get() = when (this) {
        Role.PATIENT -> R.string.role_patient
        Role.DOCTOR -> R.string.role_doctor
        Role.ADMIN -> R.string.role_admin
    }
