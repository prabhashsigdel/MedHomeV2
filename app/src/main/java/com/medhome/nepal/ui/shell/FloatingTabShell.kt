package com.medhome.nepal.ui.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.medhome.nepal.ui.components.FloatingBarClearance
import com.medhome.nepal.ui.components.FloatingBarGap
import com.medhome.nepal.ui.components.FloatingNavBar
import com.medhome.nepal.ui.components.LocalBottomBarClearance
import com.medhome.nepal.ui.components.LocalHazeState
import com.medhome.nepal.ui.components.MeshBackground
import com.medhome.nepal.ui.components.NavBarItem
import com.medhome.nepal.ui.motion.MotionTokens
import com.medhome.nepal.ui.motion.motionSpec
import dev.chrisbanes.haze.hazeSource
import kotlin.reflect.KClass

/** Test tag on the floating tab bar, for layout tests. */
const val FLOATING_NAV_BAR_TAG = "floating_nav_bar"

/**
 * A tabbed shell (patient or admin): one mesh background, the shell's NavHost from [content]
 * (given the modifier it must use, which makes it the bar's blur source), and the floating tab
 * bar on top, shown on tab roots only ([onTabRoot]).
 */
@Composable
internal fun FloatingTabShell(
    items: List<NavBarItem>,
    selectedIndex: Int,
    onTabRoot: Boolean,
    onSelect: (Int) -> Unit,
    content: @Composable BoxScope.(navHostModifier: Modifier) -> Unit,
) {
    // The real bar height (its 16dp margins included; the navigation-bar inset is added by each
    // screen's own safe-drawing padding), measured so content clears it at any font size.
    var measuredBarHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val tabRootClearance = if (measuredBarHeight > 0.dp) measuredBarHeight + FloatingBarGap else FloatingBarClearance

    MeshBackground {
        val hazeState = LocalHazeState.current
        // Content is a blur source above the background, so the bar blurs what scrolls under it.
        val navHostModifier = Modifier
            .fillMaxSize()
            .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier)
        CompositionLocalProvider(LocalTabRootClearance provides tabRootClearance) {
            content(navHostModifier)
        }

        // Always composed, so its blur (Haze) is never rebuilt: coming back to a tab root, often
        // as a back swipe is released, is then only a fade. Hidden, it is not placed at all, so it
        // draws nothing and takes no touches.
        val barShown by animateFloatAsState(
            targetValue = if (onTabRoot) 1f else 0f,
            animationSpec = motionSpec(tween(if (onTabRoot) MotionTokens.CROSSFADE_MS else MotionTokens.FEEDBACK_MS, easing = MotionTokens.EaseOut)),
            label = "tabBar",
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .placedOnlyIf(onTabRoot || barShown > 0f)
                .graphicsLayer {
                    alpha = barShown
                    val scale = MotionTokens.MATERIALIZE_SCALE + (1f - MotionTokens.MATERIALIZE_SCALE) * barShown
                    scaleX = scale
                    scaleY = scale
                }
                .then(if (onTabRoot) Modifier.testTag(FLOATING_NAV_BAR_TAG) else Modifier.clearAndSetSemantics {})
                .onSizeChanged { size ->
                    if (size.height > 0) measuredBarHeight = with(density) { size.height.toDp() }
                },
        ) {
            FloatingNavBar(items = items, selectedIndex = selectedIndex, onSelect = onSelect, animateIn = false)
        }
    }
}

/** Measured and composed as usual, but placed (drawn, touchable) only while [placed]. */
private fun Modifier.placedOnlyIf(placed: Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        if (placed) placeable.place(0, 0)
    }
}

/** The measured clearance for tab roots: bar height + [FloatingBarGap]. */
private val LocalTabRootClearance = compositionLocalOf { FloatingBarClearance }

/**
 * Tab roots keep space for the floating bar so their last item can scroll fully above it. Set
 * per screen (not shell-wide) so a screen sliding out keeps its own padding instead of jumping
 * when the bar's visibility changes.
 */
@Composable
internal fun TabRoot(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBottomBarClearance provides LocalTabRootClearance.current, content = content)
}

internal fun NavDestination?.isIn(graph: KClass<*>): Boolean =
    this?.hierarchy?.any { it.hasRoute(graph) } == true

/**
 * Standard multiple-back-stack switch to the tab [tabGraph] (whose root is [tabRoot]): save the
 * current tab's stack, restore the target's. Re-selecting the current tab ([isCurrent]) returns
 * it to its root.
 */
internal fun NavHostController.switchTab(tabGraph: Any, tabRoot: Any, isCurrent: Boolean) {
    if (isCurrent) {
        popBackStack(tabRoot, inclusive = false)
        return
    }
    navigate(tabGraph) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
