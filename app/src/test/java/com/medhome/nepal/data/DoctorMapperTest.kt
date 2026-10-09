package com.medhome.nepal.data

import com.medhome.nepal.domain.Specialty
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.domain.TimeRange
import com.medhome.nepal.domain.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoctorMapperTest {

    private fun valid(): MutableMap<String, Any?> = mutableMapOf(
        "active" to true,
        "name" to "Asha Rai",
        "specialty" to "cardiology",
        "hospital" to "Valley Care Hospital",
        "feeNpr" to 800L,
        "experienceYears" to 12L,
        "bio" to "Heart health.",
        "slotMinutes" to 20L,
        "weeklySchedule" to mapOf(
            "sun" to listOf(mapOf("start" to "10:00", "end" to "13:00")),
            "wed" to listOf(mapOf("start" to "15:00", "end" to "18:00"), mapOf("start" to "09:00", "end" to "12:00")),
        ),
    )

    private fun range(start: String, end: String) =
        TimeRange(requireNotNull(TimeOfDay.parse(start)), requireNotNull(TimeOfDay.parse(end)))

    @Test
    fun `a complete active document maps every field`() {
        val doctor = requireNotNull(DoctorMapper.parse("doc-001", valid()))
        assertEquals("doc-001", doctor.id)
        assertEquals("Asha Rai", doctor.name)
        assertEquals(Specialty.CARDIOLOGY, doctor.specialty)
        assertEquals("Valley Care Hospital", doctor.hospital)
        assertEquals(800, doctor.feeNpr)
        assertEquals(12, doctor.experienceYears)
        assertEquals("Heart health.", doctor.bio)
        assertEquals(20, doctor.slotMinutes)
        assertEquals(listOf(range("10:00", "13:00")), doctor.weeklySchedule[Weekday.SUNDAY])
        // Sorted by start time.
        assertEquals(listOf(range("09:00", "12:00"), range("15:00", "18:00")), doctor.weeklySchedule[Weekday.WEDNESDAY])
        assertEquals(listOf(Weekday.SUNDAY, Weekday.WEDNESDAY), doctor.weeklySchedule.keys.toList())
    }

    @Test
    fun `inactive, missing or non-boolean active hides the doctor`() {
        assertNull(DoctorMapper.parse("d", valid().apply { put("active", false) }))
        assertNull(DoctorMapper.parse("d", valid().apply { remove("active") }))
        assertNull(DoctorMapper.parse("d", valid().apply { put("active", "true") }))
        assertNull(DoctorMapper.parse("d", null))
        assertNull(DoctorMapper.parse(" ", valid()))
        assertNull(DoctorMapper.parse("doc_1", valid()))
    }

    @Test
    fun `missing or mistyped required fields hide the doctor without throwing`() {
        for (field in listOf("name", "specialty", "hospital", "feeNpr")) {
            assertNull(field, DoctorMapper.parse("d", valid().apply { remove(field) }))
            assertNull(field, DoctorMapper.parse("d", valid().apply { put(field, listOf(1, 2)) }))
        }
        assertNull(DoctorMapper.parse("d", valid().apply { put("name", "   ") }))
        assertNull(DoctorMapper.parse("d", valid().apply { put("specialty", "astrology") }))
    }

    @Test
    fun `fees must be whole rupees within range`() {
        assertNull(DoctorMapper.parse("d", valid().apply { put("feeNpr", -1L) }))
        assertNull(DoctorMapper.parse("d", valid().apply { put("feeNpr", 100_001L) }))
        assertNull(DoctorMapper.parse("d", valid().apply { put("feeNpr", 800.5) }))
        assertNull(DoctorMapper.parse("d", valid().apply { put("feeNpr", "800") }))
        assertEquals(900, DoctorMapper.parse("d", valid().apply { put("feeNpr", 900.0) })?.feeNpr)
    }

    @Test
    fun `optional fields fall back instead of hiding the doctor`() {
        val doctor = requireNotNull(
            DoctorMapper.parse(
                "d",
                valid().apply {
                    put("experienceYears", 200L)
                    put("bio", 42L)
                    put("slotMinutes", 1L)
                    put("weeklySchedule", "every day")
                },
            ),
        )
        assertNull(doctor.experienceYears)
        assertEquals("", doctor.bio)
        assertEquals(DoctorMapper.DEFAULT_SLOT_MINUTES, doctor.slotMinutes)
        assertTrue(doctor.weeklySchedule.isEmpty())
    }

    @Test
    fun `bad schedule entries are dropped one by one`() {
        val schedule = DoctorMapper.parseSchedule(
            mapOf(
                "sun" to listOf(
                    mapOf("start" to "10:00", "end" to "13:00"),
                    mapOf("start" to "12:00", "end" to "14:00"), // overlaps the first: dropped
                    mapOf("start" to "18:00", "end" to "17:00"), // ends before it starts
                    mapOf("start" to "25:00", "end" to "26:00"), // not a time
                    mapOf("start" to "9:00", "end" to "10:00"), // not HH:mm
                    "10:00-11:00", // not a map
                ),
                "funday" to listOf(mapOf("start" to "10:00", "end" to "11:00")),
                "mon" to "closed",
                "tue" to listOf(mapOf("start" to "20:00", "end" to "23:59")),
                42 to listOf(mapOf("start" to "10:00", "end" to "11:00")),
            ),
        )
        assertEquals(mapOf(Weekday.SUNDAY to listOf(range("10:00", "13:00")), Weekday.TUESDAY to listOf(range("20:00", "23:59"))), schedule)
    }

    @Test
    fun `text is cleaned of invisible characters and capped`() {
        val doctor = requireNotNull(
            DoctorMapper.parse(
                "d",
                valid().apply {
                    // A right-to-left override would let a name display reversed (spoofing).
                    put("name", "  Asha‮  Rai\u0000\t ")
                    put("hospital", "H".repeat(500))
                    put("bio", "Line one.\r\n\n\n\n\nLine two.​")
                },
            ),
        )
        assertEquals("Asha Rai", doctor.name)
        assertEquals(DoctorMapper.MAX_HOSPITAL_LENGTH, doctor.hospital.length)
        assertEquals("Line one.\n\nLine two.", doctor.bio)
    }

    @Test
    fun `capping text never splits an emoji, and line separators count as spaces`() {
        val name = "a".repeat(DoctorMapper.MAX_NAME_LENGTH - 1) + "\uD83D\uDE00"
        val parsed = requireNotNull(DoctorMapper.parse("d", valid().apply { put("name", name) })).name
        assertEquals("a".repeat(DoctorMapper.MAX_NAME_LENGTH - 1), parsed)
        assertEquals("Asha Rai", DoctorMapper.parse("d", valid().apply { put("name", "Asha\u2028Rai") })?.name)
    }

    @Test
    fun `Devanagari names keep their joiners`() {
        val name = "क्‍ष"
        assertEquals(name, DoctorMapper.parse("d", valid().apply { put("name", name) })?.name)
        assertNotNull(DoctorMapper.parse("d", valid().apply { put("name", "सीता शर्मा") }))
    }
}
