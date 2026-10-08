package com.medhome.nepal.data

import android.content.Context

/** A credential the user picked from the system sheet. Never stored by the app. */
sealed interface SavedCredential {
    class Password(val id: String, val password: String) : SavedCredential {
        override fun toString(): String = "Password(id=$id, password=██)"
    }

    class Google(val idToken: String) : SavedCredential {
        override fun toString(): String = "Google(idToken=██)"
    }
}

enum class SaveOutcome {
    SAVED,

    /** The user dismissed the save prompt. */
    DECLINED,

    /** No password manager could take it (none installed, or an error). Not the user's choice. */
    UNAVAILABLE,
}

/**
 * Credential Manager: Google sign-in, saved passwords and saving new ones. Every UI call takes
 * the current Activity's context, because the system sheets attach to it.
 */
interface CredentialClient {
    /** Shows the "Sign in with Google" account picker. Returns null if the user cancelled. */
    suspend fun requestGoogleIdToken(activityContext: Context): String?

    /**
     * Offers saved passwords and previously used Google accounts in one sheet. Returns null when
     * there is nothing saved or the user dismissed it; never throws for those cases.
     */
    suspend fun requestSavedCredential(activityContext: Context): SavedCredential?

    /** Offers to save an email/password pair in the user's password manager. */
    suspend fun savePassword(activityContext: Context, id: String, password: String): SaveOutcome

    /** Forgets the selected account so the picker shows again next time. */
    suspend fun clearCredentialState()
}
