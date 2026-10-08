package com.medhome.nepal.domain

/** Snapshot of the Firebase Auth user, decoupled from the SDK so session logic is testable. */
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val isEmailVerified: Boolean,
    val providerIds: Set<String>,
) {
    val usesPassword: Boolean get() = PASSWORD_PROVIDER in providerIds
    val usesGoogle: Boolean get() = GOOGLE_PROVIDER in providerIds

    /** Google accounts are verified by Google, so they never need the email link. */
    val isVerified: Boolean get() = isEmailVerified || usesGoogle

    companion object {
        const val PASSWORD_PROVIDER = "password"
        const val GOOGLE_PROVIDER = "google.com"
    }
}
