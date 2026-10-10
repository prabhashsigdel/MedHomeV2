package com.medhome.nepal.ui.admin

import com.medhome.nepal.data.AdminRepository
import com.medhome.nepal.domain.AdminError
import com.medhome.nepal.domain.AdminException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * How a bulk cancel went: [cancelled] bookings (by this run; ones the patient cancelled meanwhile
 * don't count), [failed] ones still booked, and [error], the
 * first reason something went wrong (a booking, or asking for the next page of bookings).
 */
data class BulkCancelResult(val cancelled: Int, val failed: Int, val error: AdminError?) {
    val isComplete: Boolean get() = failed == 0 && error == null
}

/**
 * Cancels every upcoming booking of a doctor for the clinic. Each booking is its own transaction
 * (booking, lock and quota place together), so one failure never undoes another; up to
 * [CHUNK_SIZE] run at once. Bookings come a page at a time from the server, each page after the
 * last one's start time, so every booking is tried once per run and those that fail don't hide
 * the rest. Running it again (Retry) goes over exactly what is still booked. A booking that
 * started meanwhile is skipped.
 */
object BulkCancel {
    const val CHUNK_SIZE = 10

    /** Pages of [AdminRepository.upcomingBookingPage]; a guard against a list that never ends. */
    const val MAX_PAGES = 25

    suspend fun cancelUpcoming(repository: AdminRepository, doctorId: String): BulkCancelResult {
        var failed = 0
        var cancelled = 0
        var error: AdminError? = null
        var after: Long? = null
        repeat(MAX_PAGES) {
            val page = try {
                repository.upcomingBookingPage(doctorId, after)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return BulkCancelResult(cancelled, failed, error ?: e.adminError())
            }
            page.bookingIds.chunked(CHUNK_SIZE).forEach { chunk ->
                cancelEach(repository, chunk).forEach { outcome ->
                    when (outcome) {
                        Outcome.Cancelled -> cancelled++
                        // Cancelled by the patient meanwhile, or no longer upcoming: nothing to cancel.
                        Outcome.AlreadyCancelled -> Unit
                        is Outcome.Failed -> if (outcome.error != AdminError.BOOKING_STARTED) {
                            failed++
                            error = error ?: outcome.error
                        }
                    }
                }
            }
            after = page.nextAfterMillis ?: return BulkCancelResult(cancelled, failed, error)
        }
        // Still more pages after the last one allowed: say so rather than claim they are all cancelled.
        return BulkCancelResult(cancelled, failed, error ?: AdminError.UNKNOWN)
    }

    private sealed interface Outcome {
        data object Cancelled : Outcome
        data object AlreadyCancelled : Outcome
        data class Failed(val error: AdminError) : Outcome
    }

    private suspend fun cancelEach(repository: AdminRepository, ids: List<String>): List<Outcome> =
        coroutineScope {
            ids.map { id ->
                async {
                    try {
                        if (repository.cancelBooking(id)) Outcome.Cancelled else Outcome.AlreadyCancelled
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Outcome.Failed(e.adminError())
                    }
                }
            }.awaitAll()
        }

    /**
     * The repository reports failures as [AdminException]; anything else (a Firestore instance
     * shut down by sign-out, say) counts as UNKNOWN rather than crashing the whole run.
     */
    private fun Exception.adminError(): AdminError = (this as? AdminException)?.error ?: AdminError.UNKNOWN
}
