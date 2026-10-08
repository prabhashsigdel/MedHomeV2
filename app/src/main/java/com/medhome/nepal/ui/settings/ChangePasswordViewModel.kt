package com.medhome.nepal.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medhome.nepal.R
import com.medhome.nepal.data.PasswordSaveOffers
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.session.SessionManager
import com.medhome.nepal.ui.common.Validators
import com.medhome.nepal.ui.common.runAuthAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChangePasswordUiState(
    val current: String = "",
    val new: String = "",
    val confirm: String = "",
    @param:StringRes val currentError: Int? = null,
    @param:StringRes val newError: Int? = null,
    @param:StringRes val confirmError: Int? = null,
    val isSaving: Boolean = false,
    val error: AuthError? = null,
    val done: Boolean = false,
)

/**
 * Re-authenticates with the current password, then sets the new one. A wrong current password is
 * shown on that field. On success the passwords are cleared from memory, and the new one is
 * offered to the password manager (as an update, even if this account was asked before).
 */
class ChangePasswordViewModel(
    private val session: SessionManager,
    private val saveOffers: PasswordSaveOffers,
    private val email: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    fun onCurrentChange(value: String) = _uiState.update { it.copy(current = value, currentError = null) }

    fun onNewChange(value: String) = _uiState.update { it.copy(new = value, newError = null) }

    fun onConfirmChange(value: String) = _uiState.update { it.copy(confirm = value, confirmError = null) }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun save() {
        val s = _uiState.value
        if (s.isSaving || s.done) return
        val currentError = Validators.loginPasswordError(s.current)
            ?.let { R.string.validation_current_password_required }
        val newError = Validators.newPasswordError(s.new) ?: Validators.newPasswordDifferentError(s.current, s.new)
        val confirmError = Validators.confirmPasswordError(s.new, s.confirm)
        if (currentError != null || newError != null || confirmError != null) {
            _uiState.update { it.copy(currentError = currentError, newError = newError, confirmError = confirmError) }
            return
        }
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val error = runAuthAction { session.changePassword(s.current, s.new) }
            when (error) {
                null -> {
                    saveOffers.offerUpdate(email, s.new)
                    _uiState.value = ChangePasswordUiState(done = true)
                }
                AuthError.INVALID_CREDENTIALS -> _uiState.update {
                    it.copy(isSaving = false, currentError = R.string.error_current_password_wrong)
                }
                else -> _uiState.update { it.copy(isSaving = false, error = error) }
            }
        }
    }
}
