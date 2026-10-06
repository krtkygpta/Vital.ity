package com.healthcompanion.app.ui.screens.heartrate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.healthcompanion.app.model.RestingHeartRateDay
import com.healthcompanion.app.model.RestingHeartRatePeriodSummary
import com.healthcompanion.app.ui.components.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.healthcompanion.app.ui.theme.LocalHealthColors
import com.healthcompanion.app.ui.theme.MetricHero
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Ranges offered by the resting-HR screen. There is no Day view. */
enum class RestingRange(val label: String, val days: Int) {
    WEEK("Week", 7),
    MONTH("Month", 30),
    THREE_MONTHS("3 months", 90),
    YEAR("Year", 365);

    fun toTimeRange(): TimeRange = when (this) {
        WEEK -> TimeRange.WEEK
        MONTH -> TimeRange.MONTH
        THREE_MONTHS -> TimeRange.THREE_MONTHS
        YEAR -> TimeRange.YEAR
    }
}

/**
 * Resting heart rate screen.
 */
@Composable
fun RestingHeartRateScreen(
    initialRange: RestingRange = RestingRange.WEEK,
    loadPeriod: suspend (range: String, days: Int, endDate: LocalDate) -> RestingHeartRatePeriodSummary?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    var selectedRange by rememberSaveable(stateSaver = enumSaver<RestingRange>()) { mutableStateOf(initialRange) }
    var selectedDate by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(LocalDate.now()) }
    var summary by remember { mutableStateOf<RestingHeartRatePeriodSummary?>(null) }
    // §3.2: this flag was computed and then never read, so the screen rendered
    // straight to its (blank) content with no loading affordance.
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(selectedDate, selectedRange) {
        loading = true
        summary = loadPeriod(selectedRange.name, selectedRange.days, selectedDate)
        loading = false
    }

    val records = summary?.dailyRecords?.sortedBy { it.date }.orEmpty()
    val values = records.map { it.bpm.toFloat() }
    val rangeMin = (summary?.personalRangeMin ?: values.minOrNull())?.toFloat() ?: 0f
    val rangeMax = (summary?.personalRangeMax ?: values.maxOrNull())?.toFloat() ?: 0f
    val outOfRangeCount = records.count { it.bpm < rangeMin || it.bpm > rangeMax }

    val bounds = periodBounds(selectedDate, selectedRange.toTimeRange())
    val visiblePeriodEnd = minOf(bounds.second, LocalDate.now())

    val dateLabel = when (selectedRange) {
        RestingRange.WEEK -> {
            val start = weekStartSun(selectedDate)
            val end = weekEndSun(selectedDate)
            "${start.dayOfMonth}–${end.dayOfMonth} ${start.month.name.take(3).lowercase()}"
        }
        RestingRange.MONTH -> when {
            selectedDate.withDayOfMonth(1) == YearMonth.now().atDay(1) -> "This month"
            else -> selectedDate.month.name.lowercase().replaceFirstChar { it.uppercase() }
        }
        RestingRange.THREE_MONTHS -> {
            val m1 = selectedDate.minusMonths(2).format(DateTimeFormatter.ofPattern("MMM"))
            val m2 = selectedDate.format(DateTimeFormatter.ofPattern("MMM"))
            "$m1–$m2"
        }
        RestingRange.YEAR -> "${selectedDate.year}"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(healthColors.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (loading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
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
                    text = "Resting heart rate",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = healthColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                )
                IconButton(onClick = { }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = healthColors.textPrimary
                    )
                }
            }
        }

        // Time period selector (M3E Expressive Connected Segmented Buttons)
        item {
            M3EConnectedButtonGroup(
                items = RestingRange.values().toList(),
                selectedItem = selectedRange,
                onItemSelected = { selectedRange = it },
                labelProvider = { it.label },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Date Navigator
        item {
            DateNavigator(
                dateLabel = dateLabel,
                canGoNext = canStepForward(selectedDate, selectedRange.toTimeRange()),
                onPrevious = { selectedDate = selectedDate.stepBackward(selectedRange.toTimeRange()) },
                onNext = { selectedDate = selectedDate.stepForward(selectedRange.toTimeRange()) }
            )
        }

        // Summary Headline
        item {
            if (!loading && records.isEmpty()) {
                // §3.3: was the literal "No data" rendered at displayMedium,
                // which reads like a rendering bug rather than an empty state.
                EmptyState(
                    title = "No resting readings",
                    body = "No resting heart rate has been recorded for this period.",
                    actionLabel = "Back",
                    onAction = onBack,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                val avgBpm = summary?.averageBpm ?: (if (values.isNotEmpty()) values.average().toInt() else 0)
                Text(
                    text = if (avgBpm > 0) "$avgBpm bpm" else "—",
                    style = MetricHero,
                    fontWeight = FontWeight.Bold,
                    color = if (avgBpm > 0) healthColors.textPrimary else healthColors.textMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (summary?.personalRangeMin != null && summary?.personalRangeMax != null) {
                    Text(
                        text = "Personal range: ${summary?.personalRangeMin}–${summary?.personalRangeMax} bpm",
                        style = MaterialTheme.typography.bodyMedium,
                        color = healthColors.textSecondary
                    )
                }
                if (outOfRangeCount > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    OutOfRangeCaption(
                        count = outOfRangeCount,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            }
        }

        // Chart
        item {
            // §5.7: an empty PersonalRangeChart draws the personal band with no
            // points in it, which reads as "every day was in range".
            if (loading || values.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (values.isEmpty() && !loading) {
                        EmptyChartPlaceholder(
                            title = "No resting readings",
                            subtitle = "Nothing has been recorded for this period."
                        )
                    }
                }
                return@item
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = healthColors.cardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    PersonalRangeChart(
                        values = values,
                        rangeMin = rangeMin,
                        rangeMax = rangeMax,
                        dates = records.map { it.date },
                        periodStart = bounds.first,
                        periodEnd = visiblePeriodEnd,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PersonalRangeLegend(showOutOfRange = outOfRangeCount > 0)
                }
            }
        }

        // Section header
        if (records.isNotEmpty()) {
            item {
                Text(
                    text = "Daily readings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = healthColors.textPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Connected List Items
            itemsIndexed(records.reversed()) { index, record ->
                ConnectedListItem(
                    index = index,
                    count = records.size,
                    trailingContent = {
                        Text(
                            text = "${record.bpm} bpm",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (record.bpm < rangeMin || record.bpm > rangeMax) healthColors.accentOrange else healthColors.accentTeal
                        )
                    }
                ) {
                    Text(
                        text = record.date.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = healthColors.textPrimary
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}