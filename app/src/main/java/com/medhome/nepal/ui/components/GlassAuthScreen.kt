package com.medhome.nepal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medhome.nepal.R
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassTheme

private val TopSpace = 32.dp
private val MinGap = 16.dp
private val BottomSpace = 8.dp
private val LogoSize = 32.dp

/** Share of the free space above the form; the rest goes below, so it sits slightly high. */
private const val SPACE_ABOVE_WEIGHT = 0.4f
private const val SPACE_BELOW_WEIGHT = 0.6f

/**
 * Layout for the signed-out screens: brand header at the top, [content] (title + form card)
 * in the middle with free space split 40/60, and [footer] near the bottom.
 *
 * The column is at least as tall as the visible area, so the weights only share out spare
 * space. When content doesn't fit (small screen, or the keyboard is open: safeDrawing
 * includes the IME) the gaps shrink to [MinGap] and everything scrolls.
 */
@Composable
fun GlassAuthScreen(
    modifier: Modifier = Modifier,
    showBack: Boolean = false,
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassBackground(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val viewportHeight = maxHeight
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = GlassDimens.FormMaxWidth)
                        .fillMaxWidth()
                        .heightIn(min = viewportHeight)
                        .padding(horizontal = GlassDimens.ScreenPadding),
                ) {
                    Spacer(Modifier.height(TopSpace))
                    BrandHeader(showBack = showBack, modifier = Modifier.entrance(0))
                    Spacer(Modifier.height(MinGap))
                    Spacer(Modifier.weight(SPACE_ABOVE_WEIGHT))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(GlassDimens.CompactItemSpacing),
                        content = content,
                    )
                    Spacer(Modifier.height(MinGap))
                    Spacer(Modifier.weight(SPACE_BELOW_WEIGHT))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        content = footer,
                    )
                    Spacer(Modifier.height(BottomSpace))
                }
            }
        }
    }
}

/** The form card on sign-in screens: the compact padding and spacing that let them fit. */
@Composable
fun AuthFormCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(
        modifier = modifier,
        contentPadding = PaddingValues(GlassDimens.CompactCardPadding),
        verticalArrangement = Arrangement.spacedBy(GlassDimens.CompactItemSpacing),
        content = content,
    )
}

/** Optional back arrow, then the logo mark and "MedHome" wordmark. */
@Composable
private fun BrandHeader(showBack: Boolean, modifier: Modifier = Modifier) {
    val colors = GlassTheme.colors
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (showBack) {
            // Pull the 48dp touch target left so the arrow itself lines up with the content edge.
            BackButton(modifier = Modifier.offset(x = (-12).dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // Read as one "MedHome" item rather than an unlabeled image plus text.
            modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
            Box(
                modifier = Modifier
                    .size(LogoSize)
                    .background(colors.accent, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_medical_cross),
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = colors.textPrimary,
            )
        }
    }
}
