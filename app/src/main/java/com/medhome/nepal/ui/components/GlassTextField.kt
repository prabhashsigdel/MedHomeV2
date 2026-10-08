package com.medhome.nepal.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
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
 * 52dp field with a white 50% fill and white border, labelled above. An error message fades in
 * with a small height expand below the field (no shaking). [hint] shows there when there is no error.
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
) {
    val colors = GlassTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = when {
            error != null -> colors.error
            focused -> colors.accent
            else -> colors.fieldBorder
        },
        animationSpec = feedbackTween(),
        label = "fieldBorder",
    )
    val errorText = error?.let { stringResource(it) }
    val labelText = stringResource(label)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
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
                .semantics { if (errorText != null) error(errorText) },
            // The label lives inside the decoration: text fields merge their decoration into one
            // TalkBack item, so it is announced with the typed value (as Material's TextField does).
            decorationBox = { innerTextField ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = labelText, style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
                    FieldBox(
                        focused = focused,
                        hasError = error != null,
                        borderColor = borderColor,
                        isPassword = isPassword,
                        innerTextField = innerTextField,
                        trailing = {
                            if (isPassword) {
                                PasswordToggle(
                                    visible = passwordVisible,
                                    enabled = enabled,
                                    onToggle = { passwordVisible = !passwordVisible },
                                )
                            }
                        },
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
    innerTextField: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val colors = GlassTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(GlassDimens.FieldHeight)
            .background(colors.fieldFill, GlassShapes.Input)
            .border(
                if (focused || hasError) FocusedBorderWidth else GlassDimens.BorderWidth,
                borderColor,
                GlassShapes.Input,
            )
            .padding(start = 16.dp, end = if (isPassword) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) { innerTextField() }
        trailing()
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
            modifier = Modifier.padding(start = 4.dp),
        )
    }
    if (errorText == null && hint != null) {
        Text(
            text = stringResource(hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
