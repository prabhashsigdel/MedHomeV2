package com.medhome.nepal.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medhome.nepal.R
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.ui.common.ErrorCard
import com.medhome.nepal.ui.common.FormScreen
import com.medhome.nepal.ui.common.FormTextField
import com.medhome.nepal.ui.common.LoadingButton
import com.medhome.nepal.ui.common.PasswordTextField
import com.medhome.nepal.ui.common.ScreenTitle
import com.medhome.nepal.ui.common.rememberGoogleIdTokenRequest

@Composable
fun LoginScreen(
    sessionError: AuthError?,
    onDismissSessionError: () -> Unit,
    onSignUp: () -> Unit,
    onForgotPassword: (email: String) -> Unit,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val signInWithGoogle = rememberGoogleIdTokenRequest(
        onStart = viewModel::beginGoogleSignIn,
        onResult = viewModel::onGoogleResult,
    )
    val enabled = !state.isLoading

    FormScreen {
        ScreenTitle(title = R.string.login_title, subtitle = R.string.login_subtitle)

        if (sessionError != null) {
            ErrorCard(error = sessionError, onDismiss = onDismissSessionError)
        }
        state.error?.let { error ->
            ErrorCard(
                error = error,
                onDismiss = viewModel::dismissError,
                onRetry = when (state.retryAttempt) {
                    LoginAttempt.EMAIL -> viewModel::signInWithEmail
                    LoginAttempt.GOOGLE -> signInWithGoogle
                    null -> null
                },
            )
        }

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
            enabled = enabled,
            onImeDone = viewModel::signInWithEmail,
        )
        TextButton(
            onClick = { onForgotPassword(state.email.trim()) },
            enabled = enabled,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.login_forgot_password))
        }
        LoadingButton(
            text = R.string.login_action,
            loading = state.isLoading,
            enabled = enabled,
            onClick = viewModel::signInWithEmail,
        )

        HorizontalDivider()

        OutlinedButton(
            onClick = signInWithGoogle,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.login_google))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.login_no_account),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onSignUp, enabled = enabled) {
                Text(stringResource(R.string.login_sign_up))
            }
        }
    }
}
