package com.medhome.nepal.ui.shell

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medhome.nepal.R
import com.medhome.nepal.domain.Role as UserRole
import com.medhome.nepal.domain.UserProfile
import com.medhome.nepal.fakes.FakeAuthDataSource
import com.medhome.nepal.fakes.FakeCredentialClient
import com.medhome.nepal.fakes.FakeDoctorRepository
import com.medhome.nepal.fakes.doctor
import com.medhome.nepal.fakes.FakeProfileStore
import com.medhome.nepal.session.SessionManager
import com.medhome.nepal.session.SessionState
import com.medhome.nepal.domain.Specialty
import com.medhome.nepal.ui.doctors.DoctorViewModels
import com.medhome.nepal.ui.home.HOME_AVATAR_TAG
import com.medhome.nepal.ui.profile.ProfileViewModel
import com.medhome.nepal.ui.theme.MedHomeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The patient shell's navigation, driven through the real UI: three tabs, Profile behind the
 * Home avatar (bar hidden, Back returns Home), its sub-pages, Find a doctor and a doctor's
 * details (over a fake repository), and tab-level Back.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w360dp-h740dp")
class PatientShellNavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val session = SessionState.SignedIn(
        profile = UserProfile("uid", "Asha Rai", "asha@example.com", UserRole.PATIENT),
        usesPassword = true,
    )

    // No Firebase in JVM tests: Profile's ViewModel gets a session manager over fakes.
    private val profileViewModelFactory = viewModelFactory {
        initializer {
            ProfileViewModel(
                SessionManager(
                    auth = FakeAuthDataSource(),
                    profiles = FakeProfileStore(),
                    credentials = FakeCredentialClient(),
                    scope = CoroutineScope(Dispatchers.Unconfined),
                ),
            )
        }
    }

    private val asha = doctor(id = "doc-001", name = "Asha Rai")
    private val bikash = doctor(id = "doc-002", name = "Bikash Thapa", specialty = Specialty.DERMATOLOGY)
    private val doctorViewModelFactory = DoctorViewModels.factory { FakeDoctorRepository(listOf(asha, bikash)) }

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun text(@StringRes id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Application>().getString(id, *args)

    @Before
    fun showShell() {
        compose.setContent {
            MedHomeTheme(darkTheme = false) { MainShell(session, profileViewModelFactory, doctorViewModelFactory) }
        }
        settle()
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
    }

    /**
     * Rows near the end can sit under the floating bar after scrolling, so run the row's click
     * action rather than tapping its centre (layout clearance has its own test).
     */
    private fun SemanticsNodeInteraction.clickRow() {
        performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
    }

    /**
     * Two taps in a row with the clock stopped, so the second lands while the first navigation
     * is still running (as a real double tap does).
     */
    private fun SemanticsNodeInteraction.doubleTap() {
        compose.mainClock.autoAdvance = false
        performSemanticsAction(SemanticsActions.OnClick)
        performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.autoAdvance = true
        settle()
    }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun openProfile() {
        compose.onNodeWithTag(HOME_AVATAR_TAG).performClick()
        settle()
    }

    private fun assertOnHome() {
        compose.onNodeWithText(text(R.string.home_greeting, "Asha")).assertIsDisplayed()
        compose.onNodeWithTag(FLOATING_NAV_BAR_TAG).assertIsDisplayed()
        compose.onNode(isTab and hasText(text(R.string.nav_home))).assertIsSelected()
    }

    /** Exists, not displayed: coming back from a sub-page restores Profile's scroll position. */
    private fun assertOnProfile() {
        compose.onNodeWithText(text(R.string.profile_title)).assertExists()
        compose.onNodeWithTag(FLOATING_NAV_BAR_TAG).assertDoesNotExist()
    }

    @Test
    fun `the bar has Home, Bookings and Records and no Profile tab`() {
        compose.onAllNodes(isTab).assertCountEquals(3)
        compose.onNode(isTab and hasText(text(R.string.nav_home))).assertExists()
        compose.onNode(isTab and hasText(text(R.string.nav_bookings))).assertExists()
        compose.onNode(isTab and hasText(text(R.string.nav_records))).assertExists()
        compose.onAllNodesWithText(text(R.string.profile_title)).assertCountEquals(0)
    }

    @Test
    fun `the avatar opens Profile without the bar`() {
        openProfile()
        assertOnProfile()
    }

    @Test
    fun `back from Profile returns to Home with the bar`() {
        openProfile()
        pressBack()
        assertOnHome()
    }

    @Test
    fun `Profile sub-pages open from Profile and back returns through Profile to Home`() {
        openProfile()
        compose.onNodeWithText(text(R.string.settings_help_center)).clickRow()
        settle()
        compose.onNodeWithText(text(R.string.help_faq_title)).assertIsDisplayed()
        compose.onNodeWithTag(FLOATING_NAV_BAR_TAG).assertDoesNotExist()

        pressBack()
        assertOnProfile()
        pressBack()
        assertOnHome()
    }

    @Test
    fun `the Health records shortcut switches to the Records tab`() {
        compose.onNodeWithText(text(R.string.shortcut_health_records)).clickRow()
        settle()
        compose.onNode(isTab and hasText(text(R.string.nav_records))).assertIsSelected()
        compose.onNodeWithTag(FLOATING_NAV_BAR_TAG).assertIsDisplayed()
    }

    @Test
    fun `back from another tab returns to Home`() {
        compose.onNode(isTab and hasText(text(R.string.nav_bookings))).performClick()
        settle()
        compose.onNode(isTab and hasText(text(R.string.nav_bookings))).assertIsSelected()

        pressBack()
        assertOnHome()
    }

    @Test
    fun `a double tap on a shortcut opens one screen`() {
        compose.onNodeWithText(text(R.string.shortcut_medicine_reminders)).performScrollTo().doubleTap()
        compose.onNodeWithText(text(R.string.coming_soon_body)).assertIsDisplayed()

        pressBack()
        assertOnHome()
    }

    private fun openFindDoctor() {
        compose.onNodeWithText(text(R.string.shortcut_find_doctor)).clickRow()
        settle()
    }

    private fun assertOnFindDoctor() {
        compose.onNodeWithText(text(R.string.doctors_search)).assertExists()
        compose.onNodeWithTag(FLOATING_NAV_BAR_TAG).assertDoesNotExist()
    }

    @Test
    fun `Find a doctor opens the doctor list without the bar`() {
        openFindDoctor()
        assertOnFindDoctor()
        compose.onNodeWithText(asha.name).assertIsDisplayed()
        compose.onNodeWithText(bikash.name).assertIsDisplayed()

        pressBack()
        assertOnHome()
    }

    @Test
    fun `a doctor opens their details and back returns through the list to Home`() {
        openFindDoctor()
        compose.onNodeWithText(bikash.name).performClick()
        settle()
        compose.onNodeWithText(text(R.string.doctor_book)).assertExists()
        compose.onNodeWithText(bikash.name).assertIsDisplayed()

        pressBack()
        assertOnFindDoctor()
        pressBack()
        assertOnHome()
    }

    @Test
    fun `a double tap on a doctor opens their details once`() {
        openFindDoctor()
        compose.onNodeWithText(asha.name).doubleTap()
        compose.onNodeWithText(text(R.string.doctor_book)).assertExists()

        pressBack()
        assertOnFindDoctor()
    }

    @Test
    fun `a double tap on a Profile row opens one page`() {
        openProfile()
        compose.onNodeWithText(text(R.string.settings_help_center)).performScrollTo().doubleTap()
        compose.onNodeWithText(text(R.string.help_faq_title)).assertIsDisplayed()

        pressBack()
        assertOnProfile()
    }

    @Test
    fun `a double tap on the avatar opens Profile once`() {
        compose.onNodeWithTag(HOME_AVATAR_TAG).doubleTap()
        assertOnProfile()

        pressBack()
        assertOnHome()
    }

    @Test
    fun `a double tap on the back arrow goes back one screen`() {
        openProfile()
        compose.onNodeWithText(text(R.string.settings_help_center)).clickRow()
        settle()

        compose.onNodeWithContentDescription(text(R.string.action_back)).doubleTap()
        assertOnProfile()
    }

    private companion object {
        const val SETTLE_MS = 1_000L
    }
}
