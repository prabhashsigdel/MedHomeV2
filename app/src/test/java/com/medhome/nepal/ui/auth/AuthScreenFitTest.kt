package com.medhome.nepal.ui.auth

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medhome.nepal.R
import com.medhome.nepal.data.PasswordSaveOffers
import com.medhome.nepal.fakes.FakeAuthDataSource
import com.medhome.nepal.fakes.FakeCredentialClient
import com.medhome.nepal.fakes.FakeProfileStore
import com.medhome.nepal.fakes.InMemorySavePromptHistory
import com.medhome.nepal.session.SessionManager
import com.medhome.nepal.ui.common.LocalCredentialClient
import com.medhome.nepal.ui.theme.MedHomeTheme
import com.medhome.nepal.ui.verify.VerifyEmailScreen
import com.medhome.nepal.ui.verify.VerifyEmailViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The footer link must be visible without scrolling on common phones. 360x668dp is a 360x740dp
 * phone minus a 24dp status bar and a 48dp three-button navigation bar (the worst common case).
 * Runs in both languages, because Nepali lines are taller.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class AuthScreenFitTest {

    @get:Rule
    val compose = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val session = SessionManager(FakeAuthDataSource(), FakeProfileStore(), FakeCredentialClient(), scope)
    private val saveOffers = PasswordSaveOffers(InMemorySavePromptHistory())

    @After
    fun tearDown() = scope.cancel()

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            MedHomeTheme {
                CompositionLocalProvider(LocalCredentialClient provides FakeCredentialClient(), content = content)
            }
        }
        compose.mainClock.advanceTimeBy(ENTRANCE_SETTLE_MS)
    }

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<Application>().getString(id)

    /**
     * Visible without scrolling: the screen's scroll range is zero (bounds can't be used, since
     * content inside a scroll container is clipped to the window), and the footer is on screen.
     */
    private fun assertFooterVisible(footerText: Int) {
        val scrollable = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        val overflowPx = scrollable.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
        val overflowDp = overflowPx / compose.density.density
        println("FIT ${string(footerText)}: overflow ${overflowDp}dp")
        assertTrue("Screen needs ${overflowDp}dp of scrolling to reach the footer", overflowPx <= 0f)
        compose.onNodeWithText(string(footerText), substring = true).assertIsDisplayed()
    }

    // ViewModels are built outside the composable, as in the app (where a factory builds them).
    private fun login() {
        val viewModel = LoginViewModel(session, saveOffers, SavedAccountsPrompt())
        show {
            LoginScreen(
                sessionError = null,
                onDismissSessionError = {},
                onSignUp = {},
                onForgotPassword = {},
                viewModel = viewModel,
            )
        }
    }

    private fun signUp() {
        val viewModel = SignUpViewModel(session, saveOffers)
        show { SignUpScreen(onBackToLogin = {}, viewModel = viewModel) }
    }

    private fun forgot() {
        val viewModel = ForgotPasswordViewModel(session, "")
        show { ForgotPasswordScreen(initialEmail = "", onBackToLogin = {}, viewModel = viewModel) }
    }

    private fun verify() {
        val viewModel = VerifyEmailViewModel(session)
        show { VerifyEmailScreen(email = "asha@example.com", verificationEmailFailed = false, viewModel = viewModel) }
    }

    @Test @Config(qualifiers = "w360dp-h668dp")
    fun `login footer fits in English`() = login().also { assertFooterVisible(R.string.login_sign_up) }

    @Test @Config(qualifiers = "ne-w360dp-h668dp")
    fun `login footer fits in Nepali`() = login().also { assertFooterVisible(R.string.login_sign_up) }

    @Test @Config(qualifiers = "w360dp-h668dp")
    fun `sign up footer fits in English`() = signUp().also { assertFooterVisible(R.string.signup_log_in) }

    @Test @Config(qualifiers = "ne-w360dp-h668dp")
    fun `sign up footer fits in Nepali`() = signUp().also { assertFooterVisible(R.string.signup_log_in) }

    @Test @Config(qualifiers = "w360dp-h668dp")
    fun `forgot password footer fits in English`() = forgot().also { assertFooterVisible(R.string.action_back_to_login) }

    @Test @Config(qualifiers = "ne-w360dp-h668dp")
    fun `forgot password footer fits in Nepali`() = forgot().also { assertFooterVisible(R.string.action_back_to_login) }

    @Test @Config(qualifiers = "w360dp-h668dp")
    fun `verify email footer fits in English`() = verify().also { assertFooterVisible(R.string.verify_use_other_account) }

    @Test @Config(qualifiers = "ne-w360dp-h668dp")
    fun `verify email footer fits in Nepali`() = verify().also { assertFooterVisible(R.string.verify_use_other_account) }

    @Test @Config(qualifiers = "w320dp-h480dp")
    fun `very small screens fall back to scrolling and the footer stays reachable`() {
        signUp()
        compose.onNodeWithText(string(R.string.signup_log_in), substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    private companion object {
        /** Lets the staggered entrance animations finish. */
        const val ENTRANCE_SETTLE_MS = 1_000L
    }
}
