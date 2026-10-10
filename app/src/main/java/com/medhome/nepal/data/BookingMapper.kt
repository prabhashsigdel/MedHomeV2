package com.medhome.nepal.data

import com.google.firebase.Timestamp
import com.medhome.nepal.domain.AdminBooking
import com.medhome.nepal.domain.AdminCancelledBy
import com.medhome.nepal.domain.BookedDoctor
import com.medhome.nepal.domain.Booking
import com.medhome.nepal.domain.BookingStatus
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.domain.CancelReason
import com.medhome.nepal.domain.CancelledBy
import com.medhome.nepal.domain.ClinicCancelReason
import com.medhome.nepal.domain.ClinicCancel
import com.medhome.nepal.domain.Doctor
import com.medhome.nepal.domain.DoctorAppointment
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
    const val FIELD_CANCELLED_BY = "cancelledBy"

    /** Why the clinic cancelled ([CancelReason.key]); clinic cancels only. */
    const val FIELD_CANCEL_REASON = "cancelReason"

    /** The clinic's optional note to the patient: one line of plain text. */
    const val FIELD_CANCEL_NOTE = "cancelNote"

    /** Which admin cancelled for the clinic (their uid). Never shown to patients. */
    const val FIELD_CANCELLED_BY_UID = "cancelledByUid"

    /** The patient's profile name when they booked (the rules check it), for the admin's list. */
    const val FIELD_PATIENT_NAME = "patientName"

    /**
     * What [FIELD_PATIENT_NAME] becomes when the patient deletes their account. Blank, because no
     * profile name can be (firestore.rules `hasValidName`), so it never stands for a real name;
     * the rules allow a patient to set exactly this on their own bookings and nothing else.
     */
    const val DELETED_PATIENT_NAME = ""

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
            cancelledBy = if (status == BookingStatus.CANCELLED) cancelledByOf(data[FIELD_CANCELLED_BY]) else null,
            clinicReason = clinicReasonOf(status, data),
        )
    }

    /**
     * The clinic's reason, for a booking the clinic cancelled; null otherwise, for cancels from
     * before reasons, and for an unknown code (the screens then say only who cancelled). The note
     * is cleaned like any other text and never comes without a reason.
     */
    private fun clinicReasonOf(status: BookingStatus, data: Map<String, Any?>): ClinicCancelReason? {
        if (status != BookingStatus.CANCELLED) return null
        if (CancelledBy.fromKey(data[FIELD_CANCELLED_BY] as? String) != CancelledBy.CLINIC) return null
        val reason = CancelReason.fromKey(data[FIELD_CANCEL_REASON] as? String) ?: return null
        return ClinicCancelReason(reason, cleanNote(data[FIELD_CANCEL_NOTE]))
    }

    /** A clinic note as stored and shown: one line of visible text, at most 150 characters. */
    fun cleanNote(value: Any?): String? = DoctorMapper.cleanLine(value, ClinicCancelReason.MAX_NOTE_LENGTH)

    /**
     * Only patients could cancel before `cancelledBy` was stored, so a cancelled booking without
     * it was cancelled by the patient. An unknown value stays unknown (null).
     */
    private fun cancelledByOf(value: Any?): CancelledBy? =
        if (value == null) CancelledBy.PATIENT else CancelledBy.fromKey(value as? String)

    /**
     * One of [doctorId]'s booked or clinic-cancelled bookings, for the admin's list: when, the
     * first word of the name it was booked under (null when missing, malformed or deleted), and
     * whether [adminUid] or another admin cancelled it. Nothing else about the patient, and no
     * admin's uid, leaves this function. Bookings the patient cancelled are null.
     */
    fun parseForDoctor(id: String, data: Map<String, Any?>?, doctorId: String, adminUid: String?): DoctorAppointment? {
        if (data == null || !isValidId(id)) return null
        if (data[FIELD_DOCTOR_ID] != doctorId) return null
        val clinicCancel = when (BookingStatus.fromKey(data[FIELD_STATUS] as? String)) {
            BookingStatus.BOOKED -> null
            BookingStatus.CANCELLED -> clinicCancelOf(data, adminUid) ?: return null
            null -> return null
        }
        val startAt = (data[FIELD_START_AT] as? Timestamp)?.toMillis()?.takeIf { it in SupportedMillis } ?: return null
        val name = data[FIELD_PATIENT_NAME]
        val deleted = name == DELETED_PATIENT_NAME
        return DoctorAppointment(
            bookingId = id,
            startAtMillis = startAt,
            patientFirstName = if (deleted) null else firstWord(name),
            patientDeleted = deleted,
            clinicCancel = clinicCancel,
        )
    }

    /** Null unless cancelled by the clinic; a cancel without the admin's uid predates the field. */
    private fun clinicCancelOf(data: Map<String, Any?>, adminUid: String?): ClinicCancel? {
        if (CancelledBy.fromKey(data[FIELD_CANCELLED_BY] as? String) != CancelledBy.CLINIC) return null
        val by = data[FIELD_CANCELLED_BY_UID] as? String
        return if (adminUid != null && by == adminUid) ClinicCancel.BY_YOU else ClinicCancel.BY_CLINIC
    }

    /**
     * Any doctor's booking for the admin's bookings tab, or null when malformed: when, the doctor
     * as booked, the first word of the patient's name (as [parseForDoctor]), and for cancelled
     * ones who cancelled ([adminUid] tells "you" from another admin) and when. The patient's UID
     * and the cancelling admin's uid never leave this function.
     */
    fun parseForAdmin(id: String, data: Map<String, Any?>?, adminUid: String?): AdminBooking? {
        if (data == null || !isValidId(id)) return null
        val doctorId = (data[FIELD_DOCTOR_ID] as? String)?.takeIf(Doctor::isValidId) ?: return null
        val startAt = (data[FIELD_START_AT] as? Timestamp)?.toMillis()?.takeIf { it in SupportedMillis } ?: return null
        val status = BookingStatus.fromKey(data[FIELD_STATUS] as? String) ?: return null
        val doctor = parseDoctor(data[FIELD_DOCTOR]) ?: return null
        val name = data[FIELD_PATIENT_NAME]
        val deleted = name == DELETED_PATIENT_NAME
        val cancelled = status == BookingStatus.CANCELLED
        return AdminBooking(
            bookingId = id,
            doctorId = doctorId,
            doctor = doctor,
            startAtMillis = startAt,
            patientFirstName = if (deleted) null else firstWord(name),
            patientDeleted = deleted,
            status = status,
            cancelledBy = if (cancelled) adminCancelledByOf(data, adminUid) else null,
            cancelledAtMillis = if (cancelled) (data[FIELD_CANCELLED_AT] as? Timestamp)?.toMillis() else null,
            clinicReason = clinicReasonOf(status, data),
        )
    }

    /** As [cancelledByOf]; a clinic cancel names its admin, except those from before the field. */
    private fun adminCancelledByOf(data: Map<String, Any?>, adminUid: String?): AdminCancelledBy? =
        when (cancelledByOf(data[FIELD_CANCELLED_BY])) {
            CancelledBy.PATIENT -> AdminCancelledBy.PATIENT
            CancelledBy.CLINIC -> when (val by = data[FIELD_CANCELLED_BY_UID] as? String) {
                null -> AdminCancelledBy.CLINIC
                adminUid -> AdminCancelledBy.YOU
                else -> AdminCancelledBy.ANOTHER_ADMIN
            }
            null -> null
        }

    /** Any patient's booking with the patient's UID, for an admin cancelling it; null when malformed. */
    fun parseAnyPatient(id: String, data: Map<String, Any?>?): PatientBooking? {
        val patientUid = (data?.get(FIELD_PATIENT_UID) as? String)?.takeIf(PATIENT_UID::matches) ?: return null
        return parse(id, data, patientUid)?.let { PatientBooking(patientUid, it) }
    }

    /** Firebase Auth UIDs (28 characters for its own accounts); never a path. */
    private val PATIENT_UID = Regex("[A-Za-z0-9_-]{1,128}")

    private fun firstWord(name: Any?): String? =
        DoctorMapper.cleanLine(name, DoctorMapper.MAX_NAME_LENGTH)?.substringBefore(' ')?.takeIf { it.isNotEmpty() }

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

/** A booking and whose it is. Stays in the data layer (admins never see the UID). */
data class PatientBooking(val patientUid: String, val booking: Booking)
