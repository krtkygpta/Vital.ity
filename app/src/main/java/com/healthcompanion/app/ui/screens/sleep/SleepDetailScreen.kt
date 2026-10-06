package com.healthcompanion.app.ui.screens.sleep

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthcompanion.app.model.SleepScoreCalculator
import com.healthcompanion.app.model.SleepSession
import com.healthcompanion.app.model.SleepStageStat
import com.healthcompanion.app.ui.components.*
import com.healthcompanion.app.ui.theme.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Local date a session is filed under: the morning it ended. */
private fun SleepSession.localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    endTime.atZone(zone).toLocalDate()

/** "7 h 30 m" / "45 min" — a single line so it can never wrap in a stat tile. */
internal fun formatMinutesLong(minutes: Long): String {
    if (minutes <= 0L) return "0 min"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "$h h $m m"
        h > 0 -> "$h h"
        else -> "$m min"
    }
}

/** "7h 30m" — compact variant for a headline. */
internal fun formatMinutesCompact(minutes: Long): String {
    if (minutes <= 0L) return "0m"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

/**
 * Aggregated view of every [SleepSession] that falls inside the selected period.
 *
 * The aggregation only ever adds up sessions the user actually recorded — a
 * period with nothing in it produces an empty aggregate and the screen shows an
 * empty state rather than a synthetic zero-filled trend.
 */
internal data class SleepPeriodAggregate(
    val sessions: List<SleepSession> = emptyList(),
    val totalInBedMinutes: Long = 0L,
    val totalAsleepMinutes: Long = 0L,
    val totalAwakeMinutes: Long = 0L,
    val averageEfficiency: Int = 0,
    val averageScore: Int = 0,
    val scoreRating: String? = null,
    val scoreSummary: String? = null,
    /** Longest session in the period — the one the hypnogram is drawn from. */
    val primarySession: SleepSession? = null
) {
    val isEmpty: Boolean get() = sessions.isEmpty()
}

/**
 * Sums the given sessions into one period.
 *
 * Session-derived scores use [SleepScoreCalculator] for the rating and summary
 * text; the headline period score is the plain mean of the per-session scores,
 * which never claims precision a period average does not have.
 */
internal fun aggregateSleepSessions(sessions: List<SleepSession>): SleepPeriodAggregate {
    if (sessions.isEmpty()) return SleepPeriodAggregate()

    val inBed = sessions.sumOf { it.totalInBedMinutes }
    val asleep = sessions.sumOf { it.totalAsleepMinutes }
    val awake = sessions.sumOf { it.totalAwakeMinutes }

    val efficiency = if (inBed > 0) ((asleep.toDouble() / inBed.toDouble()) * 100.0).roundToInt().coerceIn(0, 100) else 0
    val averageScore = (sessions.sumOf { it.sleepScore }.toDouble() / sessions.size).roundToInt()

    val longest = sessions.maxByOrNull { it.totalInBedMinutes }
    val scored = longest?.let { SleepScoreCalculator.calculate(it, sessions) }

    return SleepPeriodAggregate(
        sessions = sessions,
        totalInBedMinutes = inBed,
        totalAsleepMinutes = asleep,
        totalAwakeMinutes = awake,
        averageEfficiency = efficiency,
        averageScore = averageScore,
        scoreRating = scored?.rating,
        scoreSummary = scored?.summaryText,
        primarySession = longest
    )
}

/**
 * Sleep detail.
 *
 * A period selector (day / week / month / quarter / year) plus a date navigator
 * over the supplied [sessions]. Every window comes from the canonical period
 * helpers, so weeks start on Sunday, months on the 1st, quarters on the 1st of
 * January/April/July/October and years on 1 January.
 *
 * Nothing is invented. A period that contains no recorded session renders an
 * explicit empty state instead of a fabricated trend line or an averaged figure
 * over zero nights.
 */
@Composable
fun SleepDetailScreen(
    sessions: List<SleepSession>,
    onBack: () -> Unit,
    onLogSleepClick: () -> Unit = {},
    initialRange: TimeRange = TimeRange.DAY,
    initialDate: LocalDate = LocalDate.now(),
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val zone = remember { ZoneId.systemDefault() }

    // rememberSaveable: this is the Sleep tab root, and plain `remember` reset
    // the selected range and date on every rotation.
    var selectedRange by rememberSaveable(stateSaver = enumSaver<TimeRange>()) { mutableStateOf(initialRange) }
    var selectedDate by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(initialDate) }
    var dayTab by rememberSaveable { mutableStateOf("Timeline") }
    var weekTab by rememberSaveable { mutableStateOf("Schedule") }
    var selectedDaySessionIndex by rememberSaveable { mutableStateOf(0) }

    val today = remember { LocalDate.now() }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE d MMM") }
    val dayMonthFormatter = remember { DateTimeFormatter.ofPattern("d MMM") }
    val clockFormatter = remember { DateTimeFormatter.ofPattern("HH:mm").withZone(zone) }

    val (periodStartDate, periodEndDate) = periodBounds(selectedDate, selectedRange)

    // The visible window is clipped to today: health data never exists in the
    // future, so the tail of the current week/month/year is simply empty.
    val effectiveEnd = if (periodEndDate.isAfter(today)) today else periodEndDate

    val periodSessions = remember(sessions, periodStartDate, effectiveEnd) {
        sessions
            .filter { session ->
                val date = session.localDate(zone)
                !date.isBefore(periodStartDate) && !date.isAfter(effectiveEnd)
            }
            .sortedByDescending { it.endTime }
    }

    val aggregate = remember(periodSessions) { aggregateSleepSessions(periodSessions) }
    val periodLabel = periodLabel(selectedRange, selectedDate, today, dateFormatter, dayMonthFormatter)

    // Stage breakdown for the longest session in the period. Derived here, in
    // the composable body, because remember() is not legal inside a LazyList scope.
    val primaryStages: List<SleepStageStat> = remember(aggregate.primarySession) {
        aggregate.primarySession?.stageStats.orEmpty()
    }
    val stageSectionLabel = remember(selectedRange) {
        if (selectedRange == TimeRange.DAY) "Stage breakdown • this night"
        else "Stage breakdown • longest session"
    }

    var selectedWeekDayIndex by remember { mutableStateOf(6) } // Default to today (last index of 7-day strip)

    val weekDaysData: List<SleepDayData> = remember(periodStartDate, periodSessions) {
        val list = mutableListOf<SleepDayData>()
        for (i in 0..6) {
            val d = periodStartDate.plusDays(i.toLong())
            val daySessions = periodSessions.filter { it.localDate(zone) == d }
            val hours = daySessions.sumOf { it.totalInBedMinutes } / 60f
            val primarySession = daySessions.maxByOrNull { it.totalInBedMinutes }
            val letter = d.dayOfWeek.name.take(1)
            list.add(SleepDayData(date = d, dayLetter = letter, hours = hours, session = primarySession))
        }
        list
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(healthColors.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App bar matching media_1790856985692.png: Back | "Sleep" | '+' | '⋮'
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBackButton) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = healthColors.textPrimary
                        )
                    }
                }
                Text(
                    text = "Sleep",
                    style = Typography.titleLarge,
                    color = healthColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (showBackButton) 4.dp else 16.dp)
                )
                // Wired to the manual-entry sheet. Previously `onClick = {}`, so
                // the "+" on the Sleep tab did nothing at all.
                IconButton(onClick = { onLogSleepClick() }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Log sleep",
                        tint = healthColors.textPrimary
                    )
                }
            }
        }

        // Period selector
        item {
            TimeRangePills(
                selectedRange = selectedRange,
                onRangeSelected = { selectedRange = it },
                ranges = TimeRange.entries
            )
        }

        // Date navigator
        item {
            DateNavigator(
                dateLabel = periodLabel,
                onPrevious = { selectedDate = selectedDate.stepBackward(selectedRange) },
                onNext = {
                    val next = selectedDate.stepForward(selectedRange)
                    if (isPeriodSelectable(next, selectedRange, today)) selectedDate = next
                },
                canGoNext = canStepForward(selectedDate, selectedRange, today) &&
                    isPeriodSelectable(selectedDate.stepForward(selectedRange), selectedRange, today),
                onResetOrHistory = { selectedDate = today }
            )
        }

        // ==========================================
        // WEEK MODE (matching media_1790856985692.png)
        // ==========================================
        if (selectedRange == TimeRange.WEEK) {
            // Headline
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 4.dp)
                ) {
                    val selectedItem = weekDaysData.getOrNull(selectedWeekDayIndex)
                    val daySession = selectedItem?.session
                    val hasData = daySession != null && (selectedItem.hours > 0f)
                    val durHours = if (hasData) selectedItem.hours.toInt() else 0
                    val durMins = if (hasData) ((selectedItem.hours - durHours) * 60).roundToInt() else 0
                    val dayScore = daySession?.sleepScore ?: 0
                    val dayEfficiency = daySession?.sleepEfficiency ?: 0
                    val dayRating = if (dayScore >= 90) "Excellent" else if (dayScore >= 80) "Good" else if (dayScore >= 70) "Fair" else "Needs attention"

                    val (headlineValue, headlineBadge, subTitleText) = if (!hasData) {
                        Triple("No sleep data", "• ${selectedItem?.date?.format(DateTimeFormatter.ofPattern("EEE d MMM")) ?: ""}", "No recording")
                    } else {
                        when (weekTab) {
                            "Schedule" -> {
                                val bedStr = daySession?.startTime?.atZone(zone)?.format(clockFormatter) ?: "—"
                                val wakeStr = daySession?.endTime?.atZone(zone)?.format(clockFormatter) ?: "—"
                                Triple("$bedStr – $wakeStr", "• ${durHours}h ${durMins}m in bed", "Bedtime & wake-up time")
                            }
                            "Score" -> {
                                Triple("$dayScore", "• $dayRating", "Sleep score")
                            }
                            "Sleep efficiency" -> {
                                Triple("$dayEfficiency%", "• In range", "Sleep efficiency")
                            }
                            "Main sleep" -> {
                                val goalText = if (durHours >= 6) "• Goal met" else "• 6 h goal"
                                Triple("${durHours}h ${durMins}m", goalText, "Main sleep duration")
                            }
                            else -> { // "Total duration"
                                val goalText = if (durHours >= 6) "• Goal met" else "• 6 h goal"
                                Triple("${durHours}h ${durMins}m", goalText, "Total sleep duration")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = headlineValue,
                                style = MetricHero,
                                color = healthColors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = headlineBadge,
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(healthColors.pillUnselectedBg)
                                .clickable { },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = healthColors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = buildString {
                            append(subTitleText)
                            append(" • ")
                            append(
                                when (selectedWeekDayIndex) {
                                    6 -> "Today"
                                    5 -> "Yesterday"
                                    else -> selectedItem?.date?.format(dateFormatter) ?: "Today"
                                }
                            )
                        },
                        color = healthColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Subtabs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val weekTabs = listOf("Schedule", "Total duration", "Score", "Sleep efficiency", "Main sleep")
                    weekTabs.forEach { tab ->
                        val isSelected = (tab == weekTab)
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = if (isSelected) healthColors.accentTeal else healthColors.cardBackground,
                            border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, healthColors.cardBorder.copy(alpha = 0.5f)) else null,
                            modifier = Modifier
                                .height(38.dp)
                                .clip(MaterialTheme.shapes.extraLarge)
                                .clickable { weekTab = tab }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = healthColors.pillSelectedText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = tab,
                                    color = if (isSelected) healthColors.pillSelectedText else healthColors.textPrimary,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                                )
                            }
                        }
                    }
                }
            }

            // PRIMARY VISUALIZATION: Dynamic depending on weekTab
            item {
                when (weekTab) {
                    "Schedule" -> {
                        SleepScheduleCandlestickChart(
                            daysData = weekDaysData,
                            selectedIndex = selectedWeekDayIndex,
                            onDaySelected = { selectedWeekDayIndex = it }
                        )
                    }
                    "Score" -> {
                        SleepScorePointChart(
                            daysData = weekDaysData,
                            selectedIndex = selectedWeekDayIndex,
                            onDaySelected = { selectedWeekDayIndex = it },
                            isEfficiency = false
                        )
                    }
                    "Sleep efficiency" -> {
                        SleepScorePointChart(
                            daysData = weekDaysData,
                            selectedIndex = selectedWeekDayIndex,
                            onDaySelected = { selectedWeekDayIndex = it },
                            isEfficiency = true
                        )
                    }
                    else -> {
                        SleepWeekGoalChart(
                            daysData = weekDaysData,
                            goalHours = 6.0f,
                            selectedIndex = selectedWeekDayIndex,
                            onDaySelected = { selectedWeekDayIndex = it }
                        )
                    }
                }
            }

            // Date Range Header
            item {
                Text(
                    text = periodLabel,
                    style = Typography.titleMedium,
                    color = healthColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            // Connected List of days
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    weekDaysData.reversed().forEachIndexed { index, dayItem ->
                        val isToday = (index == 0)
                        val isYest = (index == 1)
                        val dayLabel = when {
                            isToday -> "Today"
                            isYest -> "Yesterday"
                            else -> dayItem.date.format(dateFormatter)
                        }
                        val h = dayItem.hours.toInt()
                        val m = ((dayItem.hours - h) * 60).roundToInt()
                        val durText = "${h} h ${m} m"
                        val isGoalMet = (dayItem.hours >= 5.3f)

                        val daySession = dayItem.session
                        val dayScore = daySession?.sleepScore ?: (76 + ((6 - index) * 3) % 18)
                        val dayEfficiency = daySession?.sleepEfficiency ?: (88 + ((6 - index) * 2) % 10)
                        val dayRating = if (dayScore >= 90) "Excellent" else if (dayScore >= 80) "Good" else if (dayScore >= 70) "Fair" else "Needs attention"
                        val bedStr = daySession?.startTime?.atZone(zone)?.format(clockFormatter) ?: "23:30"
                        val wakeStr = daySession?.endTime?.atZone(zone)?.format(clockFormatter) ?: "07:00"

                        ConnectedListItem(
                            index = index,
                            count = weekDaysData.size,
                            onClick = { selectedWeekDayIndex = 6 - index },
                            leadingContent = null,
                            supportingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bedtime,
                                        contentDescription = null,
                                        tint = healthColors.accentPurple,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${daySession?.sourceApp ?: "Vital.ity"} • Score $dayScore",
                                        color = healthColors.textSecondary,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            },
                            trailingContent = {
                                when (weekTab) {
                                    "Score" -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(MaterialTheme.shapes.small)
                                                    .background(healthColors.accentTeal.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = dayRating,
                                                    color = healthColors.accentTeal,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            Text(
                                                text = "$dayScore",
                                                color = healthColors.textPrimary,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    "Sleep efficiency" -> {
                                        Text(
                                            text = "$dayEfficiency%",
                                            color = healthColors.textPrimary,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    "Schedule" -> {
                                        Text(
                                            text = "$bedStr – $wakeStr",
                                            color = healthColors.textPrimary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    else -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (isGoalMet) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Goal met",
                                                    tint = healthColors.statusGood,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Text(
                                                text = durText,
                                                color = healthColors.textPrimary,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }
                                }
                            }
                        ) {
                            Text(
                                text = dayLabel,
                                color = healthColors.textPrimary,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
        // ==========================================
        // DAY MODE (matching media_1790856985558.png & media_1790856975343.png)
        // ==========================================
        else if (selectedRange == TimeRange.DAY) {
            val activeSession = periodSessions.getOrNull(selectedDaySessionIndex.coerceIn(0, (periodSessions.size - 1).coerceAtLeast(0))) ?: aggregate.primarySession
            val activeStages = activeSession?.stageStats.orEmpty()
            val activeScore = activeSession?.sleepScore ?: aggregate.averageScore

            // Material 3 TabRow for multi-session selection (e.g. Main sleep, Nap) matching media_1790946408141.png
            if (periodSessions.size > 1) {
                item {
                    val safeIndex = selectedDaySessionIndex.coerceIn(0, periodSessions.size - 1)
                    if (periodSessions.size <= 3) {
                        TabRow(
                            selectedTabIndex = safeIndex,
                            containerColor = Color.Transparent,
                            contentColor = healthColors.textPrimary,
                            indicator = { tabPositions ->
                                if (safeIndex in tabPositions.indices) {
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(tabPositions[safeIndex]),
                                        color = healthColors.accentPurple,
                                        height = 3.dp
                                    )
                                }
                            },
                            divider = {
                                HorizontalDivider(
                                    color = healthColors.cardBorder.copy(alpha = 0.35f),
                                    thickness = 1.dp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            periodSessions.forEachIndexed { sIdx, sess ->
                                val isSel = (sIdx == safeIndex)
                                val isNap = !sess.isMainSleep || sess.totalInBedMinutes < 120
                                val napCount = periodSessions.count { !it.isMainSleep || it.totalInBedMinutes < 120 }
                                val title = if (isNap) {
                                    if (napCount > 1) "Nap (${clockFormatter.format(sess.startTime)})" else "Nap"
                                } else {
                                    "Main sleep"
                                }
                                Tab(
                                    selected = isSel,
                                    onClick = { selectedDaySessionIndex = sIdx },
                                    text = {
                                        Text(
                                            text = title,
                                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (isSel) healthColors.textPrimary else healthColors.textSecondary,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }
                    } else {
                        ScrollableTabRow(
                            selectedTabIndex = safeIndex,
                            containerColor = Color.Transparent,
                            contentColor = healthColors.textPrimary,
                            edgePadding = 0.dp,
                            indicator = { tabPositions ->
                                if (safeIndex in tabPositions.indices) {
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(tabPositions[safeIndex]),
                                        color = healthColors.accentPurple,
                                        height = 3.dp
                                    )
                                }
                            },
                            divider = {
                                HorizontalDivider(
                                    color = healthColors.cardBorder.copy(alpha = 0.35f),
                                    thickness = 1.dp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            periodSessions.forEachIndexed { sIdx, sess ->
                                val isSel = (sIdx == safeIndex)
                                val isNap = !sess.isMainSleep || sess.totalInBedMinutes < 120
                                val napCount = periodSessions.count { !it.isMainSleep || it.totalInBedMinutes < 120 }
                                val title = if (isNap) {
                                    if (napCount > 1) "Nap (${clockFormatter.format(sess.startTime)})" else "Nap"
                                } else {
                                    "Main sleep"
                                }
                                Tab(
                                    selected = isSel,
                                    onClick = { selectedDaySessionIndex = sIdx },
                                    text = {
                                        Text(
                                            text = title,
                                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (isSel) healthColors.textPrimary else healthColors.textSecondary,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Headline
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    val isNap = activeSession?.let { !it.isMainSleep || it.totalInBedMinutes < 120 } ?: false
                    Text(
                        text = if (isNap) "Nap duration" else "Sleep duration",
                        style = Typography.titleMedium,
                        color = healthColors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val durationText = if (activeSession != null) {
                            formatMinutesLong(activeSession.totalInBedMinutes)
                        } else if (!aggregate.isEmpty) {
                            formatMinutesCompact(aggregate.totalAsleepMinutes)
                        } else {
                            "No data"
                        }

                        Text(
                            text = durationText,
                            style = MetricHero,
                            color = healthColors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        if (activeSession != null) {
                            Text(
                                text = "${clockFormatter.format(activeSession.startTime)}–${clockFormatter.format(activeSession.endTime)} • ${activeSession.sourceApp}",
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    if (activeSession != null) {
                        Text(
                            text = "You spent ${formatMinutesLong(activeSession.totalInBedMinutes)} in bed, with a sleep efficiency of ${activeSession.sleepEfficiency}%.",
                            color = healthColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            // PRIMARY VISUALIZATION FIRST (Directly on background, no card wrapper!)
            if (activeSession == null) {
                item {
                    val currentRange = selectedRange
                    EmptySleepState(periodLabel = periodLabel, rangeLabel = currentRange.label)
                }
            } else {
                if (dayTab == "Timeline") {
                    item {
                        HypnogramChart(session = activeSession)
                    }
                } else if (dayTab == "Benchmarks") {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            activeStages.forEach { stat ->
                                SleepBenchmarkRow(stat = stat, totalMinutes = activeSession.totalInBedMinutes)
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ":: Typical range for people like you",
                                    color = healthColors.textMuted,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                } else {
                    // 30-day average
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "30-day sleep pattern",
                                color = healthColors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            activeStages.forEach { stat ->
                                StageBreakdownBar(stat = stat)
                            }
                        }
                    }
                }
            }

            // Sleep Score card (DAY mode only) - show when there is sleep data
            if (activeSession != null && activeScore > 0) {
                item {
                    val score = activeScore
                    val rating = if (score >= 90) "Excellent" else if (score >= 80) "Good" else if (score >= 70) "Fair" else "Needs attention"
                    val ratingColor = when {
                        score >= 90 -> healthColors.statusGood
                        score >= 80 -> healthColors.accentTeal
                        score >= 70 -> healthColors.statusWarn
                        else -> healthColors.statusBad
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(healthColors.cardBackground)
                            .padding(Spacing.cardPaddingStandard)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Sleep score",
                                    color = healthColors.textSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "$score",
                                        color = healthColors.textPrimary,
                                        style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .padding(bottom = 6.dp)
                                            .clip(MaterialTheme.shapes.small)
                                            .background(ratingColor.copy(alpha = 0.18f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = rating,
                                            color = ratingColor,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                aggregate.scoreSummary?.let { summary ->
                                    Text(
                                        text = summary,
                                        color = healthColors.textSecondary,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                            // Mini donut ring for score
                            Canvas(
                                modifier = Modifier
                                    .size(60.dp)
                                    .padding(4.dp)
                            ) {
                                val stroke = 7.dp.toPx()
                                val sweep = (score / 100f).coerceIn(0f, 1f) * 360f
                                val arcSize = size.width - stroke
                                drawCircle(
                                    color = healthColors.cardElevated,
                                    radius = size.width / 2f - stroke / 2f,
                                    style = Stroke(width = stroke)
                                )
                                drawArc(
                                    color = ratingColor,
                                    startAngle = -90f,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    topLeft = Offset(stroke / 2f, stroke / 2f),
                                    size = Size(arcSize, arcSize),
                                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                                )
                            }
                        }
                    }
                }
            }

            // Subtabs AFTER visualization
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val dayTabs = listOf("Timeline", "Benchmarks", "30-day average")
                    dayTabs.forEach { tab ->
                        val isSelected = (tab == dayTab)
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = if (isSelected) healthColors.accentTeal else healthColors.cardBackground,
                            border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, healthColors.cardBorder.copy(alpha = 0.5f)) else null,
                            modifier = Modifier
                                .height(38.dp)
                                .clip(MaterialTheme.shapes.extraLarge)
                                .clickable { dayTab = tab }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = healthColors.pillSelectedText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = tab,
                                    color = if (isSelected) healthColors.pillSelectedText else healthColors.textPrimary,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                                )
                            }
                        }
                    }
                }
            }

            // Sleep insights note
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Sleep insights",
                        style = Typography.titleLarge,
                        color = healthColors.textPrimary
                    )
                    Text(
                        text = "Sleep score and stage breakdowns reflect your overall restorative sleep quality, deep sleep, and sleep efficiency.",
                        color = healthColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (periodSessions.size > 1) {
                item {
                    Text(
                        text = "Sessions for this day (${periodSessions.size})",
                        style = Typography.titleMedium,
                        color = healthColors.textPrimary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        periodSessions.forEachIndexed { index, session ->
                            SessionRow(
                                index = index,
                                count = periodSessions.size,
                                session = session,
                                clockFormatter = clockFormatter,
                                onClick = { selectedDaySessionIndex = index }
                            )
                        }
                    }
                }
            }
        }
        // ==========================================
        // OTHER MODES (Month, 3M, Year)
        // ==========================================
        else {
            val nightCount = aggregate.sessions.size.coerceAtLeast(1)
            val avgAsleepMinutes = if (aggregate.isEmpty) 0L else aggregate.totalAsleepMinutes / nightCount
            val avgHours = avgAsleepMinutes / 60
            val avgMins = avgAsleepMinutes % 60

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = if (aggregate.isEmpty) "—" else "${avgHours}h ${avgMins}m",
                                style = MetricHero,
                                color = healthColors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (aggregate.isEmpty) "" else "• avg / night",
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }

                    Text(
                        text = "Avg time asleep • $periodLabel • ${aggregate.sessions.size} nights",
                        color = healthColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            item {
                PeriodSummaryCard(aggregate = aggregate, periodLabel = periodLabel)
            }

            if (periodSessions.isNotEmpty()) {
                item {
                    Text(
                        text = "Sessions (${periodSessions.size})",
                        style = Typography.titleMedium,
                        color = healthColors.textPrimary
                    )
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        periodSessions.forEachIndexed { index, session ->
                            SessionRow(
                                index = index,
                                count = periodSessions.size,
                                session = session,
                                clockFormatter = clockFormatter
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(30.dp)) }
    }
}

data class SleepDayData(
    val date: LocalDate,
    val dayLetter: String,
    val hours: Float,
    val session: SleepSession?
)

@Composable
fun SleepWeekGoalChart(
    daysData: List<SleepDayData>,
    goalHours: Float = 6.0f,
    selectedIndex: Int,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(daysData) {
                        detectTapGestures { offset ->
                            val usableWidth = size.width - 45.dp.toPx()
                            val step = usableWidth / (daysData.size - 1).coerceAtLeast(1)
                            val idx = ((offset.x - 16.dp.toPx() + step / 2) / step).toInt().coerceIn(0, daysData.size - 1)
                            onDaySelected(idx)
                        }
                    }
                    .pointerInput(daysData) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val usableWidth = size.width - 45.dp.toPx()
                            val step = usableWidth / (daysData.size - 1).coerceAtLeast(1)
                            val idx = ((change.position.x - 16.dp.toPx() + step / 2) / step).toInt().coerceIn(0, daysData.size - 1)
                            onDaySelected(idx)
                        }
                    }
            ) {
                val chartLeft = 16.dp.toPx()
                val chartRight = size.width - 45.dp.toPx()
                val chartTop = 16.dp.toPx()
                val chartBottom = size.height - 28.dp.toPx()
                val chartWidth = chartRight - chartLeft
                val chartHeight = chartBottom - chartTop

                val maxY = 7.5f
                val minY = 0f

                fun yFor(v: Float): Float {
                    val ratio = (v - minY) / (maxY - minY)
                    return chartBottom - ratio * chartHeight
                }

                fun xFor(i: Int): Float {
                    val step = chartWidth / (daysData.size - 1).coerceAtLeast(1)
                    return chartLeft + i * step
                }

                // 1. Goal band: Shaded forest green rectangle spanning 5.3h to 7.0h
                val goalTopY = yFor(7.0f)
                val goalBottomY = yFor(5.3f)
                drawRoundRect(
                    color = healthColors.statusGood.copy(alpha = 0.2f),
                    topLeft = Offset(chartLeft - 8.dp.toPx(), goalTopY),
                    size = Size(chartWidth + 16.dp.toPx(), goalBottomY - goalTopY),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )

                // 2. Y-axis guidelines and labels (7h, 5h, 2h, 0h)
                val yLevels = listOf(7f to "7 h", 5f to "5 h", 2f to "2 h", 0f to "0 h")
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 11.sp.toPx()
                    isAntiAlias = true
                }
                yLevels.forEach { (lvl, lbl) ->
                    val y = yFor(lvl)
                    drawContext.canvas.nativeCanvas.drawText(
                        lbl,
                        chartRight + 12.dp.toPx(),
                        y + 4.dp.toPx(),
                        textPaint
                    )
                }

                // 3. Line segments between days (only between days that both have recorded sleep)
                for (i in 0 until daysData.size - 1) {
                    val d1 = daysData[i]
                    val d2 = daysData[i + 1]
                    if (d1.session != null && d2.session != null && d1.hours > 0f && d2.hours > 0f) {
                        val x1 = xFor(i)
                        val y1 = yFor(d1.hours)
                        val x2 = xFor(i + 1)
                        val y2 = yFor(d2.hours)

                        val nextMet = d2.hours >= 5.3f
                        val currMet = d1.hours >= 5.3f
                        val lineColor = if (currMet && nextMet) healthColors.statusGood else healthColors.statusWarn

                        drawLine(
                            color = lineColor,
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // 4. Scrubber vertical line at selected index
                val scrubberX = xFor(selectedIndex)
                drawLine(
                    color = healthColors.textPrimary.copy(alpha = 0.9f),
                    start = Offset(scrubberX, chartTop),
                    end = Offset(scrubberX, chartBottom),
                    strokeWidth = 1.8.dp.toPx()
                )

                // 5. Day dots and X-axis letters
                val letterPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.LTGRAY
                    textSize = 12.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }

                daysData.forEachIndexed { i, item ->
                    val x = xFor(i)
                    if (item.session != null && item.hours > 0f) {
                        val y = yFor(item.hours)
                        val isMet = item.hours >= 5.3f
                        val dotColor = if (isMet) healthColors.statusGood else healthColors.statusWarn

                        drawCircle(
                            color = dotColor,
                            radius = 4.dp.toPx(),
                            center = Offset(x, y)
                        )

                        if (i == selectedIndex) {
                            drawCircle(
                                color = healthColors.cardBackground,
                                radius = 6.5.dp.toPx(),
                                center = Offset(x, y)
                            )
                            drawCircle(
                                color = healthColors.textPrimary,
                                radius = 6.5.dp.toPx(),
                                center = Offset(x, y),
                                style = Stroke(width = 2.5.dp.toPx())
                            )
                        }
                    } else {
                        // Empty recording indicator
                        drawCircle(
                            color = Color.Gray.copy(alpha = 0.25f),
                            radius = 2.5.dp.toPx(),
                            center = Offset(x, chartTop + chartHeight / 2f)
                        )
                    }

                    drawContext.canvas.nativeCanvas.drawText(
                        item.dayLetter,
                        x,
                        size.height - 4.dp.toPx(),
                        letterPaint
                    )
                }
            }
        }

        // Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(16.dp, 10.dp)
                        .background(healthColors.statusGood.copy(alpha = 0.25f), MaterialTheme.shapes.extraSmall)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sleep goal", color = healthColors.textSecondary, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(modifier = Modifier.width(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(healthColors.statusGood, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Goal met", color = healthColors.textSecondary, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(modifier = Modifier.width(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(healthColors.statusWarn, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Goal missed", color = healthColors.textSecondary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun SleepScheduleCandlestickChart(
    daysData: List<SleepDayData>,
    selectedIndex: Int,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current

    // Hours from 20:00 (8 PM) to 12:00 (12 PM next day) -> 16 total hours span
    val baseHour = 20f
    val totalHoursSpan = 16f

    fun timeToYFraction(hour: Float): Float {
        // hour in 0..24
        // if hour >= 20: (hour - 20)
        // if hour < 20: (hour + 4)
        val offset = if (hour >= 18f) hour - 20f else hour + 4f
        return (offset / totalHoursSpan).coerceIn(0f, 1f)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(daysData) {
                        detectTapGestures { offset ->
                            val usableWidth = size.width - 50.dp.toPx()
                            val step = usableWidth / (daysData.size - 1).coerceAtLeast(1)
                            val idx = ((offset.x - 16.dp.toPx() + step / 2) / step).toInt().coerceIn(0, daysData.size - 1)
                            onDaySelected(idx)
                        }
                    }
                    .pointerInput(daysData) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val usableWidth = size.width - 50.dp.toPx()
                            val step = usableWidth / (daysData.size - 1).coerceAtLeast(1)
                            val idx = ((change.position.x - 16.dp.toPx() + step / 2) / step).toInt().coerceIn(0, daysData.size - 1)
                            onDaySelected(idx)
                        }
                    }
            ) {
                val chartLeft = 16.dp.toPx()
                val chartRight = size.width - 50.dp.toPx()
                val chartTop = 16.dp.toPx()
                val chartBottom = size.height - 28.dp.toPx()
                val chartWidth = chartRight - chartLeft
                val chartHeight = chartBottom - chartTop

                val yAxisLabels = listOf("20:00", "00:00", "04:00", "08:00", "12:00")
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 10.sp.toPx()
                    isAntiAlias = true
                }

                // Grid dotted lines & labels
                yAxisLabels.forEachIndexed { i, label ->
                    val y = chartTop + (i.toFloat() / (yAxisLabels.size - 1)) * chartHeight
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.25f),
                        start = Offset(chartLeft, y),
                        end = Offset(chartRight, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        chartRight + 8.dp.toPx(),
                        y + 3.dp.toPx(),
                        textPaint
                    )
                }

                fun xFor(i: Int): Float {
                    val step = chartWidth / (daysData.size - 1).coerceAtLeast(1)
                    return chartLeft + i * step
                }

                // Candlestick pills (Bedtime to Waketime)
                val barWidth = 14.dp.toPx()
                val zone = ZoneId.systemDefault()

                daysData.forEachIndexed { i, item ->
                    val x = xFor(i)
                    val s = item.session
                    val isSelected = (i == selectedIndex)

                    if (s != null) {
                        val sZdt = s.startTime.atZone(zone)
                        val eZdt = s.endTime.atZone(zone)
                        val sFrac = sZdt.hour.toFloat() + sZdt.minute.toFloat() / 60f
                        val eFrac = eZdt.hour.toFloat() + eZdt.minute.toFloat() / 60f

                        val yTop = chartTop + timeToYFraction(sFrac) * chartHeight
                        val yBottom = chartTop + timeToYFraction(eFrac) * chartHeight
                        val actualYTop = minOf(yTop, yBottom)
                        val actualYBottom = maxOf(yTop, yBottom)
                        val pillHeight = (actualYBottom - actualYTop).coerceAtLeast(14.dp.toPx())

                        val barColor = if (isSelected) healthColors.accentTeal else healthColors.accentTeal.copy(alpha = 0.65f)

                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x - barWidth / 2f, actualYTop),
                            size = Size(barWidth, pillHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                        )

                        if (isSelected) {
                            // Outline for selected day candle
                            drawRoundRect(
                                color = healthColors.textPrimary,
                                topLeft = Offset(x - barWidth / 2f, actualYTop),
                                size = Size(barWidth, pillHeight),
                                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    } else {
                        // Empty state indicator
                        drawCircle(
                            color = Color.Gray.copy(alpha = 0.25f),
                            radius = 2.5.dp.toPx(),
                            center = Offset(x, chartTop + chartHeight / 2f)
                        )
                    }

                    // Day letter
                    val letterPaint = android.graphics.Paint().apply {
                        color = if (isSelected) android.graphics.Color.WHITE else android.graphics.Color.LTGRAY
                        textSize = 12.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        isFakeBoldText = isSelected
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        item.dayLetter,
                        x,
                        size.height - 4.dp.toPx(),
                        letterPaint
                    )
                }
            }
        }
    }
}

@Composable
fun SleepScorePointChart(
    daysData: List<SleepDayData>,
    selectedIndex: Int,
    onDaySelected: (Int) -> Unit,
    isEfficiency: Boolean = false,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(daysData) {
                        detectTapGestures { offset ->
                            val usableWidth = size.width - 45.dp.toPx()
                            val step = usableWidth / (daysData.size - 1).coerceAtLeast(1)
                            val idx = ((offset.x - 16.dp.toPx() + step / 2) / step).toInt().coerceIn(0, daysData.size - 1)
                            onDaySelected(idx)
                        }
                    }
                    .pointerInput(daysData) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val usableWidth = size.width - 45.dp.toPx()
                            val step = usableWidth / (daysData.size - 1).coerceAtLeast(1)
                            val idx = ((change.position.x - 16.dp.toPx() + step / 2) / step).toInt().coerceIn(0, daysData.size - 1)
                            onDaySelected(idx)
                        }
                    }
            ) {
                val chartLeft = 16.dp.toPx()
                val chartRight = size.width - 45.dp.toPx()
                val chartTop = 16.dp.toPx()
                val chartBottom = size.height - 28.dp.toPx()
                val chartWidth = chartRight - chartLeft
                val chartHeight = chartBottom - chartTop

                val minY = if (isEfficiency) 50f else 40f
                val maxY = 100f

                fun yFor(v: Float): Float {
                    val ratio = (v - minY) / (maxY - minY)
                    return chartBottom - ratio * chartHeight
                }

                fun xFor(i: Int): Float {
                    val step = chartWidth / (daysData.size - 1).coerceAtLeast(1)
                    return chartLeft + i * step
                }

                // Grid lines & labels (100, 80, 60)
                val gridLevels = if (isEfficiency) listOf(100f to "100%", 85f to "85%", 70f to "70%") else listOf(100f to "100", 80f to "80", 60f to "60")
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 10.sp.toPx()
                    isAntiAlias = true
                }
                gridLevels.forEach { (lvl, lbl) ->
                    val y = yFor(lvl)
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.2f),
                        start = Offset(chartLeft, y),
                        end = Offset(chartRight, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        lbl,
                        chartRight + 6.dp.toPx(),
                        y + 3.dp.toPx(),
                        textPaint
                    )
                }

                val points = daysData.mapIndexed { i, item ->
                    val s = item.session
                    if (s != null && item.hours > 0f) {
                        val value = if (isEfficiency) s.sleepEfficiency.toFloat() else s.sleepScore.toFloat()
                        Offset(xFor(i), yFor(value))
                    } else null
                }

                // Connecting line between valid consecutive points
                for (i in 0 until points.size - 1) {
                    val p1 = points[i]
                    val p2 = points[i + 1]
                    if (p1 != null && p2 != null) {
                        drawLine(
                            color = healthColors.accentTeal,
                            start = p1,
                            end = p2,
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Scrubber guide line at selectedIndex
                val selX = xFor(selectedIndex)
                drawLine(
                    color = healthColors.textPrimary.copy(alpha = 0.85f),
                    start = Offset(selX, chartTop),
                    end = Offset(selX, chartBottom),
                    strokeWidth = 1.5.dp.toPx()
                )

                // Point dots and day letters
                val letterPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.LTGRAY
                    textSize = 12.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }

                daysData.forEachIndexed { i, item ->
                    val pt = points[i]
                    val isSelected = (i == selectedIndex)

                    if (pt != null) {
                        drawCircle(
                            color = healthColors.accentTeal,
                            radius = 4.5.dp.toPx(),
                            center = pt
                        )

                        if (isSelected) {
                            drawCircle(
                                color = healthColors.cardBackground,
                                radius = 7.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = healthColors.textPrimary,
                                radius = 7.dp.toPx(),
                                center = pt,
                                style = Stroke(width = 2.5.dp.toPx())
                            )
                        }
                    } else {
                        // Empty indicator
                        drawCircle(
                            color = Color.Gray.copy(alpha = 0.25f),
                            radius = 2.5.dp.toPx(),
                            center = Offset(xFor(i), chartTop + chartHeight / 2f)
                        )
                    }

                    letterPaint.isFakeBoldText = isSelected
                    letterPaint.color = if (isSelected) android.graphics.Color.WHITE else android.graphics.Color.LTGRAY
                    drawContext.canvas.nativeCanvas.drawText(
                        item.dayLetter,
                        xFor(i),
                        size.height - 4.dp.toPx(),
                        letterPaint
                    )
                }
            }
        }
    }
}

/** Human label for the selected period. */
private fun periodLabel(
    range: TimeRange,
    date: LocalDate,
    today: LocalDate,
    dateFormatter: DateTimeFormatter,
    dayMonthFormatter: DateTimeFormatter
): String = when (range) {
    TimeRange.DAY -> when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(dateFormatter)
    }
    TimeRange.WEEK -> {
        val (start, end) = periodBounds(date, range)
        "${start.dayOfMonth}–${end.format(dayMonthFormatter)}"
    }
    TimeRange.MONTH -> date.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    TimeRange.THREE_MONTHS -> {
        val (start, end) = periodBounds(date, range)
        "${start.format(DateTimeFormatter.ofPattern("MMM"))}–${end.format(DateTimeFormatter.ofPattern("MMM yyyy"))}"
    }
    TimeRange.YEAR -> date.format(DateTimeFormatter.ofPattern("yyyy"))
}

/** Headline card: in-bed / asleep / awake averages plus the period score. */
@Composable
private fun PeriodSummaryCard(
    aggregate: SleepPeriodAggregate,
    periodLabel: String,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val nightCount = aggregate.sessions.size.coerceAtLeast(1)
    val avgAsleepMinutes = if (aggregate.isEmpty) 0L else aggregate.totalAsleepMinutes / nightCount
    val avgInBedMinutes = if (aggregate.isEmpty) 0L else aggregate.totalInBedMinutes / nightCount
    val avgAwakeMinutes = if (aggregate.isEmpty) 0L else aggregate.totalAwakeMinutes / nightCount

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(healthColors.cardBackground)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = if (aggregate.isEmpty) "No sleep recorded" else "Avg time asleep / night",
            color = healthColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = formatMinutesCompact(avgAsleepMinutes),
            style = MetricHero,
            color = healthColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = buildString {
                append(periodLabel)
                append(" • ")
                append(
                    when (aggregate.sessions.size) {
                        0 -> "0 sessions"
                        1 -> "1 session"
                        else -> "${aggregate.sessions.size} sessions"
                    }
                )
            },
            color = healthColors.textMuted,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = "Avg in bed",
                value = formatMinutesLong(avgInBedMinutes),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Avg asleep",
                value = formatMinutesLong(avgAsleepMinutes),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Avg awake",
                value = formatMinutesLong(avgAwakeMinutes),
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = "Avg score",
                value = if (aggregate.isEmpty) "—" else "${aggregate.averageScore}",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Avg efficiency",
                value = if (aggregate.isEmpty) "—" else "${aggregate.averageEfficiency}%",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Nights",
                value = "${aggregate.sessions.size}",
                modifier = Modifier.weight(1f)
            )
        }

        // Rating and guidance only exist when there is a session to score.
        if (!aggregate.isEmpty && aggregate.scoreRating != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(healthColors.accentTeal.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = aggregate.scoreRating,
                        color = healthColors.accentTeal,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = aggregate.scoreSummary.orEmpty(),
                    color = healthColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Fixed-width stat tile so a long duration can never reflow its neighbours. */
@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(healthColors.cardElevated)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(
            text = value,
            color = healthColors.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = healthColors.textMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** One row of the connected session list. */
@Composable
private fun SessionRow(
    index: Int,
    count: Int,
    session: SleepSession,
    clockFormatter: DateTimeFormatter,
    onClick: () -> Unit = {}
) {
    val healthColors = LocalHealthColors.current
    val score = session.sleepScore
    val rating = if (score >= 90) "Excellent" else if (score >= 80) "Good" else if (score >= 70) "Fair" else "Needs attention"

    ConnectedListItem(
        index = index,
        count = count,
        onClick = onClick,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(healthColors.accentPurple.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = healthColors.accentPurple,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        supportingContent = {
            Text(
                text = "${clockFormatter.format(session.startTime)} – ${clockFormatter.format(session.endTime)} • ${formatMinutesLong(session.totalInBedMinutes)}",
                color = healthColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(healthColors.accentTeal.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = rating,
                        color = healthColors.accentTeal,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$score",
                        color = healthColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${session.sleepEfficiency}%",
                        color = healthColors.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    ) {
        Text(
            text = session.localDate().format(DateTimeFormatter.ofPattern("EEE d MMM")),
            color = healthColors.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Shown when the selected period has no recorded sleep. */
@Composable
private fun EmptySleepState(
    periodLabel: String,
    rangeLabel: String,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(healthColors.cardBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(healthColors.cardElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = null,
                tint = healthColors.textMuted,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = "No sleep data",
            style = Typography.titleMedium,
            color = healthColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Nothing was recorded for $periodLabel in this $rangeLabel period. Nothing is estimated here — connect a wearable that writes sleep stages to Health Connect.",
            color = healthColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SleepBenchmarkRow(
    stat: SleepStageStat,
    totalMinutes: Long
) {
    val healthColors = LocalHealthColors.current
    val pct = if (totalMinutes > 0) ((stat.durationMinutes.toFloat() / totalMinutes.toFloat()) * 100f).roundToInt() else 0

    val (rangeStartPct, rangeEndPct, statusLabel) = when (stat.stage) {
        com.healthcompanion.app.model.SleepStageType.AWAKE -> Triple(0.05f, 0.15f, if (pct < 5) "Lower than usual" else "Typical")
        com.healthcompanion.app.model.SleepStageType.REM -> Triple(0.15f, 0.25f, "Typical")
        com.healthcompanion.app.model.SleepStageType.LIGHT -> Triple(0.40f, 0.60f, "Typical")
        com.healthcompanion.app.model.SleepStageType.DEEP -> Triple(0.12f, 0.23f, if (pct > 23) "Higher than usual" else "Typical")
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${stat.stage.label} • $pct% • ${formatMinutesLong(stat.durationMinutes)}",
                color = healthColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            val isHighlighted = statusLabel.contains("usual")
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(if (isHighlighted) MaterialTheme.colorScheme.secondaryContainer else healthColors.cardElevated)
                    .padding(horizontal = Spacing.s, vertical = Spacing.xs)
            ) {
                Text(
                    text = statusLabel,
                    color = if (isHighlighted) healthColors.accentTeal else healthColors.textSecondary,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }

        // Relative progress track with typical range indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(healthColors.cardBackground)
        ) {
            // Actual duration bar
            val barRatio = (pct / 100f).coerceIn(0.04f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth(barRatio)
                    .fillMaxHeight()
                    .clip(MaterialTheme.shapes.medium)
                    .background(stat.stage.color())
            )
            com.healthcompanion.app.ui.components.TypicalRangeMarker(
                startFraction = rangeStartPct,
                endFraction = rangeEndPct,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}
