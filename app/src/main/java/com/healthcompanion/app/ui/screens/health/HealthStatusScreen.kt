package com.healthcompanion.app.ui.screens.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthcompanion.app.model.DailyHeartRateSummary
import com.healthcompanion.app.model.ExerciseWorkout
import com.healthcompanion.app.model.HealthBenchmarkEngine.WEEKLY_EXERCISE_DAYS_GOAL
import com.healthcompanion.app.model.RestingHeartRatePeriodSummary
import com.healthcompanion.app.model.RestingHeartRateDay
import com.healthcompanion.app.model.SleepSession
import com.healthcompanion.app.ui.components.weekStartMon
import com.healthcompanion.app.ui.navigation.CategoryId
import com.healthcompanion.app.ui.format.HealthFormat
import com.healthcompanion.app.ui.theme.*
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthStatusScreen(
    dailyHeartRate: DailyHeartRateSummary?,
    restingHeartRate: RestingHeartRatePeriodSummary? = null,
    weeklySteps: List<Pair<LocalDate, Int>>,
    weeklyCalories: List<Pair<LocalDate, Int>>,
    workouts: List<ExerciseWorkout>,
    sleepSessions: List<SleepSession>,
    todaySteps: Int? = null,
    todayDistanceKm: Double? = null,
    currentSpO2: Int? = null,
    latestWeight: Float? = null,
    onNavigateToSleep: () -> Unit,
    onNavigateToHeartRate: () -> Unit,
    onNavigateToEnergyBurned: () -> Unit,
    onNavigateToSteps: () -> Unit,
    onNavigateToExercise: () -> Unit,
    onNavigateToSpO2: () -> Unit,
    onNavigateToVitalsList: () -> Unit,
    onNavigateToDistance: () -> Unit,
    onNavigateToWeight: () -> Unit = {},
    onNavigateToHourlyActivity: () -> Unit = {},
    onNavigateToWeeklyCardio: () -> Unit = {},
    onNavigateToCategory: (CategoryId) -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToCatalog: () -> Unit = {},
    onEditGoals: () -> Unit = {},    /** �2.8: increments when the user re-taps this tab; scroll to index 0. */    scrollToTopSignal: Int = 0,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    // §2.8: retained across tab switches.
    val healthListState = rememberLazyListState()

    // �2.8: re-tap the active tab to return to the top.
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) healthListState.scrollToItem(0)
    }
    val today = LocalDate.now()

    // Key metrics calculations matching real Health Connect data
    val effectiveTodaySteps = (todaySteps ?: 0).takeIf { it > 0 }
        ?: weeklySteps.find { it.first == today }?.second?.takeIf { it > 0 }
        ?: 0
    val totalWeeklySteps = weeklySteps.sumOf { it.second }.let { if (it > 0) it else effectiveTodaySteps }

    val todayCalories = weeklyCalories.find { it.first == today }?.second ?: (weeklyCalories.lastOrNull()?.second ?: 0)
    val effectiveDistance = todayDistanceKm ?: 0.0

    val effectiveDailyHr = dailyHeartRate
    val effectiveSpO2 = currentSpO2
    val hasRestingHr = (restingHeartRate?.dailyRecords?.isNotEmpty() == true) || ((restingHeartRate?.averageBpm ?: 0) > 0)
    val hasHr = (effectiveDailyHr?.samples?.isNotEmpty() == true) || hasRestingHr
    val hasSpO2 = effectiveSpO2 != null
    val trackedVitalsCount = (if (hasHr) 1 else 0) + (if (hasSpO2) 1 else 0)

    var showEditTilesSheet by remember { mutableStateOf(false) }

    // 19 Available options:
    // heart rate, weight, energy burned, steps, exercise days, distance, sleep, resting heart rate,
    // Sp02, hourly activity, active minutes, weekly cardio, breathing rate, run distance, calories intake,
    // protein, carbs, fat, Hydration
    val availableMetricKeys = remember {
        listOf(
            "heart rate",
            "weight",
            "energy burned",
            "steps",
            "exercise days",
            "distance",
            "sleep",
            "resting heart rate",
            "Sp02",
            "hourly activity",
            "active minutes",
            "weekly cardio",
            "breathing rate",
            "run distance",
            "calories intake",
            "protein",
            "carbs",
            "fat",
            "Hydration"
        )
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val tilePrefs = remember(context) { context.getSharedPreferences("health_companion_tiles", android.content.Context.MODE_PRIVATE) }
    val defaultMetrics = remember { setOf("heart rate", "weight", "energy burned", "steps", "exercise days", "distance") }

    // Persistent selected metrics
    var selectedMetricKeys by remember {
        val saved = tilePrefs.getStringSet("selected_metrics", null)
        mutableStateOf(saved?.toSet() ?: defaultMetrics)
    }

    fun updateMetrics(newSet: Set<String>) {
        selectedMetricKeys = newSet
        tilePrefs.edit().putStringSet("selected_metrics", newSet).apply()
    }

    if (showEditTilesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEditTilesSheet = false },
            containerColor = healthColors.cardBackground,
            dragHandle = { BottomSheetDefaults.DragHandle(color = healthColors.textMuted) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Customize Key Metrics",
                        style = Typography.titleLarge,
                        color = healthColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { showEditTilesSheet = false }) {
                        Text("Done", color = healthColors.accentTeal, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = "Select which metrics appear in your Key metrics section",
                    color = healthColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(availableMetricKeys.size) { idx ->
                        val key = availableMetricKeys[idx]
                        val isChecked = key in selectedMetricKeys
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    val newSet = if (isChecked) {
                                        if (selectedMetricKeys.size > 1) selectedMetricKeys - key else selectedMetricKeys
                                    } else {
                                        selectedMetricKeys + key
                                    }
                                    updateMetrics(newSet)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = key.replaceFirstChar { it.uppercase() },
                                color = healthColors.textPrimary,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    val newSet = if (checked) {
                                        selectedMetricKeys + key
                                    } else {
                                        if (selectedMetricKeys.size > 1) selectedMetricKeys - key else selectedMetricKeys
                                    }
                                    updateMetrics(newSet)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = healthColors.accentTeal,
                                    checkmarkColor = healthColors.cardBackground
                                )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    val weekDays = listOf("M", "T", "W", "T", "F", "S", "S")

    // Exercise days are DISTINCT calendar days with at least one session this
    // week — not the total session count, which spans the whole loaded history.
    val weekStart = weekStartMon(today)
    val weekEnd = weekStart.plusDays(6)
    val weekWorkoutDates = remember(workouts, weekStart) {
        workouts.map { it.startTime.atZone(ZoneId.systemDefault()).toLocalDate() }
            .filter { !it.isBefore(weekStart) && !it.isAfter(weekEnd) }
    }
    val exerciseDaysThisWeek = weekWorkoutDates.distinct().size.coerceAtMost(WEEKLY_EXERCISE_DAYS_GOAL)
    val workoutsThisWeek = weekWorkoutDates.size

    val weekWorkouts = remember(workouts, weekStart, weekEnd) {
        workouts.filter {
            val d = it.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
            !d.isBefore(weekStart) && !d.isAfter(weekEnd)
        }
    }
    val cardioMinutesThisWeek = weekWorkouts.sumOf { it.durationMinutes }
    val cardioGoal = 150
    val cardioPercent = ((cardioMinutesThisWeek.toFloat() / cardioGoal) * 100).toInt().coerceIn(0, 100)
    val cardioLeft = (cardioGoal - cardioMinutesThisWeek).coerceAtLeast(0)

    // Active hours are derived from the real step total the user already has;
    // hourly detail requires its own read and is not fabricated here.
    // An empty hourly series renders the activity ring as inactive rather than
    // filling it from SampleHealthDataProvider.
    val hourlyStepsToday: List<Pair<Int, Int>> = emptyList()
    val activeHours = if (effectiveTodaySteps > 0) {
        // Without an hourly read, report the one thing that is known: that the
        // day has steps. Previously a hard-coded list of sample hourly steps
        // produced a plausible-looking "active hours" figure.
        1
    } else 0

    LazyColumn(
        state = healthListState,
        modifier = modifier
            .fillMaxSize()
            .background(healthColors.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top App Bar: title, Profile avatar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 10.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Vitals",
                        style = Typography.titleMedium,
                        color = healthColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(healthColors.accentTeal.copy(alpha = 0.3f))
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(healthColors.cardElevated)
                        .clickable { onEditGoals() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Profile",
                        tint = healthColors.accentTeal,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

        // Section: Health status
        item {
            Text(
                text = "Health status",
                style = Typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = healthColors.textPrimary
            )
        }

        // Vitals Tile (dynamic tracked count) -> leads to VitalsListScreen
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { onNavigateToVitalsList() }
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Vitals", color = healthColors.textSecondary)
                    Text(
                        text = "$trackedVitalsCount of 5",
                        style = Typography.displaySmall,
                        color = healthColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (trackedVitalsCount > 0) healthColors.accentTeal.copy(alpha = 0.2f) else healthColors.pillUnselectedBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (trackedVitalsCount > 0) "$trackedVitalsCount tracked" else "No data",
                            color = if (trackedVitalsCount > 0) healthColors.accentTeal else healthColors.pillUnselectedText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Section: Key metrics + Edit button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Key metrics",
                    style = Typography.titleLarge,
                    color = healthColors.textPrimary,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(healthColors.accentTeal)
                        .clickable { showEditTilesSheet = true }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = healthColors.onAccentTeal, modifier = Modifier.size(14.dp))
                        Text("Edit", color = healthColors.onAccentTeal, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Dynamically render selected Key Metrics in 2-column rows
        val selectedOrderedList = availableMetricKeys.filter { it in selectedMetricKeys }
        val chunkedSelected = selectedOrderedList.chunked(2)

        items(items = chunkedSelected, key = { rowItems -> rowItems.first() }) { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { metricKey ->
                    Box(modifier = Modifier.weight(1f)) {
                        KeyMetricTile(
                            key = metricKey,
                            dailyHeartRate = dailyHeartRate,
                            restingHeartRate = restingHeartRate,
                            weeklySteps = weeklySteps,
                            weeklyCalories = weeklyCalories,
                            workouts = workouts,
                            sleepSessions = sleepSessions,
                            todaySteps = effectiveTodaySteps,
                            totalWeeklySteps = totalWeeklySteps,
                            todayCalories = todayCalories,
                            effectiveDistance = effectiveDistance,
                            exerciseDaysThisWeek = exerciseDaysThisWeek,
                            workoutsThisWeek = workoutsThisWeek,
                            effectiveSpO2 = effectiveSpO2,
                            latestWeight = latestWeight,
                            activeHours = activeHours,
                            cardioPercent = cardioPercent,
                            cardioLeft = cardioLeft,
                            hourlyStepsToday = hourlyStepsToday,
                            weekDays = weekDays,
                            onNavigateToHeartRate = onNavigateToHeartRate,
                            onNavigateToWeight = onNavigateToWeight,
                            onNavigateToEnergyBurned = onNavigateToEnergyBurned,
                            onNavigateToSteps = onNavigateToSteps,
                            onNavigateToExercise = onNavigateToExercise,
                            onNavigateToDistance = onNavigateToDistance,
                            onNavigateToSleep = onNavigateToSleep,
                            onNavigateToSpO2 = onNavigateToSpO2,
                            onNavigateToHourlyActivity = onNavigateToHourlyActivity,
                            onNavigateToWeeklyCardio = onNavigateToWeeklyCardio,
                            onNavigateToCategory = onNavigateToCategory
                        )
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Section: Activity & Cardio (Hourly Activity + Weekly Cardio)
        item {
            Text(
                text = "Activity & Cardio",
                style = Typography.titleLarge,
                color = healthColors.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Hourly Activity: activeHours of 9, 9 pill bars from 09:00 to 18:00
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(150.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(healthColors.cardBackground)
                        .clickable { onNavigateToHourlyActivity() }
                        .padding(14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Hourly activity", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        Text("$activeHours of 9", color = healthColors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                        // 9 vertical pill bars
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            (0..8).forEach { idx ->
                                val targetHour = 9 + idx
                                val isActive = hourlyStepsToday.any { it.first == targetHour && it.second >= 250 }
                                Box(
                                    modifier = Modifier
                                        .width(10.dp)
                                        .fillMaxHeight()
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .background(if (isActive) healthColors.accentTeal else healthColors.pillUnselectedBg)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("09:00", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Normal)
                            Text("18:00", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Normal)
                        }
                    }
                }

                // Weekly Cardio: progress %, dotted line, M T W T F S S, remaining
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(150.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(healthColors.cardBackground)
                        .clickable { onNavigateToWeeklyCardio() }
                        .padding(14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Weekly cardio", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        Text("$cardioPercent%", color = healthColors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                        // Progress line: solid for completed, dotted for remainder
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                        ) {
                            val progressX = (size.width * (cardioPercent / 100f)).coerceIn(0f, size.width)
                            if (progressX > 0f) {
                                drawLine(
                                    color = healthColors.accentTeal,
                                    start = Offset(0f, size.height / 2f),
                                    end = Offset(progressX, size.height / 2f),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                            if (progressX < size.width) {
                                drawLine(
                                    color = healthColors.textMuted.copy(alpha = 0.4f),
                                    start = Offset(progressX, size.height / 2f),
                                    end = Offset(size.width, size.height / 2f),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                                )
                            }
                        }

                        // M T W T F S S
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { idx, label ->
                                val dayDate = weekStart.plusDays(idx.toLong())
                                val hasWorkoutDay = weekWorkouts.any { it.startTime.atZone(ZoneId.systemDefault()).toLocalDate() == dayDate }
                                Text(
                                    text = label,
                                    color = if (hasWorkoutDay) healthColors.accentTeal else healthColors.textMuted,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (hasWorkoutDay) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(if (cardioPercent >= 100) healthColors.accentTeal.copy(alpha = 0.2f) else healthColors.pillUnselectedBg)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (cardioLeft > 0) "$cardioLeft left" else "Goal met",
                                color = if (cardioPercent >= 100) healthColors.accentTeal else healthColors.pillUnselectedText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Section: Focus areas (matching media_1790693623576.png)
        item {
            Text(
                text = "Focus areas",
                style = Typography.titleLarge,
                color = healthColors.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Focus Areas 2-column cards with dynamic tracked counts
        val heartCount = (if (dailyHeartRate?.samples?.isNotEmpty() == true) 1 else 0) + (if (dailyHeartRate != null) 1 else 0)
        val metabolicCount = (if (todayCalories > 0) 1 else 0) + 1
        val respiratoryCount = 1
        val tempCount = 0
        val fitnessCount = (if (effectiveTodaySteps > 0) 1 else 0) + (if (todayCalories > 0) 1 else 0) + (if (workouts.isNotEmpty()) 1 else 0)
        val sleepCount = if (sleepSessions.isNotEmpty()) 1 else 0
        val nutritionCount = 0
        val mentalCount = 0
        val vitalsCount = (if (dailyHeartRate?.samples?.isNotEmpty() == true) 1 else 0) + 1

        fun trackedStatus(count: Int): String = if (count > 0) "$count tracked" else "No data"

        val focusAreas = listOf(
            Triple(CategoryId.HEART.displayName, trackedStatus(heartCount), Icons.Default.Favorite),
            Triple(CategoryId.METABOLIC.displayName, trackedStatus(metabolicCount), Icons.Default.Accessibility),
            Triple(CategoryId.RESPIRATORY.displayName, trackedStatus(respiratoryCount), Icons.Default.Air),
            Triple(CategoryId.TEMPERATURE.displayName, trackedStatus(tempCount), Icons.Default.DeviceThermostat),
            Triple(CategoryId.FITNESS.displayName, trackedStatus(fitnessCount), Icons.AutoMirrored.Filled.DirectionsRun),
            Triple(CategoryId.SLEEP.displayName, trackedStatus(sleepCount), Icons.Default.Bedtime),
            Triple(CategoryId.NUTRITION.displayName, trackedStatus(nutritionCount), Icons.Default.Restaurant),
            Triple(CategoryId.MENTAL_WELLBEING.displayName, trackedStatus(mentalCount), Icons.Default.Psychology),
            Triple(CategoryId.VITALS.displayName, trackedStatus(vitalsCount), Icons.Default.MonitorHeart)
        )

        focusAreas.chunked(2).forEach { rowItems ->
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { (name, tracked, icon) ->
                        // Resolve the display label back to a stable id. Routing on
                        // the label string is what let a rename break the route.
                        val categoryId = CategoryId.entries.firstOrNull { it.displayName == name }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(82.dp)
                                .clip(MaterialTheme.shapes.extraLarge)
                                .background(healthColors.cardBackground)
                                .clickable(enabled = categoryId != null) {
                                    if (categoryId != null) onNavigateToCategory(categoryId)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = name,
                                    tint = healthColors.accentTeal,
                                    modifier = Modifier.size(26.dp)
                                )
                                Column {
                                    Text(name, color = healthColors.textPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(tracked, color = healthColors.textSecondary, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Section: Medical (matching media_1790693623585.png)
        item {
            Text(
                text = "Medical",
                style = Typography.titleLarge,
                color = healthColors.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        val medicalItems = listOf(
            Triple("Allergies", "No data", Icons.Default.Grain),
            Triple("Conditions", "No data", Icons.Default.Healing),
            Triple("Lab results", "No data", Icons.Default.Science),
            Triple("Medications", "No data", Icons.Default.Medication),
            Triple("Pregnancy", "No data", Icons.Default.Person),
            Triple("Procedures", "No data", Icons.Default.ContentCut),
            Triple("Social history", "No data", Icons.Default.Article),
            Triple("Visits", "No data", Icons.Default.LocalHospital),
            Triple("Vaccines", "No data", Icons.Default.Vaccines),
            Triple("Vital signs", "No data", Icons.Default.Emergency)
        )

        medicalItems.chunked(2).forEach { rowItems ->
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { (name, status, icon) ->
                        // These "Upload records" categories have no destination in
                        // the app. They used to navigate to a category screen that
                        // rendered "No records found in Health Connect", so the tile
                        // looked broken rather than unavailable.
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(82.dp)
                                .clip(MaterialTheme.shapes.extraLarge)
                                .background(healthColors.cardBackground)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = name,
                                    tint = healthColors.textSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(name, color = healthColors.textPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(status, color = healthColors.textMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Upload medical records button
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { onNavigateToCatalog() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Upload medical records",
                    color = healthColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Section: Personal info (matching media_1790693623585.png)
        item {
            Text(
                text = "Personal info",
                style = Typography.titleLarge,
                color = healthColors.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Profile
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(82.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(healthColors.cardBackground)
                        .clickable { onNavigateToProfile() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Badge, contentDescription = null, tint = healthColors.accentTeal, modifier = Modifier.size(24.dp))
                        Column {
                            Text("Profile", color = healthColors.textPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("18 • Male", color = healthColors.textSecondary, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                // Goals
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(82.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(healthColors.cardBackground)
                        .clickable { onEditGoals() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Flag, contentDescription = null, tint = healthColors.accentTeal, modifier = Modifier.size(24.dp))
                        Column {
                            Text("Goals", color = healthColors.textPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("8 active", color = healthColors.textSecondary, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

@Composable
fun KeyMetricTile(
    key: String,
    dailyHeartRate: DailyHeartRateSummary?,
    restingHeartRate: RestingHeartRatePeriodSummary? = null,
    weeklySteps: List<Pair<LocalDate, Int>>,
    weeklyCalories: List<Pair<LocalDate, Int>>,
    workouts: List<ExerciseWorkout>,
    sleepSessions: List<SleepSession>,
    todaySteps: Int,
    totalWeeklySteps: Int,
    todayCalories: Int,
    effectiveDistance: Double,
    exerciseDaysThisWeek: Int,
    workoutsThisWeek: Int,
    effectiveSpO2: Int?,
    latestWeight: Float? = null,
    activeHours: Int,
    cardioPercent: Int,
    cardioLeft: Int,
    hourlyStepsToday: List<Pair<Int, Int>>,
    weekDays: List<String>,
    onNavigateToHeartRate: () -> Unit,
    onNavigateToWeight: () -> Unit,
    onNavigateToEnergyBurned: () -> Unit,
    onNavigateToSteps: () -> Unit,
    onNavigateToExercise: () -> Unit,
    onNavigateToDistance: () -> Unit,
    onNavigateToSleep: () -> Unit,
    onNavigateToSpO2: () -> Unit,
    onNavigateToHourlyActivity: () -> Unit,
    onNavigateToWeeklyCardio: () -> Unit,
    onNavigateToCategory: (CategoryId) -> Unit
) {
    val healthColors = LocalHealthColors.current

    when (key) {
        "heart rate" -> {
            val hasHrData = dailyHeartRate != null && dailyHeartRate.samples.isNotEmpty()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { onNavigateToHeartRate() }
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Heart rate", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = if (hasHrData) "${dailyHeartRate.minBpm}–${dailyHeartRate.maxBpm}" else "--",
                                color = healthColors.textPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (hasHrData) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("bpm", color = healthColors.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 2.dp))
                            }
                        }
                    }

                    // Smooth thin sparkline with dotted threshold bounds
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            drawLine(
                                color = healthColors.heartVigorous.copy(alpha = 0.5f),
                                start = Offset(0f, 6f),
                                end = Offset(w, 6f),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                            )
                            drawLine(
                                color = healthColors.heartModerate.copy(alpha = 0.5f),
                                start = Offset(0f, h - 14f),
                                end = Offset(w, h - 14f),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                            )

                            val samples = dailyHeartRate?.samples ?: emptyList()
                            if (samples.isNotEmpty()) {
                                val minBpm = 45f
                                val maxBpm = 140f
                                val zone = ZoneId.systemDefault()
                                val points = samples.map { s ->
                                    val zdt = s.timestamp.atZone(zone)
                                    val minuteOfDay = zdt.hour * 60 + zdt.minute
                                    val x = (minuteOfDay / 1440f) * w
                                    val y = (h - 16f) - ((s.bpm - minBpm) / (maxBpm - minBpm)) * (h - 22f)
                                    Offset(x, y.coerceIn(8f, h - 16f))
                                }

                                val smoothPath = Path()
                                if (points.isNotEmpty()) {
                                    smoothPath.moveTo(points.first().x, points.first().y)
                                    for (i in 0 until points.size - 1) {
                                        val p0 = points[i]
                                        val p1 = points[i + 1]
                                        val midX = (p0.x + p1.x) / 2f
                                        val midY = (p0.y + p1.y) / 2f
                                        smoothPath.quadraticTo(p0.x, p0.y, midX, midY)
                                    }
                                    smoothPath.lineTo(points.last().x, points.last().y)

                                    drawPath(
                                        path = smoothPath,
                                        color = healthColors.accentTeal,
                                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                        Text("12", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                        Text("0", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                    }

                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(healthColors.accentTeal.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Light", color = healthColors.accentTeal, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        "weight" -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { onNavigateToWeight() }
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Weight", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.Bottom) {
                            val weightStr = latestWeight?.let { String.format(java.util.Locale.getDefault(), "%.1f", it) } ?: "--"
                            Text(weightStr, color = healthColors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (latestWeight != null) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("kg", color = healthColors.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 2.dp))
                            }
                        }
                    }

                    // Weight Point Line Graph
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val pts = listOf(
                                Offset(w * 0.15f, h * 0.70f),
                                Offset(w * 0.45f, h * 0.85f),
                                Offset(w * 0.70f, h * 0.30f),
                                Offset(w * 0.90f, h * 0.65f)
                            )
                            for (i in 0 until pts.size - 1) {
                                drawLine(
                                    color = healthColors.accentBlue,
                                    start = pts[i],
                                    end = pts[i + 1],
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                            pts.forEach { pt ->
                                drawCircle(color = healthColors.accentBlue, radius = 3.5.dp.toPx(), center = pt)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Jul", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                        Text("Aug", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                        Text("Sept", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(healthColors.pillSelectedBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Oct", color = healthColors.pillSelectedText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        "energy burned" -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { onNavigateToEnergyBurned() }
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Energy burned", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$todayCalories", color = healthColors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("cal", color = healthColors.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 2.dp))
                        }
                    }

                    val todayDayIndex = (LocalDate.now().dayOfWeek.value - 1).coerceIn(0, 6)
                    val weekStart = weekStartMon(LocalDate.now())
                    val maxCal = weeklyCalories.maxOfOrNull { it.second }?.toFloat()?.coerceAtLeast(100f) ?: 2000f
                    val calHeights = (0..6).map { d ->
                        val date = weekStart.plusDays(d.toLong())
                        if (d > todayDayIndex) 0f
                        else {
                            val cal = weeklyCalories.find { it.first == date }?.second ?: if (d == todayDayIndex) todayCalories else 0
                            (cal / maxCal).coerceIn(0.12f, 1f)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        calHeights.forEachIndexed { i, ratio ->
                            val isToday = i == todayDayIndex
                            val isFuture = i > todayDayIndex
                            Box(
                                modifier = Modifier
                                    .width(11.dp)
                                    .fillMaxHeight(if (isFuture) 0.12f else ratio)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(
                                        when {
                                            isToday -> healthColors.accentTeal
                                            isFuture -> healthColors.pillUnselectedBg.copy(alpha = 0.5f)
                                            else -> healthColors.accentTeal.copy(alpha = 0.7f)
                                        }
                                    )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        weekDays.forEachIndexed { i, day ->
                            val isToday = i == todayDayIndex
                            Text(
                                text = day,
                                color = if (isToday) healthColors.textPrimary else healthColors.textMuted,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    val calLeft = (2252 - todayCalories).coerceAtLeast(0)
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(healthColors.pillUnselectedBg)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("$calLeft cal left", color = healthColors.pillUnselectedText, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        "steps" -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { onNavigateToSteps() }
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Steps", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = HealthFormat.count(todaySteps),
                            color = healthColors.textPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val todayDayIndex = (LocalDate.now().dayOfWeek.value - 1).coerceIn(0, 6)
                    val weekStart = weekStartMon(LocalDate.now())
                    val maxStep = weeklySteps.maxOfOrNull { it.second }?.toFloat()?.coerceAtLeast(1000f) ?: 10000f
                    val stepHeights = (0..6).map { d ->
                        val date = weekStart.plusDays(d.toLong())
                        if (d > todayDayIndex) 0f
                        else {
                            val s = weeklySteps.find { it.first == date }?.second ?: if (d == todayDayIndex) todaySteps else 0
                            (s / maxStep).coerceIn(0.12f, 1f)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        stepHeights.forEachIndexed { i, ratio ->
                            val isToday = i == todayDayIndex
                            val isFuture = i > todayDayIndex
                            Box(
                                modifier = Modifier
                                    .width(11.dp)
                                    .fillMaxHeight(if (isFuture) 0.12f else ratio)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(
                                        when {
                                            isToday -> healthColors.accentTeal
                                            isFuture -> healthColors.pillUnselectedBg.copy(alpha = 0.5f)
                                            else -> healthColors.accentTeal.copy(alpha = 0.7f)
                                        }
                                    )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        weekDays.forEachIndexed { i, day ->
                            val isToday = i == todayDayIndex
                            Text(
                                text = day,
                                color = if (isToday) healthColors.textPrimary else healthColors.textMuted,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(healthColors.pillUnselectedBg)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${HealthFormat.count(totalWeeklySteps)} weekly total",
                            color = healthColors.pillUnselectedText,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
        "exercise days" -> {
            val weekStart = weekStartMon(LocalDate.now())
            val activeDaysSet = workouts.map { it.startTime.atZone(ZoneId.systemDefault()).toLocalDate() }.toSet()
            val todayDayIndex = (LocalDate.now().dayOfWeek.value - 1).coerceIn(0, 6)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { onNavigateToExercise() }
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Exercise days", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "$exerciseDaysThisWeek of $WEEKLY_EXERCISE_DAYS_GOAL",
                        color = healthColors.textPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        (0..6).forEach { d ->
                            val dayDate = weekStart.plusDays(d.toLong())
                            val isDone = dayDate in activeDaysSet
                            val isToday = d == todayDayIndex

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(if (isDone) healthColors.accentTeal else healthColors.pillUnselectedBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isDone) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = healthColors.onAccentTeal, modifier = Modifier.size(10.dp))
                                    }
                                }
                                Text(
                                    text = weekDays[d],
                                    color = if (isToday) healthColors.textPrimary else healthColors.textMuted,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    Text(
                        text = "$workoutsThisWeek logged this week",
                        color = healthColors.textMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        "distance" -> {
            val weekStart = weekStartMon(LocalDate.now())
            val todayDayIndex = (LocalDate.now().dayOfWeek.value - 1).coerceIn(0, 6)
            val distValues = (0..6).map { d ->
                val date = weekStart.plusDays(d.toLong())
                if (d > todayDayIndex) 0f
                else {
                    val steps = weeklySteps.find { it.first == date }?.second
                        ?: if (d == todayDayIndex) todaySteps else 0
                    (steps * 0.00075f)
                }
            }
            val maxDist = distValues.maxOrNull()?.coerceAtLeast(1f) ?: 5f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { onNavigateToDistance() }
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Distance", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = String.format(java.util.Locale.getDefault(), "%.2f km", effectiveDistance),
                            color = healthColors.textPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 7-day weekly bar graph
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val heights = distValues.map { (it / maxDist).coerceIn(0.12f, 1f) }
                        heights.forEachIndexed { i, ratio ->
                            val isToday = i == todayDayIndex
                            val isFuture = i > todayDayIndex
                            Box(
                                modifier = Modifier
                                    .width(11.dp)
                                    .fillMaxHeight(if (isFuture) 0.12f else ratio)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(
                                        when {
                                            isToday -> healthColors.accentTeal
                                            isFuture -> healthColors.pillUnselectedBg.copy(alpha = 0.5f)
                                            else -> healthColors.accentTeal.copy(alpha = 0.7f)
                                        }
                                    )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        weekDays.forEachIndexed { i, day ->
                            val isToday = i == todayDayIndex
                            Text(
                                text = day,
                                color = if (isToday) healthColors.textPrimary else healthColors.textMuted,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    Text("Walked today", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        "sleep" -> {
            val zone = ZoneId.systemDefault()
            val todayDate = LocalDate.now()
            val todaySleep = sleepSessions.find {
                it.endTime.atZone(zone).toLocalDate() == todayDate || it.startTime.atZone(zone).toLocalDate() == todayDate
            }
            val hasSleep = todaySleep != null && todaySleep.totalInBedMinutes > 0
            val totalMins = todaySleep?.totalInBedMinutes ?: 0L
            val sleepHours = totalMins / 60
            val sleepRemainingMins = totalMins % 60
            val durationText = if (!hasSleep) "No data" else if (sleepHours > 0) "${sleepHours}h ${sleepRemainingMins}m" else "${sleepRemainingMins}m"
            val scoreResult = todaySleep?.let { com.healthcompanion.app.model.SleepScoreCalculator.calculate(it) }
            val scoreText = if (scoreResult != null) "Score: ${scoreResult.score} • ${scoreResult.rating}" else "No recent data"

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { onNavigateToSleep() }
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Sleep", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(durationText, color = healthColors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                    if (todaySleep != null && todaySleep.stages.isNotEmpty()) {
                        com.healthcompanion.app.ui.screens.today.MiniHypnogramPreview(sleepSession = todaySleep)
                    } else {
                        // Clean placeholder track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(healthColors.cardElevated)
                        )
                    }

                    Text(scoreText, color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        "resting heart rate" -> {
            val weekStart = weekStartMon(LocalDate.now())
            val todayDayIndex = (LocalDate.now().dayOfWeek.value - 1).coerceIn(0, 6)
            val restingRecords = restingHeartRate?.dailyRecords.orEmpty().associateBy { it.date }
            val dailyBpmList = (0..6).map { d ->
                val date = weekStart.plusDays(d.toLong())
                if (d > todayDayIndex) null
                else restingRecords[date]?.bpm
            }
            val restingBpm = restingHeartRate?.dailyRecords?.lastOrNull()?.bpm ?: restingHeartRate?.averageBpm ?: dailyHeartRate?.samples?.minOfOrNull { it.bpm }
            val hasData = restingBpm != null && restingBpm > 0

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { onNavigateToHeartRate() }
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Resting HR", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(if (hasData) "$restingBpm" else "--", color = healthColors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (hasData) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("bpm", color = healthColors.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 2.dp))
                            }
                        }
                    }

                    // 7-day weekly bar graph
                    val validBpms = dailyBpmList.filterNotNull().filter { it > 0 }
                    val maxBpm = validBpms.maxOrNull()?.toFloat()?.coerceAtLeast(100f) ?: 100f
                    val minBpm = (validBpms.minOrNull()?.toFloat() ?: 60f).coerceAtMost(maxBpm - 20f)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        dailyBpmList.forEachIndexed { i, bpm ->
                            val isToday = i == todayDayIndex
                            val isFuture = i > todayDayIndex
                            val barRatio = if (bpm != null && bpm > 0) {
                                ((bpm - minBpm + 10f) / (maxBpm - minBpm + 10f)).coerceIn(0.2f, 1f)
                            } else {
                                0.12f
                            }
                            Box(
                                modifier = Modifier
                                    .width(11.dp)
                                    .fillMaxHeight(if (isFuture) 0.12f else barRatio)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(
                                        when {
                                            isToday -> healthColors.heartModerate
                                            isFuture -> healthColors.pillUnselectedBg.copy(alpha = 0.5f)
                                            bpm != null && bpm > 0 -> healthColors.heartModerate.copy(alpha = 0.7f)
                                            else -> healthColors.pillUnselectedBg.copy(alpha = 0.5f)
                                        }
                                    )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        weekDays.forEachIndexed { i, day ->
                            val isToday = i == todayDayIndex
                            Text(
                                text = day,
                                color = if (isToday) healthColors.textPrimary else healthColors.textMuted,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    Text(
                        text = if (hasData) "Within normal range" else "No resting HR data",
                        color = if (hasData) healthColors.accentTeal else healthColors.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        "Sp02" -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { onNavigateToSpO2() }
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("SpO2", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text("${effectiveSpO2 ?: 98}%", color = healthColors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    ) {
                        val w = size.width
                        val h = size.height
                        drawLine(color = healthColors.accentTeal, start = Offset(0f, h * 0.5f), end = Offset(w, h * 0.5f), strokeWidth = 2.dp.toPx())
                        (1..5).forEach { i ->
                            drawCircle(color = healthColors.accentTeal, radius = 3.dp.toPx(), center = Offset((w / 6f) * i, h * 0.5f))
                        }
                    }
                    Text("Normal oxygenation", color = healthColors.accentTeal, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        "hourly activity" -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { onNavigateToHourlyActivity() }
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Hourly activity", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text("$activeHours of 9", color = healthColors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (0..8).forEach { idx ->
                            val targetHour = 9 + idx
                            val isActive = hourlyStepsToday.any { it.first == targetHour && it.second >= 250 }
                            Box(
                                modifier = Modifier
                                    .width(8.dp)
                                    .fillMaxHeight()
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(if (isActive) healthColors.accentTeal else healthColors.pillUnselectedBg)
                            )
                        }
                    }
                    Text("09:00 - 18:00", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        "active minutes" -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { onNavigateToCategory(CategoryId.FITNESS) }
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Active minutes", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text("42 min", color = healthColors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(healthColors.pillUnselectedBg)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .fillMaxHeight()
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(healthColors.statusWarn)
                        )
                    }
                    Text("Goal: 60 min", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        "weekly cardio" -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { onNavigateToWeeklyCardio() }
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Weekly cardio", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text("$cardioPercent%", color = healthColors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(healthColors.pillUnselectedBg)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(cardioPercent / 100f)
                                .fillMaxHeight()
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(healthColors.accentTeal)
                        )
                    }
                    Text("$cardioLeft min remaining", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        "breathing rate" -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { onNavigateToCategory(CategoryId.RESPIRATORY) }
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Breathing rate", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("15.2", color = healthColors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("rpm", color = healthColors.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 2.dp))
                    }
                    Canvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
                        val w = size.width
                        val h = size.height
                        val path = Path().apply {
                            moveTo(0f, h * 0.5f)
                            quadraticTo(w * 0.25f, 0f, w * 0.5f, h * 0.5f)
                            quadraticTo(w * 0.75f, h, w, h * 0.5f)
                        }
                        drawPath(path, color = healthColors.accentTeal, style = Stroke(width = 2.dp.toPx()))
                    }
                    Text("Stable during sleep", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        "run distance" -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { onNavigateToExercise() }
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Run distance", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text("3.12 km", color = healthColors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Canvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
                        val w = size.width
                        val h = size.height
                        val pts = listOf(Offset(0f, h * 0.8f), Offset(w * 0.5f, h * 0.3f), Offset(w, h * 0.6f))
                        for (i in 0 until pts.size - 1) {
                            drawLine(color = healthColors.accentTeal, start = pts[i], end = pts[i + 1], strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        }
                    }
                    Text("This week", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        "calories intake" -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { onNavigateToCategory(CategoryId.NUTRITION) }
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Calories intake", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("1,850", color = healthColors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("kcal", color = healthColors.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 2.dp))
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(10.dp).clip(MaterialTheme.shapes.extraSmall).background(healthColors.pillUnselectedBg)) {
                        Box(modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight().clip(MaterialTheme.shapes.extraSmall).background(healthColors.statusGood))
                    }
                    Text("Target: 2,200 kcal", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        "protein" -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { onNavigateToCategory(CategoryId.NUTRITION) }
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Protein", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text("92 g", color = healthColors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.fillMaxWidth().height(10.dp).clip(MaterialTheme.shapes.extraSmall).background(healthColors.pillUnselectedBg)) {
                        Box(modifier = Modifier.fillMaxWidth(0.75f).fillMaxHeight().clip(MaterialTheme.shapes.extraSmall).background(healthColors.accentPurple))
                    }
                    Text("Goal: 120 g", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        "carbs" -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { onNavigateToCategory(CategoryId.NUTRITION) }
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Carbohydrates", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text("210 g", color = healthColors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.fillMaxWidth().height(10.dp).clip(MaterialTheme.shapes.extraSmall).background(healthColors.pillUnselectedBg)) {
                        Box(modifier = Modifier.fillMaxWidth(0.7f).fillMaxHeight().clip(MaterialTheme.shapes.extraSmall).background(healthColors.heartVigorous))
                    }
                    Text("Under daily limit", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        "fat" -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { onNavigateToCategory(CategoryId.NUTRITION) }
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Fat", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text("55 g", color = healthColors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.fillMaxWidth().height(10.dp).clip(MaterialTheme.shapes.extraSmall).background(healthColors.pillUnselectedBg)) {
                        Box(modifier = Modifier.fillMaxWidth(0.65f).fillMaxHeight().clip(MaterialTheme.shapes.extraSmall).background(healthColors.heartPeak))
                    }
                    Text("Within healthy limit", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        "Hydration" -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { onNavigateToCategory(CategoryId.METABOLIC) }
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Hydration", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("2,100", color = healthColors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("ml", color = healthColors.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 2.dp))
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(10.dp).clip(MaterialTheme.shapes.extraSmall).background(healthColors.pillUnselectedBg)) {
                        Box(modifier = Modifier.fillMaxWidth(0.84f).fillMaxHeight().clip(MaterialTheme.shapes.extraSmall).background(healthColors.accentTeal))
                    }
                    Text("Goal: 2,500 ml", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
