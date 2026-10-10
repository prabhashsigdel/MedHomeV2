package com.medhome.nepal.data

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.AuthException

class CredentialManagerClient(
    private val appContext: Context,
    private val webClientId: String,
) : CredentialClient {

    override suspend fun requestGoogleIdToken(activityContext: Context): String? {
        // The "Sign in with Google" button option must be requested on its own.
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()
        val credential = try {
            CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        } catch (_: GetCredentialCancellationException) {
            return null
        } catch (e: NoCredentialException) {
            throw AuthException(AuthError.GOOGLE_NO_ACCOUNT, e)
        } catch (e: GetCredentialException) {
            throw AuthException(AuthError.GOOGLE_FAILED, e)
        }
        return googleIdToken(credential) ?: throw AuthException(AuthError.GOOGLE_FAILED)
    }

    override suspend fun requestSavedCredential(activityContext: Context): SavedCredential? {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetPasswordOption())
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setServerClientId(webClientId)
                    // Only accounts already used with this app: this is a "welcome back" sheet,
                    // not a new-account picker (the Google button covers that).
                    .setFilterByAuthorizedAccounts(true)
                    .build(),
            )
            .build()
        val credential = try {
            CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        } catch (_: GetCredentialCancellationException) {
            return null
        } catch (_: NoCredentialException) {
            return null
        } catch (e: GetCredentialException) {
            // An automatic convenience prompt: failing quietly is right, but keep a trace.
            Log.w(TAG, "Saved credential lookup failed: ${e.javaClass.simpleName}")
            return null
        }
        return when (credential) {
            is PasswordCredential -> SavedCredential.Password(credential.id, credential.password)
            else -> try {
                googleIdToken(credential)?.let { SavedCredential.Google(it) }
            } catch (e: AuthException) {
                Log.w(TAG, "Saved Google credential was unreadable: ${e.javaClass.simpleName}")
                null
            }
        }
    }

    override suspend fun savePassword(activityContext: Context, id: String, password: String): SaveOutcome =
        try {
            CredentialManager.create(activityContext)
                .createCredential(activityContext, CreatePasswordRequest(id = id, password = password))
            SaveOutcome.SAVED
        } catch (_: CreateCredentialCancellationException) {
            SaveOutcome.DECLINED
        } catch (e: CreateCredentialException) {
            Log.w(TAG, "Saving the password was not possible: ${e.javaClass.simpleName}")
            SaveOutcome.UNAVAILABLE
        }

    override suspend fun clearCredentialState() {
        try {
            CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: ClearCredentialException) {
            throw AuthException(AuthError.UNKNOWN, e)
        }
    }

    private fun googleIdToken(credential: Credential): String? {
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return null
        }
        return try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            throw AuthException(AuthError.GOOGLE_FAILED, e)
        }
    }

    private companion object {
        const val TAG = "CredentialClient"
    }
}
