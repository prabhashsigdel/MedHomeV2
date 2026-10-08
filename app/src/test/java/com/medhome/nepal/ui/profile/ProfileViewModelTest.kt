package com.medhome.nepal.ui.profile

import com.medhome.nepal.R
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.fakes.FakeAuthDataSource
import com.medhome.nepal.fakes.FakeGoogleCredentialClient
import com.medhome.nepal.fakes.FakeProfileStore
import com.medhome.nepal.fakes.passwordUser
import com.medhome.nepal.session.SessionManager
import com.medhome.nepal.session.SessionState
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

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sessionScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val user = passwordUser()
    private val auth = FakeAuthDataSource(user)
    private val profiles = FakeProfileStore()
    private val session = SessionManager(auth, profiles, FakeGoogleCredentialClient(), sessionScope)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        sessionScope.cancel()
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.signedInViewModel(): ProfileViewModel {
        session.restoreSession()
        advanceUntilIdle()
        return ProfileViewModel(session)
    }

    private fun signedInName(): String = (session.state.value as SessionState.SignedIn).profile.name

    @Test
    fun `saving a new name updates the session profile and closes the dialog`() = runTest(dispatcher) {
        val viewModel = signedInViewModel()
        viewModel.openEditName(signedInName())
        viewModel.onNameDraftChange("  Asha Rai  ")
        viewModel.saveName()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showEditName)
        assertTrue(state.nameSaved)
        assertEquals("Asha Rai", signedInName())
        assertEquals("Asha Rai", profiles.profiles[user.uid]?.name)
    }

    @Test
    fun `blank and overlong names are rejected before saving`() = runTest(dispatcher) {
        val viewModel = signedInViewModel()
        viewModel.openEditName(signedInName())

        viewModel.onNameDraftChange("   ")
        viewModel.saveName()
        assertEquals(R.string.validation_name_required, viewModel.uiState.value.nameError)

        viewModel.onNameDraftChange("a".repeat(101))
        viewModel.saveName()
        assertEquals(R.string.validation_name_too_long, viewModel.uiState.value.nameError)

        advanceUntilIdle()
        assertFalse("updateName" in profiles.calls)
    }

    @Test
    fun `a failed save keeps the dialog open with the error and the old name`() = runTest(dispatcher) {
        val viewModel = signedInViewModel()
        val original = signedInName()
        profiles.updateNameError = AuthError.NETWORK
        viewModel.openEditName(original)
        viewModel.onNameDraftChange("New Name")
        viewModel.saveName()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.showEditName)
        assertEquals(AuthError.NETWORK, state.saveNameError)
        assertFalse(state.nameSaved)
        assertEquals(original, signedInName())
    }

    @Test
    fun `save cannot be submitted twice while running`() = runTest(dispatcher) {
        val viewModel = signedInViewModel()
        viewModel.openEditName(signedInName())
        viewModel.onNameDraftChange("New Name")
        viewModel.saveName()
        assertTrue(viewModel.uiState.value.isSavingName)
        viewModel.saveName()
        advanceUntilIdle()

        assertEquals(1, profiles.calls.count { it == "updateName" })
    }

    @Test
    fun `dialogs cannot be closed or opened while saving`() = runTest(dispatcher) {
        val viewModel = signedInViewModel()
        viewModel.openEditName(signedInName())
        viewModel.onNameDraftChange("New Name")
        viewModel.saveName()

        viewModel.dismissEditName()
        assertTrue(viewModel.uiState.value.showEditName)
        viewModel.openDeleteDialog()
        assertFalse(viewModel.uiState.value.showDeleteDialog)

        advanceUntilIdle()
        assertNull(viewModel.uiState.value.saveNameError)
    }
}
