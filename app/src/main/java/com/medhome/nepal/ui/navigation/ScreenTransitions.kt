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
 * tabs) is not a push, so it crossfades. A [sharedFlows] graph (the booking flow) is opened on top
 * of whichever tab the user is in: going into it and backing out of it are push and pop, but
 * leaving it forward (to another tab, when a booking is done) is a tab switch.
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
    private val sharedFlows: List<KClass<*>> = emptyList(),
) {
    private fun <T> slideSpec() = tween<T>(MotionTokens.SCREEN_MS, easing = MotionTokens.EaseOut)
    private fun <T> fadeSpec() = tween<T>(MotionTokens.CROSSFADE_MS, easing = MotionTokens.EaseOut)

    /** Linear in time, so seeking follows the finger and the finish keeps its speed. */
    private fun <T> gestureSpec() = tween<T>(MotionTokens.SCREEN_MS, easing = LinearEasing)

    fun enter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition = when {
        reducedMotion -> EnterTransition.None
        scope.isPush() -> scope.slideIntoContainer(SlideDirection.Start, slideSpec()) + fadeIn(slideSpec())
        else -> fadeIn(fadeSpec())
    }

    fun exit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition = when {
        reducedMotion -> ExitTransition.None
        scope.isPush() ->
            scope.slideOutOfContainer(SlideDirection.Start, slideSpec(), targetOffset = { it / PARALLAX }) + fadeOut(slideSpec())
        else -> fadeOut(fadeSpec())
    }

    fun popEnter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition = when {
        reducedMotion -> EnterTransition.None
        scope.isPop() ->
            scope.slideIntoContainer(SlideDirection.End, slideSpec(), initialOffset = { it / PARALLAX }) + fadeIn(slideSpec())
        else -> fadeIn(fadeSpec())
    }

    fun popExit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition = when {
        reducedMotion -> ExitTransition.None
        scope.isPop() -> scope.slideOutOfContainer(SlideDirection.End, slideSpec()) + fadeOut(slideSpec())
        else -> fadeOut(fadeSpec())
    }

    fun predictivePopEnter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition = when {
        reducedMotion -> EnterTransition.None
        scope.isPop() ->
            scope.slideIntoContainer(SlideDirection.End, gestureSpec(), initialOffset = { it / PARALLAX }) + fadeIn(gestureSpec())
        else -> fadeIn(gestureSpec())
    }

    fun predictivePopExit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition = when {
        reducedMotion -> ExitTransition.None
        scope.isPop() -> scope.slideOutOfContainer(SlideDirection.End, gestureSpec()) + fadeOut(gestureSpec())
        else -> fadeOut(gestureSpec())
    }

    /** Forward: a step inside a flow, or from a tab into a shared flow. */
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.isPush(): Boolean {
        val from = initialState.flow()
        val to = targetState.flow()
        return from != null && (from == to || (to in sharedFlows && from !in sharedFlows))
    }

    /** Back: a step inside a flow, or out of a shared flow to the tab under it. */
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.isPop(): Boolean {
        val from = initialState.flow()
        val to = targetState.flow()
        return to != null && (from == to || (from in sharedFlows && to !in sharedFlows))
    }

    /** The innermost flow: a shared flow wins over a tab that happens to contain it. */
    private fun NavBackStackEntry.flow(): KClass<*>? =
        (sharedFlows + flows).firstOrNull { graph -> destination.hierarchy.any { it.hasRoute(graph) } }

    private companion object {
        /** The screen underneath moves a quarter as far, for depth. */
        const val PARALLAX = 4
    }
}
