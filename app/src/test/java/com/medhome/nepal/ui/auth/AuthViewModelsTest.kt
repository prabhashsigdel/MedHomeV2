package com.medhome.nepal.ui.auth

import com.medhome.nepal.R
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.fakes.FakeAuthDataSource
import com.medhome.nepal.fakes.FakeGoogleCredentialClient
import com.medhome.nepal.fakes.FakeProfileStore
import com.medhome.nepal.fakes.googleUser
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
    private val google = FakeGoogleCredentialClient()
    private val session = SessionManager(auth, profiles, google, sessionScope)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        sessionScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `login validates fields before calling firebase`() = runTest(dispatcher) {
        val viewModel = LoginViewModel(session)
        viewModel.signInWithEmail()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.validation_email_required, state.emailError)
        assertEquals(R.string.validation_password_required, state.passwordError)
        assertTrue(auth.calls.isEmpty())
    }

    @Test
    fun `login ignores a second submit while the first is running`() = runTest(dispatcher) {
        val viewModel = LoginViewModel(session)
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
        val viewModel = LoginViewModel(session)
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
        val viewModel = LoginViewModel(session)
        viewModel.onEmailChange("asha@example.com")
        viewModel.onPasswordChange("wrong-pass")
        viewModel.signInWithEmail()
        advanceUntilIdle()

        assertEquals(AuthError.INVALID_CREDENTIALS, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.retryAttempt)
    }

    @Test
    fun `google picker cannot start twice and cancel clears loading`() = runTest(dispatcher) {
        val viewModel = LoginViewModel(session)
        assertTrue(viewModel.beginGoogleSignIn())
        assertFalse(viewModel.beginGoogleSignIn())

        viewModel.onGoogleResult(GoogleIdTokenResult.Cancelled)

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `google token signs in through the session`() = runTest(dispatcher) {
        auth.nextUser = googleUser()
        val viewModel = LoginViewModel(session)
        viewModel.beginGoogleSignIn()
        viewModel.onGoogleResult(GoogleIdTokenResult.Token("id-token"))
        advanceUntilIdle()

        assertEquals(listOf("signInWithGoogle"), auth.calls)
        assertTrue(session.state.value is SessionState.SignedIn)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `google picker failure offers retry`() = runTest(dispatcher) {
        val viewModel = LoginViewModel(session)
        viewModel.beginGoogleSignIn()
        viewModel.onGoogleResult(GoogleIdTokenResult.Failed(AuthError.GOOGLE_FAILED))

        assertEquals(AuthError.GOOGLE_FAILED, viewModel.uiState.value.error)
        assertEquals(LoginAttempt.GOOGLE, viewModel.uiState.value.retryAttempt)
    }

    @Test
    fun `sign up rejects short passwords and mismatches`() = runTest(dispatcher) {
        val viewModel = SignUpViewModel(session)
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
        val viewModel = SignUpViewModel(session)
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
