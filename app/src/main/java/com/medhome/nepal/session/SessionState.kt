package com.medhome.nepal.session

import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.UserProfile

sealed interface SessionState {
    data object Loading : SessionState

    /** [error] explains why the user was signed out automatically, if they were. */
    data class SignedOut(val error: AuthError? = null) : SessionState

    data class NeedsVerification(
        val profile: UserProfile,
        val verificationEmailFailed: Boolean = false,
    ) : SessionState

    data class SignedIn(val profile: UserProfile, val usesPassword: Boolean) : SessionState

    /** Signed in, but the profile could not be fetched (offline, nothing cached). Blocks the app. */
    data object ProfileUnavailable : SessionState
}

sealed interface Reauth {
    data class Password(val password: String) : Reauth
    data class Google(val idToken: String) : Reauth
}
