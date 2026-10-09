package com.medhome.nepal.session

import android.util.Log
import com.medhome.nepal.data.AuthDataSource
import com.medhome.nepal.data.AuthErrorMapper
import com.medhome.nepal.data.CredentialClient
import com.medhome.nepal.data.ListenerRegistry
import com.medhome.nepal.data.ProfileStore
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.AuthException
import com.medhome.nepal.domain.AuthUser
import com.medhome.nepal.domain.ProfileDetails
import com.medhome.nepal.domain.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single owner of session state. Every sign-in path finishes its Firestore work before
 * publishing a state, so screens never observe a half-signed-in user.
 *
 * Operations run in [scope] (application lifetime) so they complete even if the screen that
 * started them leaves composition. All public operations throw [AuthException] on failure.
 */
class SessionManager(
    private val auth: AuthDataSource,
    private val profiles: ProfileStore,
    private val credentials: CredentialClient,
    private val scope: CoroutineScope,
    /** Open Firestore listeners, stopped on sign-out before Firestore is shut down. */
    private val listeners: ListenerRegistry = ListenerRegistry(),
    /**
     * Cancels the user's upcoming bookings so their slots free up (nobody could cancel them
     * once the account is gone). Throws [AuthException] if it couldn't; deletion then stops.
     */
    private val cancelUpcomingBookings: suspend () -> Unit = {},
    /**
     * Cancels every reminder alarm and notification and wipes the reminder database (medicine
     * names are health data). Runs after SignedOut is published, with the other local data.
     */
    private val clearReminders: suspend () -> Unit = {},
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val mutex = Mutex()

    fun start() {
        scope.launch {
            auth.authStateChanges().collect { user ->
                if (user == null && auth.currentUser == null) onExternalSignOut()
            }
        }
        scope.launch {
            runCatchingAuth { restoreSession() }.onFailure { Log.e(TAG, "restoreSession failed", it) }
        }
    }

    /** Cold start and "Retry" on the profile-unavailable screen. May use Firestore's cache. */
    suspend fun restoreSession() = exclusive {
        val user = auth.currentUser
        if (user == null) {
            _state.value = SessionState.SignedOut()
            return@exclusive
        }
        _state.value = SessionState.Loading
        val profile = try {
            profiles.getProfile(user.uid) ?: ensureProfileFor(user)
        } catch (e: AuthException) {
            if (e.error == AuthError.NETWORK) {
                _state.value = SessionState.ProfileUnavailable
            } else {
                signOutInternal(e.error)
            }
            return@exclusive
        }
        publish(user, profile)
    }

    suspend fun signInWithEmail(email: String, password: String) = exclusive {
        completeSignIn(auth.signInWithEmail(email, password))
    }

    suspend fun signInWithGoogle(idToken: String) = exclusive {
        completeSignIn(auth.signInWithGoogle(idToken))
    }

    suspend fun signUp(name: String, email: String, password: String) = exclusive {
        val user = auth.createUserWithEmail(email, password)
        // Stored on the Auth account so a later sign-in can recreate the profile with this name.
        logFailure("updateDisplayName") { auth.updateDisplayName(name) }
        val profile = loadProfileOrSignOut {
            profiles.ensureProfile(user.uid, name, profileEmail(user.email ?: email))
        }
        val sent = logFailure("sendEmailVerification") { auth.sendEmailVerification() }
        _state.value = SessionState.NeedsVerification(profile, verificationEmailFailed = !sent)
    }

    suspend fun resendVerificationEmail() = exclusive {
        auth.sendEmailVerification()
        _state.update { current ->
            if (current is SessionState.NeedsVerification) {
                current.copy(verificationEmailFailed = false)
            } else {
                current
            }
        }
    }

    /** Returns true when the user is now verified and has moved on to [SessionState.SignedIn]. */
    suspend fun checkEmailVerified(): Boolean = exclusive {
        val current = _state.value as? SessionState.NeedsVerification ?: return@exclusive false
        val user = auth.reloadUser()
        if (user.isVerified) {
            // The current token still says unverified (for up to an hour), and the rules read
            // the token: get a new one so booking works straight away.
            logFailure("refreshIdToken") { auth.emailVerifiedClaim(forceRefresh = true) }
            publish(user, current.profile)
        }
        user.isVerified
    }

    /**
     * Shows the verify-email screen to a signed-in user whose sign-in token doesn't say the
     * email is verified (booking needs it). Verifying there returns them to the app.
     */
    suspend fun showEmailVerification() = exclusive {
        val current = _state.value as? SessionState.SignedIn ?: return@exclusive
        _state.value = SessionState.NeedsVerification(current.profile)
    }

    suspend fun sendPasswordReset(email: String) = exclusive { auth.sendPasswordReset(email) }

    suspend fun signOut() = exclusive { signOutInternal(error = null) }

    /**
     * Re-authenticates first so the profile is never deleted while the Auth account survives,
     * then cancels upcoming bookings (freeing their slots) before deleting anything. Retrying
     * after a partial failure is safe: cancelled bookings are skipped and deleting a missing
     * document succeeds.
     */
    suspend fun deleteAccount(reauth: Reauth) = exclusive {
        val user = auth.currentUser ?: throw AuthException(AuthError.NOT_SIGNED_IN)
        when (reauth) {
            is Reauth.Password -> {
                val email = user.email ?: throw AuthException(AuthError.UNKNOWN)
                auth.reauthenticate(email, reauth.password)
            }
            is Reauth.Google -> auth.reauthenticateWithGoogle(reauth.idToken)
        }
        cancelUpcomingBookings()
        profiles.deleteProfile(user.uid)
        auth.deleteUser()
        endSession(error = null)
        clearCredentials()
        clearLocalData()
    }

    /** Saves the editable profile fields and updates the signed-in profile in place. Validated already. */
    suspend fun updateProfile(details: ProfileDetails) = exclusive {
        val current = _state.value as? SessionState.SignedIn ?: throw AuthException(AuthError.NOT_SIGNED_IN)
        profiles.updateDetails(current.profile.uid, details)
        // Re-read the state: an external sign-out may have happened while saving.
        _state.update { latest ->
            if (latest is SessionState.SignedIn && latest.profile.uid == current.profile.uid) {
                latest.copy(
                    profile = latest.profile.copy(
                        name = details.name,
                        phone = details.phone,
                        dateOfBirth = details.dateOfBirth,
                        gender = details.gender,
                    ),
                )
            } else {
                latest
            }
        }
    }

    /**
     * Re-authenticates with [currentPassword] (Firebase requires a recent sign-in, and it proves
     * it's really the user), then sets [newPassword]. Wrong current password -> INVALID_CREDENTIALS.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String) = exclusive {
        val user = auth.currentUser ?: throw AuthException(AuthError.NOT_SIGNED_IN)
        val email = user.email ?: throw AuthException(AuthError.UNKNOWN)
        auth.reauthenticate(email, currentPassword)
        auth.updatePassword(newPassword)
    }

    fun clearSignedOutError() {
        _state.update { current ->
            if (current is SessionState.SignedOut && current.error != null) {
                SessionState.SignedOut()
            } else {
                current
            }
        }
    }

    private suspend fun completeSignIn(user: AuthUser) {
        val profile = loadProfileOrSignOut { ensureProfileFor(user) }
        publish(user, profile)
    }

    private suspend fun ensureProfileFor(user: AuthUser): UserProfile =
        profiles.ensureProfile(user.uid, displayNameFor(user), profileEmail(user.email.orEmpty()))

    /** Lowercased on both sides (here and in firestore.rules) so letter case can never lock a user out. */
    private fun profileEmail(email: String): String = email.lowercase()

    /** Signing in without a usable profile is not allowed: sign out and report why. */
    private suspend fun loadProfileOrSignOut(load: suspend () -> UserProfile): UserProfile = try {
        load()
    } catch (e: AuthException) {
        signOutInternal(error = null)
        val error = if (e.error == AuthError.PROFILE_INVALID) e.error else AuthError.PROFILE_LOAD_FAILED
        throw AuthException(error, e)
    }

    private fun publish(user: AuthUser, profile: UserProfile) {
        // Firebase may have signed the user out while we were waiting on Firestore.
        if (auth.currentUser?.uid != user.uid) {
            _state.value = SessionState.SignedOut()
            return
        }
        _state.value = if (user.isVerified) {
            SessionState.SignedIn(profile, user.usesPassword)
        } else {
            SessionState.NeedsVerification(profile)
        }
    }

    /**
     * Publishes SignedOut first, so the signed-in screens close, and stops any Firestore
     * listener still open; only then is the cache wiped (which shuts Firestore down).
     */
    private suspend fun signOutInternal(error: AuthError?) {
        endSession(error)
        auth.signOut()
        clearCredentials()
        clearLocalData()
    }

    private fun endSession(error: AuthError?) {
        _state.value = SessionState.SignedOut(error)
        listeners.stopAll()
    }

    /** Failing to wipe the cache must not block sign-out; it is logged instead. */
    private suspend fun clearLocalData() {
        logFailure("clearReminders") { clearReminders() }
        logFailure("clearLocalData") { profiles.clearLocalData() }
    }

    private suspend fun clearCredentials() {
        logFailure("clearCredentialState") { credentials.clearCredentialState() }
    }

    private fun onExternalSignOut() {
        val before = _state.value
        val endsSession = when (before) {
            is SessionState.SignedIn,
            is SessionState.NeedsVerification,
            SessionState.ProfileUnavailable -> true
            is SessionState.SignedOut,
            SessionState.Loading -> false
        }
        // Our own sign-out paths have normally set SignedOut (and cleared the cache) by the time
        // Firebase's listener fires, so this only acts on sign-outs Firebase did on its own
        // (account disabled, token revoked). If the listener does fire mid-operation, the cache
        // is cleared twice, which is harmless. The clear queues behind any running operation.
        if (!endsSession) return
        if (_state.compareAndSet(before, SessionState.SignedOut())) {
            listeners.stopAll()
            scope.launch { runCatchingAuth { exclusive { clearLocalData() } } }
        }
    }

    /** Runs [block] in the application scope, one operation at a time. */
    private suspend fun <T> exclusive(block: suspend () -> T): T {
        val result = scope.async {
            runCatchingAuth { mutex.withLock { block() } }
        }.await()
        return result.getOrThrow()
    }

    private fun displayNameFor(user: AuthUser): String =
        (
            user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore('@')
                ?: ""
            ).take(MAX_NAME_LENGTH)

    /** Runs a step whose failure must not abort the flow. Returns false if it failed. */
    private suspend fun logFailure(step: String, block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$step failed", e)
        false
    }

    private suspend fun <T> runCatchingAuth(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(AuthErrorMapper.toException(e))
    }

    private companion object {
        const val TAG = "SessionManager"

        /** Must match hasValidName() in firestore.rules. */
        const val MAX_NAME_LENGTH = 100
    }
}
