package com.healthcompanion.app.ui.screens.exercise

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthcompanion.app.model.ExerciseWorkout
import com.healthcompanion.app.ui.components.*
import com.healthcompanion.app.ui.theme.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * Human label for the week/month switch. [ExerciseTimeRange] itself is declared
 * once in `FitnessScreen.kt` for the whole package.
 */
private fun ExerciseTimeRange.displayLabel(): String = when (this) {
    ExerciseTimeRange.WEEK -> "Week"
    ExerciseTimeRange.MONTH -> "Month"
}

/** Maps the screen's own range enum onto the shared `TimeRange`. */
private fun ExerciseTimeRange.asSharedRange(): TimeRange = when (this) {
    ExerciseTimeRange.WEEK -> TimeRange.WEEK
    ExerciseTimeRange.MONTH -> TimeRange.MONTH
}

/** Local date a workout happened on. */
private fun ExerciseWorkout.localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    startTime.atZone(zone).toLocalDate()

/** "48 min" — single line, never wraps in a stat tile. */
private fun formatDurationMinutes(minutes: Int): String = when {
    minutes <= 0 -> "0 min"
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/**
 * Exercise history.
 *
 * A week/month switch, a sub-filter switch, a period picker (a 7-capsule week
 * strip or a 7-column month grid) and the workouts for the selected day.
 *
 * Every window is anchored to a calendar boundary: weeks start on Sunday and
 * months on the 1st, taken from the canonical period helpers. Future periods are
 * unreachable because navigation is gated on `isPeriodSelectable`, and days with
 * no recorded workout are rendered as empty cells rather than being padded with
 * invented sessions.
 */
@Composable
fun ExerciseListScreen(
    workouts: List<ExerciseWorkout>,
    onWorkoutClick: (ExerciseWorkout) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now() }

    var timeRange by remember { mutableStateOf(ExerciseTimeRange.WEEK) }
    var subFilter by remember { mutableStateOf(ExerciseSubFilter.EXERCISE_DAYS) }
    var anchorDate by remember { mutableStateOf(today) }
    var selectedDate by remember { mutableStateOf(today) }

    // Weeks start on Sunday, months on the 1st — never a rolling day count.
    val sharedRange = timeRange.asSharedRange()
    val (periodStartDate, periodEndDate) = periodBounds(anchorDate, sharedRange)
    val canGoNext = canStepForward(anchorDate, sharedRange, today) &&
        isPeriodSelectable(anchorDate.stepForward(sharedRange), sharedRange, today)

    val periodLabel = remember(timeRange, anchorDate) {
        when (timeRange) {
            ExerciseTimeRange.WEEK -> {
                val f = DateTimeFormatter.ofPattern("d MMM")
                "${periodStartDate.dayOfMonth}–${periodEndDate.format(f)}"
            }
            ExerciseTimeRange.MONTH -> anchorDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        }
    }

    // Only real workouts inside the visible window.
    val periodWorkouts = remember(workouts, periodStartDate, periodEndDate) {
        workouts
            .filter { workout ->
                val date = workout.localDate(zone)
                !date.isBefore(periodStartDate) && !date.isAfter(periodEndDate)
            }
            .sortedByDescending { it.startTime }
    }

    // Day totals for the period, used by both the strip and the grid.
    val workoutsByDay = remember(periodWorkouts) {
        periodWorkouts.groupBy { it.localDate(zone) }
    }

    val selectedDayWorkouts = remember(selectedDate, periodWorkouts) {
        periodWorkouts.filter { it.localDate(zone) == selectedDate }
    }

    val periodSummary = remember(periodWorkouts) {
        summarizePeriod(periodWorkouts, timeRange)
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
                    text = "Exercise",
                    style = Typography.titleLarge,
                    color = healthColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                )
                Spacer(modifier = Modifier.size(48.dp))
            }
        }

        // Week / month connected group
        item {
            M3EConnectedButtonGroup(
                items = ExerciseTimeRange.entries.toList(),
                selectedItem = timeRange,
                onItemSelected = {
                    if (it != timeRange) {
                        timeRange = it
                        // Re-anchor so the newly selected period contains the
                        // day the user was looking at.
                        anchorDate = selectedDate
                        selectedDate = periodStart(selectedDate, it.asSharedRange())
                    }
                },
                labelProvider = { it.displayLabel() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Period header with previous / next navigation.
        item {
            PeriodHeader(
                periodLabel = periodLabel,
                canGoNext = canGoNext,
                onPrevious = {
                    anchorDate = anchorDate.stepBackward(sharedRange)
                    selectedDate = periodStart(anchorDate, sharedRange)
                },
                onNext = {
                    val next = anchorDate.stepForward(sharedRange)
                    if (isPeriodSelectable(next, sharedRange, today)) {
                        anchorDate = next
                        selectedDate = periodStart(next, sharedRange)
                    }
                },
                onReset = {
                    anchorDate = today
                    selectedDate = today
                }
            )
        }

        // Sub-filter connected group (covers full width!)
        item {
            M3EConnectedButtonGroup(
                items = ExerciseSubFilter.entries.toList(),
                selectedItem = subFilter,
                onItemSelected = { subFilter = it },
                labelProvider = { it.label },
                isScrollable = false,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Period picker / graphs — changes depending on active sub-filter
        item {
            val days = remember(anchorDate, sharedRange) {
                daysInPeriod(anchorDate, sharedRange)
            }

            when (subFilter) {
                ExerciseSubFilter.EXERCISE_DAYS -> {
                    AnimatedContent(
                        targetState = timeRange,
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(180)) },
                        label = "exercisePeriodPicker"
                    ) { range ->
                        when (range) {
                            ExerciseTimeRange.WEEK -> WeekStrip(
                                weekStart = periodStartDate,
                                selectedDate = selectedDate,
                                today = today,
                                workoutsByDay = workoutsByDay,
                                onDateSelected = { selectedDate = it }
                            )
                            ExerciseTimeRange.MONTH -> MonthGrid(
                                monthAnchor = anchorDate,
                                selectedDate = selectedDate,
                                today = today,
                                workoutsByDay = workoutsByDay,
                                onDateSelected = { selectedDate = it }
                            )
                        }
                    }
                }
                ExerciseSubFilter.DURATION -> {
                    val durationValues = remember(days, workoutsByDay) {
                        days.map { d -> (workoutsByDay[d]?.sumOf { it.durationMinutes } ?: 0).toFloat() }
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        GoogleStyleBarChart(
                            values = durationValues,
                            valueFormatter = { "${it.toInt()}m" }
                        )
                        ChartDateLabels(
                            days = days,
                            selectedDate = selectedDate,
                            isWeek = timeRange == ExerciseTimeRange.WEEK
                        )
                    }
                }
                ExerciseSubFilter.DISTANCE -> {
                    val distanceValues = remember(days, workoutsByDay) {
                        days.map { d -> (workoutsByDay[d]?.mapNotNull { it.distanceKm }?.sum() ?: 0.0).toFloat() }
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        GoogleStyleBarChart(
                            values = distanceValues,
                            valueFormatter = { String.format(Locale.getDefault(), "%.1f km", it) }
                        )
                        ChartDateLabels(
                            days = days,
                            selectedDate = selectedDate,
                            isWeek = timeRange == ExerciseTimeRange.WEEK
                        )
                    }
                }
                ExerciseSubFilter.TIME -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        WorkoutTimeChart(
                            days = days,
                            workoutsByDay = workoutsByDay
                        )
                        ChartDateLabels(
                            days = days,
                            selectedDate = selectedDate,
                            isWeek = timeRange == ExerciseTimeRange.WEEK
                        )
                    }
                }
            }
        }

        // Period totals for the active sub-filter.
        item {
            AnimatedContent(
                targetState = Triple(subFilter, timeRange, periodWorkouts.size),
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(180)) },
                label = "exercisePeriodSummary"
            ) { state ->
                PeriodSummaryCard(summary = periodSummary, filter = state.first)
            }
        }

        // Workouts on the selected day.
        item {
            Text(
                text = selectedDateHeader(selectedDate, today),
                style = Typography.titleMedium,
                color = healthColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (selectedDayWorkouts.isEmpty()) {
            item {
                NoWorkoutsCard(
                    dateLabel = selectedDate.format(DateTimeFormatter.ofPattern("EEE d MMM")),
                    isToday = selectedDate == today
                )
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    selectedDayWorkouts.forEachIndexed { index, workout ->
                        WorkoutRow(
                            index = index,
                            count = selectedDayWorkouts.size,
                            workout = workout,
                            onClick = { onWorkoutClick(workout) }
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(30.dp)) }
    }
}

/** Totals for the visible period. Derived only from recorded workouts. */
private data class PeriodTotals(
    val workoutCount: Int = 0,
    val activeDayCount: Int = 0,
    val totalDurationMinutes: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val hasDistance: Boolean = false,
    val firstStart: Instant? = null,
    val lastStart: Instant? = null
) {
    val averageDurationMinutes: Int
        get() = if (workoutCount > 0) totalDurationMinutes / workoutCount else 0
}

private fun summarizePeriod(workouts: List<ExerciseWorkout>, range: ExerciseTimeRange): PeriodTotals {
    if (workouts.isEmpty()) return PeriodTotals()
    val zone = ZoneId.systemDefault()
    val distance = workouts.mapNotNull { it.distanceKm }
    return PeriodTotals(
        workoutCount = workouts.size,
        activeDayCount = workouts.map { it.localDate(zone) }.distinct().size,
        totalDurationMinutes = workouts.sumOf { it.durationMinutes },
        totalDistanceKm = distance.sum(),
        hasDistance = distance.isNotEmpty(),
        firstStart = workouts.minOfOrNull { it.startTime },
        lastStart = workouts.maxOfOrNull { it.startTime }
    )
}

/** "Today" / "Yesterday" / "Mon 14 Sep". */
private fun selectedDateHeader(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
}

/** Period label with previous / next / reset controls. */
@Composable
private fun PeriodHeader(
    periodLabel: String,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = periodLabel,
            style = Typography.headlineSmall,
            color = healthColors.textPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArrowButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Previous period",
                onClick = onPrevious
            )
            ArrowButton(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Next period",
                enabled = canGoNext,
                onClick = onNext
            )
            ArrowButton(
                icon = Icons.Default.Today,
                contentDescription = "Jump to today",
                onClick = onReset
            )
        }
    }
}

@Composable
private fun ArrowButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val healthColors = LocalHealthColors.current
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(healthColors.pillUnselectedBg)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) healthColors.textPrimary else healthColors.textMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Seven equal-weight capsules, one per day of the Sunday-anchored week.
 *
 * Every column is `weight(1f)`, so the seven capsules always split the row into
 * exact sevenths and the weekday letters beneath them stay aligned with the
 * capsules above. A day with a recorded workout is filled; a day without one
 * stays an outline — nothing is invented to fill the row.
 */
@Composable
private fun WeekStrip(
    weekStart: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
    workoutsByDay: Map<LocalDate, List<ExerciseWorkout>>,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val days = remember(weekStart) { (0..6).map { weekStart.plusDays(it.toLong()) } }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        days.forEach { day ->
            val dayWorkouts = workoutsByDay[day].orEmpty()
            val hasWorkout = dayWorkouts.isNotEmpty()
            val isSelected = day == selectedDate
            val isToday = day == today
            val isFuture = day.isAfter(today)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = !isFuture) { onDateSelected(day) }
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // The capsule: filled when the day has a workout.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(ShapeXxl)
                        .background(
                            when {
                                hasWorkout -> healthColors.accentTeal
                                isSelected -> healthColors.cardElevated
                                else -> Color.Transparent
                            }
                        )
                        .then(
                            if (!hasWorkout) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = if (isFuture) healthColors.cardBorder else healthColors.cardBorder,
                                    shape = ShapeXxl
                                )
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasWorkout) {
                        val primaryWorkout = dayWorkouts.maxByOrNull { it.cardioLoad } ?: dayWorkouts.first()
                        Icon(
                            imageVector = primaryWorkout.type.getIcon(),
                            contentDescription = primaryWorkout.type.label,
                            tint = healthColors.pillSelectedText,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = "${day.dayOfMonth}",
                            color = if (isFuture) healthColors.textMuted else healthColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }

                // Weekday letter under the capsule.
                Text(
                    text = day.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault())
                        .take(1)
                        .uppercase(Locale.getDefault()),
                    color = when {
                        isSelected -> healthColors.textPrimary
                        isToday -> healthColors.accentTeal
                        else -> healthColors.textMuted
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Seven-column month grid, Sunday-first.
 *
 * The weekday header row and every day-cell row use the *same*
 * `horizontalArrangement = Arrangement.spacedBy(4.dp)`, and every column carries
 * `weight(1f)`, so the header letters and the cells below them line up exactly.
 * Trailing empty days are added so the final week is complete, and the fifth and
 * sixth rows are only emitted when the month actually spans them.
 */
@Composable
private fun MonthGrid(
    monthAnchor: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
    workoutsByDay: Map<LocalDate, List<ExerciseWorkout>>,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current

    val monthStart = remember(monthAnchor) { monthAnchor.withDayOfMonth(1) }
    val daysInMonth = remember(monthStart) { monthStart.lengthOfMonth() }

    // Leading blanks bring the 1st onto its Sunday-first column.
    val leadingBlanks = remember(monthStart) { monthStart.dayOfWeek.value % 7 }
    val cellCount = remember(monthStart) {
        val total = leadingBlanks + daysInMonth
        val remainder = total % 7
        if (remainder == 0) total else total + (7 - remainder)
    }

    val weekdayLetters = remember {
        listOf("S", "M", "T", "W", "T", "F", "S")
    }

    // Only render the rows the month genuinely occupies.
    val weekCount = remember(cellCount) { cellCount / 7 }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Header row — identical spacing and weights to the day rows below.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            weekdayLetters.forEach { letter ->
                Text(
                    text = letter,
                    color = healthColors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        repeat(weekCount) { weekIndex ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(7) { dayInWeek ->
                    val cellIndex = weekIndex * 7 + dayInWeek
                    val dayOfMonth = cellIndex - leadingBlanks + 1
                    val inMonth = dayOfMonth in 1..daysInMonth
                    val day = if (inMonth) monthStart.withDayOfMonth(dayOfMonth) else null

                    if (day != null) {
                        val dayWorkouts = workoutsByDay[day].orEmpty()
                        val hasWorkout = dayWorkouts.isNotEmpty()
                        val primaryWorkout = dayWorkouts.maxByOrNull { it.cardioLoad } ?: dayWorkouts.firstOrNull()
                        val isSelected = day == selectedDate
                        val isToday = day == today
                        val isFuture = day.isAfter(today)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    when {
                                        hasWorkout -> healthColors.accentTeal
                                        isSelected -> healthColors.pillUnselectedBg
                                        isToday -> healthColors.cardElevated
                                        else -> healthColors.cardBackground
                                    }
                                )
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            2.dp,
                                            if (hasWorkout) healthColors.onAccentTeal else healthColors.accentTeal,
                                            MaterialTheme.shapes.small
                                        )
                                    } else Modifier
                                )
                                .then(
                                    if (!isFuture) {
                                        Modifier.clickable { onDateSelected(day) }
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasWorkout && primaryWorkout != null) {
                                Icon(
                                    imageVector = primaryWorkout.type.getIcon(),
                                    contentDescription = primaryWorkout.type.label,
                                    tint = healthColors.onAccentTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    text = "$dayOfMonth",
                                    color = when {
                                        isFuture -> healthColors.textMuted
                                        isSelected -> healthColors.accentTeal
                                        else -> healthColors.textPrimary
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isToday || isSelected) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Medium
                                    },
                                    maxLines = 1
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Headline totals for the visible period, driven by the active sub-filter. */
@Composable
private fun PeriodSummaryCard(
    summary: PeriodTotals,
    filter: ExerciseSubFilter,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current

    val primaryValue = when (filter) {
        ExerciseSubFilter.EXERCISE_DAYS -> "${summary.activeDayCount}"
        ExerciseSubFilter.DURATION -> formatDurationMinutes(summary.totalDurationMinutes)
        ExerciseSubFilter.DISTANCE -> if (summary.hasDistance) {
            String.format(Locale.getDefault(), "%.2f km", summary.totalDistanceKm)
        } else {
            "No distance"
        }
        ExerciseSubFilter.TIME -> {
            val first = summary.firstStart
            val last = summary.lastStart
            if (first != null && last != null) {
                val f = DateTimeFormatter.ofPattern("HH:mm")
                "${first.atZone(ZoneId.systemDefault()).format(f)} – " +
                    last.atZone(ZoneId.systemDefault()).format(f)
            } else {
                "No workouts"
            }
        }
    }

    val secondaryText = buildString {
        append(
            when (summary.workoutCount) {
                0 -> "0 workouts"
                1 -> "1 workout"
                else -> "${summary.workoutCount} workouts"
            }
        )
        if (summary.workoutCount > 0) {
            append(" • avg ${formatDurationMinutes(summary.averageDurationMinutes)} each")
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(healthColors.cardBackground)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = filter.label,
                color = healthColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = primaryValue,
                style = Typography.displaySmall,
                color = healthColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = secondaryText,
                color = healthColors.textMuted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(healthColors.accentTeal.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsRun,
                contentDescription = null,
                tint = healthColors.accentTeal,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** One row of the connected workout list. */
@Composable
private fun WorkoutRow(
    index: Int,
    count: Int,
    workout: ExerciseWorkout,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val zone = remember { ZoneId.systemDefault() }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm").withZone(zone) }

    val details = buildList {
        add(formatDurationMinutes(workout.durationMinutes))
        workout.distanceKm?.let { add(String.format(Locale.getDefault(), "%.2f km", it)) }
        workout.avgPaceMinKm?.let { add("pace $it /km") }
        workout.steps?.let { add("$it steps") }
    }.joinToString(" • ")

    ConnectedListItem(
        index = index,
        count = count,
        onClick = onClick,
        modifier = modifier,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(healthColors.accentTeal.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = workout.type.getIcon(),
                    contentDescription = null,
                    tint = healthColors.accentTeal,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        supportingContent = {
            Text(
                text = details,
                color = healthColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timeFormatter.format(workout.startTime),
                    color = healthColors.textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Cardio ${workout.cardioLoad}",
                    color = healthColors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    ) {
        Text(
            text = workout.title.ifBlank { workout.type.label },
            color = healthColors.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Shown when the selected day has no recorded workout. */
@Composable
private fun NoWorkoutsCard(
    dateLabel: String,
    isToday: Boolean,
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
                imageVector = Icons.Default.DirectionsRun,
                contentDescription = null,
                tint = healthColors.textMuted,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = "No workouts",
            style = Typography.titleMedium,
            color = healthColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (isToday) {
                "Nothing has been recorded for today yet."
            } else {
                "No workouts were recorded on $dateLabel."
            },
            color = healthColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
