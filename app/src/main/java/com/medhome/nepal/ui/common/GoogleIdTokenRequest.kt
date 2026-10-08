package com.medhome.nepal.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.medhome.nepal.data.AuthErrorMapper
import com.medhome.nepal.data.CredentialClient
import com.medhome.nepal.data.SavedCredential
import com.medhome.nepal.domain.AuthError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

sealed interface GoogleIdTokenResult {
    data class Token(val idToken: String) : GoogleIdTokenResult
    data object Cancelled : GoogleIdTokenResult
    data class Failed(val error: AuthError) : GoogleIdTokenResult
}

/**
 * Returns a function that shows the Google account picker. The picker runs in the composition's
 * scope, so ViewModels never hold the Activity. [onStart] returning false (already busy) skips the
 * picker. [onResult] is always called once per started request, including when the screen is
 * destroyed mid-picker, so busy flags never get stuck.
 */
@Composable
fun rememberGoogleIdTokenRequest(
    onStart: () -> Boolean,
    onResult: (GoogleIdTokenResult) -> Unit,
): () -> Unit = rememberCredentialRequest(
    onStart = onStart,
    onResult = onResult,
    cancelled = GoogleIdTokenResult.Cancelled,
    failed = { GoogleIdTokenResult.Failed(it) },
) { context ->
    requestGoogleIdToken(context)?.let { GoogleIdTokenResult.Token(it) } ?: GoogleIdTokenResult.Cancelled
}

/**
 * Same contract for the saved-accounts sheet (saved passwords and previously used Google
 * accounts). [onResult] gets null when nothing was chosen.
 */
@Composable
fun rememberSavedCredentialRequest(
    onStart: () -> Boolean,
    onResult: (SavedCredential?) -> Unit,
): () -> Unit = rememberCredentialRequest(
    onStart = onStart,
    onResult = onResult,
    cancelled = null,
    failed = { null },
) { context -> requestSavedCredential(context) }

@Composable
private fun <T> rememberCredentialRequest(
    onStart: () -> Boolean,
    onResult: (T) -> Unit,
    cancelled: T,
    failed: (AuthError) -> T,
    request: suspend CredentialClient.(Context) -> T,
): () -> Unit {
    val context = LocalContext.current
    val client = LocalCredentialClient.current
    val scope = rememberCoroutineScope()
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnResult by rememberUpdatedState(onResult)
    val currentRequest by rememberUpdatedState(request)
    return remember(context, client, scope) {
        val launch: () -> Unit = {
            if (currentOnStart()) {
                scope.launch {
                    var result: T = cancelled
                    try {
                        result = client.currentRequest(context)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        result = failed(AuthErrorMapper.map(e))
                    } finally {
                        currentOnResult(result)
                    }
                }
            }
        }
        launch
    }
}
