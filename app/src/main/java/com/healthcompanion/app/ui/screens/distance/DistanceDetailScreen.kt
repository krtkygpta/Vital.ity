package com.healthcompanion.app.ui.screens.distance

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthcompanion.app.ui.components.*
import com.healthcompanion.app.ui.format.HealthFormat
import com.healthcompanion.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Distance detail.
 *
 * Mirrors the steps screen but in kilometres: the day total with its hourly
 * distribution, or a calendar-accurate day / week / month / quarter / year
 * series built from the canonical period helpers.
 *
 * When Health Connect has nothing for a period the screen says so instead of
 * substituting invented distances.
 */
@Composable
fun DistanceDetailScreen(
    onBack: () -> Unit,
    onQueryDailyDistance: (suspend (LocalDate) -> Double)? = null,
    onQueryHourlyDistance: (suspend (LocalDate) -> List<Pair<Int, Double>>)? = null,
    onQueryWeeklyDistance: (suspend (LocalDate) -> List<Pair<LocalDate, Double>>)? = null,
    onQueryPeriodDistance: (suspend (String, Int, LocalDate) -> List<Pair<LocalDate, Double>>?)? = null,
    dailyDistanceGoalKm: Double = 8.05,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current

    var selectedRange by rememberSaveable(stateSaver = enumSaver<TimeRange>()) { mutableStateOf(TimeRange.WEEK) }
    var selectedDate by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(LocalDate.now()) }
    var scrubbedHour by rememberSaveable { mutableStateOf<Int?>(null) }
    var scrubbedPeriodIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }

    var dailyDistance by remember { mutableStateOf<Double?>(null) }
    var hourly by remember { mutableStateOf<List<Pair<Int, Double>>>(emptyList()) }
    var period by remember { mutableStateOf<List<Pair<LocalDate, Double>>>(emptyList()) }

    val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

    // Canonical period bounds — never a rolling day count.
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
            val f = DateTimeFormatter.ofPattern("d MMM")
            "${periodStartDate.dayOfMonth}–${periodEndDate.format(f)}"
        }
        TimeRange.MONTH -> selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        TimeRange.THREE_MONTHS -> {
            val m1 = periodStartDate.format(DateTimeFormatter.ofPattern("MMM"))
            val m2 = periodEndDate.format(DateTimeFormatter.ofPattern("MMM yyyy"))
            "$m1–$m2"
        }
        TimeRange.YEAR -> selectedDate.format(DateTimeFormatter.ofPattern("yyyy"))
    }

    LaunchedEffect(selectedDate, selectedRange) {
        scrubbedHour = null
        scrubbedPeriodIndex = null
        loading = true

        when (selectedRange) {
            TimeRange.DAY -> {
                dailyDistance = onQueryDailyDistance?.invoke(selectedDate)
                hourly = onQueryHourlyDistance?.invoke(selectedDate).orEmpty()
                period = emptyList()
            }
            TimeRange.WEEK -> {
                val weekly = onQueryWeeklyDistance?.invoke(selectedDate).orEmpty()
                val byDate = weekly.associate { it.first to it.second }
                val anyData = weekly.any { (d, v) ->
                    !d.isBefore(periodStartDate) && !d.isAfter(effectiveEnd) && v > 0.0
                }
                period = if (!anyData) emptyList() else generateSequence(periodStartDate) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(periodEndDate) }
                    .map { it to (byDate[it] ?: 0.0) }
                    .toList()
                dailyDistance = null
                hourly = emptyList()
            }
            else -> {
                period = onQueryPeriodDistance?.invoke(selectedRange.name, periodDays, selectedDate)
                    .orEmpty()
                    .filter { (d, _) -> !d.isAfter(effectiveEnd) }
                    .sortedBy { it.first }
                dailyDistance = null
                hourly = emptyList()
            }
        }
        loading = false
    }

    val isDay = selectedRange == TimeRange.DAY
    val hasData = if (isDay) (dailyDistance ?: 0.0) > 0.0 else period.isNotEmpty()

    val periodSum = if (isDay) (dailyDistance ?: 0.0) else period.sumOf { it.second }
    val periodAvg = if (isDay) {
        dailyDistance ?: 0.0
    } else if (period.isNotEmpty()) periodSum / period.size else 0.0

    val scrubbedValue = scrubbedPeriodIndex?.takeIf { it in period.indices }?.let { period[it].second }

    val goal = dailyDistanceGoalKm.takeIf { it > 0.0 }

    fun formatKm(value: Double): String = HealthFormat.decimal(value, 2)

    val subtitleText: String = run {
        val scrubIdx = scrubbedPeriodIndex?.takeIf { it in period.indices }
        val scrubHour = scrubbedHour
        when {
            !hasData -> "No distance records synced to Health Connect for this period"
            isDay && scrubHour != null ->
                String.format("%02d:00–%02d:00", scrubHour, (scrubHour + 1) % 24)
            isDay -> "${formatKm(dailyDistance ?: 0.0)} km • goal ${formatKm(goal ?: dailyDistanceGoalKm)} km"
            scrubIdx != null -> period[scrubIdx].first.format(dateFormatter)
            selectedRange == TimeRange.WEEK -> "${formatKm(periodSum)} km this week"
            selectedRange == TimeRange.MONTH -> "Daily average for this month"
            selectedRange == TimeRange.THREE_MONTHS -> "Daily average over the quarter"
            else -> "Daily average over the year"
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(healthColors.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 10.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
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
                    text = "Distance",
                    style = Typography.titleLarge,
                    color = healthColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.size(48.dp))
            }
        }

        // Range selector
        item {
            TimeRangePills(
                selectedRange = selectedRange,
                onRangeSelected = {
                    selectedRange = it
                    scrubbedHour = null
                    scrubbedPeriodIndex = null
                }
            )
        }

        // Date navigator
        item {
            DateNavigator(
                dateLabel = dateLabel,
                onPrevious = { selectedDate = selectedDate.stepBackward(selectedRange) },
                onNext = {
                    val next = selectedDate.stepForward(selectedRange)
                    if (isPeriodSelectable(next, selectedRange)) selectedDate = next
                },
                canGoNext = canStepForward(selectedDate, selectedRange) &&
                    isPeriodSelectable(selectedDate.stepForward(selectedRange), selectedRange),
                onResetOrHistory = { selectedDate = LocalDate.now() }
            )
        }

        // Headline, cross-faded on range / date / scrub change.
        item {
            // The AnimatedContent key carries the values the headline renders, so
            // the animated content is driven by its target state rather than by
            // reading mutable outer state inside the content lambda.
            data class Headline(
                val range: TimeRange,
                val date: LocalDate,
                val scrubbedValue: Double?,
                val scrubbedHour: Int?
            )
            val headline = Headline(selectedRange, selectedDate, scrubbedValue, scrubbedHour)
            AnimatedContent(
                targetState = headline,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(180)) },
                label = "distanceHeadline"
            ) { target ->
                val tIsDay = target.range == TimeRange.DAY
                val displayKm = when {
                    target.scrubbedValue != null -> target.scrubbedValue
                    target.scrubbedHour != null && tIsDay ->
                        hourly.firstOrNull { it.first == target.scrubbedHour }?.second ?: 0.0
                    tIsDay -> dailyDistance ?: 0.0
                    else -> periodAvg
                }

                Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (hasData) formatKm(displayKm) else "—",
                                style = MetricHero,
                                color = if (hasData) healthColors.textPrimary else healthColors.textMuted,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "km",
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        if (target.scrubbedValue != null || target.scrubbedHour != null) {
                            IconButton(
                                onClick = {
                                    scrubbedHour = null
                                    scrubbedPeriodIndex = null
                                },
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

        // Chart
        item {
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = healthColors.accentBlue)
                }
            } else if (!hasData) {
                EmptyChartPlaceholder(
                    title = "No distance records",
                    subtitle = "Nothing has been synced to Health Connect for this period."
                )
            } else {
                // Drive the chart content from its target state, and label the
                // axis in km — §5.2: the axis previously read bare numbers while
                // the headline read "km".
                val chartRange = selectedRange
                val chartDate = selectedDate
                AnimatedContent(
                    targetState = chartRange to chartDate,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(180)) },
                    label = "distanceChart"
                ) { (tRange, tDate) ->
                    val tIsDay = tRange == TimeRange.DAY
                    Column {
                        if (tIsDay) {
                            val values = (0..23).map { hour ->
                                (hourly.firstOrNull { it.first == hour }?.second ?: 0.0).toFloat()
                            }
                            GoogleStyleBarChart(
                                values = values,
                                goalValue = null,
                                goalLabel = null,
                                valueFormatter = { HealthFormat.decimal(it.toDouble(), 1) },
                                onScrub = { idx -> scrubbedHour = idx }
                            )
                            GoogleStyleXAxis(
                                labels = listOf("12am", "6am", "12pm", "6pm", "12am"),
                                maxLabels = 5
                            )
                        } else {
                            GoogleStyleBarChart(
                                values = period.map { it.second.toFloat() },
                                goalValue = goal?.toFloat(),
                                goalLabel = goal?.let { "Goal ${formatKm(it)} km" },
                                valueFormatter = { HealthFormat.decimal(it.toDouble(), 1) },
                                onScrub = { idx -> scrubbedPeriodIndex = idx }
                            )
                            GoogleStyleXAxis(labels = xAxisLabels(period.map { it.first }, tRange))
                        }
                    }
                }
            }
        }

        // Breakdown list
        item {
            Text(
                text = if (isDay) "Hourly breakdown" else "Daily breakdown",
                style = Typography.titleMedium,
                color = healthColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val breakdownRows: List<Pair<String, Double>> = if (isDay) {
            hourly.filter { it.second > 0.0 }.reversed().map { (hour, km) ->
                String.format("%02d:00–%02d:00", hour, (hour + 1) % 24) to km
            }
        } else {
            period.reversed().take(31).map { (date, km) ->
                val title = when (date) {
                    today -> "Today"
                    today.minusDays(1) -> "Yesterday"
                    else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
                }
                title to km
            }
        }

        if (breakdownRows.isEmpty()) {
            item {
                Text(
                    text = "Nothing recorded in this period.",
                    color = healthColors.textMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    breakdownRows.forEachIndexed { index, (title, km) ->
                        DistanceValueRow(
                            index = index,
                            count = breakdownRows.size,
                            label = title,
                            value = "${formatKm(km)} km"
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

/** One row of the distance breakdown list. */
@Composable
private fun DistanceValueRow(
    index: Int,
    count: Int,
    label: String,
    value: String
) {
    val healthColors = LocalHealthColors.current
    ConnectedListItem(
        index = index,
        count = count,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = healthColors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = value,
                color = healthColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
