package com.medhome.nepal.ui.settings

import com.medhome.nepal.R
import com.medhome.nepal.data.ThemeMode
import com.medhome.nepal.domain.Gender
import com.medhome.nepal.ui.common.BirthDate
import com.medhome.nepal.ui.common.Validators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLogicTest {

    @Test
    fun `theme mode resolves against the phone setting only for System`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemIsDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemIsDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemIsDark = true))
        assertTrue(ThemeMode.DARK.isDark(systemIsDark = false))
    }

    @Test
    fun `stored theme keys round trip and unknown values fall back to System`() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromKey(it.key)) }
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("sepia"))
    }

    @Test
    fun `gender is stored as a fixed key`() {
        assertEquals("male", Gender.MALE.key)
        Gender.entries.forEach { assertEquals(it, Gender.fromKey(it.key)) }
        assertNull(Gender.fromKey("पुरुष"))
        assertNull(Gender.fromKey(null))
    }

    @Test
    fun `valid Nepali mobile numbers are normalized to 10 digits`() {
        assertEquals("9841234567", Validators.normalizePhone("9841234567"))
        assertEquals("9801234567", Validators.normalizePhone("980-123 4567"))
        assertEquals("9612345678", Validators.normalizePhone("+977 961 234 5678"))
        assertEquals("9712345678", Validators.normalizePhone("977-9712345678"))
    }

    @Test
    fun `invalid phone numbers are rejected`() {
        assertNull(Validators.normalizePhone("984123456"))
        assertNull(Validators.normalizePhone("98412345678"))
        assertNull(Validators.normalizePhone("9512345678"))
        assertNull(Validators.normalizePhone("014123456"))
        assertNull(Validators.normalizePhone("+1 984 123 4567"))
    }

    @Test
    fun `phone is optional but must be valid when given`() {
        assertNull(Validators.phoneError(""))
        assertNull(Validators.phoneError("  "))
        assertNull(Validators.phoneError("9841234567"))
        assertEquals(R.string.validation_phone_invalid, Validators.phoneError("12345"))
    }

    @Test
    fun `birth dates round trip as ISO text in UTC`() {
        val millis = BirthDate.toUtcMillis("2001-03-09")
        assertEquals("2001-03-09", millis?.let(BirthDate::toIso))
        assertNull(BirthDate.toUtcMillis("2001-02-30"))
        assertNull(BirthDate.toUtcMillis("not a date"))
    }

    @Test
    fun `birth date must be in the past and within 120 years`() {
        val today = checkNotNull(BirthDate.toUtcMillis("2026-10-08"))
        assertNull(BirthDate.error(checkNotNull(BirthDate.toUtcMillis("2000-01-01")), today))
        assertNull(BirthDate.error(today, today))
        assertEquals(
            R.string.validation_date_of_birth_invalid,
            BirthDate.error(checkNotNull(BirthDate.toUtcMillis("2026-10-09")), today),
        )
        assertEquals(
            R.string.validation_date_of_birth_invalid,
            BirthDate.error(checkNotNull(BirthDate.toUtcMillis("1906-10-07")), today),
        )
    }

    @Test
    fun `a new password must differ from the current one`() {
        assertEquals(R.string.validation_password_same, Validators.newPasswordDifferentError("password123", "password123"))
        assertNull(Validators.newPasswordDifferentError("password123", "password456"))
    }
}
