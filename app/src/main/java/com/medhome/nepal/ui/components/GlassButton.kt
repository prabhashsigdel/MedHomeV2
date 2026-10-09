package com.medhome.nepal.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medhome.nepal.R
import com.medhome.nepal.ui.motion.MotionTokens
import com.medhome.nepal.ui.motion.motionSpec
import com.medhome.nepal.ui.motion.pressScale
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassShapes
import com.medhome.nepal.ui.theme.GlassTheme

/** Primary: filled accent (the only indigo on Home). Secondary: glass. Danger: filled error, for destructive actions. */
enum class GlassButtonStyle { Primary, Secondary, Danger }

private const val DISABLED_ALPHA = 0.5f
private const val MAX_LABEL_LINES = 2

/**
 * At least 54dp tall; grows (up to two lines) when a translated label doesn't fit, instead of
 * clipping. Primary is filled accent with white text; Secondary is glass. While [loading] the
 * label fades out under a spinner but keeps its place, so the button never changes size.
 * Click semantics come from clickable (Role.Button); pressScale only adds the visual effect.
 */
@Composable
fun GlassButton(
    @StringRes text: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassButtonStyle = GlassButtonStyle.Primary,
    loading: Boolean = false,
    enabled: Boolean = true,
    /** Sized to its label (48dp tall) instead of full width, for secondary places like the danger zone. */
    compact: Boolean = false,
) {
    val colors = GlassTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val shape = GlassShapes.Button
    val contentColor = when (style) {
        GlassButtonStyle.Primary -> colors.onAccent
        GlassButtonStyle.Danger -> colors.onDanger
        GlassButtonStyle.Secondary -> colors.textPrimary
    }
    val surface = when (style) {
        GlassButtonStyle.Primary -> Modifier.background(colors.accent, shape)
        GlassButtonStyle.Danger -> Modifier.background(colors.danger, shape)
        GlassButtonStyle.Secondary -> Modifier.glassControl(shape)
    }
    val loadingLabel = stringResource(R.string.state_loading)
    Box(
        modifier = modifier
            .then(if (compact) Modifier else Modifier.fillMaxWidth())
            .heightIn(min = if (compact) GlassDimens.MinTouchTarget else GlassDimens.ButtonHeight)
            .pressScale(interactionSource)
            .alpha(if (enabled || loading) 1f else DISABLED_ALPHA)
            .clip(shape)
            .then(surface)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = contentColor),
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { if (loading) stateDescription = loadingLabel },
        contentAlignment = Alignment.Center,
    ) {
        val spec = motionSpec(tween<Float>(MotionTokens.FEEDBACK_MS, easing = MotionTokens.EaseOut))
        val labelAlpha by animateFloatAsState(if (loading) 0f else 1f, spec, label = "buttonLabel")
        val spinnerAlpha by animateFloatAsState(if (loading) 1f else 0f, spec, label = "buttonSpinner")
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = MAX_LABEL_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = if (compact) 20.dp else 16.dp, vertical = 8.dp)
                .graphicsLayer { alpha = labelAlpha },
        )
        if (spinnerAlpha > 0f) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { alpha = spinnerAlpha },
                color = contentColor,
                strokeWidth = 2.5.dp,
            )
        }
    }
}
