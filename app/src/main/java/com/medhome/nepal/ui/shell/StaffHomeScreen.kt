package com.medhome.nepal.ui.shell

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
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.components.SettingsSection
import com.medhome.nepal.ui.language.LanguageSetting
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.profile.ProfileViewModel
import com.medhome.nepal.ui.theme.GlassTheme

/** Placeholder for doctors and admins until their tools exist: language and sign out only. */
@Composable
fun StaffHomeScreen(
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    GlassScreen {
        ScreenTitle(title = R.string.staff_home_title, modifier = Modifier.entrance(0))
        GlassCard(modifier = Modifier.entrance(1)) {
            Text(
                text = stringResource(R.string.staff_home_body),
                style = MaterialTheme.typography.bodyLarge,
                color = GlassTheme.colors.textPrimary,
            )
        }
        SettingsSection(title = R.string.settings_appearance, modifier = Modifier.entrance(2)) {
            LanguageSetting(enabled = !state.isBusy)
        }
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
    }
}
