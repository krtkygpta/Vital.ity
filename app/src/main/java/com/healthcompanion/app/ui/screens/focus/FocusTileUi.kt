package com.healthcompanion.app.ui.screens.focus

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.healthcompanion.app.data.FocusTileDef
import com.healthcompanion.app.data.TileTone
import com.healthcompanion.app.ui.theme.LocalHealthColors

@Composable
fun TileTone.containerColor(): Color = when (this) {
    TileTone.TEAL -> MaterialTheme.colorScheme.secondaryContainer
    TileTone.SLATE -> MaterialTheme.colorScheme.surfaceContainerHighest
    TileTone.BLUE -> MaterialTheme.colorScheme.primaryContainer
    TileTone.PURPLE -> MaterialTheme.colorScheme.tertiaryContainer
    TileTone.GREEN -> LocalHealthColors.current.cardElevated
}

@Composable
fun TileTone.contentColor(): Color = when (this) {
    TileTone.TEAL -> MaterialTheme.colorScheme.onSecondaryContainer
    TileTone.SLATE -> MaterialTheme.colorScheme.onSurfaceVariant
    TileTone.BLUE -> MaterialTheme.colorScheme.onPrimaryContainer
    TileTone.PURPLE -> MaterialTheme.colorScheme.onTertiaryContainer
    TileTone.GREEN -> LocalHealthColors.current.statusGood
}

fun focusTileIcon(iconKey: String): ImageVector = when (iconKey) {
    "walk" -> Icons.Default.DirectionsWalk
    "fitness" -> Icons.Default.FitnessCenter
    "run" -> Icons.Default.DirectionsRun
    "trending_up" -> Icons.Default.TrendingUp
    "location" -> Icons.Default.LocationOn
    "fire" -> Icons.Default.LocalFireDepartment
    "terrain" -> Icons.Default.Terrain
    "favorite" -> Icons.Default.Favorite
    "self_improvement" -> Icons.Default.SelfImprovement
    "bedtime" -> Icons.Default.Bedtime
    "monitor_heart" -> Icons.Default.MonitorHeart
    "water_drop" -> Icons.Default.WaterDrop
    "air" -> Icons.Default.Air
    "device_thermostat" -> Icons.Default.DeviceThermostat
    "timeline" -> Icons.Default.Timeline
    "bloodtype" -> Icons.Default.Bloodtype
    "scale" -> Icons.Default.Scale
    "psychology" -> Icons.Default.Psychology
    "spa" -> Icons.Default.Spa
    "local_drink" -> Icons.Default.LocalDrink
    "emoji_events" -> Icons.Default.EmojiEvents
    "military_tech" -> Icons.Default.MilitaryTech
    "pie_chart" -> Icons.Default.PieChart
    else -> Icons.Default.Circle
}

val FocusTileDef.icon: ImageVector
    get() = focusTileIcon(iconKey)

val FocusTileDef.iconBgColor: Color
    @Composable
    get() = tone.containerColor()

val FocusTileDef.iconTint: Color
    @Composable
    get() = tone.contentColor()
