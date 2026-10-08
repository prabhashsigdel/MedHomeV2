package com.medhome.nepal.ui.language

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import com.medhome.nepal.ui.motion.LocalReducedMotion
import com.medhome.nepal.ui.motion.MotionTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** How to switch the app language. Provided by [LanguageSwitchHost]. */
@Immutable
class LanguageController(val apply: (AppLanguage) -> Unit)

/** Without a host (previews, tests) the language switches with no cover. */
val LocalLanguageController = staticCompositionLocalOf { LanguageController(LanguageSettings::apply) }

/**
 * Makes a language switch look like one smooth change instead of a freeze.
 *
 * Android 13+ ([restartsActivity] false): the activity handles the locale change itself, so the
 * app recomposes in place and keeps every screen, scroll position and field. The old frame is
 * captured to a bitmap first and faded out over the new one once the new language is in, so the
 * text crossfades rather than popping.
 *
 * Android 12 and lower ([restartsActivity] true): AppCompat applies the language without telling
 * Compose, so the activity restarts (state survives through saved state). The content fades to
 * the background before it, and the new activity fades in ([fadeInOnStart]).
 *
 * With animations turned off the switch is immediate.
 */
@Composable
fun LanguageSwitchHost(
    restartsActivity: Boolean,
    fadeInOnStart: Boolean,
    content: @Composable () -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val contentAlpha = remember { Animatable(if (fadeInOnStart && !reducedMotion) 0f else 1f) }
    val coverAlpha = remember { Animatable(1f) }
    var cover by remember { mutableStateOf<ImageBitmap?>(null) }
    val configuration = LocalConfiguration.current
    val languageTag by rememberUpdatedState(configuration.locales.takeIf { !it.isEmpty }?.get(0)?.language)

    LaunchedEffect(Unit) {
        if (contentAlpha.value < 1f) contentAlpha.animateTo(1f, tween(FADE_IN_MS, easing = MotionTokens.EaseOut))
    }

    val controller = remember(reducedMotion, restartsActivity) {
        LanguageController { language ->
            when {
                reducedMotion -> LanguageSettings.apply(language)
                restartsActivity -> scope.launch {
                    contentAlpha.animateTo(0f, tween(FADE_OUT_MS, easing = MotionTokens.EaseOut))
                    LanguageSettings.apply(language)
                    // The restart cancels this scope. If none comes, don't leave the app invisible.
                    delay(MAX_WAIT_MS)
                    contentAlpha.animateTo(1f, tween(FADE_IN_MS, easing = MotionTokens.EaseOut))
                }
                else -> scope.launch {
                    cover = captureOrNull(layer)
                    coverAlpha.snapTo(1f)
                    LanguageSettings.apply(language)
                    withTimeoutOrNull(MAX_WAIT_MS) { snapshotFlow { languageTag }.first { it == language.tag } }
                    coverAlpha.animateTo(0f, tween(CROSSFADE_MS, easing = MotionTokens.EaseOut))
                    cover = null
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha.value }
                // Drawn through a layer so the current frame can be captured on demand.
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
        ) {
            CompositionLocalProvider(LocalLanguageController provides controller, content = content)
        }
        cover?.let { image ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blockTouches()
                    .drawBehind { drawImage(image, alpha = coverAlpha.value) },
            )
        }
    }
}

/** A frozen copy of the current frame, or null if capturing fails (the switch then just pops). */
private suspend fun captureOrNull(layer: GraphicsLayer): ImageBitmap? = try {
    layer.toImageBitmap()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.w(TAG, "Couldn't capture the screen for the language crossfade", e)
    null
}

/** The cover is a picture: taps on it must not reach the screen underneath. */
private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent().changes.forEach { it.consume() }
    }
}

private const val TAG = "LanguageSwitch"
private const val CROSSFADE_MS = 220
private const val FADE_OUT_MS = 150
private const val FADE_IN_MS = 220
/** Upper bound for the system to deliver the new locale before the cover is removed anyway. */
private const val MAX_WAIT_MS = 1_500L
