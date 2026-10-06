package com.healthcompanion.app.ui.screens.health

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthcompanion.app.ui.theme.LocalHealthColors
import com.healthcompanion.app.ui.theme.Typography

data class CategoryMetricItem(
    val id: String,
    val title: String,
    val value: String?,
    val subtitle: String,
    val icon: ImageVector,
    val isTracked: Boolean,
    val onNavigate: () -> Unit
)

@Composable
fun CategoryMetricsListScreen(
    categoryName: String,
    onBack: () -> Unit,
    metrics: List<CategoryMetricItem>,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val trackedCount = metrics.count { it.isTracked && !it.value.isNullOrBlank() && it.value != "No data" && it.value != "--" }
    val totalCount = metrics.size

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(healthColors.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App Bar with Universal Back Button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = healthColors.textPrimary
                    )
                }

                Text(
                    text = categoryName,
                    style = Typography.titleLarge,
                    color = healthColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                )

                Spacer(modifier = Modifier.size(48.dp))
            }
        }

        // Summary Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$categoryName summary",
                            color = healthColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    if (trackedCount > 0) healthColors.accentTeal.copy(alpha = 0.2f)
                                    else healthColors.pillUnselectedBg
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (trackedCount > 0) "$trackedCount tracked" else "No data",
                                color = if (trackedCount > 0) healthColors.accentTeal else healthColors.pillUnselectedText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Text(
                        text = "$trackedCount of $totalCount metrics tracked",
                        style = Typography.displayMedium,
                        color = healthColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = if (trackedCount > 0)
                            "Metrics with active sensor records are tracked from Health Connect."
                        else
                            "No sensor readings recorded for this category yet.",
                        color = healthColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Section Title
        item {
            Text(
                text = "Metrics in $categoryName",
                style = Typography.titleLarge,
                color = healthColors.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // List of Metrics
        items(items = metrics, key = { it.id }) { item ->
            val hasData = item.isTracked && !item.value.isNullOrBlank() && item.value != "No data" && item.value != "--"

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { item.onNavigate() }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (hasData) healthColors.accentTeal.copy(alpha = 0.15f)
                                    else healthColors.pillUnselectedBg
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = if (hasData) healthColors.accentTeal else healthColors.textMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = item.title,
                                color = healthColors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.subtitle,
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = item.value ?: "No data",
                            color = if (hasData) healthColors.textPrimary else healthColors.textMuted,
                            fontWeight = if (hasData) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = healthColors.textMuted,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
