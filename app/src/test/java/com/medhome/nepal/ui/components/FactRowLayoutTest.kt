package com.medhome.nepal.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medhome.nepal.R
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.ui.common.DateStyle
import com.medhome.nepal.ui.common.LocaleFormat
import com.medhome.nepal.ui.common.currentLocale
import com.medhome.nepal.ui.common.feeText
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
 * A fact row's label never wraps or gets squeezed, whatever the value: the value wraps onto
 * more lines instead. Checked on a 360dp-wide phone in both languages, with the longest seeded
 * hospital and doctor name, a long full date, and every label the app passes to [FactRow].
 */
@RunWith(AndroidJUnit4::class)
// Real text measurement: the default (legacy) graphics mode makes every character 1px wide.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35])
class FactRowLayoutTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun showFacts() {
        compose.setContent {
            MedHomeTheme(darkTheme = false) {
                GlassScreen {
                    GlassCard(contentPadding = PaddingValues(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        FactRow(label = R.string.doctor_hospital, value = LONGEST_HOSPITAL)
                        FactRow(label = R.string.label_doctor, value = LONGEST_DOCTOR)
                        FactRow(
                            label = R.string.label_date,
                            // A long weekday and month.
                            value = LocaleFormat.date(CalendarDate(2026, 9, 30), DateStyle.FULL, currentLocale()),
                        )
                        FactRow(label = R.string.doctor_fee, value = feeText(1500))
                        FactRow(label = R.string.doctor_experience, value = "25")
                        FactRow(label = R.string.admin_slot_minutes, value = "15")
                        FactRow(label = R.string.label_time, value = "10:30")
                        FactRow(label = R.string.label_patient, value = "Pooja")
                        FactRow(label = R.string.label_specialty, value = "General physician")
                        FactRow(label = R.string.label_status, value = "Cancelled by another admin")
                        FactRow(label = R.string.label_cancelled_at, value = "Wednesday, 30 September 2026 · 10:30")
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        assertTrue("No text layout", action?.invoke(results) == true)
        return results.single()
    }

    private fun assertLabelsWholeAndValuesWrapped() {
        showFacts()
        val labels = compose.onAllNodesWithTag(FACT_LABEL_TAG, useUnmergedTree = true).fetchSemanticsNodes()
        val values = compose.onAllNodesWithTag(FACT_VALUE_TAG, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(ROWS, labels.size)
        assertEquals(ROWS, values.size)
        repeat(ROWS) { index ->
            val label = compose.onAllNodesWithTag(FACT_LABEL_TAG, useUnmergedTree = true)[index].textLayout()
            val value = compose.onAllNodesWithTag(FACT_VALUE_TAG, useUnmergedTree = true)[index].textLayout()
            val labelText = label.layoutInput.text.text
            assertEquals("Label \"$labelText\" wrapped", 1, label.lineCount)
            // Unwrapped text is laid out at any width (didOverflowWidth is always true), so
            // compare the line's own right edge with the width the label was given.
            assertTrue("Label \"$labelText\" was squeezed", label.getLineRight(0) <= label.size.width + 0.5f)
            assertFalse("Value of \"$labelText\" overflowed", value.didOverflowWidth)
            // Side by side, never overlapping.
            assertTrue(labels[index].boundsInRoot.right <= values[index].boundsInRoot.left)
        }
        // The long hospital name has room to wrap rather than squeeze its label (2 lines at most).
        val hospital = compose.onAllNodesWithTag(FACT_VALUE_TAG, useUnmergedTree = true)[0].textLayout()
        assertTrue("Hospital took ${hospital.lineCount} lines", hospital.lineCount <= 2)
    }

    @Test @Config(qualifiers = "w360dp-h740dp")
    fun `English labels stay whole next to long values`() = assertLabelsWholeAndValuesWrapped()

    @Test @Config(qualifiers = "ne-w360dp-h740dp")
    fun `Nepali labels stay whole next to long values`() = assertLabelsWholeAndValuesWrapped()

    private companion object {
        const val ROWS = 11

        /** The longest seeded hospital (tools/seed/doctors.js) and doctor name. */
        const val LONGEST_HOSPITAL = "Rhododendron Health Centre"
        const val LONGEST_DOCTOR = "Pooja Thapa Magar"
    }
}
