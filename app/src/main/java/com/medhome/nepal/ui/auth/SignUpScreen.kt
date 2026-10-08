package com.medhome.nepal.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medhome.nepal.R
import com.medhome.nepal.ui.common.ErrorCard
import com.medhome.nepal.ui.common.FormScreen
import com.medhome.nepal.ui.common.FormTextField
import com.medhome.nepal.ui.common.LoadingButton
import com.medhome.nepal.ui.common.PasswordTextField
import com.medhome.nepal.ui.common.ScreenTitle

@Composable
fun SignUpScreen(
    onBackToLogin: () -> Unit,
    viewModel: SignUpViewModel = viewModel(factory = SignUpViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val enabled = !state.isLoading

    FormScreen {
        ScreenTitle(title = R.string.signup_title, subtitle = R.string.signup_subtitle)

        state.error?.let { error ->
            ErrorCard(
                error = error,
                onDismiss = viewModel::dismissError,
                onRetry = if (state.canRetryWithSignIn) viewModel::retryWithSignIn else null,
            )
        }

        FormTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = R.string.field_name,
            error = state.nameError,
            enabled = enabled,
        )
        FormTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = R.string.field_email,
            error = state.emailError,
            enabled = enabled,
            keyboardType = KeyboardType.Email,
        )
        PasswordTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = R.string.field_password,
            error = state.passwordError,
            hint = R.string.validation_password_hint,
            enabled = enabled,
            imeAction = ImeAction.Next,
        )
        PasswordTextField(
            value = state.confirmPassword,
            onValueChange = viewModel::onConfirmPasswordChange,
            label = R.string.field_confirm_password,
            error = state.confirmPasswordError,
            enabled = enabled,
            onImeDone = viewModel::signUp,
        )
        LoadingButton(
            text = R.string.signup_action,
            loading = state.isLoading,
            enabled = enabled,
            onClick = viewModel::signUp,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.signup_have_account),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onBackToLogin, enabled = enabled) {
                Text(stringResource(R.string.signup_log_in))
            }
        }
    }
}
