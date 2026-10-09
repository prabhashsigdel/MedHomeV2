package com.medhome.nepal.ui.common

import com.medhome.nepal.data.AuthErrorMapper
import com.medhome.nepal.domain.AuthError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest

/**
 * Listeners stop as soon as the screen stops collecting, so none outlives it into a sign-out
 * (which shuts Firestore down). Coming back re-listens; the cache answers at once.
 */
internal const val STOP_TIMEOUT_MS = 0L

/**
 * How long an answer from the on-device cache only is treated as provisional. Online, Firestore
 * answers from the cache first and from the server a moment later; only a cache answer that
 * lasts this long means "offline", so the offline notice doesn't flash on every open. Cached
 * content shows at once regardless: this only delays the notice (and, with nothing cached,
 * the "you're offline" message), so it is kept short.
 */
internal const val OFFLINE_GRACE_MS = 800L

/** Where a live read stands. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val error: AuthError) : Load<Nothing>
}

/**
 * Restartable: [retry][MutableStateFlow.value] bumps the counter and the read starts again,
 * showing Loading. A plain re-listen (back from another screen) keeps what was shown until the
 * new answer arrives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> MutableStateFlow<Int>.load(read: () -> Flow<T>): Flow<Load<T>> = flatMapLatest { attempt ->
    read()
        .map<T, Load<T>> { Load.Ready(it) }
        .onStart { if (attempt > 0) emit(Load.Loading) }
        .catch { emit(Load.Failed(AuthErrorMapper.map(it))) }
}

/**
 * Holds back the "from cache" verdict for [OFFLINE_GRACE_MS]: a cached answer is first shown as
 * [provisional] (null: keep waiting, e.g. an empty cache isn't "offline" yet) and only counts as
 * cached if no newer answer arrives in time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> Flow<T>.withOfflineGrace(isCached: (T) -> Boolean, provisional: (T) -> T?): Flow<T> =
    transformLatest { value ->
        if (isCached(value)) {
            provisional(value)?.let { emit(it) }
            delay(OFFLINE_GRACE_MS)
        }
        emit(value)
    }

/** The time now, then again every [periodMs], so time-based state (past, too soon) moves on. */
internal fun ticker(clock: () -> Long, periodMs: Long = TICK_MS): Flow<Long> = flow {
    while (true) {
        emit(clock())
        delay(periodMs)
    }
}

internal const val TICK_MS = 60_000L
