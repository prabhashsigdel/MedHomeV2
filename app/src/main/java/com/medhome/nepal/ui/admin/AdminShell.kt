package com.medhome.nepal.ui.admin

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.medhome.nepal.session.SessionState
import com.medhome.nepal.ui.components.MeshBackground
import com.medhome.nepal.ui.motion.LocalReducedMotion
import com.medhome.nepal.ui.navigation.ScreenTransitions
import com.medhome.nepal.ui.navigation.navigateOnce
import com.medhome.nepal.ui.navigation.navigateOnceWith
import com.medhome.nepal.ui.navigation.popIfTop
import com.medhome.nepal.ui.profile.ProfileViewModel
import kotlinx.serialization.Serializable

// One graph, one back stack: the doctors list at the root, everything else pushed on it.
@Serializable internal data object AdminGraph
@Serializable internal data object AdminDoctorsRoute
// The property name is AdminViewModels.ARG_DOCTOR_ID.
@Serializable internal data class AdminDoctorRoute(val doctorId: String)
// Null adds a new doctor. The property name is AdminViewModels.ARG_DOCTOR_ID.
@Serializable internal data class DoctorFormRoute(val doctorId: String? = null)
@Serializable internal data object AdminSettingsRoute

/**
 * The admin app: one mesh background and one stack (no tab bar). The doctors list is home;
 * a doctor, the add / edit form and Settings (language, theme, sign out) are pushed on it.
 */
@Composable
fun AdminShell(
    session: SessionState.SignedIn,
    adminViewModelFactory: ViewModelProvider.Factory,
    profileViewModelFactory: ViewModelProvider.Factory,
) {
    val navController = rememberNavController()
    val transitions = ScreenTransitions(reducedMotion = LocalReducedMotion.current, flows = listOf(AdminGraph::class))

    MeshBackground {
        NavHost(
            navController = navController,
            startDestination = AdminGraph,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { transitions.enter(this) },
            exitTransition = { transitions.exit(this) },
            popEnterTransition = { transitions.popEnter(this) },
            popExitTransition = { transitions.popExit(this) },
            predictivePopEnterTransition = { transitions.predictivePopEnter(this) },
            predictivePopExitTransition = { transitions.predictivePopExit(this) },
        ) {
            navigation<AdminGraph>(startDestination = AdminDoctorsRoute) {
                composable<AdminDoctorsRoute> {
                    AdminDoctorsScreen(
                        adminName = session.profile.name,
                        viewModel = viewModel(factory = adminViewModelFactory),
                        onOpenDoctor = navigateOnceWith { id: String -> navController.navigate(AdminDoctorRoute(id)) },
                        onAddDoctor = navigateOnce { navController.navigate(DoctorFormRoute()) },
                        onOpenSettings = navigateOnce { navController.navigate(AdminSettingsRoute) { launchSingleTop = true } },
                    )
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
                composable<AdminSettingsRoute> {
                    AdminSettingsScreen(viewModel = viewModel<ProfileViewModel>(factory = profileViewModelFactory))
                }
            }
        }
    }
}
