package com.medhome.nepal.ui.home

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medhome.nepal.ui.theme.MedHomeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The avatar shows initials when the name has letters, and a person icon when it has none. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class HomeHeaderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(name: String) = compose.setContent {
        MedHomeTheme(darkTheme = false) {
            HomeHeader(name = name, firstName = null, onOpenProfile = {})
        }
    }

    @Test
    fun `a name without letters shows the person icon`() {
        show("  ")
        compose.onNodeWithTag(HOME_AVATAR_ICON_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a name with letters shows initials instead of the icon`() {
        show("Asha Rai")
        compose.onNodeWithTag(HOME_AVATAR_TAG).assertExists()
        compose.onNodeWithTag(HOME_AVATAR_ICON_TAG, useUnmergedTree = true).assertDoesNotExist()
    }
}
