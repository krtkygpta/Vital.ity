package com.healthcompanion.app.ui.screens.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthcompanion.app.data.FocusTileDef
import com.healthcompanion.app.data.FocusTileRegistry
import com.healthcompanion.app.data.HealthGoals
import com.healthcompanion.app.model.*
import com.healthcompanion.app.ui.components.LogDataBottomSheet
import com.healthcompanion.app.ui.screens.focus.*
import com.healthcompanion.app.ui.format.HealthFormat
import com.healthcompanion.app.ui.theme.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.sin

/**
 * The home dashboard.
 *
 * Top Section: 2x2 Grid of Progress Widgets against user goals:
 * 1. Steps: Circular ring (today's steps vs daily step goal)
 * 2. Distance: Circular ring (today's km vs daily distance goal)
 * 3. Weekly Cardio: Circular ring (weekly cardio load / active zone minutes vs weekly goal)
 * 4. Energy Burned / Readiness: Circular progress ring (active calories vs goal)
 *
 * Action buttons: [+ Log] [🏃 Start] [✏️ Edit]
 *
 * Bottom Section: Chronological Scrollable Activity Log:
 * - Today's entries (workout with cardio load & duration, sleep with stage graph, score & time)
 * - Material 3 Expressive Squiggly Divider with "Yesterday" badge in center
 * - Yesterday's entries (yesterday workout & sleep)
 * - Further past dividers & entries
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayDashboardScreen(
    sleepSessions: List<SleepSession> = emptyList(),
    dailyHeartRate: DailyHeartRateSummary?,
    restingHeartRate: RestingHeartRatePeriodSummary? = null,
    weeklySteps: List<Pair<LocalDate, Int>> = emptyList(),
    todaySteps: Int?,
    todayDistanceKm: Double?,
    workouts: List<ExerciseWorkout>,
    hasPermissions: Boolean,
    /**
     * Counts, not a boolean. Previously `hasPermissions = granted.isNotEmpty()`
     * meant one grant out of ~30 looked identical to a complete connection.
     */
    isConnected: Boolean = hasPermissions,
    grantedCount: Int = 0,
    requestedCount: Int = 0,
    onRequestPermissions: () -> Unit,
    onNavigateToSleep: () -> Unit,
    onNavigateToHeartRate: () -> Unit,
    onNavigateToWorkout: (com.healthcompanion.app.model.ExerciseWorkout) -> Unit = {},
    onNavigateToExercise: () -> Unit,
    onNavigateToSteps: () -> Unit,
    onNavigateToDistance: () -> Unit,
    onNavigateToEditFocus: () -> Unit = {},
    focusStore: com.healthcompanion.app.data.FocusStore? = null,
    onToggleTheme: () -> Unit = {},
    isDynamicColor: Boolean = true,
    onToggleDynamicColor: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    goals: HealthGoals = HealthGoals(),
    onLogWorkout: ((sport: String, durationMinutes: Int, calories: Int, distanceKm: Double?) -> Unit)? = null,
    onLogWeight: ((weightKg: Double) -> Unit)? = null,
    onLogWater: ((amountMl: Int) -> Unit)? = null,
    onLogSleep: ((bedHour: Int, bedMin: Int, wakeHour: Int, wakeMin: Int) -> Unit)? = null,
    onLogHeartRate: ((bpm: Int) -> Unit)? = null,
    onLogBloodPressure: ((systolic: Int, diastolic: Int) -> Unit)? = null,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},    /** �2.8: increments when the user re-taps this tab; scroll to index 0. */    scrollToTopSignal: Int = 0,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    var showLogBottomSheet by remember { mutableStateOf(false) }

    // §2.8: retained across tab switches so returning to Today restores position.
    val todayListState = rememberLazyListState()

    // �2.8: re-tap the active tab to return to the top.
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) todayListState.scrollToItem(0)
    }

    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val zone = remember { ZoneId.systemDefault() }

    // Workouts partitioned by day
    val todayWorkouts = remember(workouts, today, zone) {
        workouts.filter { it.startTime.atZone(zone).toLocalDate() == today }
            .sortedByDescending { it.startTime }
    }
    val yesterdayWorkouts = remember(workouts, yesterday, zone) {
        workouts.filter { it.startTime.atZone(zone).toLocalDate() == yesterday }
            .sortedByDescending { it.startTime }
    }
    val olderWorkouts = remember(workouts, yesterday, zone) {
        workouts.filter { it.startTime.atZone(zone).toLocalDate().isBefore(yesterday) }
            .sortedByDescending { it.startTime }
    }

    // Weekly cardio load calculation
    val weekStart = remember(today) { today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY)) }
    val weeklyWorkouts = remember(workouts, weekStart, zone) {
        workouts.filter {
            val d = it.startTime.atZone(zone).toLocalDate()
            !d.isBefore(weekStart) && !d.isAfter(today)
        }
    }
    // Sub-components take non-null primitives, so an absent read collapses to
    // zero here rather than being invented per-tile.
    val effectiveSteps = todaySteps ?: 0
    val effectiveDistanceKm = todayDistanceKm ?: 0.0

    val currentWeeklyCardioLoad = remember(weeklyWorkouts, effectiveSteps) {
        val workoutsLoad = weeklyWorkouts.sumOf { wo ->
            wo.cardioLoad.coerceAtLeast(((wo.durationMinutes * 0.7) + (wo.caloriesBurnedKcal / 25.0)).toInt())
        }
        val stepLoad = (effectiveSteps / 1000).coerceIn(0, 18)
        // No 14-minute floor: a week with no activity must read as zero.
        workoutsLoad + stepLoad
    }

    // Today active calories, summed from real workouts only.
    val todayActiveCalories = remember(todayWorkouts) {
        todayWorkouts.sumOf { it.caloriesBurnedKcal }
    }

    val todaySleepSessions = remember(sleepSessions, today, zone) {
        sleepSessions.filter {
            it.endTime.atZone(zone).toLocalDate() == today || it.startTime.atZone(zone).toLocalDate() == today
        }.sortedByDescending { it.endTime }
    }
    val yesterdaySleepSessions = remember(sleepSessions, yesterday, todaySleepSessions, zone) {
        sleepSessions.filter {
            (it.endTime.atZone(zone).toLocalDate() == yesterday || it.startTime.atZone(zone).toLocalDate() == yesterday) &&
            it !in todaySleepSessions
        }.sortedByDescending { it.endTime }
    }

    val primarySleepSession = todaySleepSessions.firstOrNull()
    val sleepSession = primarySleepSession
    val sleepScoreResult = primarySleepSession?.let { SleepScoreCalculator.calculate(it) }

    val selectedTileIds = focusStore?.selectedTileIdsState ?: listOf("distance", "heart_rate", "sleep")
    val isExpandedView = focusStore?.isExpandedViewState ?: false

    val colorScheme = MaterialTheme.colorScheme
    val dynamicBg = if (isDynamicColor) colorScheme.background else healthColors.background
    val dynamicTopContainer = if (isDynamicColor) colorScheme.surfaceContainer else healthColors.cardBackground

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .background(dynamicBg)
    ) {
        LazyColumn(
            // §2.8: hoisted so the scroll position survives tab switches. With a
            // list-local state the user was dropped back to the top every time
            // they returned to the tab.
            state = todayListState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // §3.4: `hasPermissions` and `onRequestPermissions` were declared but
        // never referenced in the body, so there was no in-content permission
        // education — only a system prompt fired once on first launch with no
        // stated reason. This card explains what is missing and offers the
        // request in context.
        if (!isConnected) {
            item {
                val body = when {
                    grantedCount == 0 ->
                        "HealthCompanion reads your health data from Health Connect. " +
                            "Nothing is stored or sent anywhere else."
                    else ->
                        "$grantedCount of $requestedCount permissions granted. " +
                            "Metrics you have not granted stay empty."
                }
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = colorScheme.tertiaryContainer,
                    contentColor = colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(Spacing.l)) {
                        Text(
                            text = if (grantedCount == 0) {
                                "Connect Health Connect"
                            } else {
                                "Finish connecting Health Connect"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(Spacing.m))
                        Button(onClick = onRequestPermissions) {
                            Text(if (grantedCount == 0) "Connect" else "Grant the rest")
                        }
                    }
                }
            }
        }

        // 1. Top Section Panel in Enclosed Surface Container with Rounded Bottom Edge (RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                color = dynamicTopContainer,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Top App Bar: "Vital.ity", Profile avatar with colored status ring
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Vital.ity",
                            style = Typography.titleMedium,
                            color = healthColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                        )

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(healthColors.cardElevated)
                                .clickable { onNavigateToProfile() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Profile & Settings",
                                tint = healthColors.accentTeal,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Top Section: 4x3 Grid Carouseled across 3 pages (HorizontalPager) with smooth scroll animations!
                    val pagerState = rememberPagerState(pageCount = { 3 })
                    val coroutineScope = rememberCoroutineScope()

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth()
                    ) { pageIndex ->
                        val pageOffset = (
                            (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                        ).absoluteValue

                        val pageTiles = focusStore?.getPageTiles(pageIndex) ?: when (pageIndex) {
                            0 -> listOf("distance", "resting_heart_rate", "sleep")
                            1 -> listOf("steps", "calories_burned", "exercise_days")
                            else -> listOf("weekly_cardio", "floors", "zone_min")
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    val progress = 1f - pageOffset.coerceIn(0f, 1f)
                                    val scale = lerp(0.92f, 1f, progress)
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = lerp(0.5f, 1f, progress)
                                },
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Left Column (2 columns of 4): Large Ring Gauge
                            Box(
                                modifier = Modifier
                                    .weight(1.05f)
                                    .height(190.dp)
                                    .clickable {
                                        when (pageIndex) {
                                            0 -> onNavigateToSteps()
                                            1 -> onNavigateToDistance()
                                            2 -> onNavigateToExercise()
                                        }
                                    }
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                when (pageIndex) {
                                    0 -> {
                                        ProgressRingWidgetContent(
                                            title = "Steps",
                                            currentValue = effectiveSteps.toFloat(),
                                            goalValue = goals.dailyStepGoal.toFloat(),
                                            displayValue = HealthFormat.count(effectiveSteps),
                                            displayGoal = "of ${HealthFormat.count(goals.dailyStepGoal)}",
                                            ringColor = if (isDynamicColor) colorScheme.primary else healthColors.accentTeal,
                                            trackColor = if (isDynamicColor) colorScheme.surfaceContainerHighest else healthColors.cardElevated,
                                            icon = Icons.Default.DirectionsWalk
                                        )
                                    }
                                    1 -> {
                                        val distGoal = goals.dailyDistanceGoalKm.toFloat()
                                        val distCurrent = effectiveDistanceKm.toFloat()
                                        ProgressRingWidgetContent(
                                            title = "Distance",
                                            currentValue = distCurrent,
                                            goalValue = distGoal,
                                            displayValue = String.format(Locale.getDefault(), "%.2f", effectiveDistanceKm),
                                            displayUnit = "km",
                                            displayGoal = "of ${String.format(Locale.getDefault(), "%.1f", goals.dailyDistanceGoalKm)} km",
                                            ringColor = if (isDynamicColor) colorScheme.secondary else healthColors.accentTeal,
                                            trackColor = if (isDynamicColor) colorScheme.surfaceContainerHighest else healthColors.cardElevated,
                                            icon = Icons.Default.LocationOn
                                        )
                                    }
                                    else -> {
                                        val cardioGoal = goals.weeklyCardioMinutesGoal.toFloat()
                                        ProgressRingWidgetContent(
                                            title = "Cardio load",
                                            subtitle = "Weekly",
                                            currentValue = currentWeeklyCardioLoad.toFloat(),
                                            goalValue = cardioGoal,
                                            displayValue = "$currentWeeklyCardioLoad",
                                            displayGoal = "of ${goals.weeklyCardioMinutesGoal}",
                                            ringColor = if (isDynamicColor) colorScheme.tertiary else healthColors.heartVigorous,
                                            trackColor = if (isDynamicColor) colorScheme.surfaceContainerHighest else healthColors.cardElevated,
                                            icon = Icons.Default.Favorite
                                        )
                                    }
                                }
                            }

                            // Right Column (2 columns of 4): 3 stacked 2x1 pills
                            Column(
                                modifier = Modifier.weight(1.15f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val displayedTiles = pageTiles.take(3)
                                displayedTiles.forEach { tileId ->
                                    RenderFocusTileCard(
                                        tileId = tileId,
                                        todaySteps = effectiveSteps,
                                        todayDistanceKm = effectiveDistanceKm,
                                        weeklySteps = weeklySteps,
                                        restingHeartRate = restingHeartRate,
                                        dailyHeartRate = dailyHeartRate,
                                        sleepSession = primarySleepSession,
                                        currentWeeklyCardioLoad = currentWeeklyCardioLoad,
                                        todayActiveCalories = todayActiveCalories,
                                        weeklyWorkouts = weeklyWorkouts,
                                        goals = goals,
                                        zone = zone,
                                        onNavigateToSteps = onNavigateToSteps,
                                        onNavigateToDistance = onNavigateToDistance,
                                        onNavigateToHeartRate = onNavigateToHeartRate,
                                        onNavigateToSleep = onNavigateToSleep,
                                        onNavigateToExercise = onNavigateToExercise,
                                        onNavigateToEditFocus = onNavigateToEditFocus
                                    )
                                }
                                if (displayedTiles.size < 3) {
                                    repeat(3 - displayedTiles.size) {
                                        HorizontalMetricPillCard(
                                            icon = Icons.Default.Add,
                                            iconTint = healthColors.textSecondary,
                                            title = "Add tile",
                                            value = "Tap to customize",
                                            containerColor = healthColors.cardBackground,
                                            progressColor = healthColors.cardElevated,
                                            progressFraction = 0f,
                                            contentColor = healthColors.textSecondary,
                                            onClick = onNavigateToEditFocus
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Pager dots centered below the entire 4x3 carouseled grid matching media_1790886050700.png!
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp, bottom = 4.dp)
                    ) {
                        repeat(3) { index ->
                            val isActive = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .size(width = if (isActive) 18.dp else 6.dp, height = 6.dp)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(
                                        if (isActive) healthColors.textPrimary
                                        else healthColors.textMuted.copy(alpha = 0.45f)
                                    )
                                    .clickable {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                            )
                        }
                    }

                    // 3. Quick Action Buttons: [+ Log] [🏃 Start] [✏️ Edit] matching media_1790886050700.png
                    val actionBtnBg = if (isDynamicColor) colorScheme.primaryContainer else healthColors.accentTeal
                    val actionBtnFg = if (isDynamicColor) colorScheme.onPrimaryContainer else healthColors.onAccentTeal
                    val editBtnBg = if (isDynamicColor) colorScheme.surfaceContainerHighest else healthColors.cardElevated
                    val editBtnFg = if (isDynamicColor) colorScheme.onSurfaceVariant else healthColors.textPrimary

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { showLogBottomSheet = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = actionBtnBg,
                                contentColor = actionBtnFg
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = actionBtnFg, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Log", color = actionBtnFg, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        }

                        Button(
                            onClick = onNavigateToExercise,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = actionBtnBg,
                                contentColor = actionBtnFg
                            )
                        ) {
                            Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = actionBtnFg, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start", color = actionBtnFg, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        }

                        IconButton(
                            onClick = onNavigateToEditFocus,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(editBtnBg)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit focus", tint = editBtnFg, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // TODAY'S LOG
        // 4a. Today's Exercise Card (ONLY show if actual workout logged today - zero fake data)
        val primaryTodayWorkout = todayWorkouts.firstOrNull()
        if (primaryTodayWorkout != null) {
            item {
                TodayWorkoutCard(
                    workout = primaryTodayWorkout,
                    onClick = { onNavigateToWorkout(primaryTodayWorkout) }
                )
            }
        }

        // 4b. Today's Sleep Cards (show ALL sleep sessions from today)
        val todaySleepSessions = sleepSessions.filter { it.startTime.atZone(zone).toLocalDate() == today }
        todaySleepSessions.forEach { session ->
            item {
                TodaySleepCard(
                    sleepSession = session,
                    sleepScoreResult = SleepScoreCalculator.calculate(session),
                    onClick = onNavigateToSleep
                )
            }
        }

        // 5. MATERIAL 3 EXPRESSIVE SQUIGGLY LINE DIVIDER WITH "Yesterday"
        // Show yesterday section if there were activities yesterday
        val primaryYesterdayWorkout = yesterdayWorkouts.firstOrNull()
        val yesterdaySleepSessions = sleepSessions.filter { it.startTime.atZone(zone).toLocalDate() == yesterday }

        if (primaryYesterdayWorkout != null || yesterdaySleepSessions.isNotEmpty()) {
            item {
                M3ExpressiveSquigglyDivider(label = "Yesterday")
            }

            if (primaryYesterdayWorkout != null) {
                item {
                    TodayWorkoutCard(
                        workout = primaryYesterdayWorkout,
                        onClick = { onNavigateToWorkout(primaryYesterdayWorkout) }
                    )
                }
            }

            yesterdaySleepSessions.forEach { session ->
                item {
                    TodaySleepCard(
                        sleepSession = session,
                        sleepScoreResult = SleepScoreCalculator.calculate(session),
                        onClick = onNavigateToSleep
                    )
                }
            }
        }

        // Older items if any
        if (olderWorkouts.isNotEmpty()) {
            val olderDate = olderWorkouts.first().startTime.atZone(zone).toLocalDate()
            val olderDateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
            item {
                M3ExpressiveSquigglyDivider(label = olderDate.format(olderDateFormatter))
            }
            item {
                TodayWorkoutCard(
                    workout = olderWorkouts.first(),
                    onClick = { onNavigateToWorkout(olderWorkouts.first()) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

    if (showLogBottomSheet) {
        LogDataBottomSheet(
            onDismiss = { showLogBottomSheet = false },
            onLogWorkout = { sport, dur, cal, dist ->
                onLogWorkout?.invoke(sport, dur, cal, dist)
            },
            onLogWeight = { w -> onLogWeight?.invoke(w) },
            onLogWater = { ml -> onLogWater?.invoke(ml) },
            onLogSleep = { bh, bm, wh, wm -> onLogSleep?.invoke(bh, bm, wh, wm) },
            onLogHeartRate = { bpm -> onLogHeartRate?.invoke(bpm) },
            onLogBloodPressure = { sys, dia -> onLogBloodPressure?.invoke(sys, dia) }
        )
    }
}

/**
 * Circular Progress Ring Widget for 2x2 Grid at top of Home Screen.
 */
@Composable
fun ProgressRingWidget(
    title: String,
    currentValue: Float,
    goalValue: Float,
    displayValue: String,
    displayGoal: String,
    ringColor: Color,
    trackColor: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    displayUnit: String? = null
) {
    val healthColors = LocalHealthColors.current
    val progress = if (goalValue > 0f) (currentValue / goalValue).coerceIn(0f, 1f) else 0f
    val sweepAngle = (progress * 360f).coerceAtLeast(8f)

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(healthColors.cardBackground)
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header with Icon & Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ringColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = title,
                        color = healthColors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = healthColors.textMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Circular Ring with Value Centered
            Box(
                modifier = Modifier
                    .size(106.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 9.dp.toPx()
                    val arcSize = size.width - strokeWidth
                    val topLeftOffset = strokeWidth / 2f

                    // Background track
                    drawCircle(
                        color = trackColor,
                        radius = size.width / 2f - strokeWidth / 2f,
                        style = Stroke(width = strokeWidth)
                    )

                    // Foreground arc
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(topLeftOffset, topLeftOffset),
                        size = Size(arcSize, arcSize),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = displayValue,
                            color = healthColors.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (displayUnit != null) {
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = displayUnit,
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = displayGoal,
                        color = healthColors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Material 3 Expressive Squiggly Line Divider with Badge in Middle.
 */
@Composable
fun M3ExpressiveSquigglyDivider(
    label: String,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val waveColor = healthColors.textMuted.copy(alpha = 0.45f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left squiggly wave
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
        ) {
            val width = size.width
            val midY = size.height / 2f
            val path = Path()
            path.moveTo(0f, midY)

            val wavelength = 18.dp.toPx()
            val amplitude = 3.5.dp.toPx()
            var x = 0f

            while (x <= width) {
                val y = midY + sin((x / wavelength) * (2 * Math.PI).toFloat()) * amplitude
                path.lineTo(x, y)
                x += 2.dp.toPx()
            }

            drawPath(
                path = path,
                color = waveColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Center Text inline with background (no pill box)
        Text(
            text = label,
            color = healthColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp)
        )

        // Right squiggly wave
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
        ) {
            val width = size.width
            val midY = size.height / 2f
            val path = Path()
            path.moveTo(0f, midY)

            val wavelength = 18.dp.toPx()
            val amplitude = 3.5.dp.toPx()
            var x = 0f

            while (x <= width) {
                val y = midY + sin((x / wavelength) * (2 * Math.PI).toFloat()) * amplitude
                path.lineTo(x, y)
                x += 2.dp.toPx()
            }

            drawPath(
                path = path,
                color = waveColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Workout Card for Activity Log showing cardio load and duration.
 */
@Composable
fun TodayWorkoutCard(
    workout: ExerciseWorkout,
    onClick: () -> Unit
) {
    val healthColors = LocalHealthColors.current
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(healthColors.cardBackground)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Top row: Type icon + title + time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(healthColors.accentTeal.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = workout.type.getIcon(),
                            contentDescription = null,
                            tint = healthColors.accentTeal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = workout.title.ifBlank { workout.type.label },
                            color = healthColors.textPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${timeFormatter.format(workout.startTime)} • ${workout.type.label}",
                            color = healthColors.textSecondary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Cardio load badge
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(healthColors.heartVigorous.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Cardio load ${workout.cardioLoad}",
                        color = healthColors.heartVigorous,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Stats row: Duration, Calories, Distance
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(healthColors.cardElevated)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Duration", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                    Text("${workout.durationMinutes} min", color = healthColors.textPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text("Energy", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                    Text("${workout.caloriesBurnedKcal} cal", color = healthColors.textPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                if (workout.distanceKm != null) {
                    Column {
                        Text("Distance", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                        Text(String.format(Locale.getDefault(), "%.2f km", workout.distanceKm), color = healthColors.textPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Column {
                        Text("Intensity", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                        Text("Optimal", color = healthColors.accentTeal, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Sleep Card for Activity Log matching media_1790886050700.png:
 * - AI timestamp header with sparkle
 * - Bold 'Sleep tracked' title
 * - AI summary text ("It looks like you took a nap...")
 * - Single large 'Sleep duration' block ("48 min")
 * - Feedback footer with thumbs up, thumbs down, 3-dot overflow
 */
@Composable
fun TodaySleepCard(
    sleepSession: SleepSession,
    sleepScoreResult: SleepScoreResult?,
    onClick: () -> Unit
) {
    val healthColors = LocalHealthColors.current
    val totalMins = sleepSession.totalInBedMinutes
    val durationText = if (totalMins < 60) "$totalMins min" else "${totalMins / 60} h ${totalMins % 60} min"

    val tfTime = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val sessionDate = sleepSession.endTime.atZone(ZoneId.systemDefault()).toLocalDate()

    val startTimeStr = tfTime.format(sleepSession.startTime)
    val endTimeStr = tfTime.format(sleepSession.endTime)

    val timestampHeader = when (sessionDate) {
        today -> "Today, $startTimeStr"
        yesterday -> "Yesterday, $startTimeStr"
        else -> "${sessionDate.format(DateTimeFormatter.ofPattern("EEE, d MMM"))}, $startTimeStr"
    }

    val isNap = !sleepSession.isMainSleep || sleepSession.totalInBedMinutes < 120
    val cardTitle = if (isNap) "Nap tracked" else "Sleep tracked"
    val summaryText = if (isNap) {
        "It looks like you took a nap at $startTimeStr for $durationText."
    } else {
        "You slept for $durationText ($startTimeStr – $endTimeStr)."
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(healthColors.cardBackground)
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header: AI/sparkle icon with timestamp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = healthColors.accentTeal,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = timestampHeader,
                    color = healthColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            // Bold Title
            Text(
                text = cardTitle,
                color = healthColors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // AI Summary sentence
            Text(
                text = summaryText,
                color = healthColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )

            // Single, large "Sleep duration" block
            val cardElevatedBg = healthColors.cardElevated
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(cardElevatedBg)
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Sleep duration",
                            color = healthColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = durationText,
                            color = healthColors.textPrimary,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Genuine sleep stage hypnogram preview matching user's actual session
                    if (sleepSession != null && sleepSession.stages.isNotEmpty()) {
                        MiniHypnogramPreview(sleepSession = sleepSession)
                    }
                }
            }

            // The thumbs-up / thumbs-down / overflow footer was removed: all three
            // IconButtons had `onClick = {}`, so nothing was ever collected and
            // the controls only looked actionable.
        }
    }
}

/**
 * Yesterday's Sleep Card.
 */
@Composable
fun YesterdaySleepCard(
    onClick: () -> Unit
) {
    val healthColors = LocalHealthColors.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(healthColors.cardBackground)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = healthColors.accentPurple,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Sleep tracked",
                        color = healthColors.textPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(healthColors.accentPurple.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Score 86",
                        color = healthColors.accentPurple,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "6 h 15 m",
                        color = healthColors.textPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "00:15 – 06:30",
                        color = healthColors.textSecondary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(healthColors.accentTeal.copy(alpha = 0.2f))
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

            // Mini Hypnogram Preview
            MiniHypnogramPreview(sleepSession = null)
        }
    }
}

/**
 * Content for the Circular Progress Ring Widget (used inside the left gauge card).
 * Matches media_1790886050700.png with thick track, large rounded stroke cap, and handwritten typography.
 */
@Composable
fun ProgressRingWidgetContent(
    title: String,
    currentValue: Float,
    goalValue: Float,
    displayValue: String,
    displayGoal: String,
    ringColor: Color,
    trackColor: Color,
    icon: ImageVector,
    subtitle: String? = null,
    displayUnit: String? = null
) {
    val healthColors = LocalHealthColors.current
    val progress = if (goalValue > 0f) (currentValue / goalValue).coerceIn(0f, 1f) else 0f
    val sweepAngle = (progress * 360f).coerceAtLeast(16f)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Standalone circular radial progress gauge floating directly without card background
        Canvas(modifier = Modifier.size(154.dp)) {
            val strokeWidth = 24.dp.toPx()
            val arcSize = size.width - strokeWidth
            val topLeftOffset = strokeWidth / 2f

            // Background track (#2C2F33)
            drawCircle(
                color = healthColors.cardElevated,
                radius = size.width / 2f - strokeWidth / 2f,
                style = Stroke(width = strokeWidth)
            )

            // Filled arc with rounded caps
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(topLeftOffset, topLeftOffset),
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Inner centered text using clean rounded sans-serif font
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = healthColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = displayValue,
                color = healthColors.textPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )

            Text(
                text = displayGoal,
                color = ringColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 2x1 Horizontal Pill Metric Card (Distance, Heart rate / Empty slot, Sleep).
 * Background acts as a horizontal progress bar from left to right based on progressFraction.
 * Base unfilled track is dark surface (#25282B), filled portion is domain tint (e.g. Teal for distance, Purple for sleep).
 */
@Composable
fun HorizontalMetricPillCard(
    icon: ImageVector?,
    iconTint: Color,
    title: String,
    value: String,
    containerColor: Color,
    progressColor: Color,
    progressFraction: Float = 0f,
    contentColor: Color = Color.Unspecified,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val effectiveContentColor = if (contentColor != Color.Unspecified) contentColor else healthColors.textPrimary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(containerColor)
            .clickable { onClick() },
        contentAlignment = Alignment.CenterStart
    ) {
        // Horizontal progress fill if progressFraction > 0
        if (progressFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = progressFraction.coerceIn(0f, 1f))
                    .background(progressColor)
            )
        }

        if (title.isNotEmpty() || value.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(effectiveContentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        color = effectiveContentColor.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = value,
                        color = effectiveContentColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun DistanceWeeklyPillCard(
    todayDistanceKm: Double,
    weeklySteps: List<Pair<LocalDate, Int>>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val distDisplay = if (todayDistanceKm > 0.0) "${String.format(Locale.getDefault(), "%.2f", todayDistanceKm)} km" else "0 km"
    val today = LocalDate.now()
    val weekStart = today.minusDays(6)

    // Build 7-day distance data
    val stepsMap = weeklySteps.toMap()
    val weekDistances = (0..6).map { i ->
        val d = weekStart.plusDays(i.toLong())
        when {
            d == today -> todayDistanceKm
            d.isAfter(today) -> 0.0
            else -> {
                val steps = stepsMap[d] ?: 0
                steps * 0.00075 // ~0.75m per step in km
            }
        }
    }
    val maxDist = weekDistances.maxOrNull()?.coerceAtLeast(1.0) ?: 5.0

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(healthColors.cardBackground)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(healthColors.cardElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = healthColors.accentTeal,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = "Distance",
                        color = healthColors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = distDisplay,
                        color = healthColors.textPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 7-day mini bar chart sparkline
            Canvas(
                modifier = Modifier
                    .width(48.dp)
                    .height(28.dp)
                    .padding(vertical = 2.dp)
            ) {
                val barW = 3.5.dp.toPx()
                val spacing = (size.width - (7 * barW)) / 6f
                val h = size.height

                weekDistances.forEachIndexed { idx, dist ->
                    val x = idx * (barW + spacing)
                    val isToday = idx == 6
                    val barH = if (dist > 0.0) ((dist / maxDist).toFloat() * h).coerceIn(4.dp.toPx(), h) else 2.dp.toPx()
                    val y = h - barH

                    val barColor = when {
                        isToday -> healthColors.accentTeal
                        dist > 0.0 -> healthColors.accentTeal.copy(alpha = 0.7f)
                        else -> healthColors.cardElevated
                    }

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(barW, barH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
fun RestingHrWeeklyPillCard(
    dailyHeartRate: DailyHeartRateSummary?,
    restingHeartRate: RestingHeartRatePeriodSummary?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val rhr = restingHeartRate?.dailyRecords?.lastOrNull()?.bpm ?: dailyHeartRate?.minBpm ?: 64
    val hasData = restingHeartRate?.dailyRecords?.isNotEmpty() == true || dailyHeartRate?.minBpm != null
    val displayVal = if (hasData) "$rhr bpm" else "No data"

    val today = LocalDate.now()
    val weekStart = today.minusDays(6)
    val rhrMap = restingHeartRate?.dailyRecords?.associate { it.date to it.bpm } ?: emptyMap()

    val weekPoints = (0..6).map { i ->
        val d = weekStart.plusDays(i.toLong())
        rhrMap[d] ?: if (d == today && dailyHeartRate?.minBpm != null) dailyHeartRate.minBpm else null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(healthColors.cardBackground)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(healthColors.cardElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MonitorHeart,
                        contentDescription = null,
                        tint = healthColors.heartModerate,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = "Resting HR",
                        color = healthColors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = displayVal,
                        color = healthColors.textPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 7-day sparkline line chart
            Canvas(
                modifier = Modifier
                    .width(48.dp)
                    .height(28.dp)
                    .padding(vertical = 3.dp)
            ) {
                val nonNullPoints = weekPoints.filterNotNull()
                if (nonNullPoints.size >= 2) {
                    val minBpm = (nonNullPoints.minOrNull() ?: 50) - 5
                    val maxBpm = (nonNullPoints.maxOrNull() ?: 80) + 5
                    val range = (maxBpm - minBpm).coerceAtLeast(1).toFloat()
                    val w = size.width
                    val h = size.height

                    val path = Path()
                    var first = true
                    var lastX = 0f
                    var lastY = 0f

                    weekPoints.forEachIndexed { idx, bpm ->
                        val x = (idx / 6f) * w
                        val validBpm = bpm ?: nonNullPoints.average().toInt()
                        val y = h - (((validBpm - minBpm) / range) * h).coerceIn(2f, h - 2f)

                        if (first) {
                            path.moveTo(x, y)
                            first = false
                        } else {
                            path.lineTo(x, y)
                        }
                        if (idx == 6 || (bpm != null && idx == weekPoints.indexOfLast { it != null })) {
                            lastX = x
                            lastY = y
                        }
                    }

                    // Stroke
                    drawPath(
                        path = path,
                        color = healthColors.heartModerate,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Dot on last point
                    drawCircle(
                        color = healthColors.heartModerate,
                        radius = 3.dp.toPx(),
                        center = Offset(lastX, lastY)
                    )
                    drawCircle(
                        color = healthColors.cardBackground,
                        radius = 1.5.dp.toPx(),
                        center = Offset(lastX, lastY)
                    )
                } else {
                    // Subtle baseline
                    drawLine(
                        color = healthColors.heartModerate.copy(alpha = 0.4f),
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }
        }
    }
}

@Composable
fun RenderFocusTileCard(
    tileId: String,
    todaySteps: Int,
    todayDistanceKm: Double,
    weeklySteps: List<Pair<LocalDate, Int>> = emptyList(),
    restingHeartRate: RestingHeartRatePeriodSummary? = null,
    dailyHeartRate: DailyHeartRateSummary?,
    sleepSession: SleepSession?,
    currentWeeklyCardioLoad: Int,
    todayActiveCalories: Int,
    weeklyWorkouts: List<ExerciseWorkout>,
    goals: HealthGoals,
    zone: ZoneId,
    onNavigateToSteps: () -> Unit,
    onNavigateToDistance: () -> Unit,
    onNavigateToHeartRate: () -> Unit,
    onNavigateToSleep: () -> Unit,
    onNavigateToExercise: () -> Unit,
    onNavigateToEditFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val tileDef = FocusTileRegistry.getDef(tileId)
    when (tileId) {
        "distance" -> {
            DistanceWeeklyPillCard(
                todayDistanceKm = todayDistanceKm,
                weeklySteps = weeklySteps,
                onClick = onNavigateToDistance,
                modifier = modifier
            )
        }
        "heart_rate" -> {
            val hrText = if (dailyHeartRate != null && dailyHeartRate.samples.isNotEmpty()) {
                "${dailyHeartRate.minBpm} - ${dailyHeartRate.maxBpm} BPM"
            } else "Resting ${dailyHeartRate?.minBpm ?: 68} BPM"
            HorizontalMetricPillCard(
                icon = Icons.Default.Favorite,
                iconTint = healthColors.heartPeak,
                title = "Heart rate",
                value = hrText,
                containerColor = healthColors.cardBackground,
                progressColor = healthColors.cardElevated,
                progressFraction = 1f,
                contentColor = healthColors.textPrimary,
                onClick = onNavigateToHeartRate,
                modifier = modifier
            )
        }
        "sleep" -> {
            val s = sleepSession
            val hasValidSleep = s != null && s.totalInBedMinutes > 0
            val sleepText = if (hasValidSleep && s != null) {
                val hrs = s.totalInBedMinutes / 60
                val mins = s.totalInBedMinutes % 60
                "${hrs} h ${mins} m"
            } else "No data"
            val sleepGoalMins = 8 * 60
            val sleepFraction = if (hasValidSleep && s != null) (s.totalInBedMinutes.toFloat() / sleepGoalMins).coerceIn(0f, 1f) else 0f
            HorizontalMetricPillCard(
                icon = Icons.Default.Bedtime,
                iconTint = healthColors.accentPurple,
                title = "Sleep",
                value = sleepText,
                containerColor = healthColors.cardBackground,
                progressColor = healthColors.cardElevated,
                progressFraction = sleepFraction,
                contentColor = healthColors.textPrimary,
                onClick = onNavigateToSleep,
                modifier = modifier
            )
        }
        "steps" -> {
            val stepsFraction = if (goals.dailyStepGoal > 0) (todaySteps.toFloat() / goals.dailyStepGoal).coerceIn(0f, 1f) else 0f
            HorizontalMetricPillCard(
                icon = Icons.Default.DirectionsWalk,
                iconTint = healthColors.accentTeal,
                title = "Steps",
                value = HealthFormat.count(todaySteps),
                containerColor = healthColors.cardBackground,
                progressColor = healthColors.cardElevated,
                progressFraction = stepsFraction,
                contentColor = healthColors.textPrimary,
                onClick = onNavigateToSteps,
                modifier = modifier
            )
        }
        "weekly_cardio" -> {
            val cardioFraction = if (goals.weeklyCardioMinutesGoal > 0) (currentWeeklyCardioLoad.toFloat() / goals.weeklyCardioMinutesGoal).coerceIn(0f, 1f) else 0f
            HorizontalMetricPillCard(
                icon = Icons.Default.Favorite,
                iconTint = healthColors.heartVigorous,
                title = "Cardio load",
                value = "$currentWeeklyCardioLoad of ${goals.weeklyCardioMinutesGoal}",
                containerColor = healthColors.cardBackground,
                progressColor = healthColors.cardElevated,
                progressFraction = cardioFraction,
                contentColor = healthColors.textPrimary,
                onClick = onNavigateToExercise,
                modifier = modifier
            )
        }
        "calories_burned" -> {
            HorizontalMetricPillCard(
                icon = Icons.Default.LocalFireDepartment,
                iconTint = healthColors.heartVigorous,
                title = "Cal burned",
                value = "$todayActiveCalories cal",
                containerColor = healthColors.cardBackground,
                progressColor = healthColors.cardElevated,
                progressFraction = (todayActiveCalories / 500f).coerceIn(0f, 1f),
                contentColor = healthColors.textPrimary,
                onClick = onNavigateToExercise,
                modifier = modifier
            )
        }
        "exercise_days" -> {
            val distinctDays = weeklyWorkouts.map { it.startTime.atZone(zone).toLocalDate() }.distinct().size
            HorizontalMetricPillCard(
                icon = Icons.Default.FitnessCenter,
                iconTint = healthColors.accentTeal,
                title = "Exercise days",
                value = "$distinctDays of 5",
                containerColor = healthColors.cardBackground,
                progressColor = healthColors.cardElevated,
                progressFraction = (distinctDays / 5f).coerceIn(0f, 1f),
                contentColor = healthColors.textPrimary,
                onClick = onNavigateToExercise,
                modifier = modifier
            )
        }
        "resting_heart_rate" -> {
            RestingHrWeeklyPillCard(
                dailyHeartRate = dailyHeartRate,
                restingHeartRate = restingHeartRate,
                onClick = onNavigateToHeartRate,
                modifier = modifier
            )
        }
        else -> {
            HorizontalMetricPillCard(
                icon = tileDef.icon,
                iconTint = tileDef.iconTint,
                title = tileDef.title,
                value = tileDef.defaultVal,
                containerColor = healthColors.cardBackground,
                progressColor = tileDef.iconBgColor,
                progressFraction = 0.5f,
                contentColor = healthColors.textPrimary,
                onClick = onNavigateToEditFocus,
                modifier = modifier
            )
        }
    }
}

/**
 * Mini 4-track hypnogram preview rendered inside sleep cards.
 * Accurately plots sleepSession.stages (Awake, REM, Light, Deep) across time.
 */
@Composable
fun MiniHypnogramPreview(sleepSession: SleepSession?) {
    val healthColors = LocalHealthColors.current
    val trackBgColor = healthColors.cardElevated

    val awakeColor = healthColors.sleepAwake
    val remColor = healthColors.sleepRem
    val lightColor = healthColors.sleepLight
    val deepColor = healthColors.sleepDeep

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(MaterialTheme.shapes.small)
    ) {
        val w = size.width
        val trackH = 5.dp.toPx()
        val spacing = (size.height - (4 * trackH)) / 3f

        // 4 tracks: Awake (row 0), REM (row 1), Light (row 2), Deep (row 3)
        for (i in 0..3) {
            val y = i * (trackH + spacing)
            drawRoundRect(
                color = trackBgColor,
                topLeft = Offset(0f, y),
                size = Size(w, trackH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2f, trackH / 2f)
            )
        }

        if (sleepSession != null && sleepSession.stages.isNotEmpty()) {
            val sessionStart = sleepSession.startTime.toEpochMilli()
            val totalMillis = (sleepSession.endTime.toEpochMilli() - sessionStart).coerceAtLeast(1L).toFloat()

            sleepSession.stages.forEach { interval ->
                val stageStart = interval.startTime.toEpochMilli()
                val stageEnd = interval.endTime.toEpochMilli()

                val startFraction = ((stageStart - sessionStart).toFloat() / totalMillis).coerceIn(0f, 1f)
                val durationFraction = ((stageEnd - stageStart).toFloat() / totalMillis).coerceAtLeast(0.01f)

                val x = startFraction * w
                val segmentWidth = (durationFraction * w).coerceAtLeast(4.dp.toPx())

                val (rowIndex, color) = when (interval.stage) {
                    SleepStageType.AWAKE -> 0 to awakeColor
                    SleepStageType.REM -> 1 to remColor
                    SleepStageType.LIGHT -> 2 to lightColor
                    SleepStageType.DEEP -> 3 to deepColor
                }

                val y = rowIndex * (trackH + spacing)

                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(segmentWidth.coerceAtMost(w - x), trackH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2f, trackH / 2f)
                )
            }
        }
    }
}
