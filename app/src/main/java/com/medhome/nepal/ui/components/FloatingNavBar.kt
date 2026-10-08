package com.medhome.nepal.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.medhome.nepal.R
import com.medhome.nepal.ui.motion.feedbackTween
import com.medhome.nepal.ui.motion.materializeIn
import com.medhome.nepal.ui.motion.motionSpec
import com.medhome.nepal.ui.motion.pressScale
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassShapes
import com.medhome.nepal.ui.theme.GlassTheme
import com.medhome.nepal.ui.theme.MedHomeTheme

data class NavBarItem(@param:StringRes val label: Int, @param:DrawableRes val icon: Int)

private val NavBarHeight = 64.dp
private val IndicatorInset = 6.dp

/**
 * Floating glass tab bar, inset 16dp from the screen edges. The selected-tab indicator slides
 * between tabs and icon colors animate. Not wired into the app yet (only one signed-in screen).
 */
@Composable
fun FloatingNavBar(
    items: List<NavBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingContainer(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(NavBarHeight)) {
            val itemWidth = maxWidth / items.size
            val indicatorOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = motionSpec(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)),
                label = "navIndicator",
            )
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .padding(IndicatorInset)
                    .background(GlassTheme.colors.accent.copy(alpha = 0.14f), GlassShapes.Chip),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, item ->
                    NavBarTab(item = item, selected = index == selectedIndex, onClick = { onSelect(index) })
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavBarTab(item: NavBarItem, selected: Boolean, onClick: () -> Unit) {
    val colors = GlassTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val tint by animateColorAsState(
        targetValue = if (selected) colors.link else colors.textSecondary,
        animationSpec = feedbackTween(),
        label = "navTint",
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .height(NavBarHeight)
            .pressScale(interactionSource)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = ripple(bounded = false),
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painter = painterResource(item.icon), contentDescription = null, tint = tint)
        Text(text = stringResource(item.label), style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/**
 * The most transparent glass, floating 16dp from the edges, materializing on first show.
 * Shared by [FloatingNavBar] and [FloatingActionBar].
 */
@Composable
fun FloatingContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visibleState = visibleState, enter = materializeIn(), modifier = modifier) {
        Box(
            modifier = Modifier
                .padding(GlassDimens.FloatingInset)
                .glassSurface(level = GlassLevel.Floating, shape = GlassShapes.Card),
        ) {
            content()
        }
    }
}

/** Floating bar for a screen's main actions. */
@Composable
fun FloatingActionBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    FloatingContainer(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 200)
@Composable
private fun FloatingNavBarPreview() {
    MedHomeTheme {
        GlassBackground {
            var selected by remember { mutableIntStateOf(0) }
            FloatingNavBar(
                items = listOf(
                    NavBarItem(R.string.nav_home, R.drawable.ic_nav_home),
                    NavBarItem(R.string.nav_appointments, R.drawable.ic_nav_calendar),
                    NavBarItem(R.string.nav_profile, R.drawable.ic_nav_person),
                ),
                selectedIndex = selected,
                onSelect = { selected = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
