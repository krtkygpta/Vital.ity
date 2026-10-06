package com.healthcompanion.app.ui.screens.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.healthcompanion.app.model.HealthCategory
import com.healthcompanion.app.model.HealthConnectCatalog
import com.healthcompanion.app.model.HealthMetricDefinition
import com.healthcompanion.app.ui.components.HealthTopAppBar
import com.healthcompanion.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthCatalogScreen(
    onBack: () -> Unit,
    onSelectMetric: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<HealthCategory?>(null) }

    val filteredMetrics = remember(searchQuery, selectedCategory) {
        HealthConnectCatalog.allMetrics.filter { metric ->
            val matchesCategory = selectedCategory == null || metric.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() ||
                    metric.name.contains(searchQuery, ignoreCase = true) ||
                    metric.description.contains(searchQuery, ignoreCase = true) ||
                    metric.category.title.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(healthColors.background)
            .statusBarsPadding()
            .padding(horizontal = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l)
    ) {
        // App Bar
        item {
            HealthTopAppBar(
                title = "Health Connect Metrics",
                onBack = onBack
            )
        }

        // Subtitle & Explanation
        item {
            Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
                Text(
                    text = "All Supported Health Connect Metrics",
                    style = MaterialTheme.typography.headlineMedium,
                    color = healthColors.textPrimary
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = "Health Connect standardizes health and fitness data across 6 categories. Browse all available data types and permissions below.",
                    color = healthColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Category Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All (${HealthConnectCatalog.allMetrics.size})", style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = healthColors.pillSelectedBg,
                        selectedLabelColor = healthColors.pillSelectedText,
                        containerColor = healthColors.pillUnselectedBg,
                        labelColor = healthColors.textSecondary
                    )
                )

                listOf(HealthCategory.SLEEP, HealthCategory.VITALS, HealthCategory.ACTIVITY).forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                        label = { Text(cat.title.substringBefore(" "), style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = healthColors.pillSelectedBg,
                            selectedLabelColor = healthColors.pillSelectedText,
                            containerColor = healthColors.pillUnselectedBg,
                            labelColor = healthColors.textSecondary
                        )
                    )
                }
            }
        }

        // Items
        items(items = filteredMetrics, key = { it.name }) { metric ->
            HealthMetricCatalogCard(
                metric = metric,
                // The catalog had no outbound navigation at all, so it was an
                // orphan screen a user could reach but never act on.
                onClick = { onSelectMetric?.invoke(metric.name) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(Spacing.xxxl))
        }
    }
}

@Composable
fun HealthMetricCatalogCard(
    metric: HealthMetricDefinition,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(healthColors.cardBackground)
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(Spacing.cardPaddingStandard)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = metric.name,
                    color = healthColors.textPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(healthColors.cardElevated)
                        .padding(horizontal = Spacing.s, vertical = Spacing.xs)
                ) {
                    Text(
                        text = metric.category.title,
                        color = healthColors.statusGood,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            Text(
                text = metric.description,
                color = healthColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Units: ${metric.units}",
                    color = healthColors.accentTeal,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text = metric.permission.substringAfterLast('.'),
                    color = healthColors.textMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
