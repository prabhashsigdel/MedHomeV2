package com.medhome.nepal.ui.profile

import com.medhome.nepal.R
import com.medhome.nepal.data.PasswordSaveOffers
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.Gender
import com.medhome.nepal.fakes.FakeAuthDataSource
import com.medhome.nepal.fakes.FakeCredentialClient
import com.medhome.nepal.fakes.FakeProfileStore
import com.medhome.nepal.fakes.InMemorySavePromptHistory
import com.medhome.nepal.fakes.passwordUser
import com.medhome.nepal.session.SessionManager
import com.medhome.nepal.session.SessionState
import com.medhome.nepal.ui.common.BirthDate
import com.medhome.nepal.ui.settings.ChangePasswordViewModel
import com.medhome.nepal.ui.settings.EditProfileViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Profile, Edit profile and Change password ViewModels against the real SessionManager. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sessionScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val user = passwordUser()
    private val auth = FakeAuthDataSource(user)
    private val profiles = FakeProfileStore()
    private val session = SessionManager(auth, profiles, FakeCredentialClient(), sessionScope)
    private val saveOffers = PasswordSaveOffers(InMemorySavePromptHistory())
    private val today = checkNotNull(BirthDate.toUtcMillis("2026-10-08"))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        sessionScope.cancel()
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.signedIn(): SessionState.SignedIn {
        session.restoreSession()
        advanceUntilIdle()
        return session.state.value as SessionState.SignedIn
    }

    private suspend fun TestScope.editViewModel() = EditProfileViewModel(session, signedIn().profile) { today }

    private fun signedInProfile() = (session.state.value as SessionState.SignedIn).profile

    // Edit profile

    @Test
    fun `saving updates the session profile with normalized values`() = runTest(dispatcher) {
        val viewModel = editViewModel()
        viewModel.onNameChange("  Asha Rai  ")
        viewModel.onPhoneChange("+977 984-123 4567")
        viewModel.onDateSelected(checkNotNull(BirthDate.toUtcMillis("2001-03-09")))
        viewModel.onGenderChange(Gender.FEMALE)
        viewModel.save()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.saved)
        val profile = signedInProfile()
        assertEquals("Asha Rai", profile.name)
        assertEquals("9841234567", profile.phone)
        assertEquals("2001-03-09", profile.dateOfBirth)
        assertEquals(Gender.FEMALE, profile.gender)
        assertEquals(profile.copy(), profiles.profiles[user.uid])
    }

    @Test
    fun `cleared optional fields are removed`() = runTest(dispatcher) {
        val viewModel = editViewModel()
        viewModel.onPhoneChange("9841234567")
        viewModel.onGenderChange(Gender.OTHER)
        viewModel.save()
        advanceUntilIdle()

        val again = EditProfileViewModel(session, signedInProfile()) { today }
        again.onPhoneChange("")
        again.onGenderChange(null)
        again.clearDateOfBirth()
        again.save()
        advanceUntilIdle()

        val profile = signedInProfile()
        assertNull(profile.phone)
        assertNull(profile.gender)
        assertNull(profile.dateOfBirth)
    }

    @Test
    fun `invalid name and phone are rejected before saving`() = runTest(dispatcher) {
        val viewModel = editViewModel()
        viewModel.onNameChange("   ")
        viewModel.onPhoneChange("12345")
        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.validation_name_required, state.nameError)
        assertEquals(R.string.validation_phone_invalid, state.phoneError)
        assertFalse("updateDetails" in profiles.calls)
    }

    @Test
    fun `a future birth date is rejected and the old value kept`() = runTest(dispatcher) {
        val viewModel = editViewModel()
        viewModel.onDateSelected(checkNotNull(BirthDate.toUtcMillis("2030-01-01")))

        val state = viewModel.uiState.value
        assertEquals(R.string.validation_date_of_birth_invalid, state.dateOfBirthError)
        assertNull(state.dateOfBirth)
        assertFalse(state.showDatePicker)
    }

    @Test
    fun `a failed save shows the error and keeps the profile`() = runTest(dispatcher) {
        val viewModel = editViewModel()
        val original = signedInProfile().name
        profiles.updateDetailsError = AuthError.NETWORK
        viewModel.onNameChange("New Name")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(AuthError.NETWORK, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.saved)
        assertEquals(original, signedInProfile().name)
    }

    @Test
    fun `save cannot be submitted twice while running`() = runTest(dispatcher) {
        val viewModel = editViewModel()
        viewModel.save()
        assertTrue(viewModel.uiState.value.isSaving)
        viewModel.save()
        advanceUntilIdle()

        assertEquals(1, profiles.calls.count { it == "updateDetails" })
    }

    // Change password

    private fun changePasswordViewModel() = ChangePasswordViewModel(session, saveOffers, user.email.orEmpty())

    @Test
    fun `changing the password reauthenticates first and offers to update the saved one`() = runTest(dispatcher) {
        signedIn()
        val viewModel = changePasswordViewModel()
        viewModel.onCurrentChange("old-password")
        viewModel.onNewChange("new-password1")
        viewModel.onConfirmChange("new-password1")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(listOf("reauthenticate", "updatePassword"), auth.calls.filter { it != "signInWithEmail" })
        val state = viewModel.uiState.value
        assertTrue(state.done)
        assertEquals("", state.current)
        assertEquals("", state.new)
        assertEquals("new-password1", saveOffers.take()?.password)
    }

    @Test
    fun `a wrong current password is shown on that field`() = runTest(dispatcher) {
        signedIn()
        auth.failures["reauthenticate"] = AuthError.INVALID_CREDENTIALS
        val viewModel = changePasswordViewModel()
        viewModel.onCurrentChange("wrong")
        viewModel.onNewChange("new-password1")
        viewModel.onConfirmChange("new-password1")
        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.error_current_password_wrong, state.currentError)
        assertNull(state.error)
        assertFalse(state.done)
        assertFalse("updatePassword" in auth.calls)
    }

    @Test
    fun `new password rules are checked before any request`() = runTest(dispatcher) {
        signedIn()
        val viewModel = changePasswordViewModel()
        viewModel.onCurrentChange("same-password")
        viewModel.onNewChange("same-password")
        viewModel.onConfirmChange("different")
        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.validation_password_same, state.newError)
        assertEquals(R.string.validation_password_mismatch, state.confirmError)
        assertFalse("reauthenticate" in auth.calls)
    }

    // Profile

    @Test
    fun `delete dialog cannot open while signing out`() = runTest(dispatcher) {
        signedIn()
        val viewModel = ProfileViewModel(session)
        viewModel.signOut()
        viewModel.openDeleteDialog()
        assertFalse(viewModel.uiState.value.showDeleteDialog)
        advanceUntilIdle()
    }
}
