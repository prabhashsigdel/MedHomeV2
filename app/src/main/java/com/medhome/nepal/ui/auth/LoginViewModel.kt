package com.medhome.nepal.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medhome.nepal.appContainer
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.session.SessionManager
import com.medhome.nepal.ui.common.GoogleIdTokenResult
import com.medhome.nepal.ui.common.Validators
import com.medhome.nepal.ui.common.isRetryable
import com.medhome.nepal.ui.common.runAuthAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoginAttempt { EMAIL, GOOGLE }

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    @param:StringRes val emailError: Int? = null,
    @param:StringRes val passwordError: Int? = null,
    val isLoading: Boolean = false,
    val error: AuthError? = null,
    val retryAttempt: LoginAttempt? = null,
)

class LoginViewModel(
    private val session: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, emailError = null) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, passwordError = null) }

    fun dismissError() = _uiState.update { it.copy(error = null, retryAttempt = null) }

    fun signInWithEmail() {
        val current = _uiState.value
        val email = current.email.trim()
        val emailError = Validators.emailError(email)
        val passwordError = Validators.loginPasswordError(current.password)
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        if (!markBusy()) return
        viewModelScope.launch {
            finish(LoginAttempt.EMAIL, runAuthAction { session.signInWithEmail(email, current.password) })
        }
    }

    /** Called before the Google picker opens. Returns false if another request is running. */
    fun beginGoogleSignIn(): Boolean = markBusy()

    fun onGoogleResult(result: GoogleIdTokenResult) {
        when (result) {
            is GoogleIdTokenResult.Token -> viewModelScope.launch {
                finish(LoginAttempt.GOOGLE, runAuthAction { session.signInWithGoogle(result.idToken) })
            }
            GoogleIdTokenResult.Cancelled -> _uiState.update { it.copy(isLoading = false) }
            is GoogleIdTokenResult.Failed -> finish(LoginAttempt.GOOGLE, result.error)
        }
    }

    private fun markBusy(): Boolean {
        if (_uiState.value.isLoading) return false
        _uiState.update { it.copy(isLoading = true, error = null, retryAttempt = null) }
        return true
    }

    private fun finish(attempt: LoginAttempt, error: AuthError?) {
        _uiState.update {
            it.copy(
                isLoading = false,
                error = error,
                retryAttempt = attempt.takeIf { error?.isRetryable == true },
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { LoginViewModel(appContainer.sessionManager) }
        }
    }
}
