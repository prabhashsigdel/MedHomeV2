package com.medhome.nepal.ui.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.medhome.nepal.R
import com.medhome.nepal.ui.auth.SuppressAutofillSave
import com.medhome.nepal.ui.common.rememberGoogleIdTokenRequest
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassButtonStyle
import com.medhome.nepal.ui.components.GlassDialog
import com.medhome.nepal.ui.components.GlassTextField
import com.medhome.nepal.ui.components.MessageKind
import com.medhome.nepal.ui.components.StatusMessage
import com.medhome.nepal.ui.theme.GlassTheme

@Composable
internal fun DeleteAccountDialog(state: ProfileUiState, usesPassword: Boolean, viewModel: ProfileViewModel) {
    val confirmWithGoogle = rememberGoogleIdTokenRequest(
        onStart = viewModel::beginGoogleDelete,
        onResult = viewModel::onGoogleDeleteResult,
    )
    // Back and tapping outside go through dismissDeleteDialog, which ignores them mid-delete.
    GlassDialog(onDismissRequest = viewModel::dismissDeleteDialog) {
        SuppressAutofillSave()
        DialogTitle(R.string.delete_title)
        Text(
            text = stringResource(if (usesPassword) R.string.delete_body_password else R.string.delete_body_google),
            style = MaterialTheme.typography.bodyMedium,
            color = GlassTheme.colors.textSecondary,
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
                contentType = ContentType.Password,
            )
        }
        state.deleteError?.let { StatusMessage(message = it.messageRes, kind = MessageKind.Error) }
        DialogButtons(
            confirmText = R.string.delete_confirm,
            confirmStyle = GlassButtonStyle.Danger,
            busy = state.isDeleting,
            onCancel = viewModel::dismissDeleteDialog,
            onConfirm = if (usesPassword) viewModel::confirmDeleteWithPassword else confirmWithGoogle,
        )
    }
}

@Composable
private fun DialogTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.headlineSmall,
        color = GlassTheme.colors.textPrimary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun DialogButtons(
    @StringRes confirmText: Int,
    confirmStyle: GlassButtonStyle,
    busy: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    // Stacked full width (confirm first) so translated labels never get squeezed side by side.
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassButton(text = confirmText, onClick = onConfirm, style = confirmStyle, loading = busy)
        GlassButton(text = R.string.action_cancel, onClick = onCancel, style = GlassButtonStyle.Secondary, enabled = !busy)
    }
}
