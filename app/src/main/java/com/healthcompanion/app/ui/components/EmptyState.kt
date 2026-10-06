package com.healthcompanion.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.healthcompanion.app.ui.theme.LocalHealthColors
import com.healthcompanion.app.ui.theme.Spacing

/**
 * The single empty-state treatment for the whole app.
 *
 * Previously empty states were a bare "No data" string with no title and no
 * action, which left the user with nothing to do next. Every caller now supplies
 * an [actionLabel]/[onAction] pair so the state is actionable — most often
 * "Connect Health Connect".
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val healthColors = LocalHealthColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = healthColors.textMuted,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(Spacing.m))
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = healthColors.textPrimary,
            textAlign = TextAlign.Center
        )

        if (body != null) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = healthColors.textSecondary,
                textAlign = TextAlign.Center
            )
        }

        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(Spacing.l))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}
