package com.medhome.nepal.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medhome.nepal.R
import com.medhome.nepal.domain.UserProfile
import com.medhome.nepal.ui.components.ErrorMessage
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassButtonStyle
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassLinkButton
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.MessageKind
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.components.SectionTitle
import com.medhome.nepal.ui.components.StatusMessage
import com.medhome.nepal.ui.language.LanguageSwitcher
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.theme.GlassTheme

@Composable
fun ProfileScreen(
    profile: UserProfile,
    usesPassword: Boolean,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    GlassScreen(drawBackground = false) {
        ScreenTitle(title = R.string.profile_title, modifier = Modifier.entrance(0))

        AccountCard(profile = profile, state = state, viewModel = viewModel, modifier = Modifier.entrance(1))

        SectionTitle(text = R.string.profile_language, modifier = Modifier.entrance(2))
        LanguageSwitcher(modifier = Modifier.entrance(2))

        GlassButton(
            text = R.string.action_sign_out,
            onClick = viewModel::signOut,
            style = GlassButtonStyle.Secondary,
            loading = state.isSigningOut,
            enabled = !state.isBusy,
            modifier = Modifier.entrance(3),
        )
        state.signOutError?.let {
            ErrorMessage(error = it, onDismiss = viewModel::dismissSignOutError, onRetry = viewModel::signOut)
        }

        SectionTitle(text = R.string.profile_danger_zone, modifier = Modifier.entrance(4))
        GlassCard(modifier = Modifier.entrance(4), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.profile_delete_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTheme.colors.textSecondary,
            )
            GlassButton(
                text = R.string.action_delete_account,
                onClick = viewModel::openDeleteDialog,
                style = GlassButtonStyle.Danger,
                enabled = !state.isBusy,
            )
        }
    }

    if (state.showEditName) EditNameDialog(state = state, viewModel = viewModel)
    if (state.showDeleteDialog) DeleteAccountDialog(state = state, usesPassword = usesPassword, viewModel = viewModel)
}

@Composable
private fun AccountCard(
    profile: UserProfile,
    state: ProfileUiState,
    viewModel: ProfileViewModel,
    modifier: Modifier = Modifier,
) {
    val colors = GlassTheme.colors
    GlassCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = profile.name, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        Text(text = profile.email, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        if (state.nameSaved) {
            StatusMessage(
                message = R.string.profile_name_updated,
                kind = MessageKind.Info,
                onDismiss = viewModel::dismissNameSaved,
            )
        }
        GlassLinkButton(
            text = R.string.profile_edit_name,
            onClick = { viewModel.openEditName(profile.name) },
            enabled = !state.isBusy,
        )
    }
}
