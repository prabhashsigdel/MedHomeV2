package com.medhome.nepal.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.Gender
import com.medhome.nepal.domain.ProfileDetails
import com.medhome.nepal.domain.UserProfile
import com.medhome.nepal.session.SessionManager
import com.medhome.nepal.ui.common.BirthDate
import com.medhome.nepal.ui.common.Validators
import com.medhome.nepal.ui.common.runAuthAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditProfileUiState(
    val name: String,
    val phone: String,
    /** ISO "YYYY-MM-DD", or null when not set. */
    val dateOfBirth: String?,
    val gender: Gender?,
    @param:StringRes val nameError: Int? = null,
    @param:StringRes val phoneError: Int? = null,
    @param:StringRes val dateOfBirthError: Int? = null,
    val showDatePicker: Boolean = false,
    val isSaving: Boolean = false,
    val error: AuthError? = null,
    val saved: Boolean = false,
)

/**
 * Edits name, phone, date of birth and gender. Phone is stored normalized (10 digits), the date
 * as ISO text and gender as a fixed key. [todayUtcMillis] is injectable for tests.
 */
class EditProfileViewModel(
    private val session: SessionManager,
    profile: UserProfile,
    private val todayUtcMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EditProfileUiState(
            name = profile.name,
            phone = profile.phone.orEmpty(),
            dateOfBirth = profile.dateOfBirth,
            gender = profile.gender,
        ),
    )
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, nameError = null) }

    fun onPhoneChange(value: String) = _uiState.update { it.copy(phone = value, phoneError = null) }

    fun onGenderChange(value: Gender?) = _uiState.update { it.copy(gender = value) }

    fun openDatePicker() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(showDatePicker = true) }
    }

    fun dismissDatePicker() = _uiState.update { it.copy(showDatePicker = false) }

    /** From the date picker, in UTC midnight milliseconds. */
    fun onDateSelected(utcMillis: Long) {
        val error = BirthDate.error(utcMillis, todayUtcMillis())
        _uiState.update {
            if (error != null) {
                it.copy(showDatePicker = false, dateOfBirthError = error)
            } else {
                it.copy(showDatePicker = false, dateOfBirth = BirthDate.toIso(utcMillis), dateOfBirthError = null)
            }
        }
    }

    fun clearDateOfBirth() = _uiState.update { it.copy(dateOfBirth = null, dateOfBirthError = null) }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun save() {
        val current = _uiState.value
        if (current.isSaving) return
        val name = current.name.trim()
        val nameError = Validators.nameError(name)
        val phoneError = Validators.phoneError(current.phone)
        if (nameError != null || phoneError != null) {
            _uiState.update { it.copy(nameError = nameError, phoneError = phoneError) }
            return
        }
        val details = ProfileDetails(
            name = name,
            phone = current.phone.takeIf { it.isNotBlank() }?.let(Validators::normalizePhone),
            dateOfBirth = current.dateOfBirth,
            gender = current.gender,
        )
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val error = runAuthAction { session.updateProfile(details) }
            _uiState.update { it.copy(isSaving = false, error = error, saved = error == null) }
        }
    }
}
