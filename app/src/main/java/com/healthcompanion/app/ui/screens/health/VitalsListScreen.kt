package com.healthcompanion.app.ui.screens.health

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthcompanion.app.ui.components.ConnectedListItem
import com.healthcompanion.app.ui.components.m3eConnectedCardShape
import com.healthcompanion.app.ui.theme.*

@Composable
fun VitalsListScreen(
    onBack: () -> Unit,
    onNavigateToSpO2: () -> Unit,
    onNavigateToHeartRate: () -> Unit,
    onNavigateToSkinTemp: () -> Unit = {},
    onNavigateToHrv: () -> Unit = {},
    onNavigateToBreathingRate: () -> Unit = {},
    currentSpO2: Int? = null,
    currentRestingBpm: Int? = null,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current

    // The denominator is every vital this page can show, not just the ones that
    // happen to have readings — otherwise "2 of 2" is displayed as "2 of 1".
    val totalVitalsCount = 5
    val recordedVitalsCount = (if (currentSpO2 != null) 1 else 0) + (if (currentRestingBpm != null) 1 else 0)
    val inRangeVitalsCount = (if (currentSpO2 != null && currentSpO2 in 95..100) 1 else 0) +
        (if (currentRestingBpm != null && currentRestingBpm in 50..100) 1 else 0)
    val allInRange = recordedVitalsCount > 0 && inRangeVitalsCount == recordedVitalsCount

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(healthColors.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 4.dp, bottom = 4.dp),
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
                    text = "Vitals",
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

        // Summary Header Headline (Clean M3E without enclosing container box)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vitals summary",
                        color = healthColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(healthColors.accentTeal.copy(alpha = 0.18f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = when {
                                recordedVitalsCount == 0 -> "No data"
                                allInRange -> "In range"
                                else -> "Check"
                            },
                            color = if (recordedVitalsCount == 0) healthColors.textMuted else healthColors.accentTeal,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Text(
                    text = "$recordedVitalsCount of $totalVitalsCount recorded",
                    style = Typography.displayMedium,
                    color = healthColors.textPrimary,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = when {
                        recordedVitalsCount == 0 -> "No vitals are synced yet. Connect a wearable that writes to Health Connect to start tracking."
                        allInRange -> "All your recorded vitals are currently within your personal baseline and medical reference ranges."
                        else -> "Some recorded vitals fall outside their reference ranges."
                    },
                    color = healthColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item {
            Text(
                text = "Body metrics",
                style = Typography.titleLarge,
                color = healthColors.textPrimary,
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
            )
        }

        // Body Metrics Connected List (Single container with 0.5dp dividers)
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Blood Oxygen (SpO2) -> opens SpO2DetailScreen
                VitalMetricRow(
                    index = 0,
                    count = 5,
                    title = "Blood oxygen (SpO2)",
                    value = currentSpO2?.let { "$it%" } ?: "No data",
                    subtitle = if (currentSpO2 != null) {
                        if (currentSpO2 in 95..100) "Sleep average • 95%–100% range"
                        else "Sleep average • Below clinical range"
                    } else "No records in Health Connect",
                    status = when {
                        currentSpO2 == null -> null
                        currentSpO2 in 95..100 -> "In range"
                        else -> "Check"
                    },
                    icon = Icons.Default.WaterDrop,
                    iconTint = healthColors.accentBlue,
                    onClick = onNavigateToSpO2
                )

                // 2. Resting Heart Rate -> opens HeartRateDetailScreen
                VitalMetricRow(
                    index = 1,
                    count = 5,
                    title = "Resting heart rate",
                    value = currentRestingBpm?.let { "$it bpm" } ?: "No data",
                    subtitle = if (currentRestingBpm != null) {
                        if (currentRestingBpm in 50..100) "Daily average • In typical range"
                        else "Daily average • Outside typical range"
                    } else "No records in Health Connect",
                    status = when {
                        currentRestingBpm == null -> null
                        currentRestingBpm in 50..100 -> "In range"
                        else -> "Check"
                    },
                    icon = Icons.Default.Favorite,
                    iconTint = healthColors.heartPeak,
                    onClick = onNavigateToHeartRate
                )

                // 3. Heart Rate Variability (HRV)
                VitalMetricRow(
                    index = 2,
                    count = 5,
                    title = "Heart rate variability (HRV)",
                    value = "No data",
                    subtitle = "No HRV records in Health Connect",
                    status = null,
                    icon = Icons.Default.Timeline,
                    iconTint = healthColors.accentPurple,
                    onClick = onNavigateToHrv
                )

                // 4. Breathing Rate
                VitalMetricRow(
                    index = 3,
                    count = 5,
                    title = "Breathing rate",
                    value = "No data",
                    subtitle = "No respiratory records in Health Connect",
                    status = null,
                    icon = Icons.Default.Air,
                    iconTint = healthColors.accentTeal,
                    onClick = onNavigateToBreathingRate
                )

                // 5. Skin Temperature
                VitalMetricRow(
                    index = 4,
                    count = 5,
                    title = "Skin temperature",
                    value = "No data",
                    subtitle = "No temperature records in Health Connect",
                    status = null,
                    icon = Icons.Default.Thermostat,
                    iconTint = healthColors.accentOrange,
                    onClick = onNavigateToSkinTemp
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun VitalMetricRow(
    index: Int,
    count: Int,
    title: String,
    value: String,
    subtitle: String,
    status: String?,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    ConnectedListItem(
        index = index,
        count = count,
        onClick = onClick,
        modifier = modifier,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        supportingContent = {
            Text(
                text = subtitle,
                color = healthColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.wrapContentWidth()
            ) {
                Text(
                    text = value,
                    color = if (value == "No data") healthColors.textMuted else healthColors.textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )

                if (status != null) {
                    val isWarning = status == "Check"
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                (if (isWarning) healthColors.accentOrange else healthColors.accentTeal)
                                    .copy(alpha = 0.2f)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = status,
                            color = if (isWarning) healthColors.accentOrange else healthColors.accentTeal,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    ) {
        Text(
            text = title,
            color = healthColors.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
