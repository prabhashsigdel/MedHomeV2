package com.medhome.nepal.ui.shell

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavBackStackEntry
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
import com.medhome.nepal.ui.components.NavBarItem
import com.medhome.nepal.ui.admin.AdminShell
import com.medhome.nepal.ui.admin.AdminViewModels
import com.medhome.nepal.ui.booking.BookAppointmentScreen
import com.medhome.nepal.ui.booking.BookingDetailScreen
import com.medhome.nepal.ui.booking.BookingViewModels
import com.medhome.nepal.ui.booking.BookingsScreen
import com.medhome.nepal.ui.booking.NextAppointmentViewModel
import com.medhome.nepal.ui.doctors.DoctorDetailScreen
import com.medhome.nepal.ui.doctors.DoctorViewModels
import com.medhome.nepal.ui.doctors.FindDoctorScreen
import com.medhome.nepal.ui.home.ComingSoonScreen
import com.medhome.nepal.ui.home.HomeShortcut
import com.medhome.nepal.ui.home.PatientHomeScreen
import com.medhome.nepal.ui.motion.LocalReducedMotion
import com.medhome.nepal.ui.navigation.ScreenTransitions
import com.medhome.nepal.ui.navigation.navigateOnce
import com.medhome.nepal.ui.navigation.navigateOnceWith
import com.medhome.nepal.ui.navigation.navigateIfTop
import com.medhome.nepal.ui.navigation.popIfTop
import com.medhome.nepal.ui.profile.ProfileScreen
import com.medhome.nepal.ui.profile.ProfileViewModel
import com.medhome.nepal.ui.reminders.FormResult
import com.medhome.nepal.ui.reminders.MedicineFormScreen
import com.medhome.nepal.ui.reminders.MedicineFormViewModel
import com.medhome.nepal.ui.reminders.MedicinesScreen
import com.medhome.nepal.ui.reminders.ReminderViewModels
import com.medhome.nepal.ui.reminders.TodayRemindersScreen
import com.medhome.nepal.ui.reminders.TodayRemindersViewModel
import com.medhome.nepal.ui.reminders.rememberNotificationPermissionRequest
import com.medhome.nepal.ui.settings.SettingsPage
import com.medhome.nepal.ui.settings.SettingsPageScreen
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/** Which signed-in experience a role gets. The role itself is never shown in the UI. */
enum class SignedInHome { PATIENT_TABS, ADMIN_PANEL, STAFF_PLACEHOLDER }

fun signedInHomeFor(role: Role): SignedInHome = when (role) {
    Role.PATIENT -> SignedInHome.PATIENT_TABS
    Role.ADMIN -> SignedInHome.ADMIN_PANEL
    Role.DOCTOR -> SignedInHome.STAFF_PLACEHOLDER
}

/**
 * The ViewModel factories are seams for JVM tests, which have no Firebase-backed app container;
 * the app always uses the defaults.
 */
@Composable
fun MainShell(
    session: SessionState.SignedIn,
    profileViewModelFactory: ViewModelProvider.Factory = ProfileViewModel.Factory,
    doctorViewModelFactory: ViewModelProvider.Factory = DoctorViewModels.Factory,
    bookingViewModelFactory: ViewModelProvider.Factory = BookingViewModels.Factory,
    adminViewModelFactory: ViewModelProvider.Factory = AdminViewModels.Factory,
    reminderViewModelFactory: ViewModelProvider.Factory = ReminderViewModels.Factory,
) {
    when (signedInHomeFor(session.profile.role)) {
        SignedInHome.PATIENT_TABS -> PatientShell(
            session,
            ShellFactories(profileViewModelFactory, doctorViewModelFactory, bookingViewModelFactory, reminderViewModelFactory),
        )
        SignedInHome.ADMIN_PANEL -> AdminShell(session, adminViewModelFactory, profileViewModelFactory)
        SignedInHome.STAFF_PLACEHOLDER -> StaffHomeScreen(viewModel = viewModel(factory = profileViewModelFactory))
    }
}

// Tab graphs. Each tab is its own nested graph, so it keeps its own back stack. Profile and its
// settings pages are pushed on Home's stack (opened from the avatar).
@Serializable internal data object HomeTab
@Serializable internal data object HomeRoute
@Serializable internal data object MedicinesRoute
// The property name is ReminderViewModels.ARG_MEDICINE_ID; 0 adds a new medicine.
@Serializable internal data class MedicineFormRoute(val medicineId: Long)
@Serializable internal data object TodayRemindersRoute
@Serializable internal data object FindDoctorRoute
// The property name is DoctorViewModels.ARG_DOCTOR_ID: the detail ViewModel reads it from there.
@Serializable internal data class DoctorDetailRoute(val doctorId: String)
// The property name is BookingViewModels.ARG_DOCTOR_ID.
@Serializable internal data class BookAppointmentRoute(val doctorId: String)
@Serializable internal data object ProfileRoute
@Serializable internal data class SettingsRoute(val page: SettingsPage)
@Serializable internal data object BookingsTab
@Serializable internal data object BookingsRoute
// The property name is BookingViewModels.ARG_BOOKING_ID.
@Serializable internal data class BookingDetailRoute(val bookingId: String)
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

/** The patient shell's ViewModel factories (fakes in JVM tests). */
private class ShellFactories(
    val profile: ViewModelProvider.Factory,
    val doctors: ViewModelProvider.Factory,
    val bookings: ViewModelProvider.Factory,
    val reminders: ViewModelProvider.Factory,
)

/**
 * Patient app: one mesh background, a nested NavHost with a back stack per tab, and the
 * floating tab bar on top (shown on tab roots only; pushed screens get a back arrow instead).
 * Back from a non-Home tab root returns to Home, because tab switches keep Home underneath.
 */
@Composable
private fun PatientShell(session: SessionState.SignedIn, factories: ShellFactories) {
    val profileViewModelFactory = factories.profile
    val doctorViewModelFactory = factories.doctors
    val bookingViewModelFactory = factories.bookings
    val reminderViewModelFactory = factories.reminders
    val navController = rememberNavController()
    // A booking sets its first reminders: ask for notifications then (Android 13+), never at launch.
    val askNotifications = rememberNotificationPermissionRequest()
    val shellNavigator = remember(navController) { ShellNavigator { navController.selectTab(PatientTab.of(it)) } }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTab = PatientTab.entries.firstOrNull { destination.isIn(it.graphClass) } ?: PatientTab.HOME
    val onTabRoot = PatientTab.entries.any { destination?.hasRoute(it.root::class) == true }
    val transitions = ScreenTransitions(
        reducedMotion = LocalReducedMotion.current,
        flows = PatientTab.entries.map { it.graphClass },
    )

    FloatingTabShell(
        items = NavItems,
        selectedIndex = currentTab.ordinal,
        onTabRoot = onTabRoot,
        onSelect = { navController.selectTab(PatientTab.entries[it]) },
    ) { navHostModifier ->
        CompositionLocalProvider(LocalShellNavigator provides shellNavigator) {
            NavHost(
                navController = navController,
                startDestination = HomeTab,
                modifier = navHostModifier,
                enterTransition = { transitions.enter(this) },
                exitTransition = { transitions.exit(this) },
                popEnterTransition = { transitions.popEnter(this) },
                popExitTransition = { transitions.popExit(this) },
                predictivePopEnterTransition = { transitions.predictivePopEnter(this) },
                predictivePopExitTransition = { transitions.predictivePopExit(this) },
            ) {
                navigation<HomeTab>(startDestination = HomeRoute) {
                    composable<HomeRoute> {
                        val nextAppointment = viewModel<NextAppointmentViewModel>(factory = bookingViewModelFactory)
                        val next by nextAppointment.state.collectAsStateWithLifecycle()
                        val todayReminders = viewModel<TodayRemindersViewModel>(factory = reminderViewModelFactory)
                        val today by todayReminders.state.collectAsStateWithLifecycle()
                        TabRoot {
                            PatientHomeScreen(
                                profile = session.profile,
                                onOpenProfile = navigateOnce { navController.navigate(ProfileRoute) { launchSingleTop = true } },
                                onShortcut = navigateOnceWith { shortcut: HomeShortcut -> navController.openShortcut(shortcut, shellNavigator) },
                                nextAppointment = next,
                                onOpenBookings = { shellNavigator.selectTab(ShellTab.BOOKINGS) },
                                today = today,
                                onToggleDose = todayReminders::toggleTaken,
                                onOpenReminders = navigateOnce { navController.navigate(TodayRemindersRoute) { launchSingleTop = true } },
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
                        DoctorDetailScreen(
                            viewModel = viewModel(factory = doctorViewModelFactory),
                            onBook = navigateOnceWith { id: String -> navController.navigate(BookAppointmentRoute(id)) },
                        )
                    }
                    composable<BookAppointmentRoute> { entry ->
                        BookAppointmentScreen(
                            viewModel = viewModel(factory = bookingViewModelFactory),
                            onBooked = {
                                if (navController.currentBackStackEntry?.id == entry.id) askNotifications()
                                navController.finishBooking(entry)
                            },
                        )
                    }
                    composable<MedicinesRoute> {
                        MedicinesScreen(
                            viewModel = viewModel(factory = reminderViewModelFactory),
                            onAdd = navigateOnce { navController.navigate(MedicineFormRoute(MedicineFormViewModel.NEW)) },
                            onOpen = navigateOnceWith { id: Long -> navController.navigate(MedicineFormRoute(id)) },
                        )
                    }
                    composable<MedicineFormRoute> { entry ->
                        MedicineFormScreen(
                            viewModel = viewModel(factory = reminderViewModelFactory),
                            onDone = { result -> navController.closeMedicineForm(entry, result) },
                        )
                    }
                    composable<TodayRemindersRoute> {
                        TodayRemindersScreen(
                            viewModel = viewModel(factory = reminderViewModelFactory),
                            onOpenMedicines = navigateOnce { navController.navigate(MedicinesRoute) { launchSingleTop = true } },
                        )
                    }
                    composable<ProfileRoute> {
                        ProfileScreen(
                            profile = session.profile,
                            usesPassword = session.usesPassword,
                            onOpenPage = navigateOnceWith { page: SettingsPage -> navController.navigate(SettingsRoute(page)) },
                            viewModel = viewModel(factory = profileViewModelFactory),
                            reminderSettings = viewModel(factory = reminderViewModelFactory),
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
                    composable<BookingsRoute> {
                        TabRoot {
                            BookingsScreen(
                                viewModel = viewModel(factory = bookingViewModelFactory),
                                onOpenBooking = navigateOnceWith { id: String -> navController.navigate(BookingDetailRoute(id)) },
                                onBookAppointment = navigateOnce { navController.startBooking() },
                            )
                        }
                    }
                    composable<BookingDetailRoute> {
                        BookingDetailScreen(viewModel = viewModel(factory = bookingViewModelFactory))
                    }
                }
                navigation<RecordsTab>(startDestination = RecordsRoute) {
                    composable<RecordsRoute> { TabRoot { ComingSoonScreen(title = R.string.nav_records, showBack = false) } }
                }
            }
        }
    }
}

/** Find a doctor and Medicine reminders are pushed on Home; Health records is a tab of its own. */
private fun NavHostController.openShortcut(shortcut: HomeShortcut, shell: ShellNavigator) {
    when (shortcut) {
        HomeShortcut.FIND_DOCTOR -> navigate(FindDoctorRoute)
        HomeShortcut.MEDICINE_REMINDERS -> navigate(MedicinesRoute)
        HomeShortcut.HEALTH_RECORDS -> shell.selectTab(ShellTab.RECORDS)
    }
}

/**
 * Book appointment on the Bookings tab: the booking flow lives on Home's stack, so switch to
 * Home, back to its root, and open Find a doctor there. Back then returns to Home, and
 * [finishBooking] brings the user back to Bookings with the new booking.
 */
private fun NavHostController.startBooking() {
    selectTab(PatientTab.HOME)
    popBackStack(HomeRoute, inclusive = false)
    navigate(FindDoctorRoute)
}

/**
 * Closes the medicine form (only while it is on top, so once). The first time a reminder is set,
 * the battery guide opens in its place, so Back from the guide returns to the medicines.
 */
private fun NavHostController.closeMedicineForm(entry: NavBackStackEntry, result: FormResult) {
    if (currentBackStackEntry?.id != entry.id) return
    val medicines = previousBackStackEntry
    popIfTop(entry)
    if (result is FormResult.Saved && result.showBatteryGuide && medicines != null) {
        navigateIfTop(medicines, SettingsRoute(SettingsPage.BATTERY_GUIDE))
    }
}

/**
 * After a booking: the booking flow (Find a doctor, the doctor, the slot picker) leaves Home's
 * stack, so Back can't return to a confirmed booking, and a fresh Bookings tab opens with the
 * new booking under Upcoming. Only while [entry] (the slot picker) is on top, so it runs once.
 */
private fun NavHostController.finishBooking(entry: NavBackStackEntry) {
    // The same check as popIfTop: the button, Back and the sheet's dismiss may all call this.
    if (currentBackStackEntry?.id != entry.id) return
    popBackStack(HomeRoute, inclusive = false)
    clearBackStack(BookingsTab)
    selectTab(PatientTab.BOOKINGS)
}

/** Switches to [tab]; re-selecting the current tab returns it to its root. */
private fun NavHostController.selectTab(tab: PatientTab) {
    val currentTab = PatientTab.entries.firstOrNull { currentDestination.isIn(it.graphClass) }
    switchTab(tab.graph, tab.root, isCurrent = tab == currentTab)
}
