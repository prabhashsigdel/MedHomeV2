package com.medhome.nepal.ui.components

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medhome.nepal.R
import com.medhome.nepal.data.ThemeMode
import com.medhome.nepal.ui.language.AppLanguage
import com.medhome.nepal.ui.language.LanguageController
import com.medhome.nepal.ui.language.LanguageSetting
import com.medhome.nepal.ui.language.LocalLanguageController
import com.medhome.nepal.ui.theme.LocalThemeController
import com.medhome.nepal.ui.theme.MedHomeTheme
import com.medhome.nepal.ui.theme.ThemeController
import com.medhome.nepal.ui.theme.ThemeSetting
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Theme and Language rows: each shows its current value, opens a sheet with that option checked,
 * and closes it on a choice. Theme applies on tap; Language only once the sheet has gone.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class SettingsSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val chosenModes = mutableListOf<ThemeMode>()
    private val chosenLanguages = mutableListOf<AppLanguage>()
    private val isRadio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private fun text(@StringRes id: Int): String =
        ApplicationProvider.getApplicationContext<Application>().getString(id)

    @Before
    fun showRows() {
        compose.setContent {
            MedHomeTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalThemeController provides ThemeController(ThemeMode.DARK) { chosenModes += it },
                    LocalLanguageController provides LanguageController { chosenLanguages += it },
                ) {
                    Column {
                        ThemeSetting()
                        LanguageSetting()
                    }
                }
            }
        }
    }

    private fun openRow(@StringRes label: Int) {
        compose.onNode(hasText(text(label)) and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(GLASS_SHEET_TAG).assertExists()
    }

    private fun radio(label: String) = compose.onNode(isRadio and hasText(label))

    @Test
    fun `the rows show the current values`() {
        compose.onNode(hasText(text(R.string.settings_theme)) and hasText(text(R.string.theme_dark))).assertExists()
        compose.onNode(hasText(text(R.string.profile_language)) and hasText(text(R.string.language_english))).assertExists()
    }

    @Test
    fun `the theme sheet checks the current option`() {
        openRow(R.string.settings_theme)
        radio(text(R.string.theme_dark)).assertIsSelected()
        radio(text(R.string.theme_light)).assertIsNotSelected()
        radio(text(R.string.theme_system)).assertIsNotSelected()
    }

    @Test
    fun `choosing a theme applies it straight away and closes the sheet`() {
        openRow(R.string.settings_theme)
        compose.mainClock.autoAdvance = false
        radio(text(R.string.theme_light)).performClick()
        assertEquals(listOf(ThemeMode.LIGHT), chosenModes)

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag(GLASS_SHEET_TAG).assertDoesNotExist()
        assertEquals(listOf(ThemeMode.LIGHT), chosenModes)
    }

    @Test
    fun `choosing a language applies it only after the sheet has closed`() {
        openRow(R.string.profile_language)
        compose.mainClock.autoAdvance = false
        radio(text(R.string.language_nepali)).performClick()
        compose.mainClock.advanceTimeByFrame()
        assertEquals("Applied while the sheet was still closing", emptyList<AppLanguage>(), chosenLanguages)

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag(GLASS_SHEET_TAG).assertDoesNotExist()
        assertEquals(listOf(AppLanguage.NEPALI), chosenLanguages)
    }

    @Test
    fun `the close button closes the sheet without choosing anything`() {
        openRow(R.string.settings_theme)
        compose.onNodeWithContentDescription(text(R.string.action_close)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(GLASS_SHEET_TAG).assertDoesNotExist()
        assertEquals(emptyList<ThemeMode>(), chosenModes)

        // And it opens again afterwards, so the dismissal went through.
        openRow(R.string.profile_language)
        compose.onNodeWithContentDescription(text(R.string.action_close)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(GLASS_SHEET_TAG).assertDoesNotExist()
        assertEquals(emptyList<AppLanguage>(), chosenLanguages)
    }

    @Test
    fun `choosing the current option just closes the sheet`() {
        openRow(R.string.profile_language)
        radio(text(R.string.language_english)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(GLASS_SHEET_TAG).assertDoesNotExist()
        assertEquals(emptyList<AppLanguage>(), chosenLanguages)
    }
}
