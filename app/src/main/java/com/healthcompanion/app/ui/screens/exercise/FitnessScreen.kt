package com.healthcompanion.app.ui.screens.exercise

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthcompanion.app.model.ExerciseWorkout
import com.healthcompanion.app.ui.components.GoogleStyleBarChart
import com.healthcompanion.app.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

enum class ExerciseTimeRange {
    WEEK,
    MONTH
}

enum class ExerciseSubFilter(val label: String) {
    EXERCISE_DAYS("Days"),
    DURATION("Duration"),
    DISTANCE("Distance"),
    TIME("Time")
}

data class SportOption(
    val name: String,
    val icon: ImageVector
)

/**
 * Fitness screen.
 *
 * Structure:
 * 1. Top bar: Fitness, Profile Avatar
 * 2. Start a workout: Horizontal row with '+' and almost all sports (Run, Spinning, Strength, Walking, Cycling, Yoga, HIIT, etc.)
 * 3. Recent activities: 7-day week capsules (M T W T F S S) + Recent workout cards with "All activities >"
 * 4. Key metrics: Scrolling down to exercise-related metrics:
 *    - Cardio fitness (VO2 max)
 *    - Energy burned (2,075 cal with 7-day bar chart)
 *    - Cardio load (198 with trend chart)
 *    - Exercise days (5 of 5 exercise days)
 */
@Composable
fun FitnessScreen(
    workouts: List<ExerciseWorkout>,
    onWorkoutClick: (ExerciseWorkout) -> Unit,
    onNavigateToAllActivities: (() -> Unit)? = null,
    onNavigateToProfile: (() -> Unit)? = null,
    // Steps, Distance, Hourly Activity and Weekly Cardio are activity metrics;
    // they belong on the Fitness tab rather than buried under Health.
    onNavigateToSteps: (() -> Unit)? = null,
    onNavigateToDistance: (() -> Unit)? = null,
    onNavigateToHourlyActivity: (() -> Unit)? = null,
    onNavigateToWeeklyCardio: (() -> Unit)? = null,
    onStartWorkout: ((String) -> Unit)? = null,    /** �2.8: increments when the user re-taps this tab; scroll to index 0. */    scrollToTopSignal: Int = 0,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val today = remember { LocalDate.now() }
    val zone = remember { ZoneId.systemDefault() }

    // §2.8: retained across tab switches.
    val fitnessListState = rememberLazyListState()

    // �2.8: re-tap the active tab to return to the top.
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) fitnessListState.scrollToItem(0)
    }

    val weekStart = remember(today) { today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)) }
    val weekEnd = remember(weekStart) { weekStart.plusDays(6) }

    // Filter workouts for current week
    val weekWorkouts = remember(workouts, weekStart, weekEnd) {
        workouts.filter { wo ->
            val wDate = wo.startTime.atZone(zone).toLocalDate()
            !wDate.isBefore(weekStart) && !wDate.isAfter(weekEnd)
        }.sortedByDescending { it.startTime }
    }

    // 7 days of the week starting Sunday or Monday
    val weekDays = remember(weekStart) {
        (0..6).map { weekStart.plusDays(it.toLong()) }
    }

    // Check which days have workouts
    val daysWithWorkouts = remember(weekDays, workouts) {
        weekDays.map { d ->
            val hasWorkout = workouts.any { it.startTime.atZone(zone).toLocalDate() == d }
            d to hasWorkout
        }
    }

    val activeDaysCount = daysWithWorkouts.count { it.second }
    val recentWorkouts = remember(workouts) { workouts.take(3) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm").withZone(zone) }

    // Broad sports list (almost all sports available)
    val sportsList = remember {
        listOf(
            SportOption("Run", Icons.Default.DirectionsRun),
            SportOption("Spinning", Icons.Default.DirectionsBike),
            SportOption("Strength training", Icons.Default.FitnessCenter),
            SportOption("Walking", Icons.Default.DirectionsWalk),
            SportOption("Cycling", Icons.Default.DirectionsBike),
            SportOption("Yoga", Icons.Default.SelfImprovement),
            SportOption("HIIT", Icons.Default.LocalFireDepartment),
            SportOption("Swimming", Icons.Default.Pool),
            SportOption("Rowing", Icons.Default.Kayaking),
            SportOption("Pilates", Icons.Default.SportsGymnastics),
            SportOption("Hiking", Icons.Default.Hiking),
            SportOption("Boxing", Icons.Default.SportsMma),
            SportOption("Tennis", Icons.Default.SportsTennis),
            SportOption("Basketball", Icons.Default.SportsBasketball),
            SportOption("Badminton", Icons.Default.SportsHandball),
            SportOption("Elliptical", Icons.Default.FitnessCenter),
            SportOption("Jump rope", Icons.Default.Timer),
            SportOption("Dance", Icons.Default.MusicNote),
            SportOption("Football", Icons.Default.SportsSoccer),
            SportOption("Treadmill", Icons.Default.DirectionsRun),
            SportOption("Outdoor cycle", Icons.Default.DirectionsBike)
        )
    }

    LazyColumn(
        state = fitnessListState,
        modifier = modifier
            .fillMaxSize()
            .background(healthColors.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Bar:
        // Fitness            [Avatar]
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 10.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fitness",
                    style = Typography.titleLarge,
                    color = healthColors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                IconButton(
                    onClick = { onNavigateToProfile?.invoke() },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(healthColors.cardElevated)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Profile",
                        tint = healthColors.accentTeal,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 2. "Log a workout" Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Log a workout",
                    style = Typography.titleMedium,
                    color = healthColors.textPrimary,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // '+' Add / Custom Button
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(healthColors.cardElevated)
                            .clickable { onStartWorkout?.invoke("Custom") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add workout",
                            tint = healthColors.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Sports Chips
                    sportsList.forEach { sport ->
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = healthColors.cardBackground,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraLarge)
                                .clickable { onStartWorkout?.invoke(sport.name) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = sport.icon,
                                    contentDescription = sport.name,
                                    tint = healthColors.accentTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = sport.name,
                                    color = healthColors.textPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. "Recent activities" Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent activities",
                        style = Typography.titleMedium,
                        color = healthColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onNavigateToAllActivities?.invoke() }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "All activities",
                            color = healthColors.accentTeal,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = healthColors.accentTeal,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // 7-day week capsules (M T W T F S S)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    daysWithWorkouts.forEach { (date, hasWorkout) ->
                        val dayLetter = date.dayOfWeek.name.take(1)
                        val isToday = (date == today)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(42.dp)
                                    .height(48.dp)
                                    .clip(MaterialTheme.shapes.extraLarge)
                                    .background(
                                        if (hasWorkout) healthColors.accentTeal.copy(alpha = 0.22f)
                                        else if (isToday) healthColors.cardElevated
                                        else healthColors.cardBackground
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (hasWorkout) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsRun,
                                        contentDescription = null,
                                        tint = healthColors.accentTeal,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isToday) healthColors.accentTeal
                                                else healthColors.textMuted.copy(alpha = 0.3f)
                                            )
                                    )
                                }
                            }
                            Text(
                                text = dayLetter,
                                color = if (isToday) healthColors.accentTeal else healthColors.textSecondary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Recent Workout Cards
                if (recentWorkouts.isNotEmpty()) {
                    recentWorkouts.forEach { workout ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraLarge)
                                .background(healthColors.cardBackground)
                                .clickable { onWorkoutClick(workout) }
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(healthColors.accentTeal.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = workout.type.getIcon(),
                                            contentDescription = workout.title,
                                            tint = healthColors.accentTeal,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            text = workout.title,
                                            color = healthColors.textPrimary,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        val startStr = timeFormatter.format(workout.startTime)
                                        val distStr = workout.distanceKm?.let { " • $it km" } ?: ""
                                        Text(
                                            text = "$startStr • ${workout.durationMinutes} min$distStr",
                                            color = healthColors.textSecondary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${workout.cardioLoad}",
                                        color = healthColors.textPrimary,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Cardio load",
                                        color = healthColors.textMuted,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(healthColors.cardBackground)
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "No recent workouts recorded this week. Start a workout above!",
                            color = healthColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Activity metrics shortcuts. Steps, Distance, Hourly Activity and
        // Weekly Cardio are exercise metrics but were only reachable from the
        // Health tab, leaving the Fitness tab with only two destinations.
        if (onNavigateToSteps != null || onNavigateToDistance != null ||
            onNavigateToHourlyActivity != null || onNavigateToWeeklyCardio != null
        ) {
            item {
                val shortcuts = listOfNotNull(
                    onNavigateToSteps?.let { "Steps" to it },
                    onNavigateToDistance?.let { "Distance" to it },
                    onNavigateToHourlyActivity?.let { "Hourly activity" to it },
                    onNavigateToWeeklyCardio?.let { "Weekly cardio" to it }
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Text(
                        text = "Activity metrics",
                        style = Typography.titleMedium,
                        color = healthColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    shortcuts.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.m)
                        ) {
                            rowItems.forEach { (label, action) ->
                                Surface(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = healthColors.cardBackground,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(MaterialTheme.shapes.extraLarge)
                                        .clickable { action() }
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = healthColors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(
                                            horizontal = Spacing.l,
                                            vertical = Spacing.m
                                        )
                                    )
                                }
                            }
                            // Keep the last row's two columns aligned.
                            if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // 4. "Key metrics" Section (scrolling down to exercise-related metrics)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Key metrics",
                        style = Typography.titleMedium,
                        color = healthColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onNavigateToAllActivities?.invoke() }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "All metrics",
                            color = healthColors.accentTeal,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = healthColors.accentTeal,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Metric Card 1: Cardio fitness
                val estimatedVo2Max = remember(workouts) {
                    val runWorkouts = workouts.filter { it.type == com.healthcompanion.app.model.ExerciseType.RUN && it.distanceKm != null && it.durationMinutes > 0 }
                    if (runWorkouts.isNotEmpty()) {
                        // Cooper 12-minute run formula estimation or average run speed estimation
                        val fastestRun = runWorkouts.maxByOrNull { (it.distanceKm ?: 0.0) / it.durationMinutes }
                        val speedKmH = fastestRun?.avgSpeedKmH ?: 10.0
                        (speedKmH * 3.5 + 10.0).coerceIn(30.0, 60.0)
                    } else null
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(healthColors.cardBackground)
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cardio fitness",
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (estimatedVo2Max != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .background(healthColors.accentTeal.copy(alpha = 0.18f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = if (estimatedVo2Max >= 45) "High" else "Good",
                                        color = healthColors.accentTeal,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        if (estimatedVo2Max != null) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = String.format(Locale.getDefault(), "%.1f", estimatedVo2Max),
                                    style = Typography.displaySmall,
                                    color = healthColors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "VO2 max",
                                    color = healthColors.textSecondary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            Text(
                                text = "Your cardio fitness is estimated based on your outdoor run activities.",
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(
                                text = "No VO2 max recorded",
                                style = Typography.titleMedium,
                                color = healthColors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Complete an outdoor run or brisk walk with GPS to measure your cardio fitness level.",
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Metric Card 2: Energy burned (calculated dynamically from workouts across the 7 days)
                val dailyCalorieValues = remember(weekDays, workouts, zone) {
                    weekDays.map { d ->
                        workouts.filter { it.startTime.atZone(zone).toLocalDate() == d }
                            .sumOf { it.caloriesBurnedKcal }.toFloat()
                    }
                }
                val todayCaloriesBurned = remember(workouts, today, zone) {
                    workouts.filter { it.startTime.atZone(zone).toLocalDate() == today }
                        .sumOf { it.caloriesBurnedKcal }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(healthColors.cardBackground)
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Energy burned",
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (todayCaloriesBurned >= 500) {
                                Box(
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .background(healthColors.accentTeal.copy(alpha = 0.18f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "Target met",
                                        color = healthColors.accentTeal,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = String.format(Locale.getDefault(), "%,d", todayCaloriesBurned),
                                style = Typography.displaySmall,
                                color = healthColors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "cal active",
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        Text(
                            text = if (todayCaloriesBurned > 0) "Today • From logged exercise workouts" else "Today • No workouts recorded yet today",
                            color = healthColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        // 7-day Bar chart with real daily calorie values
                        GoogleStyleBarChart(
                            values = dailyCalorieValues,
                            goalValue = 500f,
                            goalLabel = "Target 500 cal",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                        )
                    }
                }

                // Metric Card 3: Cardio load, derived only from real workouts.
                // The step contribution previously came from
                // `SampleHealthDataProvider.getStepsForDate`, inventing a day's
                // activity, and the total was floored at 14 minutes so a week
                // with no activity still looked active.
                val dailyCardioLoads = remember(weekDays, workouts, zone) {
                    weekDays.map { d ->
                        val dayWorkouts = workouts.filter { it.startTime.atZone(zone).toLocalDate() == d }
                        dayWorkouts.sumOf { wo ->
                            wo.cardioLoad.coerceAtLeast(
                                ((wo.durationMinutes * 0.7) + (wo.caloriesBurnedKcal / 25.0)).toInt()
                            )
                        }.toFloat().coerceAtLeast(0f)
                    }
                }
                val totalCardioLoad = remember(dailyCardioLoads) {
                    dailyCardioLoads.sum().roundToInt().coerceAtLeast(0)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(healthColors.cardBackground)
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cardio load",
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (totalCardioLoad > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .background(healthColors.accentTeal.copy(alpha = 0.18f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                    Text(
                                        text = if (totalCardioLoad in 15..50) "Optimal" else if (totalCardioLoad > 50) "High" else "Building",
                                        color = healthColors.accentTeal,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Text(
                            text = "$totalCardioLoad",
                            style = Typography.displaySmall,
                            color = healthColors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = if (totalCardioLoad > 0) {
                                "Weekly cardio load • Based on cardiovascular intensity of your sessions and steps"
                            } else {
                                "No cardio load recorded this week"
                            },
                            color = healthColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        // Cardio load mini curve with weekday labels
                        if (dailyCardioLoads.any { it > 0f }) {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                CardioLoadTrendChart(
                                    values = dailyCardioLoads,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    weekDays.forEach { d ->
                                        Text(
                                            text = d.dayOfWeek.name.take(1),
                                            color = healthColors.textMuted,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Metric Card 4: Exercise days
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(healthColors.cardBackground)
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Exercise days",
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Box(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .background(healthColors.accentTeal.copy(alpha = 0.18f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Goal met",
                                    color = healthColors.accentTeal,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$activeDaysCount",
                                style = Typography.displaySmall,
                                color = healthColors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "of 5 exercise days",
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        Text(
                            text = "You reached your weekly exercise goal with $activeDaysCount days of activity.",
                            color = healthColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

/** Cardio load trend line */
@Composable
fun CardioLoadTrendChart(
    values: List<Float>,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val min = (values.minOrNull() ?: 100f) * 0.9f
        val max = (values.maxOrNull() ?: 200f) * 1.1f
        val range = (max - min).coerceAtLeast(1f)
        val stepX = size.width / (values.size - 1)

        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - min) / range) * (size.height - 10.dp.toPx()) - 5.dp.toPx()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = healthColors.accentTeal,
            style = Stroke(width = 3.dp.toPx())
        )

        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - min) / range) * (size.height - 10.dp.toPx()) - 5.dp.toPx()
            drawCircle(
                color = healthColors.accentTeal,
                radius = 3.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}
