package com.medhome.nepal.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medhome.nepal.R
import com.medhome.nepal.domain.UserProfile
import com.medhome.nepal.ui.components.ErrorMessage
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassButtonStyle
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.components.SettingsDivider
import com.medhome.nepal.ui.components.SettingsSection
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.InitialsAvatar
import com.medhome.nepal.ui.language.LanguageSetting
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.profile.ProfileViewModel
import com.medhome.nepal.ui.theme.GlassTheme
import com.medhome.nepal.ui.theme.ThemeSetting

/**
 * The admin's Profile tab: who is signed in, then language, theme and sign out, as for other
 * roles. Staff accounts have no self-delete (see CLAUDE.md, Decisions).
 */
@Composable
fun AdminProfileScreen(profile: UserProfile, viewModel: ProfileViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = GlassTheme.colors

    GlassScreen(drawBackground = false) {
        ScreenTitle(title = R.string.profile_title, modifier = Modifier.entrance(0))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.entrance(0).semantics(mergeDescendants = true) {}) {
            InitialsAvatar(name = profile.name, size = 56.dp, textStyle = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = profile.name, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                Text(text = profile.email, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
        }
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
