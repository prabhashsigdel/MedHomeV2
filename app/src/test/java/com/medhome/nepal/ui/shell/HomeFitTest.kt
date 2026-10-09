package com.medhome.nepal.ui.shell

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medhome.nepal.R
import com.medhome.nepal.domain.Role
import com.medhome.nepal.domain.Specialty
import com.medhome.nepal.fakes.FakeBookingRepository
import com.medhome.nepal.fakes.FakeReminderRepository
import com.medhome.nepal.fakes.medicine
import com.medhome.nepal.domain.NepalTime
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.fakes.booking
import com.medhome.nepal.fakes.doctor
import com.medhome.nepal.fakes.fakeBookingViewModels
import com.medhome.nepal.fakes.fakeReminderViewModels
import com.medhome.nepal.domain.UserProfile
import com.medhome.nepal.session.SessionState
import com.medhome.nepal.ui.theme.MedHomeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Home's three sections (next appointment, today's medicines with doses, shortcuts) must all show above
 * the floating bar without scrolling in a 360x740dp window (the shell's test size, as in
 * TabBarClearanceTest; Robolectric reports no system bars). Both languages, because Nepali
 * lines are taller. Bounds come from position and size, since bounds inside the scroll
 * container are clipped to it (a row below the fold would report 0 and pass).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class HomeFitTest {

    @get:Rule
    val compose = createComposeRule()

    private val session = SessionState.SignedIn(
        profile = UserProfile("uid", "Asha Rai", "asha@example.com", Role.PATIENT),
        usesPassword = true,
    )

    /** The tallest Home: an upcoming appointment with a long doctor name. */
    private val bookings = FakeBookingRepository(
        listOf(
            booking(
                doctor = doctor(name = "Bishnu Prasad Shrestha Adhikari", specialty = Specialty.GASTROENTEROLOGY),
                startAtMillis = System.currentTimeMillis() + DAY_MS,
            ),
        ),
    )

    /** The tallest medicines card: a long name and dose, and more doses later today. */
    private val reminders = FakeReminderRepository(
        medicines = listOf(
            medicine(
                name = "Amoxicillin and clavulanic acid",
                dose = "1 tablet after food",
                times = (0 until 4).map { TimeOfDay((8 + 4 * it) * 60) },
                startDate = NepalTime.dateOf(System.currentTimeMillis()),
            ),
        ),
    )

    private fun assertShortcutsFitAboveBar() {
        compose.setContent {
            MedHomeTheme(darkTheme = false) { MainShell(session, bookingViewModelFactory = fakeBookingViewModels(bookings), reminderViewModelFactory = fakeReminderViewModels(reminders)) }
        }
        // Entrance animations run on the test clock; idle means they have finished.
        compose.waitForIdle()

        val label = ApplicationProvider.getApplicationContext<Application>().getString(R.string.shortcut_medicine_reminders)
        // Unclipped position + size: bounds are clipped to the scroll viewport (0 when off screen).
        val lastRow = compose.onNodeWithText(label).fetchSemanticsNode()
        val lastRowBottom = lastRow.positionInRoot.y + lastRow.size.height
        // The tagged box includes the bar's 16dp outer margin, so its top is where the gap ends.
        val barTop = compose.onNodeWithTag(FLOATING_NAV_BAR_TAG).fetchSemanticsNode().boundsInRoot.top
        val density = compose.density.density
        assertTrue(
            "Last shortcut ends at ${lastRowBottom / density}dp, under the bar area from ${barTop / density}dp",
            lastRowBottom <= barTop + TOLERANCE_PX,
        )
    }

    @Test @Config(qualifiers = "w360dp-h740dp")
    fun `all home sections fit above the bar in English`() = assertShortcutsFitAboveBar()

    @Test @Config(qualifiers = "ne-w360dp-h740dp")
    fun `all home sections fit above the bar in Nepali`() = assertShortcutsFitAboveBar()

    private companion object {
        const val TOLERANCE_PX = 1f
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
