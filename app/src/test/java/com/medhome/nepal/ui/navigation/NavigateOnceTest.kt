package com.medhome.nepal.ui.navigation

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Serializable
internal object FirstTestRoute

@Serializable
internal data class NextTestRoute(val name: String)

/** The guards that navigate from code (not taps): only while the given screen is on top. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class NavigateOnceTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var navController: NavHostController

    @Before
    fun setUp() {
        compose.setContent {
            navController = rememberNavController()
            NavHost(navController, startDestination = FirstTestRoute) {
                composable<FirstTestRoute> {}
                composable<NextTestRoute> {}
            }
        }
        compose.waitForIdle()
    }

    private fun top(): NavBackStackEntry = checkNotNull(navController.currentBackStackEntry)

    private fun topName(): String? = runCatching { top().toRoute<NextTestRoute>().name }.getOrNull()

    private fun onUi(block: () -> Unit) {
        compose.runOnUiThread(block)
        compose.waitForIdle()
    }

    @Test
    fun `navigateIfTop navigates from the screen on top`() {
        val first = top()
        onUi { navController.navigateIfTop(first, NextTestRoute("b")) }
        assertEquals("b", topName())
    }

    @Test
    fun `navigateIfTop does nothing from a screen that is no longer on top`() {
        val first = top()
        onUi { navController.navigateIfTop(first, NextTestRoute("b")) }
        // A repeat, or a late call from the screen that was left: ignored.
        onUi { navController.navigateIfTop(first, NextTestRoute("c")) }
        assertEquals("b", topName())
        // Only one screen was pushed: one Back returns to the first.
        onUi { navController.popBackStack() }
        assertEquals(first.id, top().id)
    }

    @Test
    fun `popIfTop then navigateIfTop from the screen underneath replaces the top once`() {
        val first = top()
        onUi { navController.navigateIfTop(first, NextTestRoute("form")) }
        val form = top()
        repeat(2) {
            onUi {
                navController.popIfTop(form)
                navController.navigateIfTop(first, NextTestRoute("guide"))
            }
        }
        assertEquals("guide", topName())
        onUi { navController.popBackStack() }
        assertEquals(first.id, top().id)
    }
}
