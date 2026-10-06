package com.healthcompanion.app.ui.screens.activity

import com.healthcompanion.app.ui.theme.ShapeXxl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
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
import com.healthcompanion.app.ui.components.DateNavigator
import com.healthcompanion.app.ui.components.TimeRange
import com.healthcompanion.app.ui.components.TimeRangePills
import com.healthcompanion.app.ui.components.canStepForward
import com.healthcompanion.app.ui.components.isPeriodSelectable
import com.healthcompanion.app.ui.components.stepBackward
import com.healthcompanion.app.ui.components.stepForward
import com.healthcompanion.app.ui.components.weekStartMon
import com.healthcompanion.app.ui.components.EmptyState
import com.healthcompanion.app.ui.theme.LocalHealthColors
import com.healthcompanion.app.ui.theme.MetricHero
import com.healthcompanion.app.ui.theme.Typography
import java.time.LocalDate

/** One row of the 24-hour activity strip. */
data class HourlyActivityPoint(
    val date: LocalDate,
    val hours: List<Pair<Int, Int>>
)

/**
 * Hourly activity: for each day, which of the 24 hours were "active"
 * (>= 250 steps between 09:00 and 18:00). A day is a row, an hour is a cell,
 * so a month or a quarter reads as a heatmap rather than a fake trend line.
 */
@Composable
fun HourlyActivityScreen(
    loadRange: suspend (start: LocalDate, end: LocalDate) -> Map<LocalDate, List<Pair<Int, Int>>>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    var selectedRange by remember { mutableStateOf(TimeRange.WEEK) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var points by remember { mutableStateOf<List<HourlyActivityPoint>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var hasAnyData by remember { mutableStateOf(false) }

    val periodDays = when (selectedRange) {
        TimeRange.DAY -> 1
        TimeRange.WEEK -> 7
        TimeRange.MONTH -> selectedDate.lengthOfMonth()
        else -> 30
    }

    LaunchedEffect(selectedDate, selectedRange) {
        loading = true
        val anchor = when (selectedRange) {
            TimeRange.DAY -> selectedDate
            TimeRange.WEEK -> weekStartMon(selectedDate)
            TimeRange.MONTH -> selectedDate.withDayOfMonth(1)
            else -> selectedDate.minusDays((periodDays - 1).toLong())
        }
        val end = when (selectedRange) {
            TimeRange.DAY -> selectedDate
            TimeRange.WEEK -> anchor.plusDays(6)
            TimeRange.MONTH -> selectedDate.withDayOfMonth(selectedDate.lengthOfMonth())
            else -> selectedDate
        }
        val buckets = loadRange(anchor, end)
        points = (0 until periodDays).map { offset ->
            val d = anchor.plusDays(offset.toLong())
            HourlyActivityPoint(d, buckets[d].orEmpty())
        }
        hasAnyData = buckets.isNotEmpty()
        loading = false
    }

    val activeHoursToday = points.lastOrNull()?.hours
        ?.count { (hour, steps) -> hour in 9..18 && steps >= 250 } ?: 0
    val peakHour = points.lastOrNull()?.hours?.maxByOrNull { it.second }

    val dateLabel = when (selectedRange) {
        TimeRange.DAY -> selectedDate.toString()
        TimeRange.WEEK -> "${weekStartMon(selectedDate).dayOfMonth}–${weekStartMon(selectedDate).plusDays(6).dayOfMonth} ${selectedDate.month.name.lowercase().replaceFirstChar { it.uppercase() }}"
        TimeRange.MONTH -> "${selectedDate.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${selectedDate.year}"
        else -> selectedDate.toString()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(healthColors.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                    text = "Hourly activity",
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

        item {
            TimeRangePills(
                selectedRange = selectedRange,
                onRangeSelected = { selectedRange = it },
                ranges = listOf(TimeRange.DAY, TimeRange.WEEK, TimeRange.MONTH)
            )
        }

        item {
            DateNavigator(
                dateLabel = dateLabel,
                onPrevious = { selectedDate = selectedDate.stepBackward(selectedRange) },
                onNext = {
                    val next = selectedDate.stepForward(selectedRange)
                    if (isPeriodSelectable(next, selectedRange)) selectedDate = next
                },
                onResetOrHistory = { selectedDate = LocalDate.now() },
                canGoNext = canStepForward(selectedDate, selectedRange) &&
                    isPeriodSelectable(selectedDate.stepForward(selectedRange), selectedRange)
            )
        }

        // Headline and primary metric
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (hasAnyData) "$activeHoursToday" else "—",
                            style = MetricHero,
                            color = if (hasAnyData) healthColors.textPrimary else healthColors.textMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "active hours",
                            color = healthColors.textSecondary,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(healthColors.accentTeal.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Goal: 9 h",
                            color = healthColors.accentTeal,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = if (selectedRange == TimeRange.DAY) "Hours with 250+ steps" else "Daily activity across the period",
                    color = healthColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // VISUALIZATION PRIMARY (Directly on screen, no heavy container wrapper)
        if (loading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = healthColors.accentTeal)
                }
            }
        } else if (!hasAnyData) {
            item {
                // §3.3: was two bare strings ("No data" at display size, "No data
                // collected") with no reason and no action.
                EmptyState(
                    title = "No activity recorded",
                    body = "No steps were synced to Health Connect for this period.",
                    actionLabel = "Back",
                    onAction = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                )
            }
        } else if (selectedRange == TimeRange.DAY) {
            // Single day 24-hour bar chart
            val singleDayHours = points.firstOrNull()?.hours ?: (0..23).map { it to 0 }
            val maxSteps = singleDayHours.maxOfOrNull { it.second }?.coerceAtLeast(100) ?: 100

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        singleDayHours.forEach { (hour, steps) ->
                            val heightRatio = (steps.toFloat() / maxSteps.toFloat()).coerceIn(0.04f, 1f)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(heightRatio)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(
                                        if (steps >= 250) healthColors.accentTeal
                                        else healthColors.accentTeal.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "12am", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                        Text(text = "6am", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                        Text(text = "12pm", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                        Text(text = "6pm", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                        Text(text = "12am", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        } else if (selectedRange == TimeRange.WEEK) {
            // 7-day capsule strip matching Exercise Days, colored by activity saturation
            val weekStart = weekStartMon(selectedDate)
            val days = (0..6).map { weekStart.plusDays(it.toLong()) }
            val maxDayActive = points.maxOfOrNull { p -> p.hours.count { it.first in 9..18 && it.second >= 250 } }?.coerceAtLeast(1) ?: 9

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        days.forEach { day ->
                            val point = points.firstOrNull { it.date == day }
                            val activeCount = point?.hours?.count { it.first in 9..18 && it.second >= 250 } ?: 0
                            val saturation = (activeCount.toFloat() / maxDayActive.toFloat()).coerceIn(0.15f, 1f)
                            val isSelected = day == selectedDate

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .clip(ShapeXxl)
                                        .background(
                                            if (activeCount > 0) healthColors.accentTeal.copy(alpha = saturation)
                                            else healthColors.cardElevated
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (activeCount > 0) "$activeCount" else "${day.dayOfMonth}",
                                        color = if (activeCount > 0) (if (saturation > 0.5f) healthColors.onAccentTeal else healthColors.textPrimary) else healthColors.textMuted,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                Text(
                                    text = day.dayOfWeek.name.take(1),
                                    color = if (isSelected) healthColors.accentTeal else healthColors.textMuted,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // 7-column calendar grid (Sunday-anchored / Month calendar)
            val monthStart = selectedDate.withDayOfMonth(1)
            val daysInMonth = monthStart.lengthOfMonth()
            val leadingBlanks = monthStart.dayOfWeek.value % 7
            val totalCells = ((leadingBlanks + daysInMonth + 6) / 7) * 7
            val maxDayActive = points.maxOfOrNull { p -> p.hours.count { it.first in 9..18 && it.second >= 250 } }?.coerceAtLeast(1) ?: 9

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Weekday header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("S", "M", "T", "W", "T", "F", "S").forEach { letter ->
                            Text(
                                text = letter,
                                color = healthColors.textMuted,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Grid cells
                    val weekCount = totalCells / 7
                    for (weekIdx in 0 until weekCount) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (dayCol in 0..6) {
                                val cellIdx = weekIdx * 7 + dayCol
                                val dayNum = cellIdx - leadingBlanks + 1
                                if (dayNum in 1..daysInMonth) {
                                    val cellDate = monthStart.withDayOfMonth(dayNum)
                                    val point = points.firstOrNull { it.date == cellDate }
                                    val activeCount = point?.hours?.count { it.first in 9..18 && it.second >= 250 } ?: 0
                                    val saturation = (activeCount.toFloat() / maxDayActive.toFloat()).coerceIn(0.18f, 1f)

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .clip(MaterialTheme.shapes.medium)
                                            .background(
                                                if (activeCount > 0) healthColors.accentTeal.copy(alpha = saturation)
                                                else healthColors.cardElevated
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$dayNum",
                                            color = if (activeCount > 0 && saturation > 0.6f) healthColors.onAccentTeal else healthColors.textPrimary,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (activeCount > 0) FontWeight.Bold else FontWeight.Normal)
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(30.dp)) }
    }
}

@Composable
private fun HourRow(point: HourlyActivityPoint) {
    val healthColors = LocalHealthColors.current
    val stepsByHour = point.hours.toMap()
    val peak = point.hours.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${point.date.dayOfMonth}",
            color = healthColors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.width(26.dp)
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            (0..23).forEach { hour ->
                val steps = stepsByHour[hour] ?: 0
                val isActive = hour in 9..18 && steps >= 250
                val intensity = (steps.toFloat() / peak).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(
                            when {
                                steps == 0 -> healthColors.cardElevated
                                isActive -> healthColors.accentTeal
                                else -> healthColors.accentBlue.copy(alpha = 0.25f + intensity * 0.5f)
                            }
                        )
                )
            }
        }
    }
}
