package com.medhome.nepal.ui.shell

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.filterToOne
import com.medhome.nepal.ui.reminders.DOSE_HISTORY_ROW_TAG
import com.medhome.nepal.ui.reminders.HISTORY_DAY_TAG
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.isHeading
import com.medhome.nepal.ui.reminders.STILL_LATE_TOGGLE_TAG
import com.medhome.nepal.ui.reminders.SETUP_ITEM_TAG
import com.medhome.nepal.reminders.ReminderSetupItem
import org.robolectric.shadows.ShadowAlarmManager
import android.os.PowerManager
import com.medhome.nepal.domain.ClinicCancelReason
import com.medhome.nepal.domain.CancelledBy
import com.medhome.nepal.domain.CancelReason
import com.medhome.nepal.domain.BookingStatus
import android.Manifest
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
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.medhome.nepal.fakes.FakeBookingRepository
import com.medhome.nepal.fakes.FakeReminderRepository
import com.medhome.nepal.fakes.FakeDoctorRepository
import com.medhome.nepal.fakes.booking
import com.medhome.nepal.fakes.fakeBookingViewModels
import com.medhome.nepal.fakes.fakeReminderViewModels
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.domain.TimeRange
import com.medhome.nepal.domain.Weekday
import com.medhome.nepal.ui.booking.BOOKING_CARD_TAG
import com.medhome.nepal.ui.booking.BOOK_APPOINTMENT_BUTTON_TAG
import com.medhome.nepal.ui.booking.SLOT_CHIP_TAG
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import com.medhome.nepal.domain.NepalTime
import com.medhome.nepal.fakes.medicine
import com.medhome.nepal.ui.home.HOME_BELL_TAG
import com.medhome.nepal.ui.reminders.DOSE_ROW_TAG
import com.medhome.nepal.ui.reminders.MEDICINE_CARD_TAG

/**
 * The patient shell's navigation, driven through the real UI: three tabs, Profile behind the
 * Home avatar (bar hidden, Back returns Home), its sub-pages, Find a doctor and a doctor's
 * details (over a fake repository), and tab-level Back; the reminder screens (bell, medicines,
 * the battery guide after the first save, Profile's switches) over a fake reminder repository.
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

    /** Open every day 06:00-22:00, so free slots exist whenever the test runs. */
    private val asha = doctor(id = "doc-001", name = "Asha Rai").copy(
        weeklySchedule = Weekday.entries.associateWith { listOf(TimeRange(TimeOfDay(6 * 60), TimeOfDay(22 * 60))) },
    )
    private val bikash = doctor(id = "doc-002", name = "Bikash Thapa", specialty = Specialty.DERMATOLOGY)
    private val doctorRepository = FakeDoctorRepository(listOf(asha, bikash))
    private val doctorViewModelFactory = DoctorViewModels.factory { doctorRepository }

    /** One past booking with Asha, so the Bookings tab has something under Past. */
    private val bookings = FakeBookingRepository(
        listOf(booking(id = "past1", doctor = asha, startAtMillis = System.currentTimeMillis() - DAY_MS)),
        clock = System::currentTimeMillis,
    )
    private val bookingViewModelFactory = fakeBookingViewModels(bookings, doctorRepository)

    private val reminders = FakeReminderRepository()

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun text(@StringRes id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Application>().getString(id, *args)

    @Before
    fun showShell() {
        // Everything reminders need is allowed here, so saving a medicine offers no setup dialog
        // (a GlassDialog never settles under Robolectric; MedicinesViewModel's tests cover it).
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(app.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(app.packageName, true)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        compose.setContent {
            MedHomeTheme(darkTheme = false) { MainShell(
                    session,
                    profileViewModelFactory,
                    doctorViewModelFactory,
                    bookingViewModelFactory,
                    reminderViewModelFactory = fakeReminderViewModels(reminders),
                ) }
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
    fun `the bar has Home, Bookings, Medicines and Records in that order, and no Profile tab`() {
        compose.onAllNodes(isTab).assertCountEquals(4)
        val labels = listOf(R.string.nav_home, R.string.nav_bookings, R.string.nav_medicines, R.string.nav_records).map { text(it) }
        val shown = compose.onAllNodes(isTab).fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].joinToString() }
        assertEquals(labels, shown)
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
        compose.onNodeWithText(text(R.string.medicines_add)).assertIsDisplayed()

        pressBack()
        assertOnHome()
    }

    // Reminders

    @Test
    fun `the bell opens the Medicines tab on Today, and Back returns Home`() {
        compose.onNodeWithTag(HOME_BELL_TAG).performClick()
        settle()
        assertOnMedicinesTab()
        compose.onNodeWithText(text(R.string.medicines_today)).assertIsSelected()

        pressBack()
        assertOnHome()
    }

    @Test
    fun `the Medicine reminders shortcut opens the Medicines tab`() {
        compose.onNodeWithText(text(R.string.shortcut_medicine_reminders)).clickRow()
        settle()
        assertOnMedicinesTab()
    }

    @Test
    fun `Today marks a dose taken with a tap, and History lists past days`() {
        val today = NepalTime.dateOf(System.currentTimeMillis())
        reminders.medicineState.value = listOf(medicine(id = 1, times = listOf(TimeOfDay(23 * 60 + 59)), startDate = today.plusDays(-3)))
        compose.onNode(isTab and hasText(text(R.string.nav_medicines))).performClick()
        settle()
        compose.onNodeWithTag(DOSE_ROW_TAG).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        assertEquals(listOf("taken:true"), reminders.calls)

        compose.onNodeWithText(text(R.string.medicines_history)).performClick()
        settle()
        compose.onAllNodesWithTag(HISTORY_DAY_TAG).assertCountEquals(3)
        compose.onAllNodesWithTag(DOSE_HISTORY_ROW_TAG).assertCountEquals(3)
        compose.onAllNodesWithText(text(R.string.dose_missed), useUnmergedTree = true).assertCountEquals(3)
    }

    private fun assertOnMedicinesTab() {
        compose.onNode(isTab and hasText(text(R.string.nav_medicines))).assertIsSelected()
        compose.onNodeWithTag(FLOATING_NAV_BAR_TAG).assertIsDisplayed()
    }

    @Test
    fun `saving a medicine closes the form back to the list, with no guide or dialog when nothing is missing`() {
        reminders.medicineState.value = listOf(medicine(id = 1, name = "Paracetamol", startDate = NepalTime.dateOf(System.currentTimeMillis())))
        compose.onNodeWithText(text(R.string.shortcut_medicine_reminders)).clickRow()
        settle()
        compose.onAllNodesWithText(text(R.string.nav_medicines))
            .filterToOne(hasClickAction() and !hasAnyAncestor(hasTestTag(FLOATING_NAV_BAR_TAG)))
            .performClick()
        settle()
        compose.onNodeWithTag(MEDICINE_CARD_TAG).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText(text(R.string.medicine_edit_title)).assertIsDisplayed()

        compose.onNodeWithText(text(R.string.action_save)).clickRow()
        settle()
        assertEquals(listOf("save:1"), reminders.calls)
        compose.onNodeWithText(text(R.string.medicines_add)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.reminder_setup_title)).assertDoesNotExist()
        assertTrue(reminders.prefsState.value.setupItemsShown.isEmpty())

        pressBack()
        assertOnHome()
    }

    @Test
    fun `a dose on Home is marked taken with a tap`() {
        reminders.medicineState.value = listOf(
            medicine(id = 1, times = listOf(TimeOfDay(23 * 60 + 59)), startDate = NepalTime.dateOf(System.currentTimeMillis()).plusDays(-1)),
        )
        settle()
        compose.onNodeWithTag(DOSE_ROW_TAG).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        assertEquals(listOf("taken:true"), reminders.calls)
        compose.onNodeWithText(text(R.string.dose_taken)).assertExists()
    }

    @Test
    fun `Profile's notification switches change the settings and Reminder setup opens from there`() {
        openProfile()
        compose.onNodeWithText(text(R.string.settings_medicine_reminders)).clickRow()
        settle()
        assertEquals(listOf("medicineReminders:false"), reminders.calls)

        compose.onNodeWithText(text(R.string.settings_reminder_setup)).clickRow()
        settle()
        compose.onNode(isHeading() and hasText(text(R.string.settings_reminder_setup))).assertIsDisplayed()
        // Every item this phone has, all on here.
        compose.onAllNodesWithTag(SETUP_ITEM_TAG).assertCountEquals(ReminderSetupItem.entries.size)
        compose.onNodeWithText(text(R.string.reminders_turn_on)).assertDoesNotExist()
        pressBack()
        assertOnProfile()
    }

    @Test
    fun `the phone makers' steps stay folded until Still late is opened`() {
        openProfile()
        compose.onNodeWithText(text(R.string.settings_reminder_setup)).clickRow()
        settle()
        compose.onNodeWithText(text(R.string.battery_samsung_title)).assertDoesNotExist()
        compose.onNodeWithTag(STILL_LATE_TOGGLE_TAG).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText(text(R.string.battery_samsung_title)).assertExists()
        compose.onNodeWithText(text(R.string.battery_xiaomi_title)).assertExists()
        compose.onNodeWithText(text(R.string.battery_other_title)).assertExists()
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

    // Booking

    private fun openBooking() {
        openFindDoctor()
        compose.onNodeWithText(asha.name).performClick()
        settle()
        compose.onNodeWithText(text(R.string.doctor_book)).clickRow()
        settle()
    }

    private fun bookFirstFreeSlot() {
        compose.onAllNodesWithTag(SLOT_CHIP_TAG)[0].performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText(text(R.string.book_confirm_action)).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText(text(R.string.book_success_title)).assertExists()
    }

    private fun assertOnBookingsTab() {
        compose.onNodeWithText(text(R.string.bookings_title)).assertIsDisplayed()
        compose.onNode(isTab and hasText(text(R.string.nav_bookings))).assertIsSelected()
        compose.onNodeWithTag(FLOATING_NAV_BAR_TAG).assertIsDisplayed()
    }

    @Test
    fun `Book appointment opens the slot picker without the bar`() {
        openBooking()
        compose.onNodeWithText(text(R.string.book_title)).assertIsDisplayed()
        compose.onNodeWithTag(FLOATING_NAV_BAR_TAG).assertDoesNotExist()

        pressBack()
        compose.onNodeWithText(text(R.string.doctor_book)).assertExists()
    }

    @Test
    fun `a booking ends on the Bookings tab and Back goes Home, not back into booking`() {
        openBooking()
        bookFirstFreeSlot()
        compose.onNodeWithText(text(R.string.book_success_action)).performSemanticsAction(SemanticsActions.OnClick)
        settle()

        assertEquals(1, bookings.booked.size)
        assertOnBookingsTab()
        compose.onAllNodesWithTag(BOOKING_CARD_TAG).assertCountEquals(1)

        pressBack()
        assertOnHome()
        // Home's stack was cleaned: Home is its root, and the next appointment shows the booking.
        compose.onNodeWithText(text(R.string.book_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.doctors_search)).assertDoesNotExist()
        compose.onNodeWithText(asha.name).assertExists()
    }

    @Test
    fun `Back on the success sheet also finishes on the Bookings tab`() {
        openBooking()
        bookFirstFreeSlot()
        pressBack()
        assertOnBookingsTab()
    }

    private fun bookFromBookingsTab() {
        compose.onNode(isTab and hasText(text(R.string.nav_bookings))).performClick()
        settle()
        // Nothing upcoming: the top button and the empty card's.
        compose.onAllNodesWithTag(BOOK_APPOINTMENT_BUTTON_TAG).assertCountEquals(2)
        compose.onAllNodesWithTag(BOOK_APPOINTMENT_BUTTON_TAG)[0].performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    @Test
    fun `Find a doctor opened from the Bookings tab goes Back to the Bookings tab`() {
        bookFromBookingsTab()
        assertOnFindDoctor()
        compose.onNodeWithText(asha.name).assertIsDisplayed()

        pressBack()
        assertOnBookingsTab()
    }

    @Test
    fun `the back arrow also returns to the Bookings tab, through the doctor`() {
        bookFromBookingsTab()
        compose.onNodeWithText(asha.name).performClick()
        settle()
        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        settle()
        assertOnFindDoctor()
        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        settle()
        assertOnBookingsTab()
    }

    @Test
    fun `Find a doctor opened from Home still goes Back Home`() {
        openFindDoctor()
        compose.onNodeWithText(asha.name).performClick()
        settle()
        pressBack()
        pressBack()
        assertOnHome()
    }

    @Test
    fun `the empty card's Book appointment opens Find a doctor too`() {
        compose.onNode(isTab and hasText(text(R.string.nav_bookings))).performClick()
        settle()
        compose.onAllNodesWithTag(BOOK_APPOINTMENT_BUTTON_TAG)[1].performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        settle()
        assertOnFindDoctor()
    }

    @Test
    fun `a double tap on Book appointment opens Find a doctor once`() {
        compose.onNode(isTab and hasText(text(R.string.nav_bookings))).performClick()
        settle()
        compose.onAllNodesWithTag(BOOK_APPOINTMENT_BUTTON_TAG)[0].doubleTap()
        assertOnFindDoctor()
        pressBack()
        assertOnBookingsTab()
    }

    @Test
    fun `booking started from the Bookings tab ends back on it`() {
        bookFromBookingsTab()
        compose.onNodeWithText(asha.name).performClick()
        settle()
        compose.onNodeWithText(text(R.string.doctor_book)).clickRow()
        settle()
        bookFirstFreeSlot()
        compose.onNodeWithText(text(R.string.book_success_action)).performSemanticsAction(SemanticsActions.OnClick)
        settle()

        assertOnBookingsTab()
        compose.onAllNodesWithTag(BOOKING_CARD_TAG).assertCountEquals(1)
        // Something upcoming now: only the top button.
        compose.onAllNodesWithTag(BOOK_APPOINTMENT_BUTTON_TAG).assertCountEquals(1)
        pressBack()
        assertOnHome()
    }

    @Test
    fun `a clinic cancel shows its reason and note in the list and in the details`() {
        bookings.bookings.value = listOf(
            booking(
                id = "c1",
                doctor = asha,
                startAtMillis = System.currentTimeMillis() + DAY_MS,
                status = BookingStatus.CANCELLED,
                cancelledBy = CancelledBy.CLINIC,
                clinicReason = ClinicCancelReason(CancelReason.CLINIC_CLOSED, "Closed for Dashain."),
            ),
        )
        compose.onNode(isTab and hasText(text(R.string.nav_bookings))).performClick()
        settle()
        compose.onNodeWithText(text(R.string.bookings_past)).performClick()
        settle()
        val reason = text(R.string.booking_clinic_reason, text(R.string.cancel_reason_clinic_closed))
        val note = text(R.string.booking_clinic_note, "Closed for Dashain.")
        compose.onNodeWithText(reason, useUnmergedTree = true).assertExists()
        compose.onNodeWithText(note, useUnmergedTree = true).assertExists()

        compose.onAllNodesWithTag(BOOKING_CARD_TAG)[0].performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText(reason, substring = true).assertExists()
        compose.onNodeWithText(note, substring = true).assertExists()
    }

    @Test
    fun `a booking opens its details and Back returns to the list`() {
        compose.onNode(isTab and hasText(text(R.string.nav_bookings))).performClick()
        settle()
        compose.onNodeWithText(text(R.string.bookings_past)).performClick()
        settle()
        compose.onAllNodesWithTag(BOOKING_CARD_TAG)[0].performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText(text(R.string.booking_past_notice)).assertExists()
        compose.onNodeWithText(text(R.string.booking_cancel)).assertDoesNotExist()
        compose.onNodeWithTag(FLOATING_NAV_BAR_TAG).assertDoesNotExist()

        pressBack()
        assertOnBookingsTab()
    }

    private companion object {
        const val SETTLE_MS = 1_000L
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
