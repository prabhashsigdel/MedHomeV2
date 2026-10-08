package com.medhome.nepal.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medhome.nepal.R
import com.medhome.nepal.ui.components.ErrorMessage
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassAuthScreen
import com.medhome.nepal.ui.components.GlassTextField
import com.medhome.nepal.ui.components.PromptWithLink
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.motion.entrance

@Composable
fun SignUpScreen(
    onBackToLogin: () -> Unit,
    viewModel: SignUpViewModel = viewModel(factory = SignUpViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val enabled = !state.isLoading

    GlassAuthScreen(
        showBack = true,
        footer = {
            PromptWithLink(
                prompt = R.string.signup_have_account,
                link = R.string.signup_log_in,
                onClick = onBackToLogin,
                enabled = enabled,
                modifier = Modifier.entrance(3),
            )
        },
    ) {
        ScreenTitle(
            title = R.string.signup_title,
            subtitle = R.string.signup_subtitle,
            modifier = Modifier.entrance(1),
        )

        GlassCard(modifier = Modifier.entrance(2)) {
            state.error?.let { error ->
                ErrorMessage(
                    error = error,
                    onDismiss = viewModel::dismissError,
                    onRetry = if (state.canRetryWithSignIn) viewModel::retryWithSignIn else null,
                )
            }
            GlassTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = R.string.field_name,
                error = state.nameError,
                enabled = enabled,
            )
            GlassTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = R.string.field_email,
                error = state.emailError,
                enabled = enabled,
                keyboardType = KeyboardType.Email,
            )
            GlassTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = R.string.field_password,
                error = state.passwordError,
                hint = R.string.validation_password_hint,
                enabled = enabled,
                isPassword = true,
            )
            GlassTextField(
                value = state.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label = R.string.field_confirm_password,
                error = state.confirmPasswordError,
                enabled = enabled,
                isPassword = true,
                imeAction = ImeAction.Done,
                onImeDone = viewModel::signUp,
            )
            GlassButton(
                text = R.string.signup_action,
                onClick = viewModel::signUp,
                loading = state.isLoading,
                enabled = enabled,
            )
        }
    }
}
