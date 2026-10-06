package com.healthcompanion.app.ui.screens.metric

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthcompanion.app.ui.components.ConnectedListItem
import com.healthcompanion.app.ui.components.DateNavigator
import com.healthcompanion.app.ui.components.GoogleStyleBarChart
import com.healthcompanion.app.ui.components.GoogleStyleLineChart
import com.healthcompanion.app.ui.components.GoogleStyleXAxis
import com.healthcompanion.app.ui.components.TimeRange
import com.healthcompanion.app.ui.components.TimeRangePills
import com.healthcompanion.app.ui.components.canStepForward
import com.healthcompanion.app.ui.components.isPeriodSelectable
import com.healthcompanion.app.ui.components.stepForward
import com.healthcompanion.app.ui.components.weekEndMon
import com.healthcompanion.app.ui.components.weekStartMon
import com.healthcompanion.app.ui.components.EmptyState
import com.healthcompanion.app.ui.theme.LocalHealthColors
import com.healthcompanion.app.ui.theme.MetricHero
import com.healthcompanion.app.ui.theme.Typography
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class MetricConfig(
    val title: String,
    val unit: String,
    val icon: ImageVector,
    val iconTint: Color,
    val baselineDescription: String,
    val clinicalRangeDescription: String,
    val aboutDescription: String,
    val isVariation: Boolean = false,
    val targetRange: ClosedFloatingPointRange<Float>? = null,
    val isSumMetric: Boolean = false,
    val targetGoal: Int? = null
)

@Composable
fun GenericMetricDetailScreen(
    config: MetricConfig,
    recordedValue: Float? = null,
    historyData: List<Pair<LocalDate, Float?>> = emptyList(),
    availableRanges: List<TimeRange> = TimeRange.entries,
    showDateNavigator: Boolean = true,
    isLoading: Boolean = false,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    var selectedRange by remember(availableRanges) { mutableStateOf(availableRanges.first()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var scrubbedIndex by remember { mutableStateOf<Int?>(null) }

    val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
    val weekFormatter = DateTimeFormatter.ofPattern("d MMM")
    val weekStart = weekStartMon(selectedDate)
    val weekRangeLabel = "${weekStart.dayOfMonth}–${weekEndMon(selectedDate).format(weekFormatter)}"

    val dateLabel = when (selectedRange) {
        TimeRange.DAY -> {
            when (selectedDate) {
                LocalDate.now() -> "Today"
                LocalDate.now().minusDays(1) -> "Yesterday"
                else -> selectedDate.format(dateFormatter)
            }
        }
        TimeRange.WEEK -> weekRangeLabel
        TimeRange.MONTH -> selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        TimeRange.THREE_MONTHS -> {
            val m1 = selectedDate.minusMonths(2).format(DateTimeFormatter.ofPattern("MMM"))
            val m2 = selectedDate.format(DateTimeFormatter.ofPattern("MMM yyyy"))
            "$m1–$m2"
        }
        TimeRange.YEAR -> selectedDate.format(DateTimeFormatter.ofPattern("yyyy"))
    }

    val displayPoints = remember(selectedRange, selectedDate, historyData) {
        when (selectedRange) {
            TimeRange.DAY -> historyData.filter { it.first == selectedDate }
            TimeRange.WEEK -> {
                val start = weekStartMon(selectedDate)
                val end = weekEndMon(selectedDate)
                val inRange = historyData.filter { !it.first.isBefore(start) && !it.first.isAfter(end) }
                if (inRange.isNotEmpty()) inRange
                else (0..6).map { start.plusDays(it.toLong()) to null }
            }
            TimeRange.MONTH -> {
                val start = selectedDate.withDayOfMonth(1)
                val end = selectedDate.withDayOfMonth(selectedDate.lengthOfMonth())
                historyData.filter { !it.first.isBefore(start) && !it.first.isAfter(end) }
            }
            TimeRange.THREE_MONTHS -> {
                val start = selectedDate.minusMonths(2).withDayOfMonth(1)
                val inRange = historyData.filter { !it.first.isBefore(start) && !it.first.isAfter(selectedDate) }
                if (inRange.isNotEmpty()) inRange else historyData.takeLast(3)
            }
            TimeRange.YEAR -> {
                val start = selectedDate.minusMonths(11).withDayOfMonth(1)
                val inRange = historyData.filter { !it.first.isBefore(start) && !it.first.isAfter(selectedDate) }
                if (inRange.isNotEmpty()) inRange else historyData.takeLast(12)
            }
        }
    }

    val historyList = remember(selectedRange, selectedDate, displayPoints, historyData) {
        when (selectedRange) {
            TimeRange.DAY -> {
                val pts = displayPoints.filter { it.second != null }
                if (pts.isNotEmpty()) pts.map { (date, v) -> "Today" to v }
                else listOf("Today" to recordedValue)
            }
            TimeRange.WEEK -> {
                val validDays = displayPoints.filter { it.second != null }
                if (validDays.isNotEmpty()) {
                    validDays.reversed().map { (date, v) ->
                        date.format(DateTimeFormatter.ofPattern("EEE d MMM")) to v
                    }
                } else {
                    listOf(selectedDate.format(DateTimeFormatter.ofPattern("EEE d MMM")) to recordedValue)
                }
            }
            TimeRange.MONTH -> {
                if (config.isSumMetric) {
                    val weekGroups = historyData.filter { it.second != null && !it.first.isBefore(selectedDate.withDayOfMonth(1)) && !it.first.isAfter(selectedDate.withDayOfMonth(selectedDate.lengthOfMonth())) }
                        .groupBy { weekStartMon(it.first) }
                    weekGroups.entries.sortedByDescending { it.key }.map { (wStart, pts) ->
                        val wEnd = weekEndMon(wStart)
                        val label = if (!wEnd.isBefore(LocalDate.now()) && !wStart.isAfter(LocalDate.now())) "This week"
                        else "${wStart.format(DateTimeFormatter.ofPattern("d MMM"))}–${wEnd.format(DateTimeFormatter.ofPattern("d MMM"))}"
                        val total = pts.mapNotNull { it.second }.sum()
                        label to total
                    }
                } else {
                    val valid = displayPoints.filter { it.second != null }
                    if (valid.isNotEmpty()) {
                        valid.reversed().mapIndexed { idx, (date, v) ->
                            val label = if (idx == 0 && date.isAfter(LocalDate.now().minusDays(7))) "This week"
                            else "${date.minusDays(6).format(DateTimeFormatter.ofPattern("d"))}–${date.format(DateTimeFormatter.ofPattern("d MMM"))}"
                            label to v
                        }
                    } else {
                        listOf("This month" to recordedValue)
                    }
                }
            }
            TimeRange.THREE_MONTHS -> {
                if (config.isSumMetric) {
                    val start = selectedDate.minusMonths(2).withDayOfMonth(1)
                    val weekGroups = historyData.filter { it.second != null && !it.first.isBefore(start) && !it.first.isAfter(selectedDate) }
                        .groupBy { weekStartMon(it.first) }
                    weekGroups.entries.sortedByDescending { it.key }.map { (wStart, pts) ->
                        val wEnd = weekEndMon(wStart)
                        val label = "${wStart.format(DateTimeFormatter.ofPattern("d MMM"))}–${wEnd.format(DateTimeFormatter.ofPattern("d MMM"))}"
                        val total = pts.mapNotNull { it.second }.sum()
                        label to total
                    }
                } else {
                    val months = (0..2).map { selectedDate.minusMonths(it.toLong()) }
                    months.mapIndexed { idx, mDate ->
                        val label = if (idx == 0) "This month" else mDate.format(DateTimeFormatter.ofPattern("MMMM"))
                        val v = historyData.firstOrNull { it.first.month == mDate.month && it.first.year == mDate.year }?.second
                            ?: recordedValue
                        label to v
                    }
                }
            }
            TimeRange.YEAR -> {
                val months = (0..11).map { selectedDate.minusMonths(it.toLong()) }
                months.mapIndexed { idx, mDate ->
                    val label = if (idx == 0) "This month" else mDate.format(DateTimeFormatter.ofPattern("MMMM"))
                    val v = historyData.firstOrNull { it.first.month == mDate.month && it.first.year == mDate.year }?.second
                        ?: recordedValue
                    label to v
                }
            }
        }
    }.filter { it.second != null }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(healthColors.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App Bar: Back + Title + Menu
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 10.dp, bottom = 4.dp),
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
                    text = config.title,
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

        // Time Range Pills [D] [W] [M] [3M] [Y]
        if (availableRanges.size > 1) {
            item {
                TimeRangePills(
                    selectedRange = selectedRange,
                    onRangeSelected = {
                        selectedRange = it
                        scrubbedIndex = null
                    },
                    ranges = availableRanges
                )
            }
        }

        // Date Navigator
        if (showDateNavigator) {
            item {
                DateNavigator(
                    dateLabel = dateLabel,
                    onPrevious = {
                        selectedDate = when (selectedRange) {
                            TimeRange.DAY -> selectedDate.minusDays(1)
                            TimeRange.WEEK -> selectedDate.minusDays(7)
                            TimeRange.MONTH -> selectedDate.minusMonths(1)
                            TimeRange.THREE_MONTHS -> selectedDate.minusMonths(3)
                            TimeRange.YEAR -> selectedDate.minusYears(1)
                        }
                    },
                    onNext = {
                        val next = selectedDate.stepForward(selectedRange)
                        if (isPeriodSelectable(next, selectedRange)) {
                            selectedDate = next
                        }
                    },
                    canGoNext = canStepForward(selectedDate, selectedRange) &&
                        isPeriodSelectable(selectedDate.stepForward(selectedRange), selectedRange),
                    onResetOrHistory = {
                        selectedDate = LocalDate.now()
                    }
                )
            }
        }

        if (isLoading) {
            item {
                val transition = rememberInfiniteTransition(label = "shimmer")
                val alpha by transition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 0.75f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(750),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )
                val shimmerColor = healthColors.cardElevated.copy(alpha = alpha)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Headline skeleton
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(16.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(shimmerColor)
                        )
                        Box(
                            modifier = Modifier
                                .width(190.dp)
                                .height(40.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(shimmerColor)
                        )
                        Box(
                            modifier = Modifier
                                .width(220.dp)
                                .height(16.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(shimmerColor)
                        )
                    }

                    // Chart skeleton card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(healthColors.cardBackground)
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            listOf(0.4f, 0.7f, 0.35f, 0.85f, 0.6f, 0.5f, 0.75f).forEach { ratio ->
                                Box(
                                    modifier = Modifier
                                        .width(18.dp)
                                        .fillMaxHeight(ratio)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(shimmerColor)
                                )
                            }
                        }
                    }

                    // History list skeleton
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .width(130.dp)
                                .height(18.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(shimmerColor)
                        )
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .clip(MaterialTheme.shapes.large)
                                    .background(healthColors.cardBackground)
                            )
                        }
                    }
                }
            }
        } else {
            // Main Value & Range Header
            item {
            val hasAnyData = displayPoints.any { it.second != null } || recordedValue != null
            val idx = scrubbedIndex
            val isScrubbed = idx != null && idx in displayPoints.indices
            val displayValue = if (isScrubbed && idx != null) {
                displayPoints[idx].second
            } else if (config.isSumMetric) {
                displayPoints.mapNotNull { it.second }.sum()
            } else {
                recordedValue ?: displayPoints.lastOrNull { it.second != null }?.second
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (hasAnyData && displayValue != null) {
                    Text(
                        text = if (isScrubbed && idx != null) displayPoints[idx].first.format(dateFormatter) else if (config.isSumMetric) "Total this period" else "Current reading",
                        color = healthColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = if (config.isVariation && displayValue > 0) "+$displayValue" else if (config.isSumMetric) "${displayValue.toInt()}" else "$displayValue",
                                style = MetricHero,
                                color = healthColors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = config.unit,
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        if (isScrubbed) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(healthColors.pillUnselectedBg)
                                    .clickable { scrubbedIndex = null },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = healthColors.textPrimary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(healthColors.accentTeal.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        val badgeText = if (config.isSumMetric && config.targetGoal != null) {
                            val total = displayValue.toInt()
                            "$total of ${config.targetGoal} ${config.unit}"
                        } else {
                            config.baselineDescription
                        }
                        Text(
                            text = badgeText,
                            color = healthColors.accentTeal,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    // §3.3: was two bare strings ("No data" at hero size, "No
                    // data collected") with no reason and no action.
                    EmptyState(
                        title = "No ${config.title.lowercase()} recorded",
                        body = "Nothing has been synced to Health Connect for this period.",
                        actionLabel = "Back",
                        onAction = onBack,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Chart or X-Axis (Matching media_1790857326236.png)
        item {
            val hasData = displayPoints.any { it.second != null }
            val isContinuousMetric = config.title.equals("Weight", ignoreCase = true) ||
                config.title.contains("Blood", ignoreCase = true) ||
                config.title.contains("Temperature", ignoreCase = true) ||
                config.title.contains("HRV", ignoreCase = true)

            val metricLabels: List<String> = when (selectedRange) {
                TimeRange.DAY -> listOf("12am", "6am", "12pm", "6pm", "12am")
                TimeRange.WEEK -> listOf("F", "S", "S", "M", "T", "W", "T")
                TimeRange.MONTH -> listOf("1", "8", "15", "22", "29")
                TimeRange.THREE_MONTHS -> listOf("Jul", "Aug", "Sep")
                TimeRange.YEAR -> listOf("Jan", "Mar", "May", "Jul", "Sep", "Nov")
            }

            if (hasData) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    if (isContinuousMetric) {
                        val validValues = displayPoints.mapNotNull { it.second }
                        val plotValues = if (validValues.size >= 2) validValues else listOf(validValues.firstOrNull() ?: 65f, validValues.firstOrNull() ?: 65f)
                        GoogleStyleLineChart(
                            values = plotValues,
                            showMarkers = true,
                            lineColor = config.iconTint,
                            valueFormatter = { v: Float -> "%.1f".format(v) },
                            onScrub = { idx -> scrubbedIndex = idx }
                        )
                    } else {
                        GoogleStyleBarChart(
                            values = displayPoints.map { it.second ?: 0f },
                            goalValue = null,
                            valueFormatter = { "$it" },
                            onScrub = { idx -> scrubbedIndex = idx }
                        )
                    }
                    GoogleStyleXAxis(labels = metricLabels)
                }
            } else {
                // When empty, show the sparse day axis matching media_1790857326236.png
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    GoogleStyleXAxis(
                        labels = metricLabels,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )
                }
            }
        }

        // Historical Readings List
        if (historyList.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateLabel,
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = healthColors.textPrimary
                    )
                    Text(
                        text = if (config.isSumMetric) "${config.title} (total)" else "${config.title} (avg)",
                        style = Typography.bodyMedium,
                        color = healthColors.textSecondary
                    )
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    historyList.forEachIndexed { index, (label, valOpt) ->
                        ConnectedListItem(
                            index = index,
                            count = historyList.size,
                            trailingContent = {
                                Text(
                                    text = if (valOpt != null) {
                                        val formatted = if (valOpt % 1.0f == 0.0f) valOpt.toInt().toString() else "%.1f".format(valOpt)
                                        "$formatted ${config.unit}"
                                    } else "--",
                                    style = Typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = healthColors.textPrimary
                                )
                            }
                        ) {
                            Text(
                                text = label,
                                style = Typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = healthColors.textPrimary
                            )
                        }
                    }
                }
            }
        }

        // About this metric & Clinical Benchmarks card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = config.iconTint, modifier = Modifier.size(20.dp))
                        Text(
                            text = "About ${config.title}",
                            style = Typography.titleMedium,
                            color = healthColors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = config.aboutDescription,
                        color = healthColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    HorizontalDivider(color = healthColors.cardBorder, thickness = 0.8.dp)

                    Text(
                        text = "Clinical Reference Standard",
                        color = healthColors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = config.clinicalRangeDescription,
                        color = healthColors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
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
