package com.medhome.nepal.ui.shell

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medhome.nepal.R
import com.medhome.nepal.domain.Role
import com.medhome.nepal.domain.UserProfile
import com.medhome.nepal.session.SessionState
import com.medhome.nepal.ui.theme.MedHomeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Home's three sections (next appointment, today's medicines, shortcuts) must all show above
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

    private fun assertShortcutsFitAboveBar() {
        compose.setContent { MedHomeTheme(darkTheme = false) { MainShell(session) } }
        compose.mainClock.advanceTimeBy(SETTLE_MS)

        val label = ApplicationProvider.getApplicationContext<Application>().getString(R.string.shortcut_medicine_reminders)
        // Unclipped position + size: bounds are clipped to the scroll viewport (0 when off screen).
        val lastRow = compose.onNodeWithText(label).fetchSemanticsNode()
        val lastRowBottom = lastRow.positionInRoot.y + lastRow.size.height
        // The tagged box includes the bar's 16dp outer margin, so its top is where the gap ends.
        val barTop = compose.onNodeWithTag(FLOATING_NAV_BAR_TAG).fetchSemanticsNode().boundsInRoot.top
        val density = compose.density.density
        println("HOME FIT: last shortcut ends ${lastRowBottom / density}dp, bar area starts ${barTop / density}dp")
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
        const val SETTLE_MS = 1_000L
        const val TOLERANCE_PX = 1f
    }
}
