package com.medhome.nepal.domain

/** [key] is what Firestore stores. */
enum class BookingStatus(val key: String) {
    BOOKED("booked"),
    CANCELLED("cancelled"),
    ;

    companion object {
        fun fromKey(key: String?): BookingStatus? = entries.firstOrNull { it.key == key }
    }
}

/** Who cancelled a booking. [key] is what Firestore stores. */
enum class CancelledBy(val key: String) {
    PATIENT("patient"),
    CLINIC("clinic"),
    ;

    companion object {
        fun fromKey(key: String?): CancelledBy? = entries.firstOrNull { it.key == key }
    }
}

/** Why the clinic cancelled a booking. [key] is what Firestore stores (firestore.rules lists the same). */
enum class CancelReason(val key: String) {
    DOCTOR_UNAVAILABLE("doctor_unavailable"),
    SCHEDULE_CHANGED("schedule_changed"),
    FULLY_BOOKED("fully_booked"),
    CLINIC_CLOSED("clinic_closed"),
    OTHER("other"),
    ;

    companion object {
        fun fromKey(key: String?): CancelReason? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The clinic's reason for a cancel, with an optional note for the patient (one line of plain
 * text, at most [MAX_NOTE_LENGTH] characters; null when there is none).
 */
data class ClinicCancelReason(val reason: CancelReason, val note: String? = null) {
    companion object {
        /** The rules allow no more (`isLine(cancelNote, 150)`). */
        const val MAX_NOTE_LENGTH = 150
    }
}

/** The doctor as they were when booked (a booking keeps its fee if the doctor's changes later). */
data class BookedDoctor(
    val name: String,
    val specialty: Specialty,
    val hospital: String,
    val feeNpr: Int,
)

/** One of the signed-in patient's bookings. Only well-formed bookings reach this type. */
data class Booking(
    val id: String,
    val doctorId: String,
    val doctor: BookedDoctor,
    val startAtMillis: Long,
    val status: BookingStatus,
    /** The slot lock's document ID, deleted when the booking is cancelled. */
    val slotId: String,
    /** Which of the patient's 3 quota places (1..3) this booking holds. */
    val quotaPlace: Int,
    /** Who cancelled it; null while booked, or when the stored value is unknown. */
    val cancelledBy: CancelledBy? = null,
    /** Why the clinic cancelled it; null otherwise, and for clinic cancels from before reasons. */
    val clinicReason: ClinicCancelReason? = null,
) {
    val date: CalendarDate get() = NepalTime.dateOf(startAtMillis)
    val start: TimeOfDay get() = NepalTime.timeOf(startAtMillis)

    /** Booked and not started yet: shown under Upcoming, and the only kind that can be cancelled. */
    fun isUpcoming(nowMillis: Long): Boolean = status == BookingStatus.BOOKED && startAtMillis > nowMillis

    companion object {
        /** A patient may hold this many upcoming bookings (firestore.rules enforces it). */
        const val MAX_UPCOMING = 3
        val QuotaPlaces = 1..MAX_UPCOMING
    }
}

/** Why booking or cancelling failed. Each screen picks its own message for each. */
enum class BookingError {
    /** Someone else booked the slot first. */
    SLOT_TAKEN,

    /** The slot is no longer offered: too soon, past, or off the doctor's schedule now. */
    SLOT_UNAVAILABLE,

    /** Already [Booking.MAX_UPCOMING] upcoming bookings. */
    LIMIT_REACHED,

    /** The sign-in token doesn't (yet) say the email is verified. */
    EMAIL_NOT_VERIFIED,

    /** The doctor is no longer taking appointments. */
    DOCTOR_UNAVAILABLE,

    /** Cancelling an appointment that has started. */
    ALREADY_STARTED,

    /** The booking no longer exists or isn't the user's. */
    NOT_FOUND,
    NETWORK,
    UNKNOWN,
}

class BookingException(val error: BookingError, cause: Throwable? = null) : Exception(error.name, cause)
