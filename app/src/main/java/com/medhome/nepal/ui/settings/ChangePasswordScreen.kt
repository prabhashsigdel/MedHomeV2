package com.medhome.nepal.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medhome.nepal.R
import com.medhome.nepal.appContainer
import com.medhome.nepal.ui.auth.SuppressAutofillSave
import com.medhome.nepal.ui.components.ErrorMessage
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.MessageKind
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.components.StatusMessage
import com.medhome.nepal.ui.components.GlassTextField
import com.medhome.nepal.ui.motion.entrance

@Composable
fun ChangePasswordScreen(
    email: String,
    onDone: () -> Unit,
    viewModel: ChangePasswordViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val container = appContainer
                ChangePasswordViewModel(container.sessionManager, container.passwordSaveOffers, email)
            }
        },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val enabled = !state.isSaving
    // Saving the new password goes through Credential Manager after success, not autofill.
    SuppressAutofillSave()

    GlassScreen(showBack = true, drawBackground = false) {
        ScreenTitle(
            title = R.string.change_password_title,
            subtitle = R.string.change_password_subtitle,
            modifier = Modifier.entrance(0),
        )
        GlassCard(modifier = Modifier.entrance(1)) {
            if (state.done) {
                StatusMessage(message = R.string.change_password_done, kind = MessageKind.Info)
                GlassButton(text = R.string.action_done, onClick = onDone)
                return@GlassCard
            }
            state.error?.let { ErrorMessage(error = it, onDismiss = viewModel::dismissError) }
            GlassTextField(
                value = state.current,
                onValueChange = viewModel::onCurrentChange,
                label = R.string.field_current_password,
                error = state.currentError,
                enabled = enabled,
                isPassword = true,
                imeAction = ImeAction.Next,
                contentType = ContentType.Password,
            )
            GlassTextField(
                value = state.new,
                onValueChange = viewModel::onNewChange,
                label = R.string.field_new_password,
                error = state.newError,
                hint = R.string.validation_password_hint,
                enabled = enabled,
                isPassword = true,
                imeAction = ImeAction.Next,
                contentType = ContentType.NewPassword,
            )
            GlassTextField(
                value = state.confirm,
                onValueChange = viewModel::onConfirmChange,
                label = R.string.field_confirm_new_password,
                error = state.confirmError,
                enabled = enabled,
                isPassword = true,
                imeAction = ImeAction.Done,
                onImeDone = viewModel::save,
                contentType = ContentType.NewPassword,
            )
            GlassButton(
                text = R.string.settings_change_password,
                onClick = viewModel::save,
                loading = state.isSaving,
                enabled = enabled,
            )
        }
    }
}
