package com.medhome.nepal.data

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.AuthException
import com.medhome.nepal.domain.AuthUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthDataSource(
    private val auth: FirebaseAuth,
) : AuthDataSource {

    override val currentUser: AuthUser? get() = auth.currentUser?.toAuthUser()

    override fun authStateChanges(): Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAuthUser()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthUser = mapErrors {
        auth.signInWithEmailAndPassword(email, password).await().user.requireUser()
    }

    override suspend fun createUserWithEmail(email: String, password: String): AuthUser = mapErrors {
        auth.createUserWithEmailAndPassword(email, password).await().user.requireUser()
    }

    override suspend fun updateDisplayName(name: String) = mapErrors {
        requireFirebaseUser().updateProfile(userProfileChangeRequest { displayName = name }).await()
        Unit
    }

    override suspend fun signInWithGoogle(idToken: String): AuthUser = mapGoogleErrors {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await().user.requireUser()
    }

    override suspend fun sendEmailVerification() = mapErrors {
        requireFirebaseUser().sendEmailVerification().await()
        Unit
    }

    override suspend fun sendPasswordReset(email: String) = mapErrors {
        auth.sendPasswordResetEmail(email).await()
        Unit
    }

    override suspend fun reloadUser(): AuthUser = mapErrors {
        val user = requireFirebaseUser()
        user.reload().await()
        (auth.currentUser ?: user).toAuthUser()
    }

    override suspend fun reauthenticate(email: String, password: String) = mapErrors {
        requireFirebaseUser().reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
        Unit
    }

    override suspend fun reauthenticateWithGoogle(idToken: String) = mapGoogleErrors {
        requireFirebaseUser().reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
        Unit
    }

    override suspend fun updatePassword(newPassword: String) = mapErrors {
        requireFirebaseUser().updatePassword(newPassword).await()
        Unit
    }

    override suspend fun deleteUser() = mapErrors {
        requireFirebaseUser().delete().await()
        Unit
    }

    override fun signOut() = auth.signOut()

    /** An invalid credential here means a bad Google token, not a wrong password. */
    private inline fun <T> mapGoogleErrors(block: () -> T): T = try {
        mapErrors(block)
    } catch (e: AuthException) {
        if (e.error == AuthError.INVALID_CREDENTIALS) throw AuthException(AuthError.GOOGLE_FAILED, e)
        throw e
    }

    private fun requireFirebaseUser(): FirebaseUser =
        auth.currentUser ?: throw AuthException(AuthError.NOT_SIGNED_IN)

    private fun FirebaseUser?.requireUser(): AuthUser =
        this?.toAuthUser() ?: throw AuthException(AuthError.UNKNOWN)

    private fun FirebaseUser.toAuthUser() = AuthUser(
        uid = uid,
        email = email,
        displayName = displayName,
        isEmailVerified = isEmailVerified,
        providerIds = providerData.map { it.providerId }.toSet(),
    )
}
