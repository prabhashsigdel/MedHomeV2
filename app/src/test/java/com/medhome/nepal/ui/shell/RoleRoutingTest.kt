package com.medhome.nepal.ui.shell

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medhome.nepal.R
import com.medhome.nepal.domain.DoctorAppointment
import com.medhome.nepal.domain.ManagedDoctor
import com.medhome.nepal.domain.Role
import com.medhome.nepal.domain.UserProfile
import com.medhome.nepal.fakes.FakeAdminRepository
import com.medhome.nepal.fakes.FakeAuthDataSource
import com.medhome.nepal.fakes.FakeBookingRepository
import com.medhome.nepal.fakes.FakeCredentialClient
import com.medhome.nepal.fakes.FakeDoctorRepository
import com.medhome.nepal.fakes.FakeProfileStore
import com.medhome.nepal.fakes.doctor
import com.medhome.nepal.fakes.fakeAdminViewModels
import com.medhome.nepal.fakes.fakeBookingViewModels
import com.medhome.nepal.session.SessionManager
import com.medhome.nepal.session.SessionState
import com.medhome.nepal.ui.admin.ADMIN_APPOINTMENT_TAG
import com.medhome.nepal.ui.admin.ADMIN_DOCTOR_ROW_TAG
import com.medhome.nepal.ui.admin.ADMIN_SETTINGS_BUTTON_TAG
import com.medhome.nepal.ui.doctors.DoctorViewModels
import com.medhome.nepal.ui.profile.ProfileViewModel
import com.medhome.nepal.ui.theme.MedHomeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The role picks the signed-in home (patient tabs, admin panel, doctor placeholder), and the
 * admin panel's own navigation: a doctor, their bookings, Edit, Settings and Back.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w360dp-h740dp")
class RoleRoutingTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

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

    private val asha = ManagedDoctor(doctor(id = "doc-001", name = "Asha Rai"), active = true)
    private val admin = FakeAdminRepository(listOf(asha)).apply {
        appointments.value = mapOf(
            "doc-001" to listOf(DoctorAppointment("b1", System.currentTimeMillis() + DAY_MS, "Sita")),
        )
    }

    private fun text(@StringRes id: Int): String = ApplicationProvider.getApplicationContext<Application>().getString(id)

    private fun show(role: Role) {
        compose.setContent {
            MedHomeTheme(darkTheme = false) {
                MainShell(
                    session = SessionState.SignedIn(UserProfile("uid", "Prabhash Sigdel", "p@example.com", role), usesPassword = true),
                    profileViewModelFactory = profileViewModelFactory,
                    doctorViewModelFactory = DoctorViewModels.factory { FakeDoctorRepository() },
                    bookingViewModelFactory = fakeBookingViewModels(FakeBookingRepository()),
                    adminViewModelFactory = fakeAdminViewModels(admin),
                )
            }
        }
        settle()
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
    }

    private fun SemanticsNodeInteraction.tap() {
        performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    @Test
    fun `a patient gets the tab bar, not the admin panel`() {
        show(Role.PATIENT)
        compose.onNodeWithTag(FLOATING_NAV_BAR_TAG).assertExists()
        compose.onNodeWithText(text(R.string.admin_doctors_title)).assertDoesNotExist()
    }

    @Test
    fun `an admin gets the doctors panel with every doctor and no tab bar`() {
        show(Role.ADMIN)
        compose.onNodeWithText(text(R.string.admin_doctors_title)).assertExists()
        compose.onAllNodesWithTag(ADMIN_DOCTOR_ROW_TAG).assertCountEquals(1)
        compose.onNodeWithTag(FLOATING_NAV_BAR_TAG).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.staff_home_title)).assertDoesNotExist()
    }

    @Test
    fun `a doctor keeps the placeholder`() {
        show(Role.DOCTOR)
        compose.onNodeWithText(text(R.string.staff_home_title)).assertExists()
        compose.onNodeWithText(text(R.string.admin_doctors_title)).assertDoesNotExist()
        compose.onNodeWithTag(FLOATING_NAV_BAR_TAG).assertDoesNotExist()
    }

    @Test
    fun `an admin opens a doctor, sees their bookings, edits and comes back`() {
        show(Role.ADMIN)
        compose.onNodeWithTag(ADMIN_DOCTOR_ROW_TAG).tap()
        compose.onNodeWithText(text(R.string.admin_hide_doctor)).assertExists()
        compose.onAllNodesWithTag(ADMIN_APPOINTMENT_TAG).assertCountEquals(1)
        compose.onNodeWithText("Sita").assertExists()

        compose.onNodeWithText(text(R.string.admin_edit_details)).tap()
        compose.onNodeWithText(text(R.string.admin_edit_title)).assertExists()

        pressBack()
        compose.onNodeWithText(text(R.string.admin_edit_details)).assertExists()
        pressBack()
        compose.onNodeWithText(text(R.string.admin_doctors_title)).assertExists()
    }

    @Test
    fun `the avatar opens Settings with language, theme and sign out`() {
        show(Role.ADMIN)
        compose.onNodeWithTag(ADMIN_SETTINGS_BUTTON_TAG).tap()
        compose.onNodeWithText(text(R.string.profile_language)).assertExists()
        compose.onNodeWithText(text(R.string.settings_theme)).assertExists()
        compose.onNodeWithText(text(R.string.action_sign_out)).assertExists()
        pressBack()
        compose.onNodeWithText(text(R.string.admin_doctors_title)).assertExists()
    }

    @Test
    fun `Add doctor opens an empty form`() {
        show(Role.ADMIN)
        compose.onNodeWithText(text(R.string.admin_add_doctor)).tap()
        compose.onNodeWithText(text(R.string.admin_add_title)).assertExists()
        compose.onNodeWithText(text(R.string.admin_specialty_none)).assertExists()
    }

    private companion object {
        const val SETTLE_MS = 1_000L
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
