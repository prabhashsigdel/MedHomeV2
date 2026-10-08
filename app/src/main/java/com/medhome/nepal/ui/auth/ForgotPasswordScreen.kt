package com.medhome.nepal.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medhome.nepal.R
import com.medhome.nepal.appContainer
import com.medhome.nepal.ui.common.ErrorCard
import com.medhome.nepal.ui.common.FormScreen
import com.medhome.nepal.ui.common.FormTextField
import com.medhome.nepal.ui.common.LoadingButton
import com.medhome.nepal.ui.common.MessageCard
import com.medhome.nepal.ui.common.ScreenTitle

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

    FormScreen {
        ScreenTitle(title = R.string.forgot_title, subtitle = R.string.forgot_subtitle)

        state.error?.let { ErrorCard(error = it, onDismiss = viewModel::dismissError) }
        if (state.linkSent) {
            MessageCard(message = R.string.forgot_link_sent, isError = false)
        }

        FormTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = R.string.field_email,
            error = state.emailError,
            enabled = enabled,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            onImeDone = viewModel::sendResetLink,
        )
        LoadingButton(
            text = R.string.forgot_action,
            loading = state.isLoading,
            enabled = enabled,
            onClick = viewModel::sendResetLink,
        )
        TextButton(onClick = onBackToLogin, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_back_to_login))
        }
    }
}
