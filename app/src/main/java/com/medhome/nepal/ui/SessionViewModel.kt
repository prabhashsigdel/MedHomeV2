package com.medhome.nepal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medhome.nepal.appContainer
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.session.SessionManager
import com.medhome.nepal.session.SessionState
import com.medhome.nepal.ui.common.runAuthAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Exposes the session to the root UI and drives the profile-unavailable screen. */
class SessionViewModel(
    private val session: SessionManager,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = session.state

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _actionError = MutableStateFlow<AuthError?>(null)
    val actionError: StateFlow<AuthError?> = _actionError.asStateFlow()

    fun retryProfile() = launchAction { session.restoreSession() }

    fun signOut() = launchAction { session.signOut() }

    fun clearSignedOutError() = session.clearSignedOutError()

    fun dismissActionError() {
        _actionError.value = null
    }

    private fun launchAction(block: suspend () -> Unit) {
        if (_isBusy.value) return
        _isBusy.value = true
        _actionError.value = null
        viewModelScope.launch {
            _actionError.value = runAuthAction(block)
            _isBusy.value = false
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SessionViewModel(appContainer.sessionManager) }
        }
    }
}
