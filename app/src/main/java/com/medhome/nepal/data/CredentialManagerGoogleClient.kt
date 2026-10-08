package com.medhome.nepal.data

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.AuthException

class CredentialManagerGoogleClient(
    private val appContext: Context,
    private val webClientId: String,
) : GoogleCredentialClient {

    override suspend fun requestIdToken(activityContext: Context): String? {
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

        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw AuthException(AuthError.GOOGLE_FAILED)
        }
        return try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            throw AuthException(AuthError.GOOGLE_FAILED, e)
        }
    }

    override suspend fun clearCredentialState() {
        try {
            CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: ClearCredentialException) {
            throw AuthException(AuthError.UNKNOWN, e)
        }
    }
}
