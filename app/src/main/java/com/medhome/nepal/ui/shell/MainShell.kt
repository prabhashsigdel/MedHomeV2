package com.medhome.nepal.ui.shell

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.medhome.nepal.R
import com.medhome.nepal.domain.Role
import com.medhome.nepal.session.SessionState
import com.medhome.nepal.ui.components.FloatingBarClearance
import com.medhome.nepal.ui.components.FloatingNavBar
import com.medhome.nepal.ui.components.GlassBackground
import com.medhome.nepal.ui.components.LocalBottomBarClearance
import com.medhome.nepal.ui.components.LocalHazeState
import com.medhome.nepal.ui.components.NavBarItem
import com.medhome.nepal.ui.home.ComingSoonFeature
import com.medhome.nepal.ui.home.ComingSoonScreen
import com.medhome.nepal.ui.home.PatientHomeScreen
import com.medhome.nepal.ui.motion.LocalReducedMotion
import com.medhome.nepal.ui.motion.materializeIn
import com.medhome.nepal.ui.motion.materializeOut
import com.medhome.nepal.ui.navigation.ScreenTransitions
import com.medhome.nepal.ui.profile.ProfileScreen
import dev.chrisbanes.haze.hazeSource
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/** Which signed-in experience a role gets. The role itself is never shown in the UI. */
enum class SignedInHome { PATIENT_TABS, STAFF_PLACEHOLDER }

fun signedInHomeFor(role: Role): SignedInHome = when (role) {
    Role.PATIENT -> SignedInHome.PATIENT_TABS
    Role.DOCTOR, Role.ADMIN -> SignedInHome.STAFF_PLACEHOLDER
}

@Composable
fun MainShell(session: SessionState.SignedIn) {
    when (signedInHomeFor(session.profile.role)) {
        SignedInHome.PATIENT_TABS -> PatientShell(session)
        SignedInHome.STAFF_PLACEHOLDER -> StaffHomeScreen()
    }
}

// Tab graphs. Each tab is its own nested graph, so it keeps its own back stack.
@Serializable private data object HomeTab
@Serializable private data object HomeRoute
@Serializable private data class ComingSoonRoute(val feature: ComingSoonFeature)
@Serializable private data object BookingsTab
@Serializable private data object BookingsRoute
@Serializable private data object RecordsTab
@Serializable private data object RecordsRoute
@Serializable private data object ProfileTab
@Serializable private data object ProfileRoute

private enum class PatientTab(
    val graph: Any,
    val root: Any,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
) {
    HOME(HomeTab, HomeRoute, R.string.nav_home, R.drawable.ic_nav_home),
    BOOKINGS(BookingsTab, BookingsRoute, R.string.nav_bookings, R.drawable.ic_nav_calendar),
    RECORDS(RecordsTab, RecordsRoute, R.string.nav_records, R.drawable.ic_nav_records),
    PROFILE(ProfileTab, ProfileRoute, R.string.nav_profile, R.drawable.ic_nav_person),
    ;

    val graphClass: KClass<*> get() = graph::class
}

private val NavItems = PatientTab.entries.map { NavBarItem(it.label, it.icon) }

/**
 * Patient app: one glass background, a nested NavHost with a back stack per tab, and the
 * floating tab bar on top (shown on tab roots only; pushed screens get a back arrow instead).
 * Back from a non-Home tab root returns to Home, because tab switches keep Home underneath.
 */
@Composable
private fun PatientShell(session: SessionState.SignedIn) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTab = PatientTab.entries.firstOrNull { destination.isIn(it.graphClass) } ?: PatientTab.HOME
    val onTabRoot = PatientTab.entries.any { destination?.hasRoute(it.root::class) == true }
    val transitions = ScreenTransitions(
        reducedMotion = LocalReducedMotion.current,
        flows = PatientTab.entries.map { it.graphClass },
    )

    GlassBackground {
        val hazeState = LocalHazeState.current
        CompositionLocalProvider(LocalBottomBarClearance provides if (onTabRoot) FloatingBarClearance else 0.dp) {
            NavHost(
                navController = navController,
                startDestination = HomeTab,
                // Content is a blur source above the background, so the bar blurs what scrolls under it.
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier),
                enterTransition = { transitions.enter(this) },
                exitTransition = { transitions.exit(this) },
                popEnterTransition = { transitions.popEnter(this) },
                popExitTransition = { transitions.popExit(this) },
            ) {
                navigation<HomeTab>(startDestination = HomeRoute) {
                    composable<HomeRoute> {
                        PatientHomeScreen(
                            profile = session.profile,
                            onOpenFeature = { navController.navigate(ComingSoonRoute(it)) },
                        )
                    }
                    composable<ComingSoonRoute> { entry ->
                        ComingSoonScreen(title = entry.toRoute<ComingSoonRoute>().feature.title, showBack = true)
                    }
                }
                navigation<BookingsTab>(startDestination = BookingsRoute) {
                    composable<BookingsRoute> { ComingSoonScreen(title = R.string.nav_bookings, showBack = false) }
                }
                navigation<RecordsTab>(startDestination = RecordsRoute) {
                    composable<RecordsRoute> { ComingSoonScreen(title = R.string.nav_records, showBack = false) }
                }
                navigation<ProfileTab>(startDestination = ProfileRoute) {
                    composable<ProfileRoute> {
                        ProfileScreen(profile = session.profile, usesPassword = session.usesPassword)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = onTabRoot,
            enter = materializeIn(),
            exit = materializeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
        ) {
            Box {
                FloatingNavBar(
                    items = NavItems,
                    selectedIndex = currentTab.ordinal,
                    onSelect = { navController.selectTab(PatientTab.entries[it], currentTab) },
                    animateIn = false,
                )
            }
        }
    }
}

private fun NavDestination?.isIn(graph: KClass<*>): Boolean =
    this?.hierarchy?.any { it.hasRoute(graph) } == true

/**
 * Standard multiple-back-stack switch: save the current tab's stack, restore the target's.
 * Re-selecting the current tab returns it to its root.
 */
private fun NavHostController.selectTab(tab: PatientTab, currentTab: PatientTab) {
    if (tab == currentTab) {
        popBackStack(tab.root, inclusive = false)
        return
    }
    navigate(tab.graph) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
