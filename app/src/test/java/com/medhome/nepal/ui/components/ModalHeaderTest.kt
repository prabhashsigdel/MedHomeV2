package com.medhome.nepal.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medhome.nepal.R
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.MedHomeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every sheet's and dialog's header on a 360dp phone in both languages: the title never runs
 * under the close button (it wraps instead) and is never cut off, and the button keeps its 48dp
 * target and "Close" label. Dialogs are measured as headers only (a GlassDialog never settles
 * under Robolectric), at a conservative width for a dialog on a 360dp phone.
 */
@RunWith(AndroidJUnit4::class)
// Real text measurement: the default (legacy) graphics mode makes every character 1px wide.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35])
class ModalHeaderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(@StringRes id: Int): String = ApplicationProvider.getApplicationContext<Application>().getString(id)

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        assertTrue("No text layout", action?.invoke(results) == true)
        return results.single()
    }

    private fun assertHeadersFit() {
        compose.setContent {
            MedHomeTheme(darkTheme = false) {
                // Scrolls, so the headers below the window aren't squeezed into what's left of it.
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SHEET_TITLES.forEach { title ->
                        Box(Modifier.width(SHEET_HEADER_WIDTH)) {
                            ModalHeader(text(title), MaterialTheme.typography.titleLarge, onClose = {}, closeEnabled = true)
                        }
                    }
                    DIALOG_TITLES.forEach { title ->
                        Box(Modifier.width(DIALOG_HEADER_WIDTH)) {
                            ModalHeader(text(title), MaterialTheme.typography.headlineSmall, onClose = {}, closeEnabled = true)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val count = SHEET_TITLES.size + DIALOG_TITLES.size
        val titles = compose.onAllNodesWithTag(MODAL_TITLE_TAG, useUnmergedTree = true)
        val closes = compose.onAllNodesWithTag(MODAL_CLOSE_TAG, useUnmergedTree = true)
        assertEquals(count, titles.fetchSemanticsNodes().size)
        assertEquals(count, closes.fetchSemanticsNodes().size)
        val density = compose.density.density
        repeat(count) { index ->
            val title = titles[index].fetchSemanticsNode()
            val close = closes[index].fetchSemanticsNode()
            val layout = titles[index].textLayout()
            val name = layout.layoutInput.text.text
            assertTrue("\"$name\" runs under the close button", title.boundsInRoot.right <= close.boundsInRoot.left)
            assertFalse("\"$name\" is cut off", layout.hasVisualOverflow)
            assertEquals("Close target of \"$name\"", 48f, close.size.width / density, 0.5f)
            assertEquals("Close target of \"$name\"", 48f, close.size.height / density, 0.5f)
        }
    }

    @Test @Config(qualifiers = "w360dp-h740dp")
    fun `English sheet and dialog titles clear the close button`() = assertHeadersFit()

    @Test @Config(qualifiers = "ne-w360dp-h740dp")
    fun `Nepali sheet and dialog titles clear the close button`() = assertHeadersFit()

    @Test
    fun `the close button is labelled Close and closes, unless disabled`() {
        var closes = 0
        var enabled by mutableStateOf(true)
        compose.setContent {
            MedHomeTheme(darkTheme = false) {
                ModalHeader("Title", MaterialTheme.typography.titleLarge, onClose = { closes++ }, closeEnabled = enabled)
            }
        }
        compose.onNodeWithContentDescription(text(R.string.action_close)).assertIsEnabled().performClick()
        assertEquals(1, closes)

        enabled = false
        compose.onNodeWithContentDescription(text(R.string.action_close)).assertIsNotEnabled().performClick()
        assertEquals(1, closes)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun `a locked sheet's close button is disabled`() {
        var dismissed = 0
        compose.setContent {
            MedHomeTheme(darkTheme = false) {
                GlassBottomSheet(
                    onDismissRequest = { dismissed++ },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    title = R.string.book_confirm_title,
                    locked = true,
                ) { Text("Sending") }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(MODAL_CLOSE_TAG).assertIsNotEnabled().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(GLASS_SHEET_TAG).assertExists()
        assertEquals(0, dismissed)
    }

    private companion object {
        /** A 360dp sheet less its body's side padding. */
        val SHEET_HEADER_WIDTH: Dp = 360.dp - GlassDimens.CardPadding * 2

        /** A dialog on a 360dp phone (window margins and the panel's 24dp padding taken off). */
        val DIALOG_HEADER_WIDTH: Dp = 264.dp

        val SHEET_TITLES = listOf(
            R.string.book_confirm_title,
            R.string.book_success_title,
            R.string.settings_theme,
            R.string.profile_language,
            R.string.admin_field_specialty,
        )

        val DIALOG_TITLES = listOf(
            R.string.booking_cancel_confirm_title,
            R.string.admin_cancel_booking_title,
            R.string.admin_show_title,
            R.string.admin_hide_title,
            R.string.admin_hidden_title,
            R.string.delete_title,
            R.string.medicine_delete_title,
            R.string.reminder_setup_title,
        )
    }
}
