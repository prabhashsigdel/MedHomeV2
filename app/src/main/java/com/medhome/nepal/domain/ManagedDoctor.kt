package com.medhome.nepal.domain

/** A doctor as admins see them: active (shown to patients) or not. Only well-formed doctors. */
data class ManagedDoctor(val doctor: Doctor, val active: Boolean)

/**
 * One upcoming booking of a doctor, as admins see it: when, and the patient's first name only,
 * from the name the booking was made under (null for bookings made before it was stored, or
 * when [patientDeleted]). Never the patient's email, phone or ID. Booked, or cancelled by the
 * clinic ([clinicCancel]); bookings the patient cancelled aren't listed.
 */
data class DoctorAppointment(
    val bookingId: String,
    val startAtMillis: Long,
    val patientFirstName: String?,
    /** The patient deleted their account, which blanked the name. */
    val patientDeleted: Boolean = false,
    /** Null while booked. */
    val clinicCancel: ClinicCancel? = null,
) {
    val date: CalendarDate get() = NepalTime.dateOf(startAtMillis)
    val start: TimeOfDay get() = NepalTime.timeOf(startAtMillis)
    val isBooked: Boolean get() = clinicCancel == null
}

/** Who, for the admin looking, cancelled a booking for the clinic. */
enum class ClinicCancel {
    /** This admin. */
    BY_YOU,

    /** Another admin, or a cancel from before the admin was recorded. */
    BY_CLINIC,
}

/** Why an admin write failed. Each screen picks its own message for each. */
enum class AdminError {
    NETWORK,

    /** The rules refused the write (no longer an admin, or invalid data). */
    PERMISSION_DENIED,

    /** The doctor (or, when cancelling, the booking) doesn't exist (any more). */
    NOT_FOUND,

    /** Cancelling a booking that has already started. */
    BOOKING_STARTED,

    /** A new doctor's ID is already taken. */
    ALREADY_EXISTS,
    UNKNOWN,
}

class AdminException(val error: AdminError, cause: Throwable? = null) : Exception(error.name, cause)
