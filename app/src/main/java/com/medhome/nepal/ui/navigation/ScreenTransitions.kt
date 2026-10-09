package com.medhome.nepal.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
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
 * right with a fade, back reverses it. Moving between flows (a session change, or switching
 * tabs) is not a push, so it crossfades.
 *
 * The back gesture has its own pair ([predictivePopEnter], [predictivePopExit]): the same
 * motion, but linear. NavHost seeks the gesture's transition with the finger's progress and, on
 * release, plays the rest of it linearly in time. With linear specs the page keeps the speed it
 * had. Without them NavHost falls back to its default springs, whose long settling tail made the
 * page stall after release and then vanish. Both NavHosts must pass all six.
 */
class ScreenTransitions(
    private val reducedMotion: Boolean,
    private val flows: List<KClass<*>>,
) {
    private fun <T> slideSpec() = tween<T>(MotionTokens.SCREEN_MS, easing = MotionTokens.EaseOut)
    private fun <T> fadeSpec() = tween<T>(MotionTokens.CROSSFADE_MS, easing = MotionTokens.EaseOut)

    /** Linear in time, so seeking follows the finger and the finish keeps its speed. */
    private fun <T> gestureSpec() = tween<T>(MotionTokens.SCREEN_MS, easing = LinearEasing)

    fun enter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition = when {
        reducedMotion -> EnterTransition.None
        scope.isFlowStep() -> scope.slideIntoContainer(SlideDirection.Start, slideSpec()) + fadeIn(slideSpec())
        else -> fadeIn(fadeSpec())
    }

    fun exit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition = when {
        reducedMotion -> ExitTransition.None
        scope.isFlowStep() ->
            scope.slideOutOfContainer(SlideDirection.Start, slideSpec(), targetOffset = { it / PARALLAX }) + fadeOut(slideSpec())
        else -> fadeOut(fadeSpec())
    }

    fun popEnter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition = when {
        reducedMotion -> EnterTransition.None
        scope.isFlowStep() ->
            scope.slideIntoContainer(SlideDirection.End, slideSpec(), initialOffset = { it / PARALLAX }) + fadeIn(slideSpec())
        else -> fadeIn(fadeSpec())
    }

    fun popExit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition = when {
        reducedMotion -> ExitTransition.None
        scope.isFlowStep() -> scope.slideOutOfContainer(SlideDirection.End, slideSpec()) + fadeOut(slideSpec())
        else -> fadeOut(fadeSpec())
    }

    fun predictivePopEnter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition = when {
        reducedMotion -> EnterTransition.None
        scope.isFlowStep() ->
            scope.slideIntoContainer(SlideDirection.End, gestureSpec(), initialOffset = { it / PARALLAX }) + fadeIn(gestureSpec())
        else -> fadeIn(gestureSpec())
    }

    fun predictivePopExit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition = when {
        reducedMotion -> ExitTransition.None
        scope.isFlowStep() -> scope.slideOutOfContainer(SlideDirection.End, gestureSpec()) + fadeOut(gestureSpec())
        else -> fadeOut(gestureSpec())
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
