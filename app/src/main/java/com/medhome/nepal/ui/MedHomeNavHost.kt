package com.medhome.nepal.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.medhome.nepal.R
import com.medhome.nepal.session.SessionState
import com.medhome.nepal.ui.auth.ForgotPasswordScreen
import com.medhome.nepal.ui.auth.LoginScreen
import com.medhome.nepal.ui.auth.SignUpScreen
import com.medhome.nepal.ui.components.ErrorMessage
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassButtonStyle
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassLoadingScreen
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.navigation.ScreenTransitions
import com.medhome.nepal.ui.shell.MainShell
import com.medhome.nepal.ui.motion.LocalReducedMotion
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.theme.GlassTheme
import com.medhome.nepal.ui.verify.VerifyEmailScreen
import kotlinx.serialization.Serializable

@Serializable private data object LoadingRoute
@Serializable private data object AuthGraph
@Serializable private data object LoginRoute
@Serializable private data object SignUpRoute
@Serializable private data class ForgotPasswordRoute(val email: String = "")
@Serializable private data object VerifyEmailRoute
@Serializable private data object ProfileUnavailableRoute
@Serializable private data object MainGraph

/**
 * The session decides which top-level destination is shown. Changing session state clears
 * the back stack, so Back can never return to a screen from a previous session.
 */
@Composable
fun MedHomeNavHost(
    sessionViewModel: SessionViewModel = viewModel(factory = SessionViewModel.Factory),
) {
    val navController = rememberNavController()
    val session by sessionViewModel.sessionState.collectAsStateWithLifecycle()

    val topLevelRoute: Any = when (session) {
        SessionState.Loading -> LoadingRoute
        is SessionState.SignedOut -> AuthGraph
        is SessionState.NeedsVerification -> VerifyEmailRoute
        SessionState.ProfileUnavailable -> ProfileUnavailableRoute
        is SessionState.SignedIn -> MainGraph
    }
    LaunchedEffect(topLevelRoute) { navController.showTopLevel(topLevelRoute) }
    val transitions = ScreenTransitions(
        reducedMotion = LocalReducedMotion.current,
        flows = listOf(AuthGraph::class, MainGraph::class),
    )

    NavHost(
        navController = navController,
        startDestination = LoadingRoute,
        enterTransition = { transitions.enter(this) },
        exitTransition = { transitions.exit(this) },
        popEnterTransition = { transitions.popEnter(this) },
        popExitTransition = { transitions.popExit(this) },
    ) {
        composable<LoadingRoute> { GlassLoadingScreen() }

        navigation<AuthGraph>(startDestination = LoginRoute) {
            composable<LoginRoute> {
                LoginScreen(
                    sessionError = (session as? SessionState.SignedOut)?.error,
                    onDismissSessionError = sessionViewModel::clearSignedOutError,
                    onSignUp = { navController.navigate(SignUpRoute) },
                    onForgotPassword = { email -> navController.navigate(ForgotPasswordRoute(email)) },
                )
            }
            composable<SignUpRoute> {
                SignUpScreen(onBackToLogin = { navController.popBackStack() })
            }
            composable<ForgotPasswordRoute> { entry ->
                ForgotPasswordScreen(
                    initialEmail = entry.toRoute<ForgotPasswordRoute>().email,
                    onBackToLogin = { navController.popBackStack() },
                )
            }
        }

        composable<VerifyEmailRoute> {
            (session as? SessionState.NeedsVerification)?.let {
                VerifyEmailScreen(
                    email = it.profile.email,
                    verificationEmailFailed = it.verificationEmailFailed,
                )
            }
        }

        composable<ProfileUnavailableRoute> {
            ProfileUnavailableScreen(sessionViewModel)
        }

        // The whole signed-in app (tabs and their own back stacks) lives in MainShell's nested
        // NavHost, so session updates and rotation never reset it: showTopLevel sees MainGraph.
        composable<MainGraph> {
            (session as? SessionState.SignedIn)?.let { MainShell(it) }
        }
    }
}

/**
 * Replaces the back stack with [route], unless it is already showing. The check matters after
 * rotation: the effect re-runs, and Sign up or Forgot password must not be reset to Login.
 */
private fun NavHostController.showTopLevel(route: Any) {
    val alreadyShowing = currentBackStackEntry?.destination?.hierarchy
        ?.any { it.hasRoute(route::class) } == true
    if (alreadyShowing) return
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
private fun ProfileUnavailableScreen(viewModel: SessionViewModel) {
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()

    GlassScreen {
        ScreenTitle(title = R.string.profile_unavailable_title, modifier = Modifier.entrance(0))
        GlassCard(modifier = Modifier.entrance(1)) {
            Text(
                text = stringResource(R.string.profile_unavailable_body),
                style = MaterialTheme.typography.bodyLarge,
                color = GlassTheme.colors.textPrimary,
            )
            actionError?.let { ErrorMessage(error = it, onDismiss = viewModel::dismissActionError) }
            GlassButton(
                text = R.string.action_retry,
                onClick = viewModel::retryProfile,
                loading = isBusy,
                enabled = !isBusy,
            )
            GlassButton(
                text = R.string.action_sign_out,
                onClick = viewModel::signOut,
                style = GlassButtonStyle.Secondary,
                enabled = !isBusy,
            )
        }
    }
}
