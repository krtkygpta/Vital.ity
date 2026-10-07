package com.healthcompanion.app.ui.screens.spo2

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthcompanion.app.ui.components.*
import com.healthcompanion.app.ui.screens.heartrate.RestingPoint
import com.healthcompanion.app.ui.format.HealthFormat
import com.healthcompanion.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Blood oxygen (SpO2) detail screen.
 * Perfectly aligned with HeartRateDetailScreen:
 * - Top app bar with back button, centered title, and more options icon
 * - TimeRangePills with padding(horizontal = 16.dp)
 * - DateNavigator
 * - Clean SpO2 line graph with connecting cyan/teal line, point dots, gridlines, right-aligned Y-axis (100%, 95%, 90%), day initials X-axis, and interactive scrubber
 * - Daily breakdown list with individual rounded cards matching Heart Rate screen
 */
@Composable
fun SpO2DetailScreen(
    onBack: () -> Unit,
    onQueryDailySpO2: (suspend (LocalDate) -> Result<Int?>)? = null,
    onQueryWeeklySpO2: (suspend (LocalDate) -> Result<List<Pair<LocalDate, Int?>>>)? = null,
    onQueryPeriodSpO2: (suspend (String, Int, LocalDate) -> Result<List<Pair<LocalDate, Int?>>>)? = null,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current

    var selectedRange by rememberSaveable(stateSaver = enumSaver<TimeRange>()) { mutableStateOf(TimeRange.WEEK) }
    var selectedDate by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(LocalDate.now()) }
    var scrubbedPeriodIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }

    var dailySpO2 by remember { mutableStateOf<Int?>(null) }
    var period by remember { mutableStateOf<List<Pair<LocalDate, Int?>>>(emptyList()) }

    val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
    val monthFmt = DateTimeFormatter.ofPattern("MMM")

    // Canonical period bounds matching Heart Rate
    val (periodStartDate, periodEndDate) = periodBounds(selectedDate, selectedRange)
    val today = LocalDate.now()
    val effectiveEnd = periodEndClamped(selectedDate, selectedRange, today)
    val periodDays = periodDayCount(selectedDate, selectedRange).toInt()

    val dateLabel = when (selectedRange) {
        TimeRange.DAY -> when (selectedDate) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> selectedDate.format(dateFormatter)
        }
        TimeRange.WEEK -> {
            // Sunday-anchored so the label matches the week the navigator shows.
            val start = weekStartSun(selectedDate)
            val end = weekEndSun(selectedDate)
            if (start.monthValue == end.monthValue) {
                "${start.dayOfMonth}–${end.dayOfMonth} ${end.format(monthFmt)}"
            } else {
                "${start.dayOfMonth} ${start.format(monthFmt)}–${end.dayOfMonth} ${end.format(monthFmt)}"
            }
        }
        TimeRange.MONTH -> {
            val start = selectedDate.minusDays(29)
            "${start.dayOfMonth} ${start.format(monthFmt)}–${selectedDate.dayOfMonth} ${selectedDate.format(monthFmt)}"
        }
        TimeRange.THREE_MONTHS -> {
            val m1 = selectedDate.minusMonths(2).format(DateTimeFormatter.ofPattern("MMM"))
            val m2 = selectedDate.format(DateTimeFormatter.ofPattern("MMM yyyy"))
            "$m1–$m2"
        }
        TimeRange.YEAR -> selectedDate.format(DateTimeFormatter.ofPattern("yyyy"))
    }

    LaunchedEffect(selectedDate, selectedRange) {
        scrubbedPeriodIndex = null
        loading = true

        when (selectedRange) {
            TimeRange.DAY -> {
                // A read that fails stays null; it is never replaced by a sample
                // reading, which previously made "no data" indistinguishable
                // from a fabricated 97%.
                dailySpO2 = onQueryDailySpO2?.invoke(selectedDate)?.getOrNull()
                period = emptyList()
            }
            TimeRange.WEEK -> {
                val weekly = onQueryWeeklySpO2?.invoke(selectedDate)?.getOrNull().orEmpty()
                val list = weekly.filter { (d, _) -> !d.isBefore(periodStartDate) && !d.isAfter(effectiveEnd) }
                period = list.sortedBy { it.first }
                dailySpO2 = null
            }
            else -> {
                val p = onQueryPeriodSpO2?.invoke(selectedRange.name, periodDays, selectedDate)
                    ?.getOrNull().orEmpty()
                period = p.filter { (d, _) -> !d.isAfter(effectiveEnd) }
                    .sortedBy { it.first }
                dailySpO2 = null
            }
        }
        loading = false
    }

    val isDay = selectedRange == TimeRange.DAY

    val readings: List<Pair<LocalDate, Int>> = remember(period, selectedRange) {
        period.mapNotNull { (d, v) -> v?.let { d to it } }.sortedBy { it.first }
    }

    val hasData = if (isDay) dailySpO2 != null else readings.isNotEmpty()

    val periodAvg = readings.takeIf { it.isNotEmpty() }
        ?.map { it.second }
        ?.average()

    // Build chart points matching GoogleRestingHeartRateChart structure
    val chartPoints: List<RestingPoint> = remember(selectedRange, selectedDate, period, dailySpO2) {
        if (isDay) {
            listOf(RestingPoint(selectedDate, dailySpO2))
        } else if (selectedRange == TimeRange.WEEK) {
            val start = weekStartSun(selectedDate)
            (0..6).map { i ->
                val d = start.plusDays(i.toLong())
                val value = period.find { it.first == d }?.second
                RestingPoint(d, value)
            }
        } else {
            period.map { RestingPoint(it.first, it.second) }
        }
    }

    val scrubbedValue = scrubbedPeriodIndex?.takeIf { it in chartPoints.indices }?.let { chartPoints[it].bpm }

    val subtitleText: String = run {
        val scrubIdx = scrubbedPeriodIndex?.takeIf { it in chartPoints.indices }
        when {
            !hasData -> "No blood oxygen readings synced to Health Connect for this period"
            scrubIdx != null -> chartPoints[scrubIdx].date.format(dateFormatter)
            isDay -> "Latest oxygen saturation reading"
            selectedRange == TimeRange.WEEK ->
                "Average ${periodAvg?.let { HealthFormat.decimal(it, 1) } ?: "—"}% across ${readings.size} readings this week"
            selectedRange == TimeRange.MONTH ->
                "Average ${periodAvg?.let { HealthFormat.decimal(it, 1) } ?: "—"}% across ${readings.size} readings"
            selectedRange == TimeRange.THREE_MONTHS ->
                "Average ${periodAvg?.let { HealthFormat.decimal(it, 1) } ?: "—"}% over the quarter"
            else ->
                "Average ${periodAvg?.let { HealthFormat.decimal(it, 1) } ?: "—"}% over the year"
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(healthColors.background)
    ) {
        // App bar aligned with Heart Rate screen: Back | Blood oxygen | More
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
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
                    text = "Blood oxygen",
                    style = Typography.titleLarge,
                    color = healthColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                )

                IconButton(onClick = { /* Menu */ }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = healthColors.textPrimary
                    )
                }
            }
        }

        // Time Range Pills (D, W, M, 3M, Y) - Always visible matching Heart Rate
        item {
            TimeRangePills(
                selectedRange = selectedRange,
                ranges = TimeRange.entries,
                modifier = Modifier.padding(horizontal = 16.dp),
                onRangeSelected = {
                    selectedRange = it
                    scrubbedPeriodIndex = null
                }
            )
        }

        // Date Navigator
        item {
            DateNavigator(
                dateLabel = dateLabel,
                onPrevious = { selectedDate = selectedDate.stepBackward(selectedRange) },
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

        // Headline, cross-faded on range / date / scrub change
        item {
            // The key carries what the headline renders, so the animated content
            // is driven by its target state rather than by outer mutable state.
            data class Headline(
                val range: TimeRange,
                val date: LocalDate,
                val scrubbedValue: Int?
            )
            val headline = Headline(selectedRange, selectedDate, scrubbedValue)
            AnimatedContent(
                targetState = headline,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(180)) },
                label = "spo2Headline"
            ) { target ->
                val tIsDay = target.range == TimeRange.DAY
                val displayValue: Int? = when {
                    target.scrubbedValue != null -> target.scrubbedValue
                    tIsDay -> dailySpO2
                    else -> periodAvg?.let { it.roundToInt() }
                }

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = displayValue?.let { "$it" } ?: "—",
                                style = MetricHero,
                                color = if (displayValue != null) healthColors.textPrimary else healthColors.textMuted,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "% SpO₂",
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        if (scrubbedValue != null) {
                            IconButton(
                                onClick = { scrubbedPeriodIndex = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear selection",
                                    tint = healthColors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = subtitleText,
                        color = healthColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // SpO2 Line Chart
        item {
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = healthColors.accentTeal)
                }
            } else if (!hasData) {
                EmptyChartPlaceholder(
                    title = "No blood oxygen readings",
                    subtitle = "Nothing has been synced to Health Connect for this period."
                )
            } else {
                val chartRange = selectedRange
                val chartDate = selectedDate
                AnimatedContent(
                    targetState = chartRange to chartDate,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(180)) },
                    label = "spo2Chart"
                ) { (tRange, _) ->
                    SpO2LineChart(
                        points = chartPoints,
                        selectedRange = tRange,
                        scrubbedIndex = scrubbedPeriodIndex,
                        onScrub = { scrubbedPeriodIndex = it }
                    )
                }
            }
        }

        // Section Breakdown below chart matching Heart Rate screen
        if (hasData) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = healthColors.textPrimary
                    )
                    Text(
                        text = if (isDay) "Reading" else "Blood oxygen (avg)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = healthColors.textSecondary
                    )
                }
            }

            // Daily list of SpO2 readings in reverse chronological order matching energy burned style
            val breakdownPoints = chartPoints.filter { it.bpm != null && !it.date.isAfter(today) }.reversed()
            if (breakdownPoints.isEmpty()) {
                item {
                    Text(
                        text = "No readings recorded in this period.",
                        color = healthColors.textMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        breakdownPoints.forEachIndexed { idx, pt ->
                            val itemLabel = when (pt.date) {
                                today -> "Today"
                                today.minusDays(1) -> "Yesterday"
                                else -> pt.date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
                            }
                            ConnectedListItem(
                                index = idx,
                                count = breakdownPoints.size,
                                trailingContent = {
                                    Text(
                                        text = "${pt.bpm}%",
                                        color = healthColors.textPrimary,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            ) {
                                Text(
                                    text = itemLabel,
                                    color = healthColors.textPrimary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

/**
 * Clean SpO2 line chart matching GoogleRestingHeartRateChart:
 * - Cyan line connecting SpO2 percentage points
 * - Circular cyan dots at every recorded reading
 * - Empty slots for future days of the week or unrecorded dates
 * - Right-aligned Y-axis labels (100%, 95%, 90%, 85%) with horizontal gridlines
 * - Day initials (M, T, W, T, F, S, S) or month date markers aligned across bottom
 * - Interactive scrub/tap with dashed guideline and glowing cyan dot
 */
@Composable
fun SpO2LineChart(
    points: List<RestingPoint>,
    selectedRange: TimeRange,
    scrubbedIndex: Int?,
    onScrub: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val validPoints = points.filter { it.bpm != null }

    // §5.7: without this the chart drew an axis with no readings on it, which
    // reads as a flat 100% rather than as "nothing recorded".
    if (validPoints.isEmpty()) {
        EmptyChartPlaceholder(
            title = "No blood oxygen readings",
            subtitle = "No SpO2 has been recorded for this period.",
            modifier = modifier
        )
        return
    }

    // §5.3: the floor adapts to the data, but only in 5-point steps, so a
    // 93% reading still sat in the bottom third of a 90–100 frame. The span is
    // now anchored to the observed minimum with a small margin.
    val observedMin = validPoints.mapNotNull { it.bpm }.min()
    val minPercent = ((observedMin - 3) / 5 * 5).coerceIn(75, 92)
    val maxPercent = 100
    val range = (maxPercent - minPercent).toFloat().coerceAtLeast(5f)

    val steps = 4
    val yAxisLabels = (0..steps).map {
        (maxPercent - range * (it / steps.toFloat())).toInt()
    }.distinct()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(points) {
                        if (points.isEmpty()) return@pointerInput
                        val yAxisWidth = 36.dp.toPx()
                        val chartWidth = size.width - yAxisWidth
                        val count = points.size

                        fun handleTouch(x: Float) {
                            val idx = ((x / chartWidth) * count).toInt().coerceIn(0, count - 1)
                            if (points[idx].bpm != null) {
                                onScrub(idx)
                            }
                        }

                        detectTapGestures { offset -> handleTouch(offset.x) }
                    }
                    .pointerInput(points) {
                        if (points.isEmpty()) return@pointerInput
                        val yAxisWidth = 36.dp.toPx()
                        val chartWidth = size.width - yAxisWidth
                        val count = points.size

                        detectDragGestures(
                            onDragStart = { offset ->
                                val idx = ((offset.x / chartWidth) * count).toInt().coerceIn(0, count - 1)
                                if (points[idx].bpm != null) onScrub(idx)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val idx = ((change.position.x / chartWidth) * count).toInt().coerceIn(0, count - 1)
                                if (points[idx].bpm != null) onScrub(idx)
                            }
                        )
                    }
            ) {
                val yAxisWidth = 36.dp.toPx()
                val chartWidth = size.width - yAxisWidth
                val topPadding = 12.dp.toPx()
                val bottomPadding = 16.dp.toPx()
                val chartHeight = size.height - topPadding - bottomPadding

                fun percentToY(p: Float): Float {
                    val norm = (p - minPercent) / range
                    return topPadding + chartHeight - (norm.coerceIn(0f, 1f) * chartHeight)
                }

                // Grid lines for Y-axis levels
                val gridColor = healthColors.cardBorder
                yAxisLabels.forEach { p ->
                    val y = percentToY(p.toFloat())
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Plot line and dots for valid readings
                val count = points.size
                val path = Path()
                var pathStarted = false
                val renderedPoints = mutableListOf<Pair<Int, Offset>>()

                points.forEachIndexed { index, pt ->
                    if (pt.bpm != null) {
                        val x = if (selectedRange == TimeRange.WEEK) {
                            val slotW = chartWidth / 7f
                            slotW * index + slotW / 2f
                        } else {
                            if (count > 1) (index.toFloat() / (count - 1)) * chartWidth else chartWidth / 2f
                        }
                        val y = percentToY(pt.bpm.toFloat())
                        val offset = Offset(x, y)
                        renderedPoints.add(index to offset)

                        if (!pathStarted) {
                            path.moveTo(x, y)
                            pathStarted = true
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                }

                // Draw connecting line in cyan
                val cyanColor = healthColors.accentTeal
                if (renderedPoints.size > 1) {
                    drawPath(
                        path = path,
                        color = cyanColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Draw circular dots on points
                renderedPoints.forEach { (index, offset) ->
                    val isScrubbed = scrubbedIndex == index
                    if (!isScrubbed) {
                        drawCircle(
                            color = cyanColor,
                            radius = 4.5.dp.toPx(),
                            center = offset
                        )
                    }
                }

                // Draw active scrubber guide line and dot
                scrubbedIndex?.let { sIdx ->
                    val match = renderedPoints.firstOrNull { it.first == sIdx }
                    if (match != null) {
                        val pt = match.second
                        // Vertical guideline
                        drawLine(
                            color = healthColors.textPrimary.copy(alpha = 0.5f),
                            start = Offset(pt.x, topPadding),
                            end = Offset(pt.x, topPadding + chartHeight),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                        // Outer cyan circle
                        drawCircle(
                            color = cyanColor,
                            radius = 7.dp.toPx(),
                            center = pt
                        )
                        // Inner dot
                        drawCircle(
                            color = healthColors.onAccentTeal,
                            radius = 3.dp.toPx(),
                            center = pt
                        )
                    }
                }
            }

            // Right-aligned Y-axis labels
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(36.dp)
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                yAxisLabels.forEach { p ->
                    Text(
                        text = "$p%",
                        color = healthColors.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // X-axis Day / Date labels underneath chart
        if (selectedRange == TimeRange.WEEK) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 36.dp, top = 6.dp)
            ) {
                points.forEach { pt ->
                    val initial = pt.date.dayOfWeek.name.take(1)
                    Text(
                        text = initial,
                        modifier = Modifier.weight(1f),
                        color = healthColors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (selectedRange == TimeRange.MONTH) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 36.dp, top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val labels = listOf("1", "10", "20", "${points.size}")
                labels.forEach { lbl ->
                    Text(
                        text = lbl,
                        color = healthColors.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}
