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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.medhome.nepal.ui.components.FloatingBarGap
import com.medhome.nepal.ui.components.FloatingNavBar
import com.medhome.nepal.ui.components.MeshBackground
import com.medhome.nepal.ui.components.LocalBottomBarClearance
import com.medhome.nepal.ui.components.LocalHazeState
import com.medhome.nepal.ui.components.NavBarItem
import com.medhome.nepal.ui.doctors.DoctorDetailScreen
import com.medhome.nepal.ui.doctors.DoctorViewModels
import com.medhome.nepal.ui.doctors.FindDoctorScreen
import com.medhome.nepal.ui.home.ComingSoonFeature
import com.medhome.nepal.ui.home.ComingSoonScreen
import com.medhome.nepal.ui.home.HomeShortcut
import com.medhome.nepal.ui.home.PatientHomeScreen
import com.medhome.nepal.ui.motion.LocalReducedMotion
import com.medhome.nepal.ui.motion.materializeIn
import com.medhome.nepal.ui.motion.materializeOut
import com.medhome.nepal.ui.navigation.ScreenTransitions
import com.medhome.nepal.ui.navigation.navigateOnce
import com.medhome.nepal.ui.navigation.navigateOnceWith
import com.medhome.nepal.ui.navigation.popIfTop
import com.medhome.nepal.ui.profile.ProfileScreen
import com.medhome.nepal.ui.profile.ProfileViewModel
import com.medhome.nepal.ui.settings.SettingsPage
import com.medhome.nepal.ui.settings.SettingsPageScreen
import dev.chrisbanes.haze.hazeSource
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/** Which signed-in experience a role gets. The role itself is never shown in the UI. */
enum class SignedInHome { PATIENT_TABS, STAFF_PLACEHOLDER }

fun signedInHomeFor(role: Role): SignedInHome = when (role) {
    Role.PATIENT -> SignedInHome.PATIENT_TABS
    Role.DOCTOR, Role.ADMIN -> SignedInHome.STAFF_PLACEHOLDER
}

/**
 * [profileViewModelFactory] and [doctorViewModelFactory] are seams for JVM tests, which have no
 * Firebase-backed app container; the app always uses the defaults.
 */
@Composable
fun MainShell(
    session: SessionState.SignedIn,
    profileViewModelFactory: ViewModelProvider.Factory = ProfileViewModel.Factory,
    doctorViewModelFactory: ViewModelProvider.Factory = DoctorViewModels.Factory,
) {
    when (signedInHomeFor(session.profile.role)) {
        SignedInHome.PATIENT_TABS -> PatientShell(session, profileViewModelFactory, doctorViewModelFactory)
        SignedInHome.STAFF_PLACEHOLDER -> StaffHomeScreen(viewModel = viewModel(factory = profileViewModelFactory))
    }
}

// Tab graphs. Each tab is its own nested graph, so it keeps its own back stack. Profile and its
// settings pages are pushed on Home's stack (opened from the avatar).
@Serializable internal data object HomeTab
@Serializable internal data object HomeRoute
@Serializable internal data class ComingSoonRoute(val feature: ComingSoonFeature)
@Serializable internal data object FindDoctorRoute
// The property name is DoctorViewModels.ARG_DOCTOR_ID: the detail ViewModel reads it from there.
@Serializable internal data class DoctorDetailRoute(val doctorId: String)
@Serializable internal data object ProfileRoute
@Serializable internal data class SettingsRoute(val page: SettingsPage)
@Serializable internal data object BookingsTab
@Serializable internal data object BookingsRoute
@Serializable internal data object RecordsTab
@Serializable internal data object RecordsRoute

private enum class PatientTab(
    val tab: ShellTab,
    val graph: Any,
    val root: Any,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
) {
    HOME(ShellTab.HOME, HomeTab, HomeRoute, R.string.nav_home, R.drawable.ic_sym_home),
    BOOKINGS(ShellTab.BOOKINGS, BookingsTab, BookingsRoute, R.string.nav_bookings, R.drawable.ic_sym_calendar_month),
    RECORDS(ShellTab.RECORDS, RecordsTab, RecordsRoute, R.string.nav_records, R.drawable.ic_sym_description),
    ;

    val graphClass: KClass<*> get() = graph::class

    companion object {
        fun of(tab: ShellTab): PatientTab = entries.first { it.tab == tab }
    }
}

private val NavItems = PatientTab.entries.map { NavBarItem(it.label, it.icon) }

/**
 * Patient app: one mesh background, a nested NavHost with a back stack per tab, and the
 * floating tab bar on top (shown on tab roots only; pushed screens get a back arrow instead).
 * Back from a non-Home tab root returns to Home, because tab switches keep Home underneath.
 */
@Composable
private fun PatientShell(
    session: SessionState.SignedIn,
    profileViewModelFactory: ViewModelProvider.Factory,
    doctorViewModelFactory: ViewModelProvider.Factory,
) {
    val navController = rememberNavController()
    val shellNavigator = remember(navController) { ShellNavigator { navController.selectTab(PatientTab.of(it)) } }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTab = PatientTab.entries.firstOrNull { destination.isIn(it.graphClass) } ?: PatientTab.HOME
    val onTabRoot = PatientTab.entries.any { destination?.hasRoute(it.root::class) == true }
    val transitions = ScreenTransitions(
        reducedMotion = LocalReducedMotion.current,
        flows = PatientTab.entries.map { it.graphClass },
    )

    // The real bar height (its 16dp margins included; the navigation-bar inset is added by each
    // screen's own safe-drawing padding), measured so content clears it at any font size.
    var measuredBarHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val tabRootClearance = if (measuredBarHeight > 0.dp) measuredBarHeight + FloatingBarGap else FloatingBarClearance

    MeshBackground {
        val hazeState = LocalHazeState.current
        CompositionLocalProvider(LocalTabRootClearance provides tabRootClearance, LocalShellNavigator provides shellNavigator) {
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
                        TabRoot {
                            PatientHomeScreen(
                                profile = session.profile,
                                onOpenProfile = navigateOnce { navController.navigate(ProfileRoute) { launchSingleTop = true } },
                                onShortcut = navigateOnceWith { shortcut: HomeShortcut -> navController.openShortcut(shortcut, shellNavigator) },
                            )
                        }
                    }
                    composable<FindDoctorRoute> {
                        FindDoctorScreen(
                            onOpenDoctor = navigateOnceWith { id: String -> navController.navigate(DoctorDetailRoute(id)) },
                            viewModel = viewModel(factory = doctorViewModelFactory),
                        )
                    }
                    composable<DoctorDetailRoute> {
                        DoctorDetailScreen(viewModel = viewModel(factory = doctorViewModelFactory))
                    }
                    composable<ComingSoonRoute> { entry ->
                        ComingSoonScreen(title = entry.toRoute<ComingSoonRoute>().feature.title, showBack = true)
                    }
                    composable<ProfileRoute> {
                        ProfileScreen(
                            profile = session.profile,
                            usesPassword = session.usesPassword,
                            onOpenPage = navigateOnceWith { page: SettingsPage -> navController.navigate(SettingsRoute(page)) },
                            viewModel = viewModel(factory = profileViewModelFactory),
                        )
                    }
                    composable<SettingsRoute> { entry ->
                        SettingsPageScreen(
                            page = entry.toRoute<SettingsRoute>().page,
                            profile = session.profile,
                            onDone = { navController.popIfTop(entry) },
                        )
                    }
                }
                navigation<BookingsTab>(startDestination = BookingsRoute) {
                    composable<BookingsRoute> { TabRoot { ComingSoonScreen(title = R.string.nav_bookings, showBack = false) } }
                }
                navigation<RecordsTab>(startDestination = RecordsRoute) {
                    composable<RecordsRoute> { TabRoot { ComingSoonScreen(title = R.string.nav_records, showBack = false) } }
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
            Box(
                modifier = Modifier
                    .testTag(FLOATING_NAV_BAR_TAG)
                    .onSizeChanged { size ->
                        // Hidden (0) while a pushed screen shows: keep the last real measurement.
                        if (size.height > 0) measuredBarHeight = with(density) { size.height.toDp() }
                    },
            ) {
                FloatingNavBar(
                    items = NavItems,
                    selectedIndex = currentTab.ordinal,
                    onSelect = { navController.selectTab(PatientTab.entries[it]) },
                    animateIn = false,
                )
            }
        }
    }
}

/** Test tag on the floating tab bar, for layout tests. */
const val FLOATING_NAV_BAR_TAG = "floating_nav_bar"

/** The measured clearance for tab roots: bar height + [FloatingBarGap]. */
private val LocalTabRootClearance = compositionLocalOf { FloatingBarClearance }

/**
 * Tab roots keep space for the floating bar so their last item can scroll fully above it. Set
 * per screen (not shell-wide) so a screen sliding out keeps its own padding instead of jumping
 * when the bar's visibility changes.
 */
@Composable
private fun TabRoot(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBottomBarClearance provides LocalTabRootClearance.current, content = content)
}

private fun NavDestination?.isIn(graph: KClass<*>): Boolean =
    this?.hierarchy?.any { it.hasRoute(graph) } == true

/** Find a doctor and placeholders are pushed on Home; Health records is a tab of its own. */
private fun NavHostController.openShortcut(shortcut: HomeShortcut, shell: ShellNavigator) {
    when (shortcut) {
        HomeShortcut.FIND_DOCTOR -> navigate(FindDoctorRoute)
        HomeShortcut.MEDICINE_REMINDERS -> navigate(ComingSoonRoute(ComingSoonFeature.MEDICINE_REMINDERS))
        HomeShortcut.HEALTH_RECORDS -> shell.selectTab(ShellTab.RECORDS)
    }
}

/**
 * Standard multiple-back-stack switch: save the current tab's stack, restore the target's.
 * Re-selecting the current tab returns it to its root.
 */
private fun NavHostController.selectTab(tab: PatientTab) {
    val currentTab = PatientTab.entries.firstOrNull { currentDestination.isIn(it.graphClass) }
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
