package com.medhome.nepal.ui.doctors

import com.medhome.nepal.domain.Specialty
import com.medhome.nepal.fakes.doctor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoctorFilterTest {

    private val asha = doctor(id = "1", name = "Asha Rai", specialty = Specialty.CARDIOLOGY)
    private val bikash = doctor(id = "2", name = "Bikash Thapa", specialty = Specialty.DERMATOLOGY)
    private val sita = doctor(id = "3", name = "सीता शर्मा", specialty = Specialty.CARDIOLOGY)
    private val all = listOf(asha, bikash, sita)

    @Test
    fun `no filter shows everyone`() {
        assertEquals(all, all.matching(DoctorFilter()))
        assertFalse(DoctorFilter(query = "   ").isActive)
    }

    @Test
    fun `search ignores case, extra spaces and word order`() {
        assertEquals(listOf(asha), all.matching(DoctorFilter(query = "  ASHA ")))
        assertEquals(listOf(asha), all.matching(DoctorFilter(query = "rai   asha")))
        assertEquals(listOf(bikash), all.matching(DoctorFilter(query = "tha")))
        assertEquals(emptyList<Any>(), all.matching(DoctorFilter(query = "asha thapa")))
    }

    @Test
    fun `search works for Devanagari names`() {
        assertEquals(listOf(sita), all.matching(DoctorFilter(query = "शर्मा")))
    }

    @Test
    fun `a specialty narrows the list and combines with the search`() {
        assertEquals(listOf(asha, sita), all.matching(DoctorFilter(specialty = Specialty.CARDIOLOGY)))
        assertEquals(listOf(asha), all.matching(DoctorFilter(query = "asha", specialty = Specialty.CARDIOLOGY)))
        assertEquals(emptyList<Any>(), all.matching(DoctorFilter(query = "asha", specialty = Specialty.DERMATOLOGY)))
        assertTrue(DoctorFilter(specialty = Specialty.CARDIOLOGY).isActive)
    }

    @Test
    fun `chips list only specialties some doctor has, in the fixed order`() {
        assertEquals(listOf(Specialty.CARDIOLOGY, Specialty.DERMATOLOGY), listOf(bikash, asha, sita).specialties())
        assertEquals(emptyList<Specialty>(), emptyList<com.medhome.nepal.domain.Doctor>().specialties())
    }
}
