package com.medhome.nepal.data

import com.medhome.nepal.domain.AuthUser
import kotlinx.coroutines.flow.Flow

/** Every method throws [com.medhome.nepal.domain.AuthException] on failure. */
interface AuthDataSource {
    val currentUser: AuthUser?
    fun authStateChanges(): Flow<AuthUser?>
    suspend fun signInWithEmail(email: String, password: String): AuthUser
    suspend fun createUserWithEmail(email: String, password: String): AuthUser
    suspend fun updateDisplayName(name: String)
    suspend fun signInWithGoogle(idToken: String): AuthUser
    suspend fun sendEmailVerification()
    suspend fun sendPasswordReset(email: String)
    suspend fun reloadUser(): AuthUser
    suspend fun reauthenticate(email: String, password: String)
    suspend fun reauthenticateWithGoogle(idToken: String)
    suspend fun updatePassword(newPassword: String)
    suspend fun deleteUser()
    fun signOut()
}
