package com.medhome.nepal.ui.admin

import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Test

/** The ViewModels read the doctor's ID by name: a renamed route property would crash at runtime. */
class AdminRoutesTest {

    @Test
    fun `routes carry the doctor id under the name the ViewModels read`() {
        assertEquals(AdminViewModels.ARG_DOCTOR_ID, serializer<AdminDoctorRoute>().descriptor.getElementName(0))
        assertEquals(AdminViewModels.ARG_DOCTOR_ID, serializer<DoctorFormRoute>().descriptor.getElementName(0))
    }
}
