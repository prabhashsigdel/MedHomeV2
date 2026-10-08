package com.medhome.nepal.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.medhome.nepal.R
import com.medhome.nepal.ui.motion.MotionTokens
import com.medhome.nepal.ui.motion.motionSpec
import com.medhome.nepal.ui.motion.pressScale
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassShapes
import com.medhome.nepal.ui.theme.GlassTheme

/** Primary: filled accent. Secondary: glass. Danger: filled error, for destructive actions. */
enum class GlassButtonStyle { Primary, Secondary, Danger }

private const val DISABLED_ALPHA = 0.5f

/**
 * 54dp button. Primary is filled accent with white text; Secondary is glass. While [loading]
 * the label crossfades into a spinner in the same fixed-size box, and clicks are ignored.
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
) {
    val colors = GlassTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val shape = GlassShapes.Button
    val contentColor = if (style == GlassButtonStyle.Secondary) colors.textPrimary else colors.onAccent
    val surface = when (style) {
        GlassButtonStyle.Primary -> Modifier.background(colors.accent, shape)
        GlassButtonStyle.Danger -> Modifier.background(colors.error, shape)
        GlassButtonStyle.Secondary -> Modifier
            .background(colors.fieldFill, shape)
            .border(GlassDimens.BorderWidth, colors.fieldBorder, shape)
    }
    val loadingLabel = stringResource(R.string.state_loading)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(GlassDimens.ButtonHeight)
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
        Crossfade(
            targetState = loading,
            animationSpec = motionSpec(tween(MotionTokens.FEEDBACK_MS, easing = MotionTokens.EaseOut)),
            label = "buttonLoading",
        ) { isLoading ->
            Box(contentAlignment = Alignment.Center) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = contentColor,
                        strokeWidth = 2.5.dp,
                    )
                } else {
                    Text(
                        text = stringResource(text),
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                    )
                }
            }
        }
    }
}
