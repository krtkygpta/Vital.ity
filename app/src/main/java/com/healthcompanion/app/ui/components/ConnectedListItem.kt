package com.healthcompanion.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.healthcompanion.app.ui.theme.LocalHealthColors

/**
 * Material 3 Expressive connected list item.
 *
 * Connected card geometry matching the design tokens.
 */
@Composable
fun ConnectedListItem(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    content: @Composable () -> Unit
) {
    val healthColors = LocalHealthColors.current
    val shape = m3eConnectedCardShape(index, count, outerCorner = 16.dp, innerCorner = 4.dp)

    // A null onClick renders a non-interactive Surface. Previously the default
    // was `{}`, which made every static breakdown row focusable and clickable
    // while doing nothing.
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            shape = shape,
            color = healthColors.cardBackground
        ) {
            ConnectedListItemContent(
                index = index,
                contentPadding = contentPadding,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                overlineContent = overlineContent,
                supportingContent = supportingContent,
                content = content
            )
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = healthColors.cardBackground
        ) {
            ConnectedListItemContent(
                index = index,
                contentPadding = contentPadding,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                overlineContent = overlineContent,
                supportingContent = supportingContent,
                content = content
            )
        }
    }
}

@Composable
private fun ConnectedListItemContent(
    index: Int,
    contentPadding: PaddingValues,
    leadingContent: (@Composable () -> Unit)?,
    trailingContent: (@Composable () -> Unit)?,
    overlineContent: (@Composable () -> Unit)?,
    supportingContent: (@Composable () -> Unit)?,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (index > 0) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingContent != null) {
                leadingContent()
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                if (overlineContent != null) {
                    overlineContent()
                    Spacer(modifier = Modifier.height(2.dp))
                }
                content()
                if (supportingContent != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    supportingContent()
                }
            }
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(12.dp))
                trailingContent()
            }
        }
    }
}
