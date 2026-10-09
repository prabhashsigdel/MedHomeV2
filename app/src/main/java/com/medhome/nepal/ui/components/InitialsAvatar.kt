package com.medhome.nepal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.medhome.nepal.R
import com.medhome.nepal.ui.home.initialsOf
import com.medhome.nepal.ui.theme.GlassTheme

/**
 * A glass circle with [name]'s initials (a person icon when it has none). Decorative: the name
 * itself is always shown next to it, so TalkBack skips the avatar.
 */
@Composable
fun InitialsAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val colors = GlassTheme.colors
    val initials = remember(name) { initialsOf(name) }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.glassFill)
            .glassBorder(CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (initials != null) {
            Text(text = initials, style = textStyle, color = colors.textPrimary, maxLines = 1)
        } else {
            Icon(painter = painterResource(R.drawable.ic_sym_person), contentDescription = null, tint = colors.textPrimary)
        }
    }
}
