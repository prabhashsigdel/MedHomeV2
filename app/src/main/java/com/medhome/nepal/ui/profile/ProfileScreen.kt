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
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.components.SectionTitle
import com.medhome.nepal.ui.components.SettingsDivider
import com.medhome.nepal.ui.components.SettingsRow
import com.medhome.nepal.ui.components.SettingsSection
import com.medhome.nepal.ui.language.LanguageSetting
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.settings.SettingsPage
import com.medhome.nepal.ui.theme.GlassTheme
import com.medhome.nepal.ui.theme.ThemeSetting

/**
 * Profile and settings, as grouped sections: account, appearance, support, sign out and the
 * danger zone. Opened from the Home avatar (a pushed screen with a back arrow); rows push
 * [SettingsPage] screens on top of it.
 */
@Composable
fun ProfileScreen(
    profile: UserProfile,
    usesPassword: Boolean,
    onOpenPage: (SettingsPage) -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val enabled = !state.isBusy

    GlassScreen(showBack = true, drawBackground = false) {
        ScreenTitle(title = R.string.profile_title, modifier = Modifier.entrance(0))
        AccountHeader(profile = profile, modifier = Modifier.entrance(1))

        SettingsSection(title = R.string.settings_account, modifier = Modifier.entrance(2)) {
            SettingsRow(
                title = R.string.settings_edit_profile,
                subtitle = R.string.settings_edit_profile_hint,
                enabled = enabled,
                onClick = { onOpenPage(SettingsPage.EDIT_PROFILE) },
            )
            // Only accounts with a password have one to change (Google-only accounts don't).
            if (usesPassword) {
                SettingsDivider()
                SettingsRow(
                    title = R.string.settings_change_password,
                    subtitle = R.string.settings_change_password_hint,
                    enabled = enabled,
                    onClick = { onOpenPage(SettingsPage.CHANGE_PASSWORD) },
                )
            }
        }

        SettingsSection(title = R.string.settings_appearance, modifier = Modifier.entrance(3)) {
            ThemeSetting(enabled = enabled)
            SettingsDivider()
            LanguageSetting(enabled = enabled)
        }

        SettingsSection(title = R.string.settings_support, modifier = Modifier.entrance(4)) {
            SettingsRow(title = R.string.settings_help_center, onClick = { onOpenPage(SettingsPage.HELP_CENTER) })
            SettingsDivider()
            SettingsRow(title = R.string.settings_privacy_policy, onClick = { onOpenPage(SettingsPage.PRIVACY_POLICY) })
            SettingsDivider()
            SettingsRow(title = R.string.settings_terms, onClick = { onOpenPage(SettingsPage.TERMS_OF_SERVICE) })
            SettingsDivider()
            SettingsRow(title = R.string.settings_about, onClick = { onOpenPage(SettingsPage.ABOUT) })
        }

        GlassButton(
            text = R.string.action_sign_out,
            onClick = viewModel::signOut,
            style = GlassButtonStyle.Secondary,
            loading = state.isSigningOut,
            enabled = enabled,
            modifier = Modifier.entrance(5),
        )
        state.signOutError?.let {
            ErrorMessage(error = it, onDismiss = viewModel::dismissSignOutError, onRetry = viewModel::signOut)
        }

        SectionTitle(text = R.string.profile_danger_zone, modifier = Modifier.entrance(6))
        GlassCard(modifier = Modifier.entrance(6), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.profile_delete_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTheme.colors.textSecondary,
            )
            // Compact on purpose: a destructive action shouldn't be the biggest thing on the screen.
            GlassButton(
                text = R.string.action_delete_account,
                onClick = viewModel::openDeleteDialog,
                style = GlassButtonStyle.Danger,
                enabled = enabled,
                compact = true,
            )
        }
    }

    if (state.showDeleteDialog) DeleteAccountDialog(state = state, usesPassword = usesPassword, viewModel = viewModel)
}

@Composable
private fun AccountHeader(profile: UserProfile, modifier: Modifier = Modifier) {
    val colors = GlassTheme.colors
    GlassCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = profile.name, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        Text(text = profile.email, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
    }
}
