package com.medhome.nepal.data

import com.google.firebase.Timestamp
import com.medhome.nepal.domain.BookedDoctor
import com.medhome.nepal.domain.Booking
import com.medhome.nepal.domain.BookingStatus
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.domain.Doctor
import com.medhome.nepal.domain.NepalTime
import com.medhome.nepal.domain.Slots
import com.medhome.nepal.domain.Specialty
import com.medhome.nepal.domain.TimeOfDay

/**
 * Turns a bookings/{id} document into a [Booking], or null when it isn't a well-formed booking
 * of [patientUid]. Like [DoctorMapper], nothing from Firestore is trusted: the snapshot's text
 * is cleaned, numbers are range-checked, and the slot ID must match the doctor and time.
 * Never throws.
 */
object BookingMapper {

    const val FIELD_PATIENT_UID = "patientUid"
    const val FIELD_DOCTOR_ID = "doctorId"
    const val FIELD_DOCTOR = "doctor"
    const val FIELD_START_AT = "startAt"
    const val FIELD_SLOT_ID = "slotId"
    const val FIELD_QUOTA_PLACE = "quotaPlace"
    const val FIELD_STATUS = "status"
    const val FIELD_CREATED_AT = "createdAt"
    const val FIELD_CANCELLED_AT = "cancelledAt"

    /** Firestore auto IDs (20 characters); anything else never names a booking. */
    private val BOOKING_ID = Regex("[A-Za-z0-9]{1,40}")

    fun isValidId(id: String): Boolean = BOOKING_ID.matches(id)

    fun parse(id: String, data: Map<String, Any?>?, patientUid: String): Booking? {
        if (data == null || !isValidId(id)) return null
        if (data[FIELD_PATIENT_UID] != patientUid) return null
        val doctorId = (data[FIELD_DOCTOR_ID] as? String)?.takeIf(Doctor::isValidId) ?: return null
        val startAt = (data[FIELD_START_AT] as? Timestamp)?.toMillis()?.takeIf { it in SupportedMillis } ?: return null
        val status = BookingStatus.fromKey(data[FIELD_STATUS] as? String) ?: return null
        val place = DoctorMapper.wholeNumber(data[FIELD_QUOTA_PLACE])?.takeIf { it in Booking.QuotaPlaces } ?: return null
        val slotId = data[FIELD_SLOT_ID] as? String ?: return null
        val expectedSlotId = Slots.slotId(doctorId, NepalTime.dateOf(startAt), NepalTime.timeOf(startAt))
        if (slotId != expectedSlotId) return null
        val doctor = parseDoctor(data[FIELD_DOCTOR]) ?: return null
        return Booking(
            id = id,
            doctorId = doctorId,
            doctor = doctor,
            startAtMillis = startAt,
            status = status,
            slotId = slotId,
            quotaPlace = place,
        )
    }

    /**
     * One of [doctorId]'s booked (not cancelled) bookings, for the admin's read-only list: when,
     * and whose (the UID only to look up a first name; null when it isn't a plausible UID).
     */
    fun parseForDoctor(id: String, data: Map<String, Any?>?, doctorId: String): DoctorBookingRow? {
        if (data == null || !isValidId(id)) return null
        if (data[FIELD_DOCTOR_ID] != doctorId) return null
        if (BookingStatus.fromKey(data[FIELD_STATUS] as? String) != BookingStatus.BOOKED) return null
        val startAt = (data[FIELD_START_AT] as? Timestamp)?.toMillis()?.takeIf { it in SupportedMillis } ?: return null
        val patientUid = (data[FIELD_PATIENT_UID] as? String)?.takeIf(PATIENT_UID::matches)
        return DoctorBookingRow(id, patientUid, startAt)
    }

    /** Firebase Auth UIDs (28 characters for its own accounts); never a path. */
    private val PATIENT_UID = Regex("[A-Za-z0-9_-]{1,128}")

    /** The {name, specialty, hospital, feeNpr} snapshot, cleaned the same way as a doctor. */
    private fun parseDoctor(value: Any?): BookedDoctor? {
        val doctor = value as? Map<*, *> ?: return null
        return BookedDoctor(
            name = DoctorMapper.cleanLine(doctor[DoctorMapper.FIELD_NAME], DoctorMapper.MAX_NAME_LENGTH) ?: return null,
            specialty = Specialty.fromKey(doctor[DoctorMapper.FIELD_SPECIALTY] as? String) ?: return null,
            hospital = DoctorMapper.cleanLine(doctor[DoctorMapper.FIELD_HOSPITAL], DoctorMapper.MAX_HOSPITAL_LENGTH) ?: return null,
            feeNpr = DoctorMapper.wholeNumber(doctor[DoctorMapper.FIELD_FEE])?.takeIf { it in 0..DoctorMapper.MAX_FEE_NPR }
                ?: return null,
        )
    }

    private fun Timestamp.toMillis(): Long = seconds * MILLIS_PER_SECOND + nanoseconds / NANOS_PER_MILLI

    /** Instants whose Nepal date a [CalendarDate] can hold (anything else is malformed). */
    private val SupportedMillis: LongRange =
        NepalTime.epochMillis(CalendarDate(CalendarDate.MIN_YEAR, 1, 1), TimeOfDay(0)) until
            NepalTime.epochMillis(CalendarDate(CalendarDate.MAX_YEAR, 12, 31), TimeOfDay(0)) + NepalTime.MILLIS_PER_DAY

    private const val MILLIS_PER_SECOND = 1_000L
    private const val NANOS_PER_MILLI = 1_000_000
}

/** A doctor's booking before the patient's first name is looked up. Stays in the data layer. */
data class DoctorBookingRow(val id: String, val patientUid: String?, val startAtMillis: Long)
