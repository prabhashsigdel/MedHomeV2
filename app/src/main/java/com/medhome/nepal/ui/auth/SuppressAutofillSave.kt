package com.medhome.nepal.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalAutofillManager

/**
 * Autofill fills the email and password fields, but saving goes through Credential Manager
 * (PasswordSavePrompter) after a successful sign-in. Compose commits the autofill session (and the
 * autofill service may offer to save) when the fields leave the screen; cancelling it here avoids
 * showing the user two "save password?" prompts.
 */
@Composable
fun SuppressAutofillSave() {
    val autofillManager = LocalAutofillManager.current
    DisposableEffect(autofillManager) {
        onDispose { autofillManager?.cancel() }
    }
}
