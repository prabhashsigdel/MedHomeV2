package com.medhome.nepal.ui.reminders

import com.medhome.nepal.R
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.domain.MedicineDays
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.domain.Weekday
import com.medhome.nepal.fakes.medicine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicineFormTest {

    private val today = CalendarDate(2026, 10, 9)
    private val valid = MedicineForm.newFields(today).copy(name = "Paracetamol", dose = "1 tablet")

    private fun hours(vararg h: Int) = h.map { TimeOfDay(it * 60) }

    @Test
    fun `a filled-in form makes a medicine with cleaned text and sorted times`() {
        val fields = valid.copy(name = "  Para\tcetamol \n 500 ", times = hours(20, 8))
        val medicine = MedicineForm.toMedicine(0, fields)
        assertNotNull(medicine)
        assertEquals("Para cetamol 500", medicine!!.name)
        assertEquals(hours(8, 20), medicine.times)
        assertEquals(MedicineDays.EveryDay, medicine.days)
    }

    @Test
    fun `each field reports its own problem`() {
        val errors = MedicineForm.errors(
            valid.copy(
                name = "   ",
                dose = "x".repeat(41),
                times = hours(8, 8),
                everyDay = false,
                days = emptySet(),
                endDate = today.plusDays(-1),
            ),
        )
        assertEquals(R.string.medicine_error_name_required, errors.name)
        assertEquals(R.string.medicine_error_dose_long, errors.dose)
        assertEquals(R.string.medicine_error_times_same, errors.times)
        assertEquals(R.string.medicine_error_days, errors.days)
        assertEquals(R.string.medicine_error_end_before_start, errors.endDate)
        assertNull(MedicineForm.toMedicine(0, valid.copy(name = "")))
    }

    @Test
    fun `names over 60 characters are refused, 60 is fine`() {
        assertEquals(R.string.medicine_error_name_long, MedicineForm.errors(valid.copy(name = "a".repeat(61))).name)
        assertTrue(MedicineForm.errors(valid.copy(name = "a".repeat(60))).isEmpty)
    }

    @Test
    fun `chosen days make a weekday medicine`() {
        val fields = MedicineForm.withDayToggled(MedicineForm.withDayToggled(valid.copy(everyDay = false), Weekday.MONDAY), Weekday.FRIDAY)
        assertEquals(MedicineDays.Chosen(setOf(Weekday.MONDAY, Weekday.FRIDAY)), MedicineForm.toMedicine(0, fields)?.days)
        assertEquals(setOf(Weekday.FRIDAY), MedicineForm.withDayToggled(fields, Weekday.MONDAY).days)
    }

    @Test
    fun `more times a day keeps the times already set and adds unused suggestions`() {
        val custom = valid.copy(times = hours(9))
        val three = MedicineForm.withTimesPerDay(custom, 3)
        assertEquals(3, three.times.size)
        assertTrue(TimeOfDay(9 * 60) in three.times)
        assertEquals(three.times.distinct().sorted(), three.times)
        (1..6).forEach { count -> assertEquals(count, MedicineForm.withTimesPerDay(three, count).times.distinct().size) }
    }

    @Test
    fun `fewer times a day keeps the earliest`() {
        assertEquals(hours(7, 9), MedicineForm.withTimesPerDay(valid.copy(times = hours(21, 7, 9)), 2).times)
    }

    @Test
    fun `an end date defaults to a week's course and can be removed`() {
        val withEnd = MedicineForm.withEndDate(valid, hasEnd = true)
        assertEquals(today.plusDays(6), withEnd.endDate)
        assertNull(MedicineForm.withEndDate(withEnd, hasEnd = false).endDate)
    }

    @Test
    fun `editing loads every field back`() {
        val med = medicine(times = hours(8, 20), days = MedicineDays.Chosen(setOf(Weekday.SUNDAY)), startDate = today, endDate = today.plusDays(3))
        assertEquals(med, MedicineForm.toMedicine(med.id, MedicineForm.fieldsOf(med)))
    }

    @Test
    fun `a time can be changed by its position only`() {
        assertEquals(hours(8, 21), MedicineForm.withTime(valid.copy(times = hours(8, 20)), 1, TimeOfDay(21 * 60)).times)
        assertEquals(hours(8), MedicineForm.withTime(valid.copy(times = hours(8)), 4, TimeOfDay(0)).times)
    }
}
