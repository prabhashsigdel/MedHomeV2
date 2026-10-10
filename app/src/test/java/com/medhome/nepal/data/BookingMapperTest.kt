package com.medhome.nepal.data

import com.google.firebase.Timestamp
import com.medhome.nepal.domain.BookingStatus
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.domain.CancelledBy
import com.medhome.nepal.domain.ClinicCancel
import com.medhome.nepal.domain.DoctorAppointment
import com.medhome.nepal.domain.NepalTime
import com.medhome.nepal.domain.Specialty
import com.medhome.nepal.domain.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingMapperTest {

    /** 10:30 on 12 October 2026, Nepal time. */
    private val startAt = NepalTime.epochMillis(CalendarDate(2026, 10, 12), TimeOfDay(10 * 60 + 30))

    private fun valid(): Map<String, Any?> = mapOf(
        "patientUid" to "alice",
        "doctorId" to "doc-001",
        "doctor" to mapOf("name" to "Asha Rai", "specialty" to "cardiology", "hospital" to "Valley Care", "feeNpr" to 800L),
        "startAt" to Timestamp(startAt / 1000, 0),
        "slotId" to "doc-001_20261012_1030",
        "quotaPlace" to 2L,
        "status" to "booked",
        "createdAt" to null,
    )

    private fun parse(data: Map<String, Any?>?, id: String = "abc123", uid: String = "alice") = BookingMapper.parse(id, data, uid)

    @Test
    fun `a well-formed booking parses`() {
        val booking = requireNotNull(parse(valid()))
        assertEquals("abc123", booking.id)
        assertEquals("Asha Rai", booking.doctor.name)
        assertEquals(Specialty.CARDIOLOGY, booking.doctor.specialty)
        assertEquals(800, booking.doctor.feeNpr)
        assertEquals(startAt, booking.startAtMillis)
        assertEquals(CalendarDate(2026, 10, 12), booking.date)
        assertEquals(BookingStatus.BOOKED, booking.status)
        assertEquals(2, booking.quotaPlace)
    }

    @Test
    fun `someone else's booking is never shown`() {
        assertNull(parse(valid(), uid = "bob"))
        assertNull(parse(valid() + ("patientUid" to null)))
    }

    @Test
    fun `malformed ids and fields drop the booking`() {
        assertNull(parse(null))
        assertNull(parse(valid(), id = "a/b"))
        assertNull(parse(valid() + ("doctorId" to "doc_1")))
        assertNull(parse(valid() + ("startAt" to "2026-10-12")))
        assertNull(parse(valid() + ("status" to "done")))
        assertNull(parse(valid() + ("quotaPlace" to 4L)))
        assertNull(parse(valid() + ("doctor" to "Asha")))
        assertNull(parse(valid() + ("doctor" to mapOf("name" to "Asha", "specialty" to "magic", "hospital" to "X", "feeNpr" to 1L))))
        assertNull(parse(valid() + ("doctor" to mapOf("name" to "Asha", "specialty" to "ent", "hospital" to "X", "feeNpr" to -1L))))
    }

    @Test
    fun `a start time outside the supported years drops the booking instead of crashing`() {
        assertNull(parse(valid() + ("startAt" to Timestamp(-1L, 0))))
        assertNull(parse(valid() + ("startAt" to Timestamp(40_000_000_000L, 0))))
    }

    @Test
    fun `the slot id must match the doctor and time`() {
        assertNull(parse(valid() + ("slotId" to "doc-001_20261012_1045")))
        assertNull(parse(valid() + ("slotId" to "doc-002_20261012_1030")))
        assertNull(parse(valid() + ("slotId" to null)))
    }

    @Test
    fun `snapshot text is cleaned like a doctor's`() {
        val doctor = mapOf("name" to "  Asha\u202E Rai ", "specialty" to "ent", "hospital" to "Valley\tCare", "feeNpr" to 500L)
        val booking = requireNotNull(parse(valid() + ("doctor" to doctor)))
        assertEquals("Asha Rai", booking.doctor.name)
        assertEquals("Valley Care", booking.doctor.hospital)
    }

    @Test
    fun `who cancelled is read, and an old cancel without it was the patient's`() {
        assertNull(parse(valid())?.cancelledBy)
        val cancelled = valid() + ("status" to "cancelled")
        assertEquals(CancelledBy.PATIENT, parse(cancelled)?.cancelledBy)
        assertEquals(CancelledBy.PATIENT, parse(cancelled + ("cancelledBy" to "patient"))?.cancelledBy)
        assertEquals(CancelledBy.CLINIC, parse(cancelled + ("cancelledBy" to "clinic"))?.cancelledBy)
        assertNull(parse(cancelled + ("cancelledBy" to "robot"))?.cancelledBy)
        assertNull(parse(cancelled + ("cancelledBy" to 1L))?.cancelledBy)
        // Only a cancelled booking has a canceller.
        assertNull(parse(valid() + ("cancelledBy" to "clinic"))?.cancelledBy)
    }

    private fun forDoctor(
        data: Map<String, Any?>?,
        id: String = "abc123",
        doctorId: String = "doc-001",
        adminUid: String? = "admin-1",
    ) = BookingMapper.parseForDoctor(id, data, doctorId, adminUid)

    @Test
    fun `an admin reads a doctor's booked bookings with the first name they were booked under`() {
        val data = valid() + ("patientName" to "  Sita   Kumari Rai ")
        assertEquals(DoctorAppointment("abc123", startAt, "Sita"), forDoctor(data))
    }

    @Test
    fun `an old booking without a name, or a malformed one, shows no name`() {
        assertEquals(DoctorAppointment("abc123", startAt, null), forDoctor(valid()))
        assertNull(forDoctor(valid() + ("patientName" to 42L))?.patientFirstName)
        assertNull(forDoctor(valid() + ("patientName" to "   "))?.patientFirstName)
        // Malformed is not deleted: only the exact placeholder is.
        assertFalse(requireNotNull(forDoctor(valid() + ("patientName" to "   "))).patientDeleted)
        assertFalse(requireNotNull(forDoctor(valid())).patientDeleted)
    }

    @Test
    fun `a deleted patient's booking shows as deleted, with no name`() {
        val deleted = requireNotNull(forDoctor(valid() + ("patientName" to BookingMapper.DELETED_PATIENT_NAME)))
        assertTrue(deleted.patientDeleted)
        assertNull(deleted.patientFirstName)
        // Cancelled by the clinic too.
        val cancelled = valid() + mapOf("status" to "cancelled", "cancelledBy" to "clinic", "patientName" to "")
        assertTrue(requireNotNull(forDoctor(cancelled)).patientDeleted)
    }

    @Test
    fun `a clinic cancel says whether it was this admin, another or an unrecorded one`() {
        val cancelled = valid() + mapOf("status" to "cancelled", "cancelledBy" to "clinic", "patientName" to "Sita Rai")
        assertEquals(ClinicCancel.BY_YOU, forDoctor(cancelled + ("cancelledByUid" to "admin-1"))?.clinicCancel)
        assertEquals(ClinicCancel.BY_CLINIC, forDoctor(cancelled + ("cancelledByUid" to "admin-2"))?.clinicCancel)
        // Cancelled before the admin was recorded, or not a string.
        assertEquals(ClinicCancel.BY_CLINIC, forDoctor(cancelled)?.clinicCancel)
        assertEquals(ClinicCancel.BY_CLINIC, forDoctor(cancelled + ("cancelledByUid" to 7L))?.clinicCancel)
        // No signed-in admin to compare with: never "you".
        assertEquals(ClinicCancel.BY_CLINIC, forDoctor(cancelled + ("cancelledByUid" to "admin-1"), adminUid = null)?.clinicCancel)
        assertEquals("Sita", forDoctor(cancelled)?.patientFirstName)
        assertNull(forDoctor(valid())?.clinicCancel)
        assertTrue(requireNotNull(forDoctor(valid())).isBooked)
    }

    @Test
    fun `an admin's list skips patient cancels, other doctors' and malformed bookings`() {
        val cancelled = valid() + ("status" to "cancelled")
        // Patient cancels, also from before cancelledBy was stored, and unknown cancellers.
        assertNull(forDoctor(cancelled))
        assertNull(forDoctor(cancelled + ("cancelledBy" to "patient")))
        assertNull(forDoctor(cancelled + ("cancelledBy" to "robot")))
        assertNull(forDoctor(valid() + ("status" to "done")))
        assertNull(forDoctor(valid(), doctorId = "doc-002"))
        assertNull(forDoctor(valid() - "startAt"))
        assertNull(forDoctor(valid(), id = "a/b"))
        assertNull(forDoctor(null))
    }

    @Test
    fun `the patient's own view never carries the admin's uid`() {
        val cancelled = valid() + mapOf("status" to "cancelled", "cancelledBy" to "clinic", "cancelledByUid" to "admin-1")
        val booking = requireNotNull(parse(cancelled))
        assertEquals(CancelledBy.CLINIC, booking.cancelledBy)
        assertFalse(booking.toString().contains("admin-1"))
    }

    @Test
    fun `an admin cancelling reads any patient's booking, but never a path as its uid`() {
        assertEquals("alice", BookingMapper.parseAnyPatient("abc123", valid())?.patientUid)
        assertEquals(2, BookingMapper.parseAnyPatient("abc123", valid())?.booking?.quotaPlace)
        assertNull(BookingMapper.parseAnyPatient("abc123", valid() + ("patientUid" to "../users/x")))
        assertNull(BookingMapper.parseAnyPatient("abc123", valid() + ("patientUid" to 42L)))
        assertNull(BookingMapper.parseAnyPatient("abc123", null))
    }
}
