package com.medhome.nepal.ui.shell

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
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
import com.medhome.nepal.domain.AdminBooking
import com.medhome.nepal.domain.AdminCancelledBy
import com.medhome.nepal.domain.BookedDoctor
import com.medhome.nepal.domain.BookingStatus
import com.medhome.nepal.domain.CancelReason
import com.medhome.nepal.domain.ClinicCancelReason
import com.medhome.nepal.domain.DoctorAppointment
import com.medhome.nepal.domain.ManagedDoctor
import com.medhome.nepal.domain.Role
import com.medhome.nepal.domain.Specialty
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
import com.medhome.nepal.fakes.fakeReminderViewModels
import com.medhome.nepal.session.SessionManager
import com.medhome.nepal.session.SessionState
import com.medhome.nepal.ui.admin.ADMIN_APPOINTMENT_TAG
import com.medhome.nepal.ui.admin.ADMIN_DOCTOR_ROW_TAG
import com.medhome.nepal.ui.admin.ADMIN_BOOKING_ROW_TAG
import com.medhome.nepal.ui.doctors.DoctorViewModels
import com.medhome.nepal.ui.profile.ProfileViewModel
import com.medhome.nepal.ui.theme.MedHomeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The role picks the signed-in home (patient tabs, admin tabs, doctor placeholder), and the
 * admin's own navigation: a doctor, their bookings, Edit, the Bookings tab (every doctor's
 * bookings, one opened and cancelled), the Profile tab and Back.
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
        allBookings.value = listOf(
            adminBooking("b1", "Asha Rai", System.currentTimeMillis() + DAY_MS, "Sita"),
            adminBooking("b2", "Bikash Thapa", System.currentTimeMillis() + 2 * DAY_MS, "Hari"),
            adminBooking(
                "b3", "Asha Rai", System.currentTimeMillis() + 3 * DAY_MS, "Gita",
                status = BookingStatus.CANCELLED, cancelledBy = AdminCancelledBy.ANOTHER_ADMIN,
            ),
        )
    }

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, SemanticsRole.Tab)

    private fun openTab(@StringRes label: Int) {
        compose.onNode(isTab and hasText(text(label))).performClick()
        settle()
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
                    reminderViewModelFactory = fakeReminderViewModels(),
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
    fun `an admin gets the Doctors, Bookings and Profile tabs, on Doctors`() {
        show(Role.ADMIN)
        compose.onNodeWithText(text(R.string.admin_doctors_title)).assertExists()
        compose.onAllNodesWithTag(ADMIN_DOCTOR_ROW_TAG).assertCountEquals(1)
        compose.onNodeWithTag(FLOATING_NAV_BAR_TAG).assertExists()
        compose.onAllNodes(isTab).assertCountEquals(3)
        compose.onNode(isTab and hasText(text(R.string.nav_doctors))).assertIsSelected()
        compose.onNode(isTab and hasText(text(R.string.nav_bookings))).assertExists()
        compose.onNode(isTab and hasText(text(R.string.profile_title))).assertExists()
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

        compose.onNodeWithTag(FLOATING_NAV_BAR_TAG).assertDoesNotExist()

        compose.onNodeWithText(text(R.string.admin_edit_details)).tap()
        compose.onNodeWithText(text(R.string.admin_edit_title)).assertExists()

        pressBack()
        compose.onNodeWithText(text(R.string.admin_edit_details)).assertExists()
        pressBack()
        compose.onNodeWithText(text(R.string.admin_doctors_title)).assertExists()
        compose.onNodeWithTag(FLOATING_NAV_BAR_TAG).assertExists()
    }

    @Test
    fun `the Profile tab has the admin's name, language, theme and sign out, and Back goes to Doctors`() {
        show(Role.ADMIN)
        openTab(R.string.profile_title)
        compose.onNodeWithText("Prabhash Sigdel").assertExists()
        compose.onNodeWithText(text(R.string.profile_language)).assertExists()
        compose.onNodeWithText(text(R.string.settings_theme)).assertExists()
        compose.onNodeWithText(text(R.string.action_sign_out)).assertExists()
        compose.onNode(isTab and hasText(text(R.string.profile_title))).assertIsSelected()
        pressBack()
        compose.onNodeWithText(text(R.string.admin_doctors_title)).assertExists()
    }

    @Test
    fun `the Bookings tab lists every doctor's bookings by filter`() {
        show(Role.ADMIN)
        openTab(R.string.nav_bookings)
        compose.onNode(isHeading() and hasText(text(R.string.admin_bookings_title))).assertExists()
        compose.onAllNodesWithTag(ADMIN_BOOKING_ROW_TAG).assertCountEquals(2)
        compose.onNodeWithText("Bikash Thapa").assertExists()
        compose.onNodeWithText("Hari").assertExists()

        compose.onNodeWithText(text(R.string.admin_bookings_cancelled)).tap()
        compose.onAllNodesWithTag(ADMIN_BOOKING_ROW_TAG).assertCountEquals(1)
        compose.onNodeWithText(text(R.string.admin_cancelled_by_other_admin)).assertExists()

        compose.onNodeWithText(text(R.string.bookings_past)).tap()
        compose.onNodeWithText(text(R.string.admin_bookings_none_past)).assertExists()
    }

    @Test
    fun `Add doctor opens an empty form`() {
        show(Role.ADMIN)
        compose.onNodeWithText(text(R.string.admin_add_doctor)).tap()
        compose.onNodeWithText(text(R.string.admin_add_title)).assertExists()
        compose.onNodeWithText(text(R.string.admin_specialty_none)).assertExists()
    }

    /**
     * The confirm dialog itself isn't opened here: a GlassDialog never settles under Robolectric
     * (AdminBookingsViewModelsTest covers the dialog's flow). The cancel goes through the
     * repository instead, and the open screen must follow it live.
     */
    @Test
    fun `an admin opens a booking from the Bookings tab, sees it cancelled live, and comes back`() {
        show(Role.ADMIN)
        openTab(R.string.nav_bookings)
        compose.onAllNodesWithTag(ADMIN_BOOKING_ROW_TAG)[0].tap()
        compose.onNodeWithText(text(R.string.admin_booking_title)).assertExists()
        compose.onNodeWithText("Sita").assertExists()
        compose.onNodeWithText(text(R.string.admin_booking_booked)).assertExists()
        compose.onNodeWithText(text(R.string.admin_cancel_booking_confirm)).assertExists()
        compose.onNodeWithTag(FLOATING_NAV_BAR_TAG).assertDoesNotExist()

        runBlocking { admin.cancelBooking("b1", ClinicCancelReason(CancelReason.CLINIC_CLOSED, "Holiday")) }
        settle()
        compose.onNodeWithText(text(R.string.admin_cancelled_by_you)).assertExists()
        compose.onNodeWithText(text(R.string.label_cancelled_at)).assertExists()
        compose.onNodeWithText(text(R.string.cancel_reason_clinic_closed)).assertExists()
        compose.onNodeWithText("Holiday").assertExists()
        compose.onNodeWithText(text(R.string.admin_cancel_booking_confirm)).assertDoesNotExist()

        pressBack()
        compose.onNode(isHeading() and hasText(text(R.string.admin_bookings_title))).assertExists()
        // No longer upcoming.
        compose.onAllNodesWithTag(ADMIN_BOOKING_ROW_TAG).assertCountEquals(1)
    }

    private companion object {
        const val SETTLE_MS = 1_000L
        const val DAY_MS = 24 * 60 * 60 * 1000L

        fun adminBooking(
            id: String,
            doctorName: String,
            startAtMillis: Long,
            patient: String,
            status: BookingStatus = BookingStatus.BOOKED,
            cancelledBy: AdminCancelledBy? = null,
        ) = AdminBooking(
            bookingId = id,
            doctorId = "doc-001",
            doctor = BookedDoctor(doctorName, Specialty.CARDIOLOGY, "Valley Care Hospital", 800),
            startAtMillis = startAtMillis,
            patientFirstName = patient,
            status = status,
            cancelledBy = cancelledBy,
        )
    }
}
