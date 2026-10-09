package com.medhome.nepal.fakes

import com.medhome.nepal.data.BookingRepository
import com.medhome.nepal.data.BookingsSnapshot
import com.medhome.nepal.data.DoctorRepository
import com.medhome.nepal.domain.BookedDoctor
import com.medhome.nepal.domain.Booking
import com.medhome.nepal.domain.BookingError
import com.medhome.nepal.domain.BookingException
import com.medhome.nepal.domain.BookingStatus
import com.medhome.nepal.domain.CancelledBy
import com.medhome.nepal.domain.Doctor
import com.medhome.nepal.domain.NepalTime
import com.medhome.nepal.domain.Slot
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.AuthException
import com.medhome.nepal.ui.booking.BookingViewModels
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** A booking of [doctor] at [startAtMillis], as the mapper would produce it. */
fun booking(
    id: String = "b1",
    doctor: Doctor = doctor(),
    startAtMillis: Long,
    status: BookingStatus = BookingStatus.BOOKED,
    quotaPlace: Int = 1,
    cancelledBy: CancelledBy? = null,
): Booking {
    val slot = Slot(doctor.id, NepalTime.dateOf(startAtMillis), NepalTime.timeOf(startAtMillis))
    return Booking(
        id = id,
        doctorId = doctor.id,
        doctor = BookedDoctor(doctor.name, doctor.specialty, doctor.hospital, doctor.feeNpr),
        startAtMillis = startAtMillis,
        status = status,
        slotId = slot.id,
        quotaPlace = quotaPlace,
        cancelledBy = cancelledBy,
    )
}

/**
 * Bookings in memory. [book] succeeds unless [bookFailure] is set; a successful booking is added
 * to [bookings] and its slot to [taken], as Firestore's listeners would show. [gate], when set,
 * holds [book] and [cancel] until completed (to see the in-progress state).
 */
class FakeBookingRepository(
    initial: List<Booking> = emptyList(),
    private val clock: () -> Long = { 0L },
) : BookingRepository {
    val bookings = MutableStateFlow<List<Booking>?>(initial)
    val taken = MutableStateFlow<Set<String>>(emptySet())
    var fromCache = false
    var verified = true
    var bookFailure: BookingError? = null
    var cancelFailure: BookingError? = null
    var listFailure: AuthError? = null
    var gate: CompletableDeferred<Unit>? = null
    val booked = mutableListOf<Slot>()
    val cancelled = mutableListOf<String>()
    private var nextId = 1

    override fun takenSlotIds(doctorId: String, window: LongRange): Flow<Set<String>> = taken

    override fun myBookings(): Flow<BookingsSnapshot> = flow {
        listFailure?.let { throw AuthException(it) }
        bookings.filterNotNull().map { BookingsSnapshot(it, fromCache) }.collect { emit(it) }
    }

    override suspend fun hasVerifiedEmail(): Boolean = verified

    override suspend fun book(slot: Slot): String {
        gate?.await()
        bookFailure?.let { throw BookingException(it) }
        booked += slot
        val id = "new${nextId++}"
        val doctor = doctor(id = slot.doctorId)
        bookings.value = bookings.value.orEmpty() + booking(id = id, doctor = doctor, startAtMillis = slot.startAtMillis)
        taken.value = taken.value + slot.id
        return id
    }

    override suspend fun cancel(booking: Booking) {
        gate?.await()
        cancelFailure?.let { throw BookingException(it) }
        if (booking.startAtMillis <= clock()) throw BookingException(BookingError.ALREADY_STARTED)
        cancelled += booking.id
        bookings.value = bookings.value.orEmpty().map {
            if (it.id == booking.id) it.copy(status = BookingStatus.CANCELLED, cancelledBy = CancelledBy.PATIENT) else it
        }
        taken.value = taken.value - booking.slotId
    }

    /** What the patient's listener shows after the clinic cancels [bookingId]. */
    fun cancelByClinic(bookingId: String) {
        val cancelled = bookings.value.orEmpty().firstOrNull { it.id == bookingId } ?: return
        bookings.value = bookings.value.orEmpty().map {
            if (it.id == bookingId) it.copy(status = BookingStatus.CANCELLED, cancelledBy = CancelledBy.CLINIC) else it
        }
        taken.value = taken.value - cancelled.slotId
    }

    override suspend fun cancelAllUpcoming() {
        bookings.value.orEmpty().filter { it.isUpcoming(clock()) }.forEach { cancel(it) }
    }
}

/** Booking ViewModels over fakes, for shell tests (no Firebase app container in JVM tests). */
fun fakeBookingViewModels(
    bookings: FakeBookingRepository = FakeBookingRepository(),
    doctors: DoctorRepository = FakeDoctorRepository(),
    clock: () -> Long = System::currentTimeMillis,
    requestEmailVerification: suspend () -> Unit = {},
) = BookingViewModels.factory {
    BookingViewModels.Dependencies(bookings, doctors, clock, requestEmailVerification)
}
