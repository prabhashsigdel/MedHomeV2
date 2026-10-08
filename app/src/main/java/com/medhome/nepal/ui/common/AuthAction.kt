package com.medhome.nepal.ui.common

import com.medhome.nepal.data.AuthErrorMapper
import com.medhome.nepal.domain.AuthError
import kotlinx.coroutines.CancellationException

/** Runs an auth operation and returns its error, or null on success. */
suspend fun runAuthAction(block: suspend () -> Unit): AuthError? = try {
    block()
    null
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AuthErrorMapper.map(e)
}

/** Errors where trying the exact same thing again can reasonably succeed. */
val AuthError.isRetryable: Boolean
    get() = when (this) {
        AuthError.PROFILE_LOAD_FAILED,
        AuthError.NETWORK,
        AuthError.GOOGLE_FAILED,
        AuthError.UNKNOWN -> true
        else -> false
    }
