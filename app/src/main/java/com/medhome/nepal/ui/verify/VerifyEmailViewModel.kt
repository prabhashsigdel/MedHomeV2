package com.medhome.nepal.ui.verify

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medhome.nepal.R
import com.medhome.nepal.appContainer
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.session.SessionManager
import com.medhome.nepal.ui.common.runAuthAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VerifyAction { RESEND, CHECK, SIGN_OUT }

data class VerifyEmailUiState(
    val runningAction: VerifyAction? = null,
    @param:StringRes val message: Int? = null,
    val error: AuthError? = null,
) {
    val isBusy: Boolean get() = runningAction != null
}

class VerifyEmailViewModel(
    private val session: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VerifyEmailUiState())
    val uiState: StateFlow<VerifyEmailUiState> = _uiState.asStateFlow()

    fun resend() = run(VerifyAction.RESEND) {
        session.resendVerificationEmail()
        R.string.verify_email_resent
    }

    fun checkVerified() = run(VerifyAction.CHECK) {
        // On success the session moves to SignedIn and this screen goes away.
        if (session.checkEmailVerified()) null else R.string.verify_not_yet
    }

    fun signOut() = run(VerifyAction.SIGN_OUT) {
        session.signOut()
        null
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    private fun run(action: VerifyAction, block: suspend () -> Int?) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(runningAction = action, message = null, error = null) }
        viewModelScope.launch {
            var message: Int? = null
            val error = runAuthAction { message = block() }
            _uiState.update { it.copy(runningAction = null, message = message, error = error) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { VerifyEmailViewModel(appContainer.sessionManager) }
        }
    }
}
