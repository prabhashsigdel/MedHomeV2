package com.medhome.nepal.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medhome.nepal.appContainer
import com.medhome.nepal.data.PasswordSaveOffers
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.session.SessionManager
import com.medhome.nepal.ui.common.Validators
import com.medhome.nepal.ui.common.isRetryable
import com.medhome.nepal.ui.common.runAuthAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignUpUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    @param:StringRes val nameError: Int? = null,
    @param:StringRes val emailError: Int? = null,
    @param:StringRes val passwordError: Int? = null,
    @param:StringRes val confirmPasswordError: Int? = null,
    val isLoading: Boolean = false,
    val error: AuthError? = null,
    /** The Auth account exists but profile setup failed, so retrying means signing in. */
    val accountCreated: Boolean = false,
) {
    val canRetryWithSignIn: Boolean get() = accountCreated && error?.isRetryable == true
}

class SignUpViewModel(
    private val session: SessionManager,
    private val saveOffers: PasswordSaveOffers,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, nameError = null) }

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, emailError = null, accountCreated = false) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, passwordError = null, accountCreated = false) }

    fun onConfirmPasswordChange(value: String) =
        _uiState.update { it.copy(confirmPassword = value, confirmPasswordError = null) }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun signUp() {
        val current = _uiState.value
        val name = current.name.trim()
        val email = current.email.trim()
        val validated = current.copy(
            nameError = Validators.nameError(name),
            emailError = Validators.emailError(email),
            passwordError = Validators.newPasswordError(current.password),
            confirmPasswordError = Validators.confirmPasswordError(current.password, current.confirmPassword),
        )
        val hasFieldErrors = listOf(
            validated.nameError,
            validated.emailError,
            validated.passwordError,
            validated.confirmPasswordError,
        ).any { it != null }
        if (hasFieldErrors) {
            _uiState.value = validated
            return
        }
        launchRequest(offerToSave = email to current.password) { session.signUp(name, email, current.password) }
    }

    fun retryWithSignIn() {
        val current = _uiState.value
        val email = current.email.trim()
        launchRequest(offerToSave = email to current.password) { session.signInWithEmail(email, current.password) }
    }

    /** [offerToSave] is the email and password to offer to the password manager on success. */
    private fun launchRequest(offerToSave: Pair<String, String>, block: suspend () -> Unit) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val error = runAuthAction(block)
            if (error == null) saveOffers.offer(offerToSave.first, offerToSave.second)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = error,
                    accountCreated = it.accountCreated || error == AuthError.PROFILE_LOAD_FAILED,
                )
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer
                SignUpViewModel(container.sessionManager, container.passwordSaveOffers)
            }
        }
    }
}
