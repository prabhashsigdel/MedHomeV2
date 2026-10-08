package com.medhome.nepal.ui.auth

/**
 * Whether the login screen should open the saved-accounts sheet by itself. Once the user
 * dismisses it, it stays closed until the app restarts; they can still type or use Google.
 */
class SavedAccountsPrompt {
    var dismissedThisSession: Boolean = false
        private set

    fun onDismissed() {
        dismissedThisSession = true
    }
}
