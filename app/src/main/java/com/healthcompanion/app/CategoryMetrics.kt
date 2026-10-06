package com.healthcompanion.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.healthcompanion.app.model.DailyHeartRateSummary
import com.healthcompanion.app.model.ExerciseWorkout
import com.healthcompanion.app.model.RestingHeartRatePeriodSummary
import com.healthcompanion.app.model.SleepSession
import com.healthcompanion.app.ui.components.EmptyState
import com.healthcompanion.app.ui.format.HealthFormat
import com.healthcompanion.app.ui.navigation.CategoryId
import com.healthcompanion.app.ui.navigation.Screen
import com.healthcompanion.app.ui.screens.health.CategoryMetricItem
import com.healthcompanion.app.ui.theme.LocalHealthColors
import java.time.LocalDate

@Composable
fun WorkoutMissingState(reason: String, onBack: () -> Unit, onBrowse: () -> Unit) {
    EmptyState(
        title = "Workout unavailable",
        body = reason,
        icon = Icons.Default.DirectionsRun,
        actionLabel = "Browse workouts",
        onAction = onBrowse,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun UnknownCategoryState(onBack: () -> Unit) {
    EmptyState(
        title = "Unknown category",
        body = "That category isn't recognised.",
        icon = Icons.Default.Folder,
        actionLabel = "Go back",
        onAction = onBack,
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Builds the rows for a category browse screen.
 *
 * Every value here comes from a real Health Connect read. A metric with no data
 * renders `value = null` and `isTracked = false`; nothing is backfilled with a
 * plausible default.
 */
@Composable
fun buildCategoryMetrics(
    category: CategoryId,
    dailyHeartRate: DailyHeartRateSummary?,
    restingHeartRate: RestingHeartRatePeriodSummary?,
    todaySteps: Int?,
    todayDistance: Double?,
    weeklyCalories: List<Pair<LocalDate, Int>>,
    workouts: List<ExerciseWorkout>,
    sleepSessions: List<SleepSession>,
    todaySpO2: Int?,
    latestWeight: Float?,
    navController: NavHostController
): List<CategoryMetricItem> {
    val heartRateRow = CategoryMetricItem(
        id = "hr",
        title = "Heart rate",
        value = dailyHeartRate?.latestBpm?.let { "$it bpm" },
        subtitle = if (dailyHeartRate?.latestBpm != null) "Latest reading" else "No records yet",
        icon = Icons.Default.Favorite,
        isTracked = dailyHeartRate?.latestBpm != null,
        onNavigate = { navController.navigate(Screen.HeartRate.route) }
    )

    val restingHeartRateRow = CategoryMetricItem(
        id = "rhr",
        title = "Resting heart rate",
        value = restingHeartRate?.averageBpm?.let { "$it bpm" },
        subtitle = if (restingHeartRate?.averageBpm != null) "Weekly baseline" else "No records yet",
        icon = Icons.Default.FavoriteBorder,
        isTracked = restingHeartRate?.averageBpm != null,
        onNavigate = { navController.navigate(Screen.RestingHeartRate.route) }
    )

    val hrvRow = CategoryMetricItem(
        id = "hrv",
        title = "Heart rate variability",
        value = null,
        subtitle = "Not yet recorded",
        icon = Icons.Default.ShowChart,
        isTracked = false,
        onNavigate = { navController.navigate(Screen.GenericMetric.createRoute("hrv")) }
    )

    val cardioRow = CategoryMetricItem(
        id = "cardio",
        title = "Weekly cardio",
        value = if (workouts.isNotEmpty()) {
            HealthFormat.duration(workouts.sumOf { it.durationMinutes })
        } else null,
        subtitle = if (workouts.isNotEmpty()) "Active zone minutes" else "No workouts recorded",
        icon = Icons.Default.Favorite,
        isTracked = workouts.isNotEmpty(),
        onNavigate = { navController.navigate(Screen.GenericMetric.createRoute("weekly_cardio")) }
    )

    val stepsRow = CategoryMetricItem(
        id = "steps",
        title = "Steps",
        value = todaySteps?.takeIf { it > 0 }?.let { HealthFormat.count(it) },
        subtitle = if ((todaySteps ?: 0) > 0) "Daily steps" else "No steps recorded today",
        icon = Icons.AutoMirrored.Filled.DirectionsWalk,
        isTracked = (todaySteps ?: 0) > 0,
        onNavigate = { navController.navigate(Screen.Steps.route) }
    )

    val distanceRow = CategoryMetricItem(
        id = "distance",
        title = "Distance",
        value = todayDistance?.takeIf { it > 0.0 }?.let { HealthFormat.decimal(it, 2) + " km" },
        subtitle = if ((todayDistance ?: 0.0) > 0.0) "Daily distance" else "No distance recorded today",
        icon = Icons.AutoMirrored.Filled.DirectionsRun,
        isTracked = (todayDistance ?: 0.0) > 0.0,
        onNavigate = { navController.navigate(Screen.Distance.route) }
    )

    val latestWeekCalories = weeklyCalories.lastOrNull()?.second?.takeIf { it > 0 }
    val energyRow = CategoryMetricItem(
        id = "energy",
        title = "Energy burned",
        value = latestWeekCalories?.let { HealthFormat.count(it) + " kcal" },
        subtitle = if (latestWeekCalories != null) "Active & basal energy" else "No energy recorded",
        icon = Icons.Default.LocalFireDepartment,
        isTracked = latestWeekCalories != null,
        onNavigate = { navController.navigate(Screen.EnergyBurned.route) }
    )

    val workoutsRow = CategoryMetricItem(
        id = "workouts",
        title = "Exercise workouts",
        value = if (workouts.isNotEmpty()) HealthFormat.count(workouts.size) else null,
        subtitle = if (workouts.isNotEmpty()) "Recorded activities" else "No workouts recorded",
        icon = Icons.AutoMirrored.Filled.DirectionsRun,
        isTracked = workouts.isNotEmpty(),
        onNavigate = { navController.navigate(Screen.Fitness.route) }
    )

    val hourlyRow = CategoryMetricItem(
        id = "hourly",
        title = "Hourly activity",
        value = null,
        subtitle = "See your hourly step pattern",
        icon = Icons.Default.Schedule,
        isTracked = false,
        onNavigate = { navController.navigate(Screen.HourlyActivity.route) }
    )

    val spo2Row = CategoryMetricItem(
        id = "spo2",
        title = "Blood oxygen (SpO2)",
        value = todaySpO2?.let { "$it%" },
        subtitle = if (todaySpO2 != null) "Daily oxygen saturation" else "No records yet",
        icon = Icons.Default.Air,
        isTracked = todaySpO2 != null,
        onNavigate = { navController.navigate(Screen.SpO2.route) }
    )

    val skinTempRow = CategoryMetricItem(
        id = "skin_temp",
        title = "Skin temperature",
        value = null,
        subtitle = "No records yet",
        icon = Icons.Default.DeviceThermostat,
        isTracked = false,
        onNavigate = { navController.navigate(Screen.GenericMetric.createRoute("skin_temp")) }
    )

    val breathingRow = CategoryMetricItem(
        id = "breathing",
        title = "Breathing rate",
        value = null,
        subtitle = "No records yet",
        icon = Icons.Default.Air,
        isTracked = false,
        onNavigate = { navController.navigate(Screen.GenericMetric.createRoute("breathing_rate")) }
    )

    val latestSleep = sleepSessions.firstOrNull()
    val sleepDurationRow = CategoryMetricItem(
        id = "sleep_duration",
        title = "Sleep duration",
        value = latestSleep?.let { HealthFormat.duration(it.totalInBedMinutes.toInt()) },
        subtitle = if (latestSleep != null) "Latest sleep session" else "No sleep recorded",
        icon = Icons.Default.Bedtime,
        isTracked = latestSleep != null,
        onNavigate = { navController.navigate(Screen.SleepDetail.route) }
    )

    val sleepEfficiencyRow = CategoryMetricItem(
        id = "sleep_efficiency",
        title = "Sleep efficiency",
        value = latestSleep?.let { "${it.sleepEfficiency}%" },
        subtitle = if (latestSleep != null) "Time asleep vs in bed" else "No sleep recorded",
        icon = Icons.Default.Bedtime,
        isTracked = latestSleep != null,
        onNavigate = { navController.navigate(Screen.SleepDetail.route) }
    )

    val weightRow = CategoryMetricItem(
        id = "weight",
        title = "Weight",
        value = latestWeight?.let { HealthFormat.decimal(it, 1) + " kg" },
        subtitle = if (latestWeight != null) "Latest measurement" else "No weight recorded",
        icon = Icons.Default.Accessibility,
        isTracked = latestWeight != null,
        onNavigate = { navController.navigate(Screen.GenericMetric.createRoute("weight")) }
    )

    return when (category) {
        CategoryId.HEART -> listOf(heartRateRow, restingHeartRateRow, hrvRow, cardioRow)
        CategoryId.FITNESS -> listOf(stepsRow, distanceRow, energyRow, workoutsRow, hourlyRow)
        CategoryId.VITALS -> listOf(heartRateRow, spo2Row, restingHeartRateRow, skinTempRow, breathingRow)
        CategoryId.RESPIRATORY -> listOf(spo2Row, breathingRow)
        CategoryId.SLEEP -> listOf(sleepDurationRow, sleepEfficiencyRow)
        CategoryId.METABOLIC -> listOf(energyRow, weightRow)
        CategoryId.TEMPERATURE -> listOf(skinTempRow)
        CategoryId.MENTAL_WELLBEING -> emptyList()
        CategoryId.NUTRITION -> emptyList()
    }
}
