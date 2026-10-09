package com.medhome.nepal.ui.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medhome.nepal.appContainer
import com.medhome.nepal.data.BookingRepository
import com.medhome.nepal.data.BookingsSnapshot
import com.medhome.nepal.data.DoctorRepository
import com.medhome.nepal.domain.Booking
import com.medhome.nepal.domain.BookingError
import com.medhome.nepal.domain.BookingException
import com.medhome.nepal.ui.common.Load
import com.medhome.nepal.ui.common.STOP_TIMEOUT_MS
import com.medhome.nepal.ui.common.load
import com.medhome.nepal.ui.common.ticker
import com.medhome.nepal.ui.common.withOfflineGrace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The patient's bookings, with the offline verdict held back as for doctors. */
private fun BookingRepository.myBookingsWithGrace(): Flow<BookingsSnapshot> =
    myBookings().withOfflineGrace(
        isCached = { it.fromCache },
        provisional = { if (it.bookings.isNotEmpty()) it.copy(fromCache = false) else null },
    )

/** Upcoming soonest first; past (started or cancelled) most recent first. */
internal fun splitBookings(bookings: List<Booking>, nowMillis: Long): Pair<List<Booking>, List<Booking>> {
    val (upcoming, past) = bookings.partition { it.isUpcoming(nowMillis) }
    return upcoming.sortedBy { it.startAtMillis } to past.sortedByDescending { it.startAtMillis }
}

// Bookings tab

enum class BookingsSegment { UPCOMING, PAST }

sealed interface BookingsUiState {
    data object Loading : BookingsUiState
    data object Failed : BookingsUiState
    data class Ready(
        val upcoming: List<Booking>,
        val past: List<Booking>,
        /** Shown from the on-device cache: say it may be out of date. */
        val showingSaved: Boolean,
    ) : BookingsUiState
}

class BookingsViewModel(repository: BookingRepository, clock: () -> Long) : ViewModel() {

    private val attempts = MutableStateFlow(0)

    /** Which list is shown. Kept here so it survives leaving the tab and coming back. */
    val segment = MutableStateFlow(BookingsSegment.UPCOMING)

    val uiState: StateFlow<BookingsUiState> =
        combine(attempts.load(repository::myBookingsWithGrace), ticker(clock)) { load, now ->
            when (load) {
                Load.Loading -> BookingsUiState.Loading
                is Load.Failed -> BookingsUiState.Failed
                is Load.Ready -> {
                    val (upcoming, past) = splitBookings(load.value.bookings, now)
                    BookingsUiState.Ready(upcoming, past, showingSaved = load.value.fromCache)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BookingsUiState.Loading)

    fun selectSegment(value: BookingsSegment) = segment.update { value }

    fun retry() = attempts.update { it + 1 }
}

// Booking detail

sealed interface BookingDetailUiState {
    data object Loading : BookingDetailUiState
    data object Failed : BookingDetailUiState

    /** Not among the patient's bookings (wrong ID, or the account changed). */
    data object NotFound : BookingDetailUiState
    data class Ready(
        val booking: Booking,
        /** Booked and not started: the Cancel button shows. */
        val canCancel: Boolean,
        val confirmingCancel: Boolean = false,
        val cancelling: Boolean = false,
        val cancelError: BookingError? = null,
    ) : BookingDetailUiState
}

private data class CancelState(
    val confirming: Boolean = false,
    val cancelling: Boolean = false,
    val error: BookingError? = null,
    /** Cancelled here: no Cancel button while the listener catches up. */
    val done: Boolean = false,
)

class BookingDetailViewModel(
    private val bookingId: String,
    private val repository: BookingRepository,
    clock: () -> Long,
) : ViewModel() {

    private val attempts = MutableStateFlow(0)
    private val cancel = MutableStateFlow(CancelState())

    /** The live booking, from a listener on the patient's bookings (the query the list uses). */
    private val booking: Flow<Load<Booking?>> = attempts.load {
        repository.myBookings().map { snapshot -> snapshot.bookings.firstOrNull { it.id == bookingId } }
    }

    val uiState: StateFlow<BookingDetailUiState> =
        combine(booking, ticker(clock), cancel) { load, now, cancelState ->
            when (load) {
                Load.Loading -> BookingDetailUiState.Loading
                is Load.Failed -> BookingDetailUiState.Failed
                is Load.Ready -> load.value?.let { found ->
                    val canCancel = found.isUpcoming(now) && !cancelState.done
                    BookingDetailUiState.Ready(
                        booking = found,
                        canCancel = canCancel,
                        // A booking that stops being cancellable (it started) closes the dialog.
                        confirmingCancel = cancelState.confirming && canCancel,
                        cancelling = cancelState.cancelling,
                        cancelError = cancelState.error,
                    )
                } ?: BookingDetailUiState.NotFound
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BookingDetailUiState.Loading)

    fun retry() = attempts.update { it + 1 }

    fun askToCancel() = cancel.update { if (it.cancelling) it else it.copy(confirming = true, error = null) }

    /** Closing the dialog; ignored while the cancel is being sent. */
    fun dismissCancel() = cancel.update { if (it.cancelling) it else it.copy(confirming = false) }

    fun dismissError() = cancel.update { it.copy(error = null) }

    fun confirmCancel() {
        val current = (uiState.value as? BookingDetailUiState.Ready)?.booking ?: return
        if (cancel.value.cancelling) return
        cancel.update { it.copy(cancelling = true, error = null) }
        viewModelScope.launch {
            val failure = try {
                repository.cancel(current)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: BookingException) {
                e.error
            }
            // Success: the listener shows the booking as cancelled; the dialog closes either way.
            cancel.value = CancelState(error = failure, done = failure == null)
        }
    }
}

// Home's next appointment

sealed interface NextAppointment {
    data object Loading : NextAppointment
    data object None : NextAppointment
    data object Failed : NextAppointment
    data class Upcoming(val booking: Booking) : NextAppointment
}

class NextAppointmentViewModel(repository: BookingRepository, clock: () -> Long) : ViewModel() {
    val state: StateFlow<NextAppointment> =
        combine(MutableStateFlow(0).load(repository::myBookings), ticker(clock)) { load, now ->
            when (load) {
                Load.Loading -> NextAppointment.Loading
                is Load.Failed -> NextAppointment.Failed
                is Load.Ready -> splitBookings(load.value.bookings, now).first.firstOrNull()
                    ?.let(NextAppointment::Upcoming)
                    ?: NextAppointment.None
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NextAppointment.Loading)
}

/**
 * Builds every booking ViewModel. The app uses [Factory]; tests pass fakes. [requestEmailVerification]
 * shows the verify-email screen (SessionManager.showEmailVerification in the app).
 */
object BookingViewModels {
    /** Navigation arguments (the routes' property names). */
    const val ARG_DOCTOR_ID = "doctorId"
    const val ARG_BOOKING_ID = "bookingId"

    class Dependencies(
        val bookings: BookingRepository,
        val doctors: DoctorRepository,
        val clock: () -> Long = System::currentTimeMillis,
        val requestEmailVerification: suspend () -> Unit,
    )

    fun factory(dependencies: (CreationExtras) -> Dependencies): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val deps = dependencies(this)
            BookAppointmentViewModel(
                doctorId = createSavedStateHandle().require(ARG_DOCTOR_ID),
                bookings = deps.bookings,
                doctors = deps.doctors,
                clock = deps.clock,
                requestEmailVerification = deps.requestEmailVerification,
            )
        }
        initializer {
            val deps = dependencies(this)
            BookingsViewModel(deps.bookings, deps.clock)
        }
        initializer {
            val deps = dependencies(this)
            BookingDetailViewModel(createSavedStateHandle().require(ARG_BOOKING_ID), deps.bookings, deps.clock)
        }
        initializer {
            val deps = dependencies(this)
            NextAppointmentViewModel(deps.bookings, deps.clock)
        }
    }

    val Factory: ViewModelProvider.Factory = factory { extras ->
        val container = extras.appContainer
        Dependencies(
            bookings = container.bookingRepository,
            doctors = container.doctorRepository,
            requestEmailVerification = container.sessionManager::showEmailVerification,
        )
    }

    private fun SavedStateHandle.require(key: String): String =
        requireNotNull(get<String>(key)) { "Opened without $key" }
}
