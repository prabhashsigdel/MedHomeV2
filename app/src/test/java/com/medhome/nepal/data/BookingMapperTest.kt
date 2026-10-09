package com.medhome.nepal.data

import com.google.firebase.Timestamp
import com.medhome.nepal.domain.BookingStatus
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.domain.NepalTime
import com.medhome.nepal.domain.Specialty
import com.medhome.nepal.domain.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `an admin reads a doctor's booked bookings with the patient's uid only`() {
        assertEquals(DoctorBookingRow("abc123", "alice", startAt), BookingMapper.parseForDoctor("abc123", valid(), "doc-001"))
    }

    @Test
    fun `an admin's list skips cancelled, other doctors' and malformed bookings`() {
        assertNull(BookingMapper.parseForDoctor("abc123", valid() + ("status" to "cancelled"), "doc-001"))
        assertNull(BookingMapper.parseForDoctor("abc123", valid(), "doc-002"))
        assertNull(BookingMapper.parseForDoctor("abc123", valid() - "startAt", "doc-001"))
        assertNull(BookingMapper.parseForDoctor("a/b", valid(), "doc-001"))
    }

    @Test
    fun `a patient uid that could be a path is dropped, not followed`() {
        assertNull(BookingMapper.parseForDoctor("abc123", valid() + ("patientUid" to "../users/x"), "doc-001")?.patientUid)
        assertNull(BookingMapper.parseForDoctor("abc123", valid() + ("patientUid" to 42L), "doc-001")?.patientUid)
    }
}
