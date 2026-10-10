package com.medhome.nepal.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medhome.nepal.data.AdminBookingsPage
import com.medhome.nepal.data.AdminRepository
import com.medhome.nepal.data.AuthErrorMapper
import com.medhome.nepal.domain.AdminBooking
import com.medhome.nepal.domain.AdminBookingFilter
import com.medhome.nepal.domain.AdminError
import com.medhome.nepal.domain.AdminException
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.CancelReason
import com.medhome.nepal.domain.ClinicCancelReason
import com.medhome.nepal.domain.DoctorAppointment
import com.medhome.nepal.ui.common.Load
import com.medhome.nepal.ui.common.STOP_TIMEOUT_MS
import com.medhome.nepal.ui.common.load
import com.medhome.nepal.ui.common.withOfflineGrace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Bookings tab: every doctor's bookings, by filter, a page at a time

enum class AdminBookingsStatus { LOADING, READY, EMPTY, FAILED }

data class AdminBookingsUiState(
    val filter: AdminBookingFilter = AdminBookingFilter.UPCOMING,
    val status: AdminBookingsStatus = AdminBookingsStatus.LOADING,
    val bookings: List<AdminBooking> = emptyList(),
    /** There may be more: offer Show more. */
    val hasMore: Boolean = false,
    /** Show more was pressed and the longer list hasn't arrived yet (the shorter one stays). */
    val loadingMore: Boolean = false,
    /** The list came from the on-device cache: say it may be out of date. */
    val showingSaved: Boolean = false,
    /** When the list was last worked out, to tell upcoming from past. */
    val nowMillis: Long = 0L,
)

/**
 * The admin's Bookings tab. The list is live: Show more asks for one more page
 * ([PAGE_SIZE]) of the same query, so cancels and new bookings appear without a reload.
 */
class AdminBookingsViewModel(
    private val repository: AdminRepository,
    private val clock: () -> Long,
) : ViewModel() {

    /** What is asked for: changing it restarts the read. */
    private data class Request(val filter: AdminBookingFilter, val pages: Int, val attempt: Int)

    /** An answer and the request it answers. */
    private data class Answer(val request: Request, val load: Load<AdminBookingsPage>)

    private val request = MutableStateFlow(Request(AdminBookingFilter.UPCOMING, pages = 1, attempt = 0))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val answers: Flow<Answer?> = request
        .flatMapLatest { asked ->
            repository.bookingsWithGrace(asked.filter, asked.pages * PAGE_SIZE)
                .map<AdminBookingsPage, Load<AdminBookingsPage>> { Load.Ready(it) }
                .catch { emit(Load.Failed(AuthErrorMapper.map(it))) }
                .map<Load<AdminBookingsPage>, Answer?> { Answer(asked, it) }
        }
        .onStart { emit(null) }

    val uiState: StateFlow<AdminBookingsUiState> =
        combine(request, answers) { asked, answer -> stateOf(asked, answer) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AdminBookingsUiState())

    fun selectFilter(filter: AdminBookingFilter) = request.update { if (it.filter == filter) it else Request(filter, 1, it.attempt) }

    /** One more page, unless the list is complete or already at its longest. */
    fun showMore() {
        val state = uiState.value
        if (state.status != AdminBookingsStatus.READY || !state.hasMore || state.loadingMore) return
        request.update { if (it.pages < MAX_PAGES) it.copy(pages = it.pages + 1) else it }
    }

    fun retry() = request.update { it.copy(attempt = it.attempt + 1) }

    private fun stateOf(asked: Request, answer: Answer?): AdminBookingsUiState {
        // Nothing for this filter (or this retry) yet: loading. A longer page of the same list
        // keeps showing the shorter one until it arrives.
        val current = answer?.takeIf { it.request.filter == asked.filter && it.request.attempt == asked.attempt }
            ?: return AdminBookingsUiState(filter = asked.filter)
        return when (val load = current.load) {
            Load.Loading -> AdminBookingsUiState(filter = asked.filter)
            is Load.Failed -> AdminBookingsUiState(filter = asked.filter, status = AdminBookingsStatus.FAILED)
            is Load.Ready -> {
                val now = clock()
                // Upcoming as of now: one that has started since the list was read is past.
                val shown = if (asked.filter == AdminBookingFilter.UPCOMING) {
                    load.value.bookings.filter { it.isUpcoming(now) }
                } else {
                    load.value.bookings
                }
                AdminBookingsUiState(
                    filter = asked.filter,
                    status = if (shown.isEmpty() && !load.value.hasMore) AdminBookingsStatus.EMPTY else AdminBookingsStatus.READY,
                    bookings = shown,
                    hasMore = load.value.hasMore && asked.pages < MAX_PAGES,
                    loadingMore = current.request.pages < asked.pages,
                    showingSaved = load.value.fromCache,
                    nowMillis = now,
                )
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 25

        /** 500 bookings at most (the repository's limit); older ones are for the console. */
        const val MAX_PAGES = 20
    }
}

private fun AdminRepository.bookingsWithGrace(filter: AdminBookingFilter, limit: Int) =
    bookings(filter, limit).withOfflineGrace(
        isCached = { it.fromCache },
        provisional = { if (it.bookings.isNotEmpty()) it.copy(fromCache = false) else null },
    )

// One booking: details and Cancel

sealed interface AdminBookingUiState {
    data object Loading : AdminBookingUiState

    /** [canCancel]: booked and not started, as of when it was shown. */
    data class Ready(val booking: AdminBooking, val canCancel: Boolean) : AdminBookingUiState

    /** Gone, malformed, or not readable. */
    data object Missing : AdminBookingUiState
    data class Failed(val error: AuthError) : AdminBookingUiState
}

class AdminBookingViewModel(
    private val bookingId: String,
    private val repository: AdminRepository,
    private val clock: () -> Long,
) : ViewModel() {

    private val attempts = MutableStateFlow(0)
    private val cancel = ClinicCancelFlow(viewModelScope, repository)

    val uiState: StateFlow<AdminBookingUiState> =
        attempts.load { repository.booking(bookingId) }
            .map(::stateOf)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AdminBookingUiState.Loading)

    val cancelDialog: StateFlow<CancelBookingDialogState?> = cancel.dialog

    fun retry() = attempts.update { it + 1 }

    /** Asks before cancelling, only while the booking can still be cancelled. */
    fun requestCancel() {
        val ready = uiState.value as? AdminBookingUiState.Ready ?: return
        if (ready.canCancel) cancel.request(ready.booking.toAppointment())
    }

    fun selectCancelReason(reason: CancelReason) = cancel.selectReason(reason)

    fun editCancelNote(note: String) = cancel.editNote(note)

    fun confirmCancel() = cancel.confirm()

    fun dismissCancel() = cancel.dismiss()

    private fun stateOf(load: Load<AdminBooking?>): AdminBookingUiState = when (load) {
        Load.Loading -> AdminBookingUiState.Loading
        is Load.Failed -> AdminBookingUiState.Failed(load.error)
        is Load.Ready -> load.value
            ?.let { AdminBookingUiState.Ready(it, canCancel = it.isUpcoming(clock())) }
            ?: AdminBookingUiState.Missing
    }
}

/**
 * The confirm dialog for cancelling one booking for the clinic: the reason the admin picks
 * (required) and an optional note for the patient.
 */
data class CancelBookingDialogState(
    val appointment: DoctorAppointment,
    val reason: CancelReason? = null,
    val note: String = "",
    val isCancelling: Boolean = false,
    val error: AdminError? = null,
) {
    val canConfirm: Boolean get() = reason != null && !isCancelling
}

/**
 * Cancelling one booking for the clinic, behind a confirm dialog: shared by a doctor's screen
 * and a booking's. The live lists show the result, so a success just closes the dialog.
 */
internal class ClinicCancelFlow(
    private val scope: CoroutineScope,
    private val repository: AdminRepository,
) {
    private val _dialog = MutableStateFlow<CancelBookingDialogState?>(null)
    val dialog: StateFlow<CancelBookingDialogState?> = _dialog.asStateFlow()

    val isOpen: Boolean get() = _dialog.value != null

    /** Opens the dialog for [appointment], unless one is already open. */
    fun request(appointment: DoctorAppointment) {
        if (_dialog.value == null) _dialog.value = CancelBookingDialogState(appointment)
    }

    fun selectReason(reason: CancelReason) = _dialog.update { if (it == null || it.isCancelling) it else it.copy(reason = reason) }

    /** Capped at the note's limit as it is typed. */
    fun editNote(note: String) =
        _dialog.update { if (it == null || it.isCancelling) it else it.copy(note = note.take(ClinicCancelReason.MAX_NOTE_LENGTH)) }

    /** Cancels with the chosen reason; nothing happens until one is chosen. */
    fun confirm() {
        val current = _dialog.value ?: return
        val reason = current.reason ?: return
        if (current.isCancelling) return
        _dialog.value = current.copy(isCancelling = true, error = null)
        scope.launch {
            try {
                repository.cancelBooking(current.appointment.bookingId, ClinicCancelReason(reason, current.note.ifBlank { null }))
                _dialog.value = null
            } catch (e: AdminException) {
                _dialog.update { it?.copy(isCancelling = false, error = e.error) }
            }
        }
    }

    /** Closes the dialog, unless the booking is being cancelled. */
    fun dismiss() {
        if (_dialog.value?.isCancelling == true) return
        _dialog.value = null
    }
}
