package com.medhome.nepal.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medhome.nepal.R
import com.medhome.nepal.ui.components.ErrorMessage
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassButtonStyle
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.components.SettingsDivider
import com.medhome.nepal.ui.components.SettingsSection
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.language.LanguageSetting
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.profile.ProfileViewModel
import com.medhome.nepal.ui.theme.ThemeSetting

/**
 * The admin's settings, pushed from the doctors list: language, theme and sign out, as for
 * other roles. Staff accounts have no self-delete (see CLAUDE.md, Decisions).
 */
@Composable
fun AdminSettingsScreen(viewModel: ProfileViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    GlassScreen(showBack = true, drawBackground = false) {
        ScreenTitle(title = R.string.admin_settings_title, modifier = Modifier.entrance(0))
        SettingsSection(title = R.string.settings_appearance, modifier = Modifier.entrance(1)) {
            LanguageSetting(enabled = !state.isBusy)
            SettingsDivider()
            ThemeSetting(enabled = !state.isBusy)
        }
        GlassButton(
            text = R.string.action_sign_out,
            onClick = viewModel::signOut,
            style = GlassButtonStyle.Secondary,
            loading = state.isSigningOut,
            enabled = !state.isBusy,
            modifier = Modifier.entrance(2),
        )
        state.signOutError?.let {
            ErrorMessage(error = it, onDismiss = viewModel::dismissSignOutError, onRetry = viewModel::signOut)
        }
    }
}
