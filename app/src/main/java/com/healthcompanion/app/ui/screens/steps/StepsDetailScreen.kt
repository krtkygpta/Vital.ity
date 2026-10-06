package com.healthcompanion.app.ui.screens.steps

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
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Steps detail.
 *
 * Shows the day total with its hourly distribution, or a calendar-accurate day /
 * week / month / quarter / year series. Weeks start on Sunday, months on the 1st,
 * quarters on the 1st of the quarter month and years on 1 January, all derived
 * from the canonical [periodBounds] helpers.
 *
 * Nothing is invented: when Health Connect returns nothing for a period the
 * chart is replaced by an explicit empty state rather than a synthetic series.
 */
@Composable
fun StepsDetailScreen(
    onBack: () -> Unit,
    onQueryDailySteps: (suspend (LocalDate) -> Int)? = null,
    onQueryHourlySteps: (suspend (LocalDate) -> List<Pair<Int, Int>>)? = null,
    onQueryWeeklySteps: (suspend (LocalDate) -> List<Pair<LocalDate, Int>>)? = null,
    onQueryPeriodSteps: (suspend (String, Int, LocalDate) -> List<Pair<LocalDate, Int>>?)? = null,
    dailyStepGoal: Int = 10_000,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current

    var selectedRange by rememberSaveable(stateSaver = enumSaver<TimeRange>()) { mutableStateOf(TimeRange.WEEK) }
    var selectedDate by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(LocalDate.now()) }
    var scrubbedHour by rememberSaveable { mutableStateOf<Int?>(null) }
    var scrubbedPeriodIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }

    var dailySteps by remember { mutableStateOf<Int?>(null) }
    var hourly by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    var period by remember { mutableStateOf<List<Pair<LocalDate, Int>>>(emptyList()) }

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
                dailySteps = onQueryDailySteps?.invoke(selectedDate)
                hourly = onQueryHourlySteps?.invoke(selectedDate).orEmpty()
                period = emptyList()
            }
            TimeRange.WEEK -> {
                val weekly = onQueryWeeklySteps?.invoke(selectedDate).orEmpty()
                val weeklyMap = weekly.toMap()
                val list = (0..6).map { i ->
                    val d = periodStartDate.plusDays(i.toLong())
                    val count = if (d.isAfter(today)) 0 else (weeklyMap[d] ?: 0)
                    d to count
                }
                period = list
                dailySteps = null
                hourly = emptyList()
            }
            else -> {
                period = onQueryPeriodSteps?.invoke(selectedRange.name, periodDays, selectedDate)
                    .orEmpty()
                    .filter { (d, _) -> !d.isAfter(effectiveEnd) }
                    .sortedBy { it.first }
                dailySteps = null
                hourly = emptyList()
            }
        }
        loading = false
    }

    val isDay = selectedRange == TimeRange.DAY
    val hasData = if (isDay) (dailySteps ?: 0) > 0 else period.isNotEmpty()

    val periodSum = if (isDay) (dailySteps ?: 0) else period.sumOf { it.second }
    val periodAvg = if (isDay) {
        dailySteps ?: 0
    } else if (period.isNotEmpty()) periodSum / period.size else 0

    val scrubbedValue = scrubbedPeriodIndex?.takeIf { it in period.indices }?.let { period[it].second }

    val goal = dailyStepGoal.takeIf { it > 0 }

    val subtitleText: String = run {
        val scrubIdx = scrubbedPeriodIndex?.takeIf { it in period.indices }
        val scrubHour = scrubbedHour
        when {
            !hasData -> "No step records synced to Health Connect for this period"
            isDay && scrubHour != null ->
                String.format("%02d:00–%02d:00", scrubHour, (scrubHour + 1) % 24)
            isDay -> "${HealthFormat.count(dailySteps ?: 0)} steps • goal ${HealthFormat.count(goal ?: dailyStepGoal)}"
            scrubIdx != null -> period[scrubIdx].first.format(dateFormatter)
            selectedRange == TimeRange.WEEK -> "${HealthFormat.count(periodSum)} steps this week"
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
                    text = "Steps",
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
                label = "stepsHeadline"
            ) { target ->
                val tIsDay = target.range == TimeRange.DAY
                val displaySteps = when {
                    target.scrubbedValue != null -> target.scrubbedValue
                    tIsDay -> dailySteps ?: 0
                    else -> periodAvg
                }

                Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (hasData) HealthFormat.count(displaySteps) else "—",
                                style = MetricHero,
                                color = if (hasData) healthColors.textPrimary else healthColors.textMuted,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "steps",
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        if (target.scrubbedValue != null || scrubbedHour != null) {
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
                    CircularProgressIndicator(color = healthColors.accentTeal)
                }
            } else if (!hasData) {
                EmptyChartPlaceholder(
                    title = "No step records",
                    subtitle = "Nothing has been synced to Health Connect for this period."
                )
            } else {
                val chartRange = selectedRange
                val chartDate = selectedDate
                AnimatedContent(
                    targetState = chartRange to chartDate,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(180)) },
                    label = "stepsChart"
                ) { (tRange, tDate) ->
                    val tIsDay = tRange == TimeRange.DAY
                    Column {
                        if (tIsDay) {
                            val values = (0..23).map { hour ->
                                (hourly.firstOrNull { it.first == hour }?.second ?: 0).toFloat()
                            }
                            // In Day mode, do not scale against full day goal (10k), scale to hourly max so bars fill height
                            GoogleStyleBarChart(
                                values = values,
                                goalValue = null,
                                goalLabel = null,
                                // §5.2: the axis showed a bare 4500 while the
                                // headline read "12,345 steps".
                                valueFormatter = { HealthFormat.compact(it.toDouble()) },
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
                                goalLabel = goal?.let { "Goal ${HealthFormat.count(it)}" },
                                valueFormatter = { HealthFormat.compact(it.toDouble()) },
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

        val breakdownRows: List<Pair<String, Int>> = if (isDay) {
            hourly.filter { it.second > 0 }.reversed().map { (hour, steps) ->
                String.format("%02d:00–%02d:00", hour, (hour + 1) % 24) to steps
            }
        } else {
            period.filter { (date, _) -> !date.isAfter(today) }.reversed().take(31).map { (date, steps) ->
                val title = when (date) {
                    today -> "Today"
                    today.minusDays(1) -> "Yesterday"
                    else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
                }
                title to steps
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
                    breakdownRows.forEachIndexed { index, (title, steps) ->
                        StepsValueRow(
                            index = index,
                            count = breakdownRows.size,
                            label = title,
                            value = "${HealthFormat.count(steps)} steps"
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

/** One row of the steps breakdown list. */
@Composable
private fun StepsValueRow(
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
