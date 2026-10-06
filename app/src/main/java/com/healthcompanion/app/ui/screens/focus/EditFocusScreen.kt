
package com.healthcompanion.app.ui.screens.focus

import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.semantics
import com.healthcompanion.app.ui.theme.ShapeXxl
import kotlinx.coroutines.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthcompanion.app.data.FocusStore
import com.healthcompanion.app.data.FocusTileDef
import com.healthcompanion.app.data.FocusTileRegistry
import com.healthcompanion.app.ui.screens.today.ProgressRingWidgetContent
import com.healthcompanion.app.ui.format.HealthFormat
import com.healthcompanion.app.ui.theme.LocalHealthColors

@Composable
fun EditFocusScreen(
    focusStore: FocusStore,
    todaySteps: Int,
    dailyStepGoal: Int,
    todayDistanceKm: Double,
    dailyDistanceGoalKm: Double,
    weeklyCardioMinutes: Int,
    weeklyCardioGoal: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedEditPage by remember { mutableIntStateOf(0) }
    var page0Tiles by remember { mutableStateOf(focusStore.getPageTiles(0)) }
    var page1Tiles by remember { mutableStateOf(focusStore.getPageTiles(1)) }
    var page2Tiles by remember { mutableStateOf(focusStore.getPageTiles(2)) }
    var isExpandedView by remember { mutableStateOf(focusStore.isExpandedView()) }

    // §4.2: removing a tile used to write straight to FocusStore with no undo,
    // so adding a tile and backing out left the dashboard changed. The removed
    // id is held here and can be restored from the snackbar.
    var lastRemovedTileId by remember { mutableStateOf<String?>(null) }
    var lastRemovedIndex by remember { mutableIntStateOf(0) }

    fun currentTiles(): List<String> = when (selectedEditPage) {
        0 -> page0Tiles
        1 -> page1Tiles
        else -> page2Tiles
    }

    fun updateSelection(newList: List<String>) {
        when (selectedEditPage) {
            0 -> {
                page0Tiles = newList
                focusStore.savePageTiles(0, newList)
            }
            1 -> {
                page1Tiles = newList
                focusStore.savePageTiles(1, newList)
            }
            else -> {
                page2Tiles = newList
                focusStore.savePageTiles(2, newList)
            }
        }
    }

    val scope = rememberCoroutineScope()

    fun removeTile(tileId: String, label: String) {
        val existing = currentTiles()
        val index = existing.indexOf(tileId)
        if (index < 0) return
        updateSelection(existing.filterNot { it == tileId })
        lastRemovedTileId = tileId
        lastRemovedIndex = index
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Removed $label",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                val restored = currentTiles().toMutableList()
                restored.add(lastRemovedIndex.coerceIn(0, restored.size), tileId)
                updateSelection(restored)
            }
            lastRemovedTileId = null
        }
    }

    /** Adds a tile, telling the user when the page is already full. */
    fun addTile(tileId: String, label: String) {
        val maxTiles = if (isExpandedView) 6 else 3
        if (currentTiles().size >= maxTiles) {
            // §4.2: this used to fall through silently, so tapping "+" at the
            // limit looked like a broken button.
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "This page already has $maxTiles tiles — remove one first",
                    duration = SnackbarDuration.Short
                )
            }
            return
        }
        updateSelection(currentTiles() + tileId)
    }

    // A Scaffold so the undo / limit snackbars have somewhere to appear.
    Scaffold(
        modifier = modifier,
        containerColor = healthColors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        // 1. Top bar: Back arrow + "Edit focus"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
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
                text = "Edit focus",
                style = MaterialTheme.typography.titleLarge,
                color = healthColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 2. Preview Panel (matching media_1790884772857.png)
            item {
                if (!isExpandedView) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Left Gauge Card with Remove/Settings indicator
                        Box(
                            modifier = Modifier
                                .weight(1.05f)
                                .height(180.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(ShapeXxl)
                                    .background(healthColors.cardBackground)
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                when (selectedEditPage) {
                                    0 -> {
                                        ProgressRingWidgetContent(
                                            title = "Steps",
                                            currentValue = todaySteps.toFloat(),
                                            goalValue = dailyStepGoal.toFloat(),
                                            displayValue = HealthFormat.count(todaySteps),
                                            displayGoal = "of ${HealthFormat.count(dailyStepGoal)}",
                                            ringColor = healthColors.accentTeal,
                                            trackColor = healthColors.cardElevated,
                                            icon = androidx.compose.material.icons.Icons.Default.DirectionsWalk
                                        )
                                    }
                                    1 -> {
                                        ProgressRingWidgetContent(
                                            title = "Distance",
                                            currentValue = todayDistanceKm.toFloat(),
                                            goalValue = dailyDistanceGoalKm.toFloat(),
                                            displayValue = String.format(java.util.Locale.getDefault(), "%.2f", todayDistanceKm),
                                            displayUnit = "km",
                                            displayGoal = "of ${String.format(java.util.Locale.getDefault(), "%.1f", dailyDistanceGoalKm)} km",
                                            ringColor = healthColors.accentTeal,
                                            trackColor = healthColors.cardElevated,
                                            icon = androidx.compose.material.icons.Icons.Default.LocationOn
                                        )
                                    }
                                    else -> {
                                        ProgressRingWidgetContent(
                                            title = "Cardio load",
                                            subtitle = "Weekly",
                                            currentValue = weeklyCardioMinutes.toFloat(),
                                            goalValue = weeklyCardioGoal.toFloat(),
                                            displayValue = "$weeklyCardioMinutes",
                                            displayGoal = "of $weeklyCardioGoal",
                                            ringColor = healthColors.heartVigorous,
                                            trackColor = healthColors.cardElevated,
                                            icon = androidx.compose.material.icons.Icons.Default.Favorite
                                        )
                                    }
                                }
                            }

                            // Badge button on top-right of ring widget
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-4).dp)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(healthColors.accentTeal)
                                    // The gauge cannot be removed. This badge used
                                    // to be a `clickable {}` no-op, so it looked
                                    // like a button that did nothing; it is now
                                    // decorative and hidden from accessibility.
                                    .semantics { },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = null,
                                    tint = healthColors.onAccentTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Right 3 mini-tiles slots with Remove (-) badge
                        Column(
                            modifier = Modifier.weight(1.15f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (i in 0..2) {
                                val tileId = currentTiles().getOrNull(i)
                                if (tileId != null) {
                                    val tileDef = FocusTileRegistry.getDef(tileId)
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        Surface(
                                            shape = MaterialTheme.shapes.extraLarge,
                                            color = tileDef.iconBgColor,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(54.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                    .clip(CircleShape)
                                                    .background(healthColors.cardBackground.copy(alpha = 0.5f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = tileDef.icon,
                                                    contentDescription = null,
                                                    tint = tileDef.iconTint,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = tileDef.title,
                                                    color = healthColors.textPrimary,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (tileDef.id == "distance") "${String.format(java.util.Locale.getDefault(), "%.2f", todayDistanceKm)} km"
                                                    else tileDef.defaultVal,
                                                    color = healthColors.textSecondary,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }

                                    // Minus button at top-right
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 4.dp, y = (-4).dp)
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(healthColors.accentTeal)
                                            .clickable {
                                                updateSelection(currentTiles() - tileId)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Remove",
                                            tint = healthColors.onAccentTeal,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            } else {
                                // Empty placeholder slot
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .clip(MaterialTheme.shapes.extraLarge)
                                        .background(healthColors.cardBackground.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+ Empty slot",
                                        color = healthColors.textMuted,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Expanded View: 2-column grid of equal small tiles preview
                val previewIds = currentTiles().take(6)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val rows = previewIds.chunked(2)
                    rows.forEach { rowTiles ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowTiles.forEach { tileId ->
                                val tileDef = FocusTileRegistry.getDef(tileId)
                                Box(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        shape = MaterialTheme.shapes.extraLarge,
                                        color = tileDef.iconBgColor,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .background(healthColors.cardBackground.copy(alpha = 0.5f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = tileDef.icon,
                                                    contentDescription = null,
                                                    tint = tileDef.iconTint,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = tileDef.title,
                                                    color = healthColors.textPrimary,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (tileDef.id == "distance") "${String.format(java.util.Locale.getDefault(), "%.2f", todayDistanceKm)} km"
                                                    else tileDef.defaultVal,
                                                    color = healthColors.textSecondary,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }

                                    // Minus button at top-right
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 4.dp, y = (-4).dp)
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(healthColors.accentTeal)
                                            .clickable {
                                                updateSelection(currentTiles() - tileId)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Remove",
                                            tint = healthColors.onAccentTeal,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            if (rowTiles.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

            // Pager Indicator under preview (click to switch between the 3 grid pages)
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Grid Page ${selectedEditPage + 1} of 3 • Tap dot to customize other pages",
                        color = healthColors.textMuted,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(3) { index ->
                            val isActive = selectedEditPage == index
                            Box(
                                modifier = Modifier
                                    .size(if (isActive) 18.dp else 7.dp, 7.dp)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(
                                        if (isActive) healthColors.accentTeal
                                        else healthColors.textMuted.copy(alpha = 0.35f)
                                    )
                                    .clickable { selectedEditPage = index }
                            )
                        }
                    }
                }
            }

            // Material 3 SingleChoiceSegmentedButtonRow: [ Standard view ] [ Expanded view ]
            item {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = !isExpandedView,
                        onClick = {
                            isExpandedView = false
                            focusStore.setExpandedView(false)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = healthColors.accentTeal,
                            activeContentColor = healthColors.accentTeal,
                            inactiveContainerColor = healthColors.cardBackground,
                            inactiveContentColor = healthColors.textSecondary
                        )
                    ) {
                        Text(text = "Standard view", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    }

                    SegmentedButton(
                        selected = isExpandedView,
                        onClick = {
                            isExpandedView = true
                            focusStore.setExpandedView(true)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = healthColors.accentTeal,
                            activeContentColor = healthColors.accentTeal,
                            inactiveContainerColor = healthColors.cardBackground,
                            inactiveContentColor = healthColors.textSecondary
                        )
                    ) {
                        Text(text = "Expanded view", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Section: Add tiles
            item {
                Text(
                    text = "Add tiles",
                    style = MaterialTheme.typography.titleLarge,
                    color = healthColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                Text(
                    text = "Small",
                    style = MaterialTheme.typography.titleMedium,
                    color = healthColors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 2-column Grid of Small Tiles matching media_1790884772857.png
            val availableTiles = FocusTileRegistry.allTiles
            val chunkedTiles = availableTiles.chunked(2)

            items(items = chunkedTiles, key = { pair -> pair.first().id }) { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEach { tile ->
                        val isSelected = currentTiles().contains(tile.id)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = tile.iconBgColor,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(healthColors.cardBackground.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = tile.icon,
                                            contentDescription = null,
                                            tint = tile.iconTint,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tile.title,
                                            color = healthColors.textPrimary,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = tile.defaultVal,
                                            color = healthColors.textSecondary,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            // '+' Add button or '-' Remove button at top-right
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-4).dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) healthColors.accentTeal else healthColors.accentTeal)
                                    .clickable {
if (isSelected) {
                                            removeTile(tile.id, tile.title)
                                        } else {
                                            addTile(tile.id, tile.title)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.Remove else Icons.Default.Add,
                                    contentDescription = if (isSelected) "Remove" else "Add",
                                    tint = healthColors.onAccentTeal,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            item {
                Text(
                    text = "Large",
                    style = MaterialTheme.typography.titleMedium,
                    color = healthColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            // Large Ring Preview Cards (Steps & Weekly Cardio) matching media_1790884772804.png
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Large Steps Ring
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(160.dp)
                            .clip(ShapeXxl)
                            .background(healthColors.cardBackground)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ProgressRingWidgetContent(
                            title = "Steps",
                            currentValue = todaySteps.toFloat(),
                            goalValue = dailyStepGoal.toFloat(),
                            displayValue = HealthFormat.count(todaySteps),
                            displayGoal = "of ${HealthFormat.count(dailyStepGoal)}",
                            ringColor = healthColors.accentTeal,
                            trackColor = healthColors.cardElevated,
                            icon = Icons.Default.DirectionsWalk
                        )
                    }

                    // Large Weekly Cardio Ring
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(160.dp)
                            .clip(ShapeXxl)
                            .background(healthColors.cardBackground)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ProgressRingWidgetContent(
                            title = "Weekly cardio",
                            currentValue = weeklyCardioMinutes.toFloat(),
                            goalValue = weeklyCardioGoal.toFloat(),
                            displayValue = "${((weeklyCardioMinutes.toFloat() / weeklyCardioGoal.coerceAtLeast(1)) * 100).toInt()}%",
                            displayGoal = "$weeklyCardioMinutes of $weeklyCardioGoal",
                            ringColor = healthColors.accentTeal,
                            trackColor = healthColors.cardElevated,
                            icon = Icons.Default.Favorite
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
    }
}
