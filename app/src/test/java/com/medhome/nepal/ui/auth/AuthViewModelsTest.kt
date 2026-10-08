package com.medhome.nepal.ui.auth

import com.medhome.nepal.R
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.fakes.FakeAuthDataSource
import com.medhome.nepal.fakes.FakeCredentialClient
import com.medhome.nepal.fakes.FakeProfileStore
import com.medhome.nepal.data.PasswordSaveOffers
import com.medhome.nepal.data.SaveOutcome
import com.medhome.nepal.data.SavedCredential
import com.medhome.nepal.fakes.InMemorySavePromptHistory
import com.medhome.nepal.fakes.googleUser
import com.medhome.nepal.fakes.passwordUser
import com.medhome.nepal.session.SessionManager
import com.medhome.nepal.session.SessionState
import com.medhome.nepal.ui.common.GoogleIdTokenResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
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
class AuthViewModelsTest {

    private val dispatcher = StandardTestDispatcher()
    private val sessionScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val auth = FakeAuthDataSource()
    private val profiles = FakeProfileStore()
    private val google = FakeCredentialClient()
    private val session = SessionManager(auth, profiles, google, sessionScope)
    private val history = InMemorySavePromptHistory()
    private val saveOffers = PasswordSaveOffers(history)
    private val savedAccountsPrompt = SavedAccountsPrompt()

    private fun loginViewModel() = LoginViewModel(session, saveOffers, savedAccountsPrompt)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        sessionScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `login validates fields before calling firebase`() = runTest(dispatcher) {
        val viewModel = loginViewModel()
        viewModel.signInWithEmail()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.validation_email_required, state.emailError)
        assertEquals(R.string.validation_password_required, state.passwordError)
        assertTrue(auth.calls.isEmpty())
    }

    @Test
    fun `login ignores a second submit while the first is running`() = runTest(dispatcher) {
        val viewModel = loginViewModel()
        viewModel.onEmailChange("asha@example.com")
        viewModel.onPasswordChange("password123")

        viewModel.signInWithEmail()
        assertTrue(viewModel.uiState.value.isLoading)
        viewModel.signInWithEmail()
        advanceUntilIdle()

        assertEquals(1, auth.calls.count { it == "signInWithEmail" })
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `profile failure after login offers a retry`() = runTest(dispatcher) {
        profiles.ensureError = AuthError.NETWORK
        val viewModel = loginViewModel()
        viewModel.onEmailChange("asha@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.signInWithEmail()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AuthError.PROFILE_LOAD_FAILED, state.error)
        assertEquals(LoginAttempt.EMAIL, state.retryAttempt)
        assertNull(auth.currentUser)
    }

    @Test
    fun `wrong password does not offer retry`() = runTest(dispatcher) {
        auth.failures["signInWithEmail"] = AuthError.INVALID_CREDENTIALS
        val viewModel = loginViewModel()
        viewModel.onEmailChange("asha@example.com")
        viewModel.onPasswordChange("wrong-pass")
        viewModel.signInWithEmail()
        advanceUntilIdle()

        assertEquals(AuthError.INVALID_CREDENTIALS, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.retryAttempt)
    }

    @Test
    fun `google picker cannot start twice and cancel clears loading`() = runTest(dispatcher) {
        val viewModel = loginViewModel()
        assertTrue(viewModel.beginGoogleSignIn())
        assertFalse(viewModel.beginGoogleSignIn())

        viewModel.onGoogleResult(GoogleIdTokenResult.Cancelled)

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `google token signs in through the session`() = runTest(dispatcher) {
        auth.nextUser = googleUser()
        val viewModel = loginViewModel()
        viewModel.beginGoogleSignIn()
        viewModel.onGoogleResult(GoogleIdTokenResult.Token("id-token"))
        advanceUntilIdle()

        assertEquals(listOf("signInWithGoogle"), auth.calls)
        assertTrue(session.state.value is SessionState.SignedIn)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `google picker failure offers retry`() = runTest(dispatcher) {
        val viewModel = loginViewModel()
        viewModel.beginGoogleSignIn()
        viewModel.onGoogleResult(GoogleIdTokenResult.Failed(AuthError.GOOGLE_FAILED))

        assertEquals(AuthError.GOOGLE_FAILED, viewModel.uiState.value.error)
        assertEquals(LoginAttempt.GOOGLE, viewModel.uiState.value.retryAttempt)
    }

    @Test
    fun `typed login success offers to save the password once`() = runTest(dispatcher) {
        val viewModel = loginViewModel()
        viewModel.onEmailChange("asha@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.signInWithEmail()
        advanceUntilIdle()

        val offer = saveOffers.take()
        assertEquals("asha@example.com", offer?.email)
        assertEquals("password123", offer?.password)
        assertNull("taken offers don't linger", saveOffers.take())
    }

    @Test
    fun `failed login does not offer to save`() = runTest(dispatcher) {
        auth.failures["signInWithEmail"] = AuthError.INVALID_CREDENTIALS
        val viewModel = loginViewModel()
        viewModel.onEmailChange("asha@example.com")
        viewModel.onPasswordChange("wrong-pass")
        viewModel.signInWithEmail()
        advanceUntilIdle()

        assertNull(saveOffers.pending.value)
    }

    @Test
    fun `an account already offered is not offered again`() = runTest(dispatcher) {
        saveOffers.record("Asha@Example.com", SaveOutcome.DECLINED)
        val viewModel = loginViewModel()
        viewModel.onEmailChange("asha@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.signInWithEmail()
        advanceUntilIdle()

        assertNull(saveOffers.pending.value)
    }

    @Test
    fun `signing in with a saved password does not offer to save it again`() = runTest(dispatcher) {
        val viewModel = loginViewModel()
        assertTrue(viewModel.beginSavedAccounts())
        viewModel.onSavedCredential(SavedCredential.Password("asha@example.com", "password123"))
        advanceUntilIdle()

        assertEquals(listOf("signInWithEmail"), auth.calls)
        assertTrue(session.state.value is SessionState.SignedIn)
        assertNull(saveOffers.pending.value)
    }

    @Test
    fun `a saved Google account signs in with Google`() = runTest(dispatcher) {
        auth.nextUser = googleUser()
        val viewModel = loginViewModel()
        viewModel.beginSavedAccounts()
        viewModel.onSavedCredential(SavedCredential.Google("id-token"))
        advanceUntilIdle()

        assertEquals(listOf("signInWithGoogle"), auth.calls)
    }

    @Test
    fun `saved accounts sheet opens once and not again after it is dismissed`() = runTest(dispatcher) {
        val viewModel = loginViewModel()
        assertTrue(viewModel.shouldOfferSavedAccounts())
        assertTrue(viewModel.beginSavedAccounts())
        assertFalse(viewModel.beginSavedAccounts())

        viewModel.onSavedCredential(null)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse("a new visit this session stays quiet", loginViewModel().shouldOfferSavedAccounts())
    }

    @Test
    fun `editing the password after picking a saved one offers to save the new one`() = runTest(dispatcher) {
        auth.failures["signInWithEmail"] = AuthError.INVALID_CREDENTIALS
        val viewModel = loginViewModel()
        viewModel.beginSavedAccounts()
        viewModel.onSavedCredential(SavedCredential.Password("asha@example.com", "old-password"))
        advanceUntilIdle()

        auth.failures.clear()
        viewModel.onPasswordChange("new-password1")
        viewModel.signInWithEmail()
        advanceUntilIdle()

        assertEquals("new-password1", saveOffers.take()?.password)
    }

    @Test
    fun `sign up success offers to save the new password`() = runTest(dispatcher) {
        auth.nextUser = passwordUser(verified = false)
        val viewModel = SignUpViewModel(session, saveOffers)
        viewModel.onNameChange("Asha")
        viewModel.onEmailChange("asha@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.onConfirmPasswordChange("password123")
        viewModel.signUp()
        advanceUntilIdle()

        assertEquals("asha@example.com", saveOffers.take()?.email)
    }

    @Test
    fun `sign up rejects short passwords and mismatches`() = runTest(dispatcher) {
        val viewModel = SignUpViewModel(session, saveOffers)
        viewModel.onNameChange("Asha")
        viewModel.onEmailChange("asha@example.com")
        viewModel.onPasswordChange("short")
        viewModel.onConfirmPasswordChange("other")
        viewModel.signUp()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.validation_password_too_short, state.passwordError)
        assertEquals(R.string.validation_password_mismatch, state.confirmPasswordError)
        assertTrue(auth.calls.isEmpty())
    }

    @Test
    fun `sign up profile failure retries by signing in`() = runTest(dispatcher) {
        profiles.ensureError = AuthError.NETWORK
        val viewModel = SignUpViewModel(session, saveOffers)
        viewModel.onNameChange("Asha")
        viewModel.onEmailChange("asha@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.onConfirmPasswordChange("password123")
        viewModel.signUp()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canRetryWithSignIn)

        profiles.ensureError = null
        viewModel.retryWithSignIn()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertEquals(1, auth.calls.count { it == "createUserWithEmail" })
        assertEquals(1, auth.calls.count { it == "signInWithEmail" })
    }
}
