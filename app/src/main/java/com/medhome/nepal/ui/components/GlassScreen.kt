package com.medhome.nepal.ui.components

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.medhome.nepal.R
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.motion.pressScale
import com.medhome.nepal.ui.navigation.navigateOnce
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassShapes
import com.medhome.nepal.ui.theme.GlassTheme
import com.medhome.nepal.ui.theme.STATUS_TINT_ALPHA
import dev.chrisbanes.haze.hazeSource

/**
 * Estimated space for the floating bar (60dp bar + 2 x 16dp margin + 16dp gap), used only until
 * the shell has measured the real bar. The shell then provides the exact value.
 */
val FloatingBarClearance = 108.dp

/** Gap kept between the last item of a tab screen and the top of the floating bar. */
val FloatingBarGap = 16.dp

/**
 * Extra bottom space a parent with its own floating bar (the signed-in shell) asks screens to
 * keep free. 0 when no bar is showing.
 */
val LocalBottomBarClearance = staticCompositionLocalOf { 0.dp }

/**
 * Mesh background, optional top bar, scrollable keyboard-aware content and an optional
 * floating bottom bar. safeDrawing insets include the keyboard, so forms stay reachable.
 */
@Composable
fun GlassScreen(
    modifier: Modifier = Modifier,
    showBack: Boolean = false,
    /** False inside a parent that already draws the background (the signed-in shell). */
    drawBackground: Boolean = true,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val parentBarClearance = LocalBottomBarClearance.current
    MaybeMeshBackground(drawBackground = drawBackground, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            if (showBack) GlassTopBar()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Content is a blur source too, so the floating bar blurs what scrolls under it.
                    .then(if (bottomBar != null) Modifier.hazeContentLayer() else Modifier)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = GlassDimens.FormMaxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = GlassDimens.ScreenPadding)
                        // With a floating bar, the bottom padding is exactly the bar's clearance
                        // (bar height + 16dp gap); otherwise the normal 24dp.
                        .padding(
                            top = if (showBack) 4.dp else 28.dp,
                            bottom = when {
                                bottomBar != null -> FloatingBarClearance
                                parentBarClearance > 0.dp -> parentBarClearance
                                else -> 24.dp
                            },
                        ),
                    verticalArrangement = Arrangement.spacedBy(GlassDimens.ItemSpacing),
                    content = content,
                )
            }
        }
        if (bottomBar != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            ) {
                bottomBar()
            }
        }
    }
}

@Composable
private fun MaybeMeshBackground(
    drawBackground: Boolean,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (drawBackground) {
        MeshBackground(modifier = modifier, content = content)
    } else {
        Box(modifier = modifier.fillMaxSize(), content = content)
    }
}

@Composable
private fun Modifier.hazeContentLayer(): Modifier {
    val state = LocalHazeState.current ?: return this
    return hazeSource(state, zIndex = 1f)
}

/**
 * Back arrow that goes through the system back dispatcher, so it behaves exactly like the back
 * gesture (including predictive back and any registered handlers).
 */
@Composable
fun GlassTopBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton()
    }
}

/**
 * 48dp back arrow routed through the system back dispatcher (same as the back gesture). Guarded
 * by [navigateOnce], so a double tap goes back one screen, not two.
 */
@Composable
fun BackButton(modifier: Modifier = Modifier) {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    IconButton(onClick = navigateOnce { dispatcher?.onBackPressed() }, modifier = modifier) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = stringResource(R.string.action_back),
            tint = GlassTheme.colors.textPrimary,
        )
    }
}

@Composable
fun ScreenTitle(
    @StringRes title: Int,
    modifier: Modifier = Modifier,
    @StringRes subtitle: Int? = null,
) {
    val colors = GlassTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Text(
                text = stringResource(subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

/** Small heading above a group of cards ("Language", "Danger zone"). */
@Composable
fun SectionTitle(
    @StringRes text: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = GlassTheme.colors.textSecondary,
        modifier = modifier
            .padding(top = 8.dp)
            .semantics { heading() },
    )
}

/** Accent-colored text action (darker accent, readable on glass), at least 48dp tall. */
@Composable
fun GlassLinkButton(
    @StringRes text: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = GlassShapes.Chip,
        modifier = modifier
            .heightIn(min = GlassDimens.MinTouchTarget)
            .pressScale(interactionSource),
    ) {
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.labelLarge,
            color = GlassTheme.colors.link,
        )
    }
}

/** A line of text plus a link, e.g. "Don't have an account? Sign up". */
@Composable
fun PromptWithLink(
    @StringRes prompt: Int,
    @StringRes link: Int,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(prompt),
            style = MaterialTheme.typography.bodyMedium,
            color = GlassTheme.colors.textSecondary,
        )
        GlassLinkButton(text = link, onClick = onClick, enabled = enabled)
    }
}

enum class MessageKind { Error, Warning, Info }

/** An inline message inside a card: tinted panel (darkened first in dark), readable text, optional actions. */
@Composable
fun StatusMessage(
    @StringRes message: Int,
    kind: MessageKind,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
) {
    val colors = GlassTheme.colors
    val tone = when (kind) {
        MessageKind.Error -> colors.error
        MessageKind.Warning -> colors.warning
        MessageKind.Info -> colors.link
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.statusUnderlay, GlassShapes.Input)
            .background(tone.copy(alpha = STATUS_TINT_ALPHA), GlassShapes.Input)
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = if (onDismiss != null || onRetry != null) 2.dp else 12.dp),
    ) {
        Text(
            text = stringResource(message),
            style = MaterialTheme.typography.bodyMedium,
            color = tone,
            modifier = Modifier.padding(end = 8.dp),
        )
        if (onDismiss != null || onRetry != null) {
            Row(modifier = Modifier.align(Alignment.End)) {
                if (onDismiss != null) GlassLinkButton(text = R.string.action_dismiss, onClick = onDismiss)
                if (onRetry != null) GlassLinkButton(text = R.string.action_retry, onClick = onRetry)
            }
        }
    }
}

@Composable
fun ErrorMessage(error: AuthError, onDismiss: () -> Unit, onRetry: (() -> Unit)? = null) {
    StatusMessage(message = error.messageRes, kind = MessageKind.Error, onDismiss = onDismiss, onRetry = onRetry)
}

@Composable
fun GlassLoadingScreen() {
    MeshBackground {
        CircularProgressIndicator(
            color = GlassTheme.colors.accentEmphasis,
            modifier = Modifier
                .align(Alignment.Center)
                .size(40.dp)
                .entrance(0),
        )
    }
}
