package com.medhome.nepal.ui.shell

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medhome.nepal.R
import com.medhome.nepal.domain.Role
import com.medhome.nepal.fakes.fakeBookingViewModels
import com.medhome.nepal.fakes.fakeReminderViewModels
import com.medhome.nepal.domain.UserProfile
import com.medhome.nepal.session.SessionState
import com.medhome.nepal.ui.theme.MedHomeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The last item of a tab screen must scroll fully above the floating bar (with the 16dp gap),
 * whatever the language or font size. Uses the real patient shell on its Home tab.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class TabBarClearanceTest {

    @get:Rule
    val compose = createComposeRule()

    private val session = SessionState.SignedIn(
        profile = UserProfile("uid", "Asha Rai", "asha@example.com", Role.PATIENT),
        usesPassword = true,
    )

    private fun assertLastShortcutClearsBar(fontScale: Float = 1f) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { MedHomeTheme(darkTheme = false) { MainShell(session, bookingViewModelFactory = fakeBookingViewModels(), reminderViewModelFactory = fakeReminderViewModels()) } }
        compose.mainClock.advanceTimeBy(SETTLE_MS)

        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, SCROLL_TO_END_PX) }
        compose.mainClock.advanceTimeBy(SETTLE_MS)

        val label = ApplicationProvider.getApplicationContext<Application>().getString(R.string.shortcut_medicine_reminders)
        val lastItemBottom = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.bottom
        // The tagged box includes the bar's 16dp outer margin, so its top is where the gap ends.
        val barTop = compose.onNodeWithTag(FLOATING_NAV_BAR_TAG).fetchSemanticsNode().boundsInRoot.top
        val density = compose.density.density
        println("CLEARANCE x$fontScale: last item ends ${lastItemBottom / density}dp, bar area starts ${barTop / density}dp")
        assertTrue(
            "Last item ends at ${lastItemBottom / density}dp, under the bar area from ${barTop / density}dp",
            lastItemBottom <= barTop + TOLERANCE_PX,
        )
    }

    @Test @Config(qualifiers = "w360dp-h740dp")
    fun `home clears the bar in English`() = assertLastShortcutClearsBar()

    @Test @Config(qualifiers = "ne-w360dp-h740dp")
    fun `home clears the bar in Nepali`() = assertLastShortcutClearsBar()

    @Test @Config(qualifiers = "w360dp-h740dp")
    fun `home clears the bar with large text`() = assertLastShortcutClearsBar(fontScale = 1.3f)

    @Test @Config(qualifiers = "ne-w360dp-h740dp")
    fun `home clears the bar in Nepali with large text`() = assertLastShortcutClearsBar(fontScale = 1.3f)

    private companion object {
        const val SETTLE_MS = 1_000L
        const val SCROLL_TO_END_PX = 100_000f
        const val TOLERANCE_PX = 1f
    }
}
