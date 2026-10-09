package com.medhome.nepal.ui.settings

import androidx.compose.runtime.Composable
import com.medhome.nepal.domain.UserProfile
import com.medhome.nepal.ui.reminders.BatteryGuideScreen

/** Shows the screen for one [SettingsPage]. [onDone] returns to Profile. */
@Composable
fun SettingsPageScreen(
    page: SettingsPage,
    profile: UserProfile,
    onDone: () -> Unit,
) {
    when (page) {
        SettingsPage.EDIT_PROFILE -> EditProfileScreen(profile = profile, onDone = onDone)
        SettingsPage.CHANGE_PASSWORD -> ChangePasswordScreen(email = profile.email, onDone = onDone)
        SettingsPage.HELP_CENTER -> HelpCenterScreen()
        SettingsPage.PRIVACY_POLICY -> PrivacyPolicyScreen()
        SettingsPage.TERMS_OF_SERVICE -> TermsOfServiceScreen()
        SettingsPage.ABOUT -> AboutScreen()
        SettingsPage.BATTERY_GUIDE -> BatteryGuideScreen()
    }
}
