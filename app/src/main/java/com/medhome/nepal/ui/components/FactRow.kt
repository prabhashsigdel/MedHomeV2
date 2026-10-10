package com.medhome.nepal.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medhome.nepal.ui.theme.GlassTheme

/** Test tags on a [FactRow]'s label and value, for layout tests. */
const val FACT_LABEL_TAG = "fact_label"
const val FACT_VALUE_TAG = "fact_value"

/** A label on the left and its value on the right; TalkBack reads them together. */
@Composable
fun FactRow(@StringRes label: Int, value: String) {
    FactRow(label = stringResource(label), value = value)
}

/**
 * [FactRow] with its label already resolved (a date, say). The label keeps its natural width
 * on one line (it is measured first, unweighted); the value gets the rest and wraps onto more
 * lines, right-aligned. Both line up on their first baseline.
 */
@Composable
fun FactRow(label: String, value: String) {
    val colors = GlassTheme.colors
    Row(
        modifier = Modifier
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            softWrap = false,
            modifier = Modifier
                .alignByBaseline()
                .testTag(FACT_LABEL_TAG),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f)
                .alignByBaseline()
                .testTag(FACT_VALUE_TAG),
        )
    }
}
