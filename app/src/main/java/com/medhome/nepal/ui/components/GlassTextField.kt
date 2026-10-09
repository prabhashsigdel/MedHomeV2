package com.medhome.nepal.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.roundToInt
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.medhome.nepal.R
import com.medhome.nepal.ui.motion.MotionTokens
import com.medhome.nepal.ui.motion.feedbackTween
import com.medhome.nepal.ui.motion.motionSpec
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassShapes
import com.medhome.nepal.ui.theme.GlassTheme

private val FocusedBorderWidth = 1.5.dp

/**
 * 52dp glass field (the control fill and glass border; accent or error border when focused or
 * invalid). The label sits inside the field: centred
 * like a placeholder when empty, floating small to the top once focused or filled (as Material's
 * filled text field), which saves a label line above every field. An error message fades in with
 * a small height expand below the field (no shaking). [hint] shows there when there is no error.
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    @StringRes error: Int?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    @StringRes hint: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeDone: () -> Unit = {},
    isPassword: Boolean = false,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    /** Autofill hint, e.g. ContentType.Username + ContentType.EmailAddress, or NewPassword. */
    contentType: ContentType? = null,
) {
    val colors = GlassTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    // Drawn over the glass border: transparent at rest, so focus and errors fade in on top of it.
    val borderColor by animateColorAsState(
        targetValue = when {
            error != null -> colors.error
            focused -> colors.accentEmphasis
            else -> Color.Transparent
        },
        animationSpec = feedbackTween(),
        label = "fieldBorder",
    )
    val errorText = error?.let { stringResource(it) }
    val labelText = stringResource(label)

    Column(modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accentEmphasis),
            visualTransformation = if (isPassword && !passwordVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
                capitalization = capitalization,
            ),
            keyboardActions = KeyboardActions(onDone = { onImeDone() }),
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    if (errorText != null) error(errorText)
                    if (contentType != null) this.contentType = contentType
                },
            // The label lives inside the decoration: text fields merge their decoration into one
            // TalkBack item, so it is announced with the typed value (as Material's TextField does).
            decorationBox = { innerTextField ->
                FieldBox(
                    focused = focused,
                    hasError = error != null,
                    borderColor = borderColor,
                    isPassword = isPassword,
                    trailing = {
                        if (isPassword) {
                            PasswordToggle(
                                visible = passwordVisible,
                                enabled = enabled,
                                onToggle = { passwordVisible = !passwordVisible },
                            )
                        }
                    },
                ) {
                    FloatingLabelLayout(
                        label = labelText,
                        floated = focused || value.isNotEmpty(),
                        labelColor = when {
                            error != null -> colors.error
                            focused -> colors.link
                            else -> colors.textSecondary
                        },
                        innerTextField = innerTextField,
                    )
                }
            },
        )
        SupportingText(errorText = errorText, hint = hint)
    }
}

@Composable
private fun FieldBox(
    focused: Boolean,
    hasError: Boolean,
    borderColor: Color,
    isPassword: Boolean,
    trailing: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(GlassDimens.FieldHeight)
            .glassControl(GlassShapes.Input)
            .border(
                if (focused || hasError) FocusedBorderWidth else GlassDimens.BorderWidth,
                borderColor,
                GlassShapes.Input,
            )
            .padding(start = 16.dp, end = if (isPassword) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) { content() }
        trailing()
    }
}

/**
 * Places the label and the input inside the field. Floated: label (small) above the input, the
 * pair centred vertically. Resting: label (body size) centred alone. Positions come from the
 * measured line heights, so it works for Manrope and the taller Devanagari lines alike.
 */
@Composable
private fun FloatingLabelLayout(
    label: String,
    floated: Boolean,
    labelColor: Color,
    innerTextField: @Composable () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = if (floated) 1f else 0f,
        animationSpec = feedbackTween(),
        label = "floatingLabel",
    )
    val color by animateColorAsState(labelColor, animationSpec = feedbackTween(), label = "labelColor")
    val restingStyle = MaterialTheme.typography.bodyLarge
    val floatedStyle = MaterialTheme.typography.labelSmall
    Layout(
        content = {
            Text(
                text = label,
                style = lerp(restingStyle, floatedStyle, progress),
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box { innerTextField() }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val labelPlaceable = measurables[0].measure(loose)
        val inputPlaceable = measurables[1].measure(loose.copy(minWidth = constraints.maxWidth))
        val height = constraints.maxHeight
        val floatedLabelHeight = floatedStyle.lineHeight.roundToPx()
        // Floated: label and input as one block, centred in the field.
        val floatedTop = ((height - floatedLabelHeight - inputPlaceable.height) / 2).coerceAtLeast(0)
        val restingLabelY = (height - labelPlaceable.height) / 2
        val labelY = (restingLabelY + (floatedTop - restingLabelY) * progress).roundToInt()
        val inputY = floatedTop + floatedLabelHeight
        layout(constraints.maxWidth, height) {
            inputPlaceable.placeRelative(0, inputY)
            labelPlaceable.placeRelative(0, labelY)
        }
    }
}

@Composable
private fun PasswordToggle(visible: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle, enabled = enabled) {
        Text(
            text = stringResource(if (visible) R.string.action_hide_password else R.string.action_show_password),
            style = MaterialTheme.typography.labelMedium,
            color = GlassTheme.colors.link,
        )
    }
}

@Composable
private fun SupportingText(errorText: String?, @StringRes hint: Int?) {
    val colors = GlassTheme.colors
    // Keep the last message while the exit animation plays.
    var lastError by remember { mutableStateOf(errorText) }
    if (errorText != null) lastError = errorText
    val spec = motionSpec(tween<Float>(MotionTokens.FEEDBACK_MS, easing = MotionTokens.EaseOut))
    AnimatedVisibility(
        visible = errorText != null,
        enter = fadeIn(spec) + expandVertically(motionSpec(tween(MotionTokens.FEEDBACK_MS, easing = MotionTokens.EaseOut))),
        exit = fadeOut(spec) + shrinkVertically(motionSpec(tween(MotionTokens.FEEDBACK_MS, easing = MotionTokens.EaseOut))),
    ) {
        Text(
            text = lastError.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = colors.error,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
    }
    if (errorText == null && hint != null) {
        Text(
            text = stringResource(hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
    }
}
