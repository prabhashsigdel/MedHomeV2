package com.medhome.nepal.domain

/** The admin's bookings lists, across all doctors. */
enum class AdminBookingFilter {
    /** Booked and not started yet, soonest first. */
    UPCOMING,

    /** Booked and started (or over), latest first. */
    PAST,

    /** Cancelled by anyone, latest appointment first. */
    CANCELLED,
}

/** Who cancelled a booking, as the admin looking at it sees it. */
enum class AdminCancelledBy {
    PATIENT,

    /** The admin looking (their uid is the booking's `cancelledByUid`). */
    YOU,
    ANOTHER_ADMIN,

    /** The clinic, from before the cancelling admin was recorded: could have been anyone. */
    CLINIC,
}

/**
 * Any doctor's booking as admins see it: when, the doctor as booked, and the patient's first
 * name only (null for bookings made before it was stored, or when [patientDeleted]). Never the
 * patient's ID, email or phone, and never another admin's uid.
 */
data class AdminBooking(
    val bookingId: String,
    val doctorId: String,
    val doctor: BookedDoctor,
    val startAtMillis: Long,
    val patientFirstName: String?,
    /** The patient deleted their account, which blanked the name. */
    val patientDeleted: Boolean = false,
    val status: BookingStatus,
    /** Who cancelled it; null while booked, or when the stored value is unknown. */
    val cancelledBy: AdminCancelledBy? = null,
    /** When it was cancelled (server time); null while booked and for cancels before it was stored. */
    val cancelledAtMillis: Long? = null,
    /** Why the clinic cancelled it; null otherwise, and for clinic cancels from before reasons. */
    val clinicReason: ClinicCancelReason? = null,
) {
    val date: CalendarDate get() = NepalTime.dateOf(startAtMillis)
    val start: TimeOfDay get() = NepalTime.timeOf(startAtMillis)

    /** Booked and not started: the only kind the clinic may cancel. */
    fun isUpcoming(nowMillis: Long): Boolean = status == BookingStatus.BOOKED && startAtMillis > nowMillis

    /** For the cancel dialog shared with a doctor's own list. */
    fun toAppointment(): DoctorAppointment =
        DoctorAppointment(bookingId, startAtMillis, patientFirstName, patientDeleted)
}
