package com.medhome.nepal.ui.admin

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.medhome.nepal.R
import com.medhome.nepal.session.SessionState
import com.medhome.nepal.ui.components.NavBarItem
import com.medhome.nepal.ui.motion.LocalReducedMotion
import com.medhome.nepal.ui.navigation.ScreenTransitions
import com.medhome.nepal.ui.navigation.navigateOnce
import com.medhome.nepal.ui.navigation.navigateOnceWith
import com.medhome.nepal.ui.navigation.popIfTop
import com.medhome.nepal.ui.profile.ProfileViewModel
import com.medhome.nepal.ui.shell.FloatingTabShell
import com.medhome.nepal.ui.shell.TabRoot
import com.medhome.nepal.ui.shell.isIn
import com.medhome.nepal.ui.shell.switchTab
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

// Tab graphs, each with its own back stack, as in the patient shell.
@Serializable internal data object AdminDoctorsTab
@Serializable internal data object AdminDoctorsRoute
// The property name is AdminViewModels.ARG_DOCTOR_ID.
@Serializable internal data class AdminDoctorRoute(val doctorId: String)
// Null adds a new doctor. The property name is AdminViewModels.ARG_DOCTOR_ID.
@Serializable internal data class DoctorFormRoute(val doctorId: String? = null)
@Serializable internal data object AdminBookingsTab
@Serializable internal data object AdminBookingsRoute
// The property name is AdminViewModels.ARG_BOOKING_ID.
@Serializable internal data class AdminBookingRoute(val bookingId: String)
@Serializable internal data object AdminProfileTab
@Serializable internal data object AdminProfileRoute

private enum class AdminTab(
    val graph: Any,
    val root: Any,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
) {
    DOCTORS(AdminDoctorsTab, AdminDoctorsRoute, R.string.nav_doctors, R.drawable.ic_sym_home),
    BOOKINGS(AdminBookingsTab, AdminBookingsRoute, R.string.nav_bookings, R.drawable.ic_sym_calendar_month),
    PROFILE(AdminProfileTab, AdminProfileRoute, R.string.profile_title, R.drawable.ic_sym_person),
    ;

    val graphClass: KClass<*> get() = graph::class
}

private val AdminNavItems = AdminTab.entries.map { NavBarItem(it.label, it.icon) }

/**
 * The admin app: the floating tab bar (shown on tab roots) over three tabs, each with its own
 * stack. Doctors (a doctor and the add / edit form are pushed on it), Bookings (every doctor's
 * bookings; a booking is pushed on it) and Profile (language, theme, sign out).
 */
@Composable
fun AdminShell(
    session: SessionState.SignedIn,
    adminViewModelFactory: ViewModelProvider.Factory,
    profileViewModelFactory: ViewModelProvider.Factory,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTab = AdminTab.entries.firstOrNull { destination.isIn(it.graphClass) } ?: AdminTab.DOCTORS
    val onTabRoot = AdminTab.entries.any { destination?.hasRoute(it.root::class) == true }
    val transitions = ScreenTransitions(reducedMotion = LocalReducedMotion.current, flows = AdminTab.entries.map { it.graphClass })

    FloatingTabShell(
        items = AdminNavItems,
        selectedIndex = currentTab.ordinal,
        onTabRoot = onTabRoot,
        onSelect = { navController.selectTab(AdminTab.entries[it]) },
    ) { navHostModifier ->
        NavHost(
            navController = navController,
            startDestination = AdminDoctorsTab,
            modifier = navHostModifier,
            enterTransition = { transitions.enter(this) },
            exitTransition = { transitions.exit(this) },
            popEnterTransition = { transitions.popEnter(this) },
            popExitTransition = { transitions.popExit(this) },
            predictivePopEnterTransition = { transitions.predictivePopEnter(this) },
            predictivePopExitTransition = { transitions.predictivePopExit(this) },
        ) {
            navigation<AdminDoctorsTab>(startDestination = AdminDoctorsRoute) {
                composable<AdminDoctorsRoute> {
                    TabRoot {
                        AdminDoctorsScreen(
                            viewModel = viewModel(factory = adminViewModelFactory),
                            onOpenDoctor = navigateOnceWith { id: String -> navController.navigate(AdminDoctorRoute(id)) },
                            onAddDoctor = navigateOnce { navController.navigate(DoctorFormRoute()) },
                        )
                    }
                }
                composable<AdminDoctorRoute> {
                    AdminDoctorScreen(
                        viewModel = viewModel(factory = adminViewModelFactory),
                        onEdit = navigateOnceWith { id: String -> navController.navigate(DoctorFormRoute(id)) },
                    )
                }
                composable<DoctorFormRoute> { entry ->
                    DoctorFormScreen(
                        viewModel = viewModel(factory = adminViewModelFactory),
                        onSaved = { navController.popIfTop(entry) },
                    )
                }
            }
            navigation<AdminBookingsTab>(startDestination = AdminBookingsRoute) {
                composable<AdminBookingsRoute> {
                    TabRoot {
                        AdminBookingsScreen(
                            viewModel = viewModel(factory = adminViewModelFactory),
                            onOpenBooking = navigateOnceWith { id: String -> navController.navigate(AdminBookingRoute(id)) },
                        )
                    }
                }
                composable<AdminBookingRoute> {
                    AdminBookingScreen(viewModel = viewModel(factory = adminViewModelFactory))
                }
            }
            navigation<AdminProfileTab>(startDestination = AdminProfileRoute) {
                composable<AdminProfileRoute> {
                    TabRoot {
                        AdminProfileScreen(
                            profile = session.profile,
                            viewModel = viewModel<ProfileViewModel>(factory = profileViewModelFactory),
                        )
                    }
                }
            }
        }
    }
}

/** Switches to [tab]; re-selecting the current tab returns it to its root. */
private fun NavHostController.selectTab(tab: AdminTab) {
    val currentTab = AdminTab.entries.firstOrNull { currentDestination.isIn(it.graphClass) }
    switchTab(tab.graph, tab.root, isCurrent = tab == currentTab)
}
