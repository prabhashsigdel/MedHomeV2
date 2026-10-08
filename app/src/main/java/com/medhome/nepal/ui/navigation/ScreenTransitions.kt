package com.medhome.nepal.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.medhome.nepal.ui.motion.MotionTokens
import kotlin.reflect.KClass

/**
 * Steps inside one flow (a nested graph in [flows]) push and pop: forward slides in from the
 * right with a fade, back reverses it and follows the predictive back gesture. Moving between
 * flows (a session change, or switching tabs) is not a push, so it crossfades.
 */
class ScreenTransitions(
    private val reducedMotion: Boolean,
    private val flows: List<KClass<*>>,
) {
    private fun <T> spec() = tween<T>(MotionTokens.SCREEN_MS, easing = MotionTokens.EaseOut)

    fun enter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition = when {
        reducedMotion -> EnterTransition.None
        scope.isFlowStep() -> scope.slideIntoContainer(SlideDirection.Start, spec()) + fadeIn(spec())
        else -> fadeIn(spec())
    }

    fun exit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition = when {
        reducedMotion -> ExitTransition.None
        scope.isFlowStep() ->
            scope.slideOutOfContainer(SlideDirection.Start, spec(), targetOffset = { it / PARALLAX }) + fadeOut(spec())
        else -> fadeOut(spec())
    }

    fun popEnter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition = when {
        reducedMotion -> EnterTransition.None
        scope.isFlowStep() ->
            scope.slideIntoContainer(SlideDirection.End, spec(), initialOffset = { it / PARALLAX }) + fadeIn(spec())
        else -> fadeIn(spec())
    }

    fun popExit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition = when {
        reducedMotion -> ExitTransition.None
        scope.isFlowStep() -> scope.slideOutOfContainer(SlideDirection.End, spec()) + fadeOut(spec())
        else -> fadeOut(spec())
    }

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.isFlowStep(): Boolean {
        val flow = initialState.flow()
        return flow != null && flow == targetState.flow()
    }

    private fun NavBackStackEntry.flow(): KClass<*>? =
        flows.firstOrNull { graph -> destination.hierarchy.any { it.hasRoute(graph) } }

    private companion object {
        /** The screen underneath moves a quarter as far, for depth. */
        const val PARALLAX = 4
    }
}
