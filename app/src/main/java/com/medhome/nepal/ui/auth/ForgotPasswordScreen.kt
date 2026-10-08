package com.medhome.nepal.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medhome.nepal.R
import com.medhome.nepal.appContainer
import com.medhome.nepal.ui.components.ErrorMessage
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.AuthFormCard
import com.medhome.nepal.ui.components.GlassLinkButton
import com.medhome.nepal.ui.components.GlassAuthScreen
import com.medhome.nepal.ui.components.GlassTextField
import com.medhome.nepal.ui.components.MessageKind
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.components.StatusMessage
import com.medhome.nepal.ui.motion.entrance

@Composable
fun ForgotPasswordScreen(
    initialEmail: String,
    onBackToLogin: () -> Unit,
    viewModel: ForgotPasswordViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ForgotPasswordViewModel(appContainer.sessionManager, initialEmail) }
        },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val enabled = !state.isLoading

    GlassAuthScreen(
        showBack = true,
        footer = {
            GlassLinkButton(
                text = R.string.action_back_to_login,
                onClick = onBackToLogin,
                modifier = Modifier.entrance(3),
            )
        },
    ) {
        ScreenTitle(
            title = R.string.forgot_title,
            subtitle = R.string.forgot_subtitle,
            modifier = Modifier.entrance(1),
        )

        AuthFormCard(modifier = Modifier.entrance(2)) {
            state.error?.let { ErrorMessage(error = it, onDismiss = viewModel::dismissError) }
            if (state.linkSent) {
                StatusMessage(message = R.string.forgot_link_sent, kind = MessageKind.Info)
            }
            GlassTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = R.string.field_email,
                error = state.emailError,
                enabled = enabled,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
                onImeDone = viewModel::sendResetLink,
            )
            GlassButton(
                text = R.string.forgot_action,
                onClick = viewModel::sendResetLink,
                loading = state.isLoading,
                enabled = enabled,
            )
        }
    }
}
