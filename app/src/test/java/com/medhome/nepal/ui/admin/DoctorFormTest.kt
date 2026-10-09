package com.medhome.nepal.ui.admin

import com.medhome.nepal.R
import com.medhome.nepal.domain.Specialty
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.domain.Weekday
import com.medhome.nepal.fakes.doctor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoctorFormTest {

    private val valid = DoctorFormFields(
        name = "Asha Rai",
        specialty = Specialty.CARDIOLOGY,
        hospital = "Valley Care Hospital",
        fee = "800",
        experience = "12",
        bio = "Heart health.",
        slotMinutes = "15",
        schedule = mapOf(Weekday.SUNDAY to listOf(RangeInput(TimeOfDay(600), TimeOfDay(780)))),
    )

    @Test
    fun `a complete form has no errors and becomes a doctor`() {
        assertTrue(DoctorForm.validate(valid).isEmpty)
        val saved = requireNotNull(DoctorForm.toDoctor("doc-abc", valid))
        assertEquals("doc-abc", saved.id)
        assertEquals(800, saved.feeNpr)
        assertEquals(12, saved.experienceYears)
        assertEquals(15, saved.slotMinutes)
        assertEquals(setOf(Weekday.SUNDAY), saved.weeklySchedule.keys)
    }

    @Test
    fun `an empty form asks for every required field`() {
        val errors = DoctorForm.validate(DoctorFormFields(slotMinutes = ""))
        assertEquals(R.string.admin_error_required, errors.name)
        assertEquals(R.string.admin_error_specialty, errors.specialty)
        assertEquals(R.string.admin_error_required, errors.hospital)
        assertEquals(R.string.admin_error_fee, errors.fee)
        assertEquals(R.string.admin_error_experience, errors.experience)
        assertEquals(R.string.admin_error_required, errors.bio)
        assertEquals(R.string.admin_error_slot_minutes, errors.slotMinutes)
        assertNull(DoctorForm.toDoctor("doc-abc", DoctorFormFields()))
    }

    @Test
    fun `text is cleaned before it is checked and saved`() {
        val fields = valid.copy(name = "  Asha ‮  Rai ", hospital = "Valley\tCare", bio = "Line one  \n\n\n\nLine two ")
        val saved = requireNotNull(DoctorForm.toDoctor("doc-abc", fields))
        assertEquals("Asha Rai", saved.name)
        assertEquals("Valley Care", saved.hospital)
        assertEquals("Line one\n\nLine two", saved.bio)
        assertEquals(R.string.admin_error_required, DoctorForm.validate(valid.copy(name = " ​ ")).name)
    }

    @Test
    fun `over-long text is reported, never cut`() {
        assertNull(DoctorForm.validate(valid.copy(name = "n".repeat(100))).name)
        assertEquals(R.string.admin_error_name_too_long, DoctorForm.validate(valid.copy(name = "n".repeat(101))).name)
        assertEquals(R.string.admin_error_hospital_too_long, DoctorForm.validate(valid.copy(hospital = "h".repeat(121))).hospital)
        assertEquals(R.string.admin_error_bio_too_long, DoctorForm.validate(valid.copy(bio = "b".repeat(2001))).bio)
    }

    @Test
    fun `numbers must be whole and in range`() {
        assertNull(DoctorForm.validate(valid.copy(fee = "0")).fee)
        assertNull(DoctorForm.validate(valid.copy(fee = "100000")).fee)
        assertEquals(R.string.admin_error_fee, DoctorForm.validate(valid.copy(fee = "100001")).fee)
        assertEquals(R.string.admin_error_fee, DoctorForm.validate(valid.copy(fee = "8.5")).fee)
        assertEquals(R.string.admin_error_fee, DoctorForm.validate(valid.copy(fee = "-1")).fee)
        assertEquals(R.string.admin_error_experience, DoctorForm.validate(valid.copy(experience = "71")).experience)
        assertEquals(R.string.admin_error_slot_minutes, DoctorForm.validate(valid.copy(slotMinutes = "4")).slotMinutes)
        assertEquals(R.string.admin_error_slot_minutes, DoctorForm.validate(valid.copy(slotMinutes = "241")).slotMinutes)
    }

    @Test
    fun `nepali digits are read as numbers`() {
        assertEquals(800, DoctorForm.wholeNumber("८००"))
        assertEquals(15, DoctorForm.wholeNumber(" 15 "))
        assertNull(DoctorForm.wholeNumber(""))
        assertNull(DoctorForm.wholeNumber("1234567"))
    }

    @Test
    fun `number fields keep digits only`() {
        assertEquals("800", DoctorForm.numberInput("8,0 0"))
        assertEquals("123456", DoctorForm.numberInput("12345678"))
    }

    @Test
    fun `a schedule problem blocks saving`() {
        val bad = valid.copy(schedule = mapOf(Weekday.MONDAY to listOf(RangeInput(TimeOfDay(780), TimeOfDay(600)))))
        assertTrue(DoctorForm.validate(bad).schedule)
        assertNull(DoctorForm.toDoctor("doc-abc", bad))
    }

    @Test
    fun `an invalid id is never saved`() {
        assertNull(DoctorForm.toDoctor("doc_001", valid))
    }

    @Test
    fun `an existing doctor fills the form and saves back the same`() {
        val original = doctor(id = "doc-001")
        assertEquals(original, DoctorForm.toDoctor("doc-001", DoctorForm.fieldsOf(original)))
    }
}
