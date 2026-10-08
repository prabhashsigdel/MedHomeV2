package com.medhome.nepal.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.medhome.nepal.ui.common.ErrorCard
import com.medhome.nepal.ui.common.FormScreen
import com.medhome.nepal.ui.common.FullScreenLoading
import com.medhome.nepal.ui.common.LoadingButton
import com.medhome.nepal.ui.common.ScreenTitle
import com.medhome.nepal.ui.home.HomeScreen
import com.medhome.nepal.ui.verify.VerifyEmailScreen
import kotlinx.serialization.Serializable

@Serializable private data object LoadingRoute
@Serializable private data object AuthGraph
@Serializable private data object LoginRoute
@Serializable private data object SignUpRoute
@Serializable private data class ForgotPasswordRoute(val email: String = "")
@Serializable private data object VerifyEmailRoute
@Serializable private data object ProfileUnavailableRoute
@Serializable private data object HomeRoute

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
        is SessionState.SignedIn -> HomeRoute
    }
    LaunchedEffect(topLevelRoute) { navController.showTopLevel(topLevelRoute) }

    NavHost(navController = navController, startDestination = LoadingRoute) {
        composable<LoadingRoute> { FullScreenLoading() }

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

        composable<HomeRoute> {
            (session as? SessionState.SignedIn)?.let {
                HomeScreen(profile = it.profile, usesPassword = it.usesPassword)
            }
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

    FormScreen {
        ScreenTitle(title = R.string.profile_unavailable_title)
        Text(
            text = stringResource(R.string.profile_unavailable_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        actionError?.let { ErrorCard(error = it, onDismiss = viewModel::dismissActionError) }
        LoadingButton(
            text = R.string.action_retry,
            loading = isBusy,
            enabled = !isBusy,
            onClick = viewModel::retryProfile,
        )
        OutlinedButton(
            onClick = viewModel::signOut,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_sign_out))
        }
    }
}
