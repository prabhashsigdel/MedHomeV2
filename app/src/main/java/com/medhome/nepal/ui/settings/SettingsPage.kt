package com.medhome.nepal.ui.settings

import androidx.annotation.Keep

/** Screens opened from Profile's settings. Part of a navigation route, hence @Keep. */
@Keep
enum class SettingsPage {
    EDIT_PROFILE,
    CHANGE_PASSWORD,
    HELP_CENTER,
    PRIVACY_POLICY,
    TERMS_OF_SERVICE,
    ABOUT,
}
