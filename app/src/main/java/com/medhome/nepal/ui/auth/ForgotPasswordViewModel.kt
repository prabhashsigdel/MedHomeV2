package com.medhome.nepal.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.session.SessionManager
import com.medhome.nepal.ui.common.Validators
import com.medhome.nepal.ui.common.runAuthAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val email: String = "",
    @param:StringRes val emailError: Int? = null,
    val isLoading: Boolean = false,
    val error: AuthError? = null,
    val linkSent: Boolean = false,
)

class ForgotPasswordViewModel(
    private val session: SessionManager,
    initialEmail: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState(email = initialEmail))
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) =
        _uiState.update { it.copy(email = value, emailError = null, linkSent = false) }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun sendResetLink() {
        val current = _uiState.value
        if (current.isLoading) return
        val email = current.email.trim()
        val emailError = Validators.emailError(email)
        if (emailError != null) {
            _uiState.update { it.copy(emailError = emailError) }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null, linkSent = false) }
        viewModelScope.launch {
            val error = runAuthAction { session.sendPasswordReset(email) }
            // With email enumeration protection, success never confirms the account exists.
            _uiState.update { it.copy(isLoading = false, error = error, linkSent = error == null) }
        }
    }
}
