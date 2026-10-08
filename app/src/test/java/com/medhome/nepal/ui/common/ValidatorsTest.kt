package com.medhome.nepal.ui.common

import com.medhome.nepal.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {

    @Test
    fun `email must be present and well formed`() {
        assertEquals(R.string.validation_email_required, Validators.emailError(""))
        assertEquals(R.string.validation_email_invalid, Validators.emailError("not-an-email"))
        assertNull(Validators.emailError("asha@example.com"))
    }

    @Test
    fun `new passwords need at least 8 characters`() {
        assertEquals(R.string.validation_password_required, Validators.newPasswordError(""))
        assertEquals(R.string.validation_password_too_short, Validators.newPasswordError("1234567"))
        assertNull(Validators.newPasswordError("12345678"))
    }

    @Test
    fun `login only requires a password to be present`() {
        assertEquals(R.string.validation_password_required, Validators.loginPasswordError(""))
        assertNull(Validators.loginPasswordError("short"))
    }

    @Test
    fun `confirm password must match`() {
        assertEquals(R.string.validation_password_mismatch, Validators.confirmPasswordError("abcdefgh", "abcdefgx"))
        assertNull(Validators.confirmPasswordError("abcdefgh", "abcdefgh"))
    }

    @Test
    fun `name must not be blank`() {
        assertEquals(R.string.validation_name_required, Validators.nameError("   "))
        assertNull(Validators.nameError("Asha"))
        assertEquals(R.string.validation_name_too_long, Validators.nameError("a".repeat(101)))
    }
}
