package com.medhome.nepal.ui.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medhome.nepal.appContainer
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.session.Reauth
import com.medhome.nepal.session.SessionManager
import com.medhome.nepal.ui.common.GoogleIdTokenResult
import com.medhome.nepal.ui.common.Validators
import com.medhome.nepal.ui.common.runAuthAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isSigningOut: Boolean = false,
    val signOutError: AuthError? = null,
    val showDeleteDialog: Boolean = false,
    val deletePassword: String = "",
    @param:StringRes val deletePasswordError: Int? = null,
    val isDeleting: Boolean = false,
    val deleteError: AuthError? = null,
) {
    val isBusy: Boolean get() = isSigningOut || isDeleting
}

class HomeViewModel(
    private val session: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun signOut() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isSigningOut = true, signOutError = null) }
        viewModelScope.launch {
            val error = runAuthAction { session.signOut() }
            _uiState.update { it.copy(isSigningOut = false, signOutError = error) }
        }
    }

    fun dismissSignOutError() = _uiState.update { it.copy(signOutError = null) }

    fun openDeleteDialog() = _uiState.update {
        it.copy(showDeleteDialog = true, deletePassword = "", deletePasswordError = null, deleteError = null)
    }

    fun dismissDeleteDialog() {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(showDeleteDialog = false, deletePassword = "") }
    }

    fun onDeletePasswordChange(value: String) =
        _uiState.update { it.copy(deletePassword = value, deletePasswordError = null) }

    fun confirmDeleteWithPassword() {
        val current = _uiState.value
        val passwordError = Validators.loginPasswordError(current.deletePassword)
        if (passwordError != null) {
            _uiState.update { it.copy(deletePasswordError = passwordError) }
            return
        }
        if (!markDeleting()) return
        deleteWith(Reauth.Password(current.deletePassword))
    }

    /** Called before the Google picker opens to confirm deletion. Returns false if busy. */
    fun beginGoogleDelete(): Boolean = markDeleting()

    fun onGoogleDeleteResult(result: GoogleIdTokenResult) {
        when (result) {
            is GoogleIdTokenResult.Token -> deleteWith(Reauth.Google(result.idToken))
            GoogleIdTokenResult.Cancelled -> _uiState.update { it.copy(isDeleting = false) }
            is GoogleIdTokenResult.Failed -> _uiState.update { it.copy(isDeleting = false, deleteError = result.error) }
        }
    }

    private fun markDeleting(): Boolean {
        if (_uiState.value.isBusy) return false
        _uiState.update { it.copy(isDeleting = true, deleteError = null) }
        return true
    }

    private fun deleteWith(reauth: Reauth) {
        viewModelScope.launch {
            val error = runAuthAction { session.deleteAccount(reauth) }
            _uiState.update { it.copy(isDeleting = false, deleteError = error) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(appContainer.sessionManager) }
        }
    }
}
