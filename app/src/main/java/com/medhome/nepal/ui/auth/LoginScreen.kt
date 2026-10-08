package com.medhome.nepal.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medhome.nepal.R
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.ui.common.rememberGoogleIdTokenRequest
import com.medhome.nepal.ui.common.rememberSavedCredentialRequest
import com.medhome.nepal.ui.components.ErrorMessage
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassButtonStyle
import com.medhome.nepal.ui.components.AuthFormCard
import com.medhome.nepal.ui.components.GlassLinkButton
import com.medhome.nepal.ui.components.GlassAuthScreen
import com.medhome.nepal.ui.components.GlassTextField
import com.medhome.nepal.ui.components.PromptWithLink
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.motion.entrance

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
    val offerSavedAccounts = rememberSavedCredentialRequest(
        onStart = viewModel::beginSavedAccounts,
        onResult = viewModel::onSavedCredential,
    )
    val enabled = !state.isLoading

    // Open the saved-accounts sheet by itself once per visit (not after the user dismissed it).
    LaunchedEffect(Unit) {
        if (viewModel.shouldOfferSavedAccounts()) offerSavedAccounts()
    }
    SuppressAutofillSave()

    GlassAuthScreen(
        footer = {
            PromptWithLink(
                prompt = R.string.login_no_account,
                link = R.string.login_sign_up,
                onClick = onSignUp,
                enabled = enabled,
                modifier = Modifier.entrance(4),
            )
        },
    ) {
        ScreenTitle(
            title = R.string.login_title,
            subtitle = R.string.login_subtitle,
            modifier = Modifier.entrance(1),
        )

        AuthFormCard(modifier = Modifier.entrance(2)) {
            if (sessionError != null) {
                ErrorMessage(error = sessionError, onDismiss = onDismissSessionError)
            }
            state.error?.let { error ->
                ErrorMessage(
                    error = error,
                    onDismiss = viewModel::dismissError,
                    onRetry = when (state.retryAttempt) {
                        LoginAttempt.EMAIL -> viewModel::signInWithEmail
                        LoginAttempt.GOOGLE -> signInWithGoogle
                        null -> null
                    },
                )
            }
            GlassTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = R.string.field_email,
                error = state.emailError,
                enabled = enabled,
                keyboardType = KeyboardType.Email,
                contentType = ContentType.Username + ContentType.EmailAddress,
            )
            GlassTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = R.string.field_password,
                error = state.passwordError,
                enabled = enabled,
                isPassword = true,
                imeAction = ImeAction.Done,
                onImeDone = viewModel::signInWithEmail,
                contentType = ContentType.Password,
            )
            GlassLinkButton(
                text = R.string.login_forgot_password,
                onClick = { onForgotPassword(state.email.trim()) },
                enabled = enabled,
                modifier = Modifier.align(Alignment.End),
            )
            GlassButton(
                text = R.string.login_action,
                onClick = viewModel::signInWithEmail,
                loading = state.isLoading,
                enabled = enabled,
            )
        }

        GlassButton(
            text = R.string.login_google,
            onClick = signInWithGoogle,
            style = GlassButtonStyle.Secondary,
            enabled = enabled,
            modifier = Modifier.entrance(3),
        )
    }
}
