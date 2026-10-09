package com.medhome.nepal.ui.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medhome.nepal.data.BookingRepository
import com.medhome.nepal.data.DoctorLookup
import com.medhome.nepal.data.DoctorRepository
import com.medhome.nepal.domain.BookingError
import com.medhome.nepal.domain.BookingException
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.domain.DaySlots
import com.medhome.nepal.domain.Doctor
import com.medhome.nepal.domain.NepalTime
import com.medhome.nepal.domain.Slot
import com.medhome.nepal.domain.Slots
import com.medhome.nepal.ui.common.Load
import com.medhome.nepal.ui.common.STOP_TIMEOUT_MS
import com.medhome.nepal.ui.common.load
import com.medhome.nepal.ui.common.ticker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The slot picker's content. */
sealed interface SlotsState {
    data object Loading : SlotsState

    /** The sign-in token doesn't say the email is verified: booking would be refused. */
    data object NeedsVerification : SlotsState

    /** The doctor is gone or inactive; [offline] when they may just not be cached. */
    data class DoctorUnavailable(val offline: Boolean) : SlotsState
    data object Failed : SlotsState

    /** [days]: the next 14 days, each with its free slots (possibly none). */
    data class Ready(val doctor: Doctor, val days: List<DaySlots>) : SlotsState {
        val hasAnySlot: Boolean get() = days.any { it.slots.isNotEmpty() }
    }
}

data class BookAppointmentUiState(
    val slots: SlotsState = SlotsState.Loading,
    /** The day whose slots are shown; null until the slots are ready. */
    val selectedDate: CalendarDate? = null,
    /** The slot being confirmed: the confirm sheet is open while set. */
    val pendingSlot: Slot? = null,
    val submitting: Boolean = false,
    /** Why the last attempt failed. Shown in the sheet while it is open, otherwise above the slots. */
    val error: BookingError? = null,
    /** The slot just booked, and its doctor: the success state is shown. */
    val booked: Slot? = null,
    val bookedDoctor: Doctor? = null,
)

/** What the user has done, kept apart from what the live reads say. */
private data class Interaction(
    val chosenDate: CalendarDate? = null,
    val pendingSlot: Slot? = null,
    val submitting: Boolean = false,
    val error: BookingError? = null,
    val booked: Slot? = null,
    val bookedDoctor: Doctor? = null,
)

/**
 * Picking a slot for one doctor and booking it. Slots come from the doctor's weekly schedule
 * (live), minus the locked ones (live) and those starting within the lead time (re-checked every
 * minute). [requestEmailVerification] shows the verify-email screen.
 */
class BookAppointmentViewModel(
    private val doctorId: String,
    private val bookings: BookingRepository,
    doctors: DoctorRepository,
    private val clock: () -> Long,
    private val requestEmailVerification: suspend () -> Unit,
) : ViewModel() {

    private val attempts = MutableStateFlow(0)

    /** Null while checking. A failed check (offline, say) doesn't block: booking re-checks. */
    private val verified = MutableStateFlow<Boolean?>(null)
    private val interaction = MutableStateFlow(Interaction())

    /** Slots found taken while booking, hidden at once instead of waiting for the listener. */
    private val knownTaken = MutableStateFlow<Set<String>>(emptySet())

    /** Locks read cover the whole booking window, plus a day so it stays covered past midnight. */
    private val lockWindow = clock().let { now -> now..(Slots.windowMillis(now).last + NepalTime.MILLIS_PER_DAY) }

    private val slots = combine(
        verified,
        attempts.load {
            combine(doctors.doctor(doctorId), bookings.takenSlotIds(doctorId, lockWindow)) { doctor, taken -> doctor to taken }
        },
        ticker(clock),
        knownTaken,
    ) { isVerified, load, now, taken -> slotsStateOf(isVerified, load, now, taken) }

    val uiState: StateFlow<BookAppointmentUiState> =
        combine(slots, interaction, ::stateOf)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BookAppointmentUiState())

    init {
        checkVerified()
    }

    fun selectDate(date: CalendarDate) = interaction.update { it.copy(chosenDate = date) }

    fun selectSlot(slot: Slot) = interaction.update {
        if (it.submitting || it.booked != null) it else it.copy(pendingSlot = slot, error = null)
    }

    /** Closing the sheet; ignored while the booking is being sent. */
    fun dismissConfirm() = interaction.update { if (it.submitting) it else it.copy(pendingSlot = null, error = null) }

    fun dismissError() = interaction.update { it.copy(error = null) }

    fun retry() {
        interaction.update { it.copy(error = null) }
        attempts.update { it + 1 }
        checkVerified()
    }

    fun verifyEmail() {
        viewModelScope.launch { requestEmailVerification() }
    }

    fun confirm() {
        val slot = interaction.value.pendingSlot ?: return
        if (interaction.value.submitting || interaction.value.booked != null) return
        interaction.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val failure = try {
                bookings.book(slot)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: BookingException) {
                e.error
            }
            val doctor = (uiState.value.slots as? SlotsState.Ready)?.doctor
            interaction.update { current -> afterBooking(current, slot, doctor, failure) }
            // Only a lock we saw proves the slot is gone. A refusal can have other causes
            // (a phone clock that is off, say), so it doesn't hide a slot that may be free.
            if (failure == BookingError.SLOT_TAKEN) knownTaken.update { it + slot.id }
            if (failure == BookingError.EMAIL_NOT_VERIFIED) verified.value = false
        }
    }

    private fun checkVerified() {
        viewModelScope.launch {
            verified.value = try {
                bookings.hasVerifiedEmail()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                true
            }
        }
    }

    private fun slotsStateOf(
        isVerified: Boolean?,
        load: Load<Pair<DoctorLookup, Set<String>>>,
        now: Long,
        knownTaken: Set<String>,
    ): SlotsState = when {
        isVerified == null -> SlotsState.Loading
        !isVerified -> SlotsState.NeedsVerification
        else -> when (load) {
            Load.Loading -> SlotsState.Loading
            is Load.Failed -> SlotsState.Failed
            is Load.Ready -> when (val lookup = load.value.first) {
                is DoctorLookup.Unavailable -> SlotsState.DoctorUnavailable(offline = lookup.fromCache)
                is DoctorLookup.Found -> try {
                    SlotsState.Ready(
                        doctor = lookup.doctor,
                        days = Slots.upcoming(lookup.doctor, now, taken = load.value.second + knownTaken),
                    )
                } catch (_: IllegalArgumentException) {
                    // A phone clock set outside the years a date can hold.
                    SlotsState.Failed
                }
            }
        }
    }

    private fun stateOf(slots: SlotsState, current: Interaction): BookAppointmentUiState {
        val days = (slots as? SlotsState.Ready)?.days.orEmpty()
        val selected = days.firstOrNull { it.date == current.chosenDate }?.date
            ?: days.firstOrNull { it.slots.isNotEmpty() }?.date
            ?: days.firstOrNull()?.date
        return BookAppointmentUiState(
            slots = slots,
            selectedDate = selected,
            pendingSlot = current.pendingSlot,
            submitting = current.submitting,
            error = current.error,
            booked = current.booked,
            bookedDoctor = current.bookedDoctor,
        )
    }

    companion object {
        /**
         * The next interaction after a booking attempt for [slot]: success, or the sheet closes
         * (the slot is gone or verification is needed), or it stays open with the error.
         */
        private fun afterBooking(current: Interaction, slot: Slot, doctor: Doctor?, failure: BookingError?): Interaction = when (failure) {
            null -> current.copy(submitting = false, pendingSlot = null, booked = slot, bookedDoctor = doctor)
            BookingError.SLOT_TAKEN,
            BookingError.SLOT_UNAVAILABLE,
            BookingError.EMAIL_NOT_VERIFIED,
            BookingError.DOCTOR_UNAVAILABLE ->
                current.copy(submitting = false, pendingSlot = null, error = failure)
            else -> current.copy(submitting = false, error = failure)
        }
    }
}
