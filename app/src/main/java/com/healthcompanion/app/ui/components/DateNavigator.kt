package com.healthcompanion.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.healthcompanion.app.ui.theme.LocalHealthColors
import com.healthcompanion.app.ui.theme.Spacing

@Composable
fun DateNavigator(
    dateLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onResetOrHistory: () -> Unit = {},
    canGoNext: Boolean = true,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.m),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = healthColors.textPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onPrevious,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = healthColors.pillUnselectedBg,
                    contentColor = healthColors.textPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous day",
                    modifier = Modifier.size(18.dp)
                )
            }

            FilledTonalIconButton(
                onClick = onNext,
                enabled = canGoNext,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = healthColors.pillUnselectedBg,
                    contentColor = healthColors.textPrimary,
                    disabledContainerColor = healthColors.pillUnselectedBg.copy(alpha = 0.5f),
                    disabledContentColor = healthColors.textMuted
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next day",
                    modifier = Modifier.size(18.dp)
                )
            }

            FilledTonalIconButton(
                onClick = onResetOrHistory,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = healthColors.pillUnselectedBg,
                    contentColor = healthColors.textPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset to today",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
