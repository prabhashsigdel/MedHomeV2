package com.medhome.nepal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController

/*
 * The navigate-once guard. Every navigation call goes through one of these, so rapid repeat taps
 * (a double tap, or a tap on a screen that is already sliding away) navigate only once.
 *
 * - Taps that open a screen: [navigateOnce] / [navigateOnceWith]. A tap only counts while its
 *   screen is RESUMED, which a NavHost destination is only once its enter transition has ended
 *   and until the next navigation starts. Taps during a transition are dropped.
 * - Going back: [popIfTop]. It pops only while the screen's entry is still on top, so a repeat
 *   (or a late call from an effect) can never pop the screen underneath. It works whatever the
 *   lifecycle, so a save that finishes in the background still closes its screen.
 *
 * Tab switches are not guarded: switching to a tab is idempotent, and dropping taps on the bar
 * would make it feel unresponsive.
 */

/** [block], but ignored unless the calling screen is resumed. */
@Composable
fun navigateOnce(block: () -> Unit): () -> Unit = dropUnlessResumed(block = block)

/** One-argument form of [navigateOnce], for callbacks like `onOpenPage(page)`. */
@Composable
fun <T> navigateOnceWith(block: (T) -> Unit): (T) -> Unit {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentBlock by rememberUpdatedState(block)
    return remember(lifecycleOwner) {
        { value: T ->
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) currentBlock(value)
        }
    }
}

/** Pops [entry] only if it is still the top of the back stack. */
fun NavHostController.popIfTop(entry: NavBackStackEntry) {
    if (currentBackStackEntry?.id == entry.id) popBackStack()
}

/**
 * Navigates to [route] only if [entry] is still the top of the back stack: for navigation that
 * follows from code rather than a tap (a save finishing), so it happens once and never from a
 * screen that has already been left.
 */
fun <T : Any> NavHostController.navigateIfTop(entry: NavBackStackEntry, route: T) {
    if (currentBackStackEntry?.id == entry.id) navigate(route)
}
