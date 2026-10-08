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
}
