package com.medhome.nepal.ui.common

import androidx.annotation.StringRes
import com.medhome.nepal.R

object Validators {
    const val MIN_PASSWORD_LENGTH = 8

    /** Must match hasValidName() in firestore.rules. */
    const val MAX_NAME_LENGTH = 100

    // Deliberately loose: Firebase is the real authority on email validity.
    private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    @StringRes
    fun nameError(name: String): Int? = when {
        name.isBlank() -> R.string.validation_name_required
        name.length > MAX_NAME_LENGTH -> R.string.validation_name_too_long
        else -> null
    }

    @StringRes
    fun emailError(email: String): Int? = when {
        email.isBlank() -> R.string.validation_email_required
        !EMAIL_PATTERN.matches(email) -> R.string.validation_email_invalid
        else -> null
    }

    /** Sign-in only checks presence; the minimum length applies to new passwords. */
    @StringRes
    fun loginPasswordError(password: String): Int? =
        if (password.isEmpty()) R.string.validation_password_required else null

    @StringRes
    fun newPasswordError(password: String): Int? = when {
        password.isEmpty() -> R.string.validation_password_required
        password.length < MIN_PASSWORD_LENGTH -> R.string.validation_password_too_short
        else -> null
    }

    @StringRes
    fun confirmPasswordError(password: String, confirm: String): Int? =
        if (password != confirm) R.string.validation_password_mismatch else null

    /** Nepali mobile numbers: 10 digits starting with 96, 97 or 98. Must match firestore.rules. */
    private val NEPALI_MOBILE = Regex("^9[678][0-9]{8}$")
    private const val NEPAL_COUNTRY_CODE = "977"
    private const val MOBILE_DIGITS = 10

    /**
     * The number as stored: digits only, without a +977 prefix. Null if it isn't a valid Nepali
     * mobile number. Spaces, dashes and brackets are ignored.
     */
    fun normalizePhone(input: String): String? {
        var digits = input.filter { it.isDigit() }
        if (digits.length == NEPAL_COUNTRY_CODE.length + MOBILE_DIGITS && digits.startsWith(NEPAL_COUNTRY_CODE)) {
            digits = digits.removePrefix(NEPAL_COUNTRY_CODE)
        }
        return digits.takeIf { NEPALI_MOBILE.matches(it) }
    }

    /** Phone is optional: blank is fine, anything else must be a Nepali mobile number. */
    @StringRes
    fun phoneError(input: String): Int? = when {
        input.isBlank() -> null
        normalizePhone(input) == null -> R.string.validation_phone_invalid
        else -> null
    }

    @StringRes
    fun newPasswordDifferentError(current: String, new: String): Int? =
        if (current.isNotEmpty() && current == new) R.string.validation_password_same else null
}
