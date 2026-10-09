package com.medhome.nepal.domain

/** A doctor as admins see them: active (shown to patients) or not. Only well-formed doctors. */
data class ManagedDoctor(val doctor: Doctor, val active: Boolean)

/**
 * One upcoming booking of a doctor, as admins see it: when, and the patient's first name only
 * (null when it couldn't be read). Never the patient's email, phone or ID.
 */
data class DoctorAppointment(
    val bookingId: String,
    val startAtMillis: Long,
    val patientFirstName: String?,
) {
    val date: CalendarDate get() = NepalTime.dateOf(startAtMillis)
    val start: TimeOfDay get() = NepalTime.timeOf(startAtMillis)
}

/** Why an admin write failed. Each screen picks its own message for each. */
enum class AdminError {
    NETWORK,

    /** The rules refused the write (no longer an admin, or invalid data). */
    PERMISSION_DENIED,

    /** The doctor doesn't exist (any more). */
    NOT_FOUND,

    /** A new doctor's ID is already taken. */
    ALREADY_EXISTS,
    UNKNOWN,
}

class AdminException(val error: AdminError, cause: Throwable? = null) : Exception(error.name, cause)
