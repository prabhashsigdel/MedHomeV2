package com.medhome.nepal.ui.verify

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medhome.nepal.R
import com.medhome.nepal.ui.common.ErrorCard
import com.medhome.nepal.ui.common.FormScreen
import com.medhome.nepal.ui.common.LoadingButton
import com.medhome.nepal.ui.common.MessageCard
import com.medhome.nepal.ui.common.ScreenTitle

@Composable
fun VerifyEmailScreen(
    email: String,
    verificationEmailFailed: Boolean,
    viewModel: VerifyEmailViewModel = viewModel(factory = VerifyEmailViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val enabled = !state.isBusy

    FormScreen {
        ScreenTitle(title = R.string.verify_title)
        Text(
            text = stringResource(R.string.verify_body, email),
            style = MaterialTheme.typography.bodyLarge,
        )

        if (verificationEmailFailed) {
            MessageCard(message = R.string.verify_initial_send_failed, isError = true)
        }
        state.error?.let { ErrorCard(error = it, onDismiss = viewModel::dismissError) }
        state.message?.let { MessageCard(message = it, isError = false) }

        LoadingButton(
            text = R.string.verify_check_action,
            loading = state.runningAction == VerifyAction.CHECK,
            enabled = enabled,
            onClick = viewModel::checkVerified,
        )
        OutlinedButton(
            onClick = viewModel::resend,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.verify_resend_action))
        }
        TextButton(
            onClick = viewModel::signOut,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.verify_use_other_account))
        }
    }
}
