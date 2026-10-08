package com.medhome.nepal.fakes

import android.content.Context
import com.medhome.nepal.data.AuthDataSource
import com.medhome.nepal.data.GoogleCredentialClient
import com.medhome.nepal.data.ProfileStore
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.AuthException
import com.medhome.nepal.domain.AuthUser
import com.medhome.nepal.domain.Role
import com.medhome.nepal.domain.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

fun passwordUser(uid: String = "uid-1", verified: Boolean = true, name: String? = "Asha") = AuthUser(
    uid = uid,
    email = "$uid@example.com",
    displayName = name,
    isEmailVerified = verified,
    providerIds = setOf(AuthUser.PASSWORD_PROVIDER),
)

fun googleUser(uid: String = "uid-g") = AuthUser(
    uid = uid,
    email = "$uid@gmail.com",
    displayName = "Google Person",
    isEmailVerified = false,
    providerIds = setOf(AuthUser.GOOGLE_PROVIDER),
)

class FakeAuthDataSource(initialUser: AuthUser? = null) : AuthDataSource {
    private val user = MutableStateFlow(initialUser)

    /** The user returned by sign-in and sign-up calls. */
    var nextUser: AuthUser = passwordUser()
    var userAfterReload: AuthUser? = null

    /** Operation name -> error to throw. */
    val failures = mutableMapOf<String, AuthError>()
    val calls = mutableListOf<String>()

    override val currentUser: AuthUser? get() = user.value

    override fun authStateChanges(): Flow<AuthUser?> = user

    override suspend fun signInWithEmail(email: String, password: String) = signIn("signInWithEmail")

    override suspend fun createUserWithEmail(email: String, password: String) = signIn("createUserWithEmail")

    override suspend fun signInWithGoogle(idToken: String) = signIn("signInWithGoogle")

    override suspend fun updateDisplayName(name: String) = record("updateDisplayName")

    override suspend fun sendEmailVerification() = record("sendEmailVerification")

    override suspend fun sendPasswordReset(email: String) = record("sendPasswordReset")

    override suspend fun reloadUser(): AuthUser {
        record("reloadUser")
        val reloaded = userAfterReload ?: checkNotNull(user.value)
        user.value = reloaded
        return reloaded
    }

    override suspend fun reauthenticate(email: String, password: String) = record("reauthenticate")

    override suspend fun reauthenticateWithGoogle(idToken: String) = record("reauthenticateWithGoogle")

    override suspend fun deleteUser() {
        record("deleteUser")
        user.value = null
    }

    override fun signOut() {
        calls += "signOut"
        user.value = null
    }

    /** Simulates Firebase signing the user out on its own (account disabled, token revoked). */
    fun signOutExternally() {
        user.value = null
    }

    private fun signIn(op: String): AuthUser {
        record(op)
        user.value = nextUser
        return nextUser
    }

    private fun record(op: String) {
        calls += op
        failures[op]?.let { throw AuthException(it) }
    }
}

/** In-memory store with the same create-only-if-missing semantics as Firestore. */
class FakeProfileStore : ProfileStore {
    val profiles = mutableMapOf<String, UserProfile>()
    val calls = mutableListOf<String>()
    var getError: AuthError? = null
    var ensureError: AuthError? = null
    var deleteError: AuthError? = null
    var updateNameError: AuthError? = null
    var clearCount = 0
        private set

    override suspend fun getProfile(uid: String): UserProfile? {
        calls += "getProfile"
        getError?.let { throw AuthException(it) }
        return profiles[uid]
    }

    override suspend fun ensureProfile(uid: String, name: String, email: String): UserProfile {
        calls += "ensureProfile"
        ensureError?.let { throw AuthException(it) }
        return profiles.getOrPut(uid) { UserProfile(uid, name, email, Role.PATIENT) }
    }

    override suspend fun deleteProfile(uid: String) {
        calls += "deleteProfile"
        deleteError?.let { throw AuthException(it) }
        profiles.remove(uid)
    }

    override suspend fun updateName(uid: String, name: String) {
        calls += "updateName"
        updateNameError?.let { throw AuthException(it) }
        profiles[uid]?.let { profiles[uid] = it.copy(name = name) }
    }

    override suspend fun clearLocalData() {
        clearCount++
    }
}

class FakeGoogleCredentialClient : GoogleCredentialClient {
    var idToken: String? = "google-id-token"
    var clearCount = 0
        private set

    override suspend fun requestIdToken(activityContext: Context): String? = idToken

    override suspend fun clearCredentialState() {
        clearCount++
    }
}
