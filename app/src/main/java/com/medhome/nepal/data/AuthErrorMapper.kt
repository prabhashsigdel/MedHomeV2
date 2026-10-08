package com.medhome.nepal.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.AuthException
import kotlinx.coroutines.CancellationException

object AuthErrorMapper {

    fun map(throwable: Throwable): AuthError = when (throwable) {
        is AuthException -> throwable.error
        is FirebaseNetworkException -> AuthError.NETWORK
        is FirebaseTooManyRequestsException -> AuthError.TOO_MANY_REQUESTS
        is FirebaseAuthWeakPasswordException -> AuthError.WEAK_PASSWORD
        is FirebaseAuthRecentLoginRequiredException -> AuthError.REQUIRES_RECENT_LOGIN
        is FirebaseAuthException -> mapAuthCode(throwable.errorCode)
        is FirebaseFirestoreException -> mapFirestoreCode(throwable.code)
        else -> AuthError.UNKNOWN
    }

    fun toException(throwable: Throwable): AuthException =
        throwable as? AuthException ?: AuthException(map(throwable), throwable)

    private fun mapAuthCode(code: String): AuthError = when (code) {
        // With email enumeration protection, a wrong password and an unknown user
        // both arrive as ERROR_INVALID_CREDENTIAL.
        "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD" -> AuthError.INVALID_CREDENTIALS
        "ERROR_USER_NOT_FOUND" -> AuthError.USER_NOT_FOUND
        "ERROR_USER_DISABLED" -> AuthError.USER_DISABLED
        "ERROR_INVALID_EMAIL" -> AuthError.INVALID_EMAIL
        "ERROR_EMAIL_ALREADY_IN_USE" -> AuthError.EMAIL_IN_USE
        "ERROR_WEAK_PASSWORD" -> AuthError.WEAK_PASSWORD
        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL",
        "ERROR_CREDENTIAL_ALREADY_IN_USE" -> AuthError.ACCOUNT_EXISTS_WITH_PASSWORD
        "ERROR_USER_MISMATCH" -> AuthError.WRONG_ACCOUNT
        "ERROR_REQUIRES_RECENT_LOGIN" -> AuthError.REQUIRES_RECENT_LOGIN
        "ERROR_TOO_MANY_REQUESTS" -> AuthError.TOO_MANY_REQUESTS
        "ERROR_NETWORK_REQUEST_FAILED" -> AuthError.NETWORK
        else -> AuthError.UNKNOWN
    }

    private fun mapFirestoreCode(code: FirebaseFirestoreException.Code): AuthError = when (code) {
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> AuthError.NETWORK
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> AuthError.PERMISSION_DENIED
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> AuthError.TOO_MANY_REQUESTS
        else -> AuthError.UNKNOWN
    }
}

/** Runs [block], converting any SDK exception into an [AuthException]. */
internal inline fun <T> mapErrors(block: () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    throw AuthErrorMapper.toException(e)
}
