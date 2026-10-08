package com.medhome.nepal.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.AuthException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AuthErrorMapperTest {

    @Test
    fun `invalid credential maps to incorrect email or password`() {
        val e = FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "bad")
        assertEquals(AuthError.INVALID_CREDENTIALS, AuthErrorMapper.map(e))
    }

    @Test
    fun `wrong password maps to incorrect email or password`() {
        val e = FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "bad")
        assertEquals(AuthError.INVALID_CREDENTIALS, AuthErrorMapper.map(e))
    }

    @Test
    fun `user not found and disabled are distinguished`() {
        assertEquals(
            AuthError.USER_NOT_FOUND,
            AuthErrorMapper.map(FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "x")),
        )
        assertEquals(
            AuthError.USER_DISABLED,
            AuthErrorMapper.map(FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "x")),
        )
    }

    @Test
    fun `email in use maps to its own message`() {
        val e = FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "x")
        assertEquals(AuthError.EMAIL_IN_USE, AuthErrorMapper.map(e))
    }

    @Test
    fun `different credential collision tells the user to use their password`() {
        val e = FirebaseAuthUserCollisionException("ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL", "x")
        assertEquals(AuthError.ACCOUNT_EXISTS_WITH_PASSWORD, AuthErrorMapper.map(e))
    }

    @Test
    fun `weak password maps to weak password`() {
        val e = FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "x", "too short")
        assertEquals(AuthError.WEAK_PASSWORD, AuthErrorMapper.map(e))
    }

    @Test
    fun `network and throttling errors map correctly`() {
        assertEquals(AuthError.NETWORK, AuthErrorMapper.map(FirebaseNetworkException("x")))
        assertEquals(AuthError.TOO_MANY_REQUESTS, AuthErrorMapper.map(FirebaseTooManyRequestsException("x")))
    }

    @Test
    fun `firestore codes map to network or permission errors`() {
        assertEquals(
            AuthError.NETWORK,
            AuthErrorMapper.map(FirebaseFirestoreException("x", FirebaseFirestoreException.Code.UNAVAILABLE)),
        )
        assertEquals(
            AuthError.PERMISSION_DENIED,
            AuthErrorMapper.map(FirebaseFirestoreException("x", FirebaseFirestoreException.Code.PERMISSION_DENIED)),
        )
    }

    @Test
    fun `existing auth exceptions pass through unchanged`() {
        val e = AuthException(AuthError.PROFILE_INVALID)
        assertSame(e, AuthErrorMapper.toException(e))
    }

    @Test
    fun `unknown exceptions map to unknown`() {
        assertEquals(AuthError.UNKNOWN, AuthErrorMapper.map(IllegalStateException("boom")))
    }
}
