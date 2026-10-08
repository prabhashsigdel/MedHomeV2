package com.medhome.nepal.ui.verify

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medhome.nepal.R
import com.medhome.nepal.ui.components.ErrorMessage
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassButtonStyle
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassLinkButton
import com.medhome.nepal.ui.components.GlassAuthScreen
import com.medhome.nepal.ui.components.MessageKind
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.components.StatusMessage
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.theme.GlassTheme

@Composable
fun VerifyEmailScreen(
    email: String,
    verificationEmailFailed: Boolean,
    viewModel: VerifyEmailViewModel = viewModel(factory = VerifyEmailViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val enabled = !state.isBusy

    GlassAuthScreen(
        footer = {
            GlassLinkButton(
                text = R.string.verify_use_other_account,
                onClick = viewModel::signOut,
                enabled = enabled,
                modifier = Modifier.entrance(3),
            )
        },
    ) {
        ScreenTitle(title = R.string.verify_title, modifier = Modifier.entrance(1))

        GlassCard(modifier = Modifier.entrance(2)) {
            Text(
                text = stringResource(R.string.verify_body, email),
                style = MaterialTheme.typography.bodyLarge,
                color = GlassTheme.colors.textPrimary,
            )
            if (verificationEmailFailed) {
                StatusMessage(message = R.string.verify_initial_send_failed, kind = MessageKind.Warning)
            }
            state.error?.let { ErrorMessage(error = it, onDismiss = viewModel::dismissError) }
            state.message?.let { StatusMessage(message = it, kind = MessageKind.Info) }

            GlassButton(
                text = R.string.verify_check_action,
                onClick = viewModel::checkVerified,
                loading = state.runningAction == VerifyAction.CHECK,
                enabled = enabled,
            )
            GlassButton(
                text = R.string.verify_resend_action,
                onClick = viewModel::resend,
                style = GlassButtonStyle.Secondary,
                loading = state.runningAction == VerifyAction.RESEND,
                enabled = enabled,
            )
        }
    }
}
