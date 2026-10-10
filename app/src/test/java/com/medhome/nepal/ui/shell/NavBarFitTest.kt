package com.medhome.nepal.ui.shell

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medhome.nepal.R
import com.medhome.nepal.domain.Role
import com.medhome.nepal.domain.UserProfile
import com.medhome.nepal.fakes.fakeBookingViewModels
import com.medhome.nepal.fakes.fakeReminderViewModels
import com.medhome.nepal.session.SessionState
import com.medhome.nepal.ui.theme.MedHomeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The patient's four tabs (Home, Bookings, Medicines, Records) fit the floating bar of a 360dp
 * phone: every label on one line, nothing cut off with an ellipsis (labels are one line,
 * ellipsized when too wide), in both languages and with
 * larger text.
 */
@RunWith(AndroidJUnit4::class)
// Real text measurement: the default (legacy) graphics mode makes every character 1px wide.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35])
class NavBarFitTest {

    @get:Rule
    val compose = createComposeRule()

    private val session = SessionState.SignedIn(
        profile = UserProfile("uid", "Asha Rai", "asha@example.com", Role.PATIENT),
        usesPassword = true,
    )

    private val labels = listOf(R.string.nav_home, R.string.nav_bookings, R.string.nav_medicines, R.string.nav_records)

    private fun assertLabelsFit(fontScale: Float = 1f) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            MedHomeTheme(darkTheme = false) {
                MainShell(session, bookingViewModelFactory = fakeBookingViewModels(), reminderViewModelFactory = fakeReminderViewModels())
            }
        }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        val app = ApplicationProvider.getApplicationContext<Application>()
        labels.forEach { id ->
            val label = app.getString(id)
            val results = mutableListOf<TextLayoutResult>()
            // The Home title says "Home" too in some languages: the bar's label is the last one.
            val nodes = compose.onAllNodesWithTextUnmerged(label)
            val node = nodes.last()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            val layout = results.single()
            assertEquals("\"$label\" wraps", 1, layout.lineCount)
            assertFalse("\"$label\" is cut off", layout.isLineEllipsized(0))
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextUnmerged(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text), useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.config.contains(SemanticsActions.GetTextLayoutResult) }
            .also { check(it.isNotEmpty()) { "No text \"$text\"" } }

    @Test @Config(qualifiers = "w360dp-h740dp")
    fun `four tabs fit in English`() = assertLabelsFit()

    @Test @Config(qualifiers = "ne-w360dp-h740dp")
    fun `four tabs fit in Nepali`() = assertLabelsFit()

    @Test @Config(qualifiers = "w360dp-h740dp")
    fun `four tabs fit in English with large text`() = assertLabelsFit(fontScale = 1.3f)

    @Test @Config(qualifiers = "ne-w360dp-h740dp")
    fun `four tabs fit in Nepali with large text`() = assertLabelsFit(fontScale = 1.3f)

    private companion object {
        const val SETTLE_MS = 1_000L
    }
}
