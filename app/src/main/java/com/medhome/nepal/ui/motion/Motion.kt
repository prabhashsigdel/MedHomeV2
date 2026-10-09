package com.medhome.nepal.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

object MotionTokens {
    const val FEEDBACK_MS = 120

    /** Push and pop slides. */
    const val SCREEN_MS = 250

    /** Crossfades: tab switches, session changes, theme and language. */
    const val CROSSFADE_MS = 180

    /** First appearance of a screen's items. Short, so content never trickles in. */
    const val ENTRANCE_MS = 180
    const val STAGGER_MS = 20L

    /** Later items share the last step, so a whole screen is in within about 240ms. */
    const val MAX_STAGGER_STEPS = 3
    const val PRESSED_SCALE = 0.97f
    const val MATERIALIZE_SCALE = 0.96f
    val EntranceOffset = 12.dp

    /** Ease-out: fast start, gentle stop. Never linear. */
    val EaseOut = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/** True when the user turned animations off (animator duration scale 0). */
val LocalReducedMotion = staticCompositionLocalOf { false }

fun isReducedMotion(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

/** [spec], or an instant change when animations are off. */
@Composable
fun <T> motionSpec(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spec

@Composable
fun <T> feedbackTween(): FiniteAnimationSpec<T> =
    motionSpec(tween(MotionTokens.FEEDBACK_MS, easing = MotionTokens.EaseOut))

/**
 * Visual press feedback only. It never handles clicks: the element keeps its own clickable/Button
 * semantics for TalkBack and keyboard. The pointer listener observes touches (Initial pass) so the
 * scale starts on finger-down, before clickable's tap delay. A scroll consumes the gesture and
 * releases it. [interactionSource] adds keyboard/D-pad presses.
 */
fun Modifier.pressScale(interactionSource: InteractionSource? = null): Modifier = composed {
    var touchPressed by remember { mutableStateOf(false) }
    val keyPressed = interactionSource?.collectIsPressedAsState()?.value ?: false
    val scale by animateFloatAsState(
        targetValue = if (touchPressed || keyPressed) MotionTokens.PRESSED_SCALE else 1f,
        animationSpec = motionSpec(spring(stiffness = Spring.StiffnessHigh, dampingRatio = 0.7f)),
        label = "pressScale",
    )
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                touchPressed = true
                try {
                    // Ends on release, or when a scroll consumes movement. The clickable consuming
                    // the down/up itself carries no movement, so it does not end the press early.
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val released = event.changes.none { it.pressed }
                        val scrolled = event.changes.any { it.isConsumed && it.positionChange() != Offset.Zero }
                        if (released || scrolled) break
                    }
                } finally {
                    touchPressed = false
                }
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

/**
 * Fades in and rises [MotionTokens.EntranceOffset] the first time a screen shows, staggered by
 * [index] (capped at [MotionTokens.MAX_STAGGER_STEPS]). Saved across rotation so it doesn't
 * replay; animates only alpha and translation. Instant with animations off.
 */
fun Modifier.entrance(index: Int): Modifier = composed {
    val reduced = LocalReducedMotion.current
    var played by rememberSaveable { mutableStateOf(reduced) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            delay(index.coerceAtMost(MotionTokens.MAX_STAGGER_STEPS) * MotionTokens.STAGGER_MS)
            progress.animateTo(1f, tween(MotionTokens.ENTRANCE_MS, easing = MotionTokens.EaseOut))
            played = true
        }
    }
    val offsetPx = with(LocalDensity.current) { MotionTokens.EntranceOffset.toPx() }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offsetPx
    }
}

/** Glass surfaces (bars, sheets, dialogs) fade in while scaling up from 0.96. */
@Composable
fun materializeIn(): EnterTransition =
    fadeIn(motionSpec(tween(MotionTokens.FEEDBACK_MS + 60, easing = MotionTokens.EaseOut))) +
        scaleIn(
            animationSpec = motionSpec(tween(MotionTokens.FEEDBACK_MS + 60, easing = MotionTokens.EaseOut)),
            initialScale = MotionTokens.MATERIALIZE_SCALE,
        )

@Composable
fun materializeOut(): ExitTransition =
    fadeOut(motionSpec(tween(MotionTokens.FEEDBACK_MS, easing = MotionTokens.EaseOut))) +
        scaleOut(
            animationSpec = motionSpec(tween(MotionTokens.FEEDBACK_MS, easing = MotionTokens.EaseOut)),
            targetScale = MotionTokens.MATERIALIZE_SCALE,
        )
