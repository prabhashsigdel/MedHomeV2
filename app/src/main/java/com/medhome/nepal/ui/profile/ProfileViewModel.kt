package com.medhome.nepal.ui.profile

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

data class ProfileUiState(
    val isSigningOut: Boolean = false,
    val signOutError: AuthError? = null,
    val showEditName: Boolean = false,
    val nameDraft: String = "",
    @param:StringRes val nameError: Int? = null,
    val isSavingName: Boolean = false,
    val saveNameError: AuthError? = null,
    val nameSaved: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val deletePassword: String = "",
    @param:StringRes val deletePasswordError: Int? = null,
    val isDeleting: Boolean = false,
    val deleteError: AuthError? = null,
) {
    val isBusy: Boolean get() = isSigningOut || isDeleting || isSavingName
}

class ProfileViewModel(
    private val session: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    // Sign out

    fun signOut() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isSigningOut = true, signOutError = null) }
        viewModelScope.launch {
            val error = runAuthAction { session.signOut() }
            _uiState.update { it.copy(isSigningOut = false, signOutError = error) }
        }
    }

    fun dismissSignOutError() = _uiState.update { it.copy(signOutError = null) }

    // Edit name

    fun openEditName(currentName: String) {
        if (_uiState.value.isBusy) return
        _uiState.update {
            it.copy(showEditName = true, nameDraft = currentName, nameError = null, saveNameError = null, nameSaved = false)
        }
    }

    fun onNameDraftChange(value: String) = _uiState.update { it.copy(nameDraft = value, nameError = null) }

    fun dismissEditName() {
        if (_uiState.value.isSavingName) return
        _uiState.update { it.copy(showEditName = false) }
    }

    fun saveName() {
        val current = _uiState.value
        if (current.isBusy) return
        val name = current.nameDraft.trim()
        val nameError = Validators.nameError(name)
        if (nameError != null) {
            _uiState.update { it.copy(nameError = nameError) }
            return
        }
        _uiState.update { it.copy(isSavingName = true, saveNameError = null) }
        viewModelScope.launch {
            val error = runAuthAction { session.updateName(name) }
            _uiState.update {
                it.copy(
                    isSavingName = false,
                    saveNameError = error,
                    showEditName = error != null,
                    nameSaved = error == null,
                )
            }
        }
    }

    fun dismissNameSaved() = _uiState.update { it.copy(nameSaved = false) }

    // Delete account

    fun openDeleteDialog() {
        if (_uiState.value.isBusy) return
        _uiState.update {
            it.copy(showDeleteDialog = true, deletePassword = "", deletePasswordError = null, deleteError = null)
        }
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
            initializer { ProfileViewModel(appContainer.sessionManager) }
        }
    }
}
