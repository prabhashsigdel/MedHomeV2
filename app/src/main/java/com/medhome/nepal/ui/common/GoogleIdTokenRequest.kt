package com.medhome.nepal.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.medhome.nepal.MedHomeApplication
import com.medhome.nepal.data.AuthErrorMapper
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
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnResult by rememberUpdatedState(onResult)
    return remember(context, scope) {
        val client = (context.applicationContext as MedHomeApplication).container.googleClient
        val request: () -> Unit = {
            if (currentOnStart()) {
                scope.launch {
                    var result: GoogleIdTokenResult = GoogleIdTokenResult.Cancelled
                    try {
                        result = client.requestIdToken(context)
                            ?.let { GoogleIdTokenResult.Token(it) }
                            ?: GoogleIdTokenResult.Cancelled
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        result = GoogleIdTokenResult.Failed(AuthErrorMapper.map(e))
                    } finally {
                        currentOnResult(result)
                    }
                }
            }
        }
        request
    }
}
