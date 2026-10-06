package com.healthcompanion.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.healthcompanion.app.data.HealthGoals
import com.healthcompanion.app.data.healthconnect.HealthConnectManager
import com.healthcompanion.app.model.ExerciseWorkout
import com.healthcompanion.app.ui.screens.metric.GenericMetricDetailScreen
import com.healthcompanion.app.ui.screens.metric.MetricConfig
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Metric configurations keyed by a stable id.
 *
 * `GenericMetricDetailScreen` used to be registered under five routes, each
 * carrying its own inline `MetricConfig` block — around 150 lines of duplicated
 * configuration in the NavHost. One destination plus this registry makes every
 * metric behave identically and keeps configuration out of the navigation layer.
 *
 * Icon tints are resolved at composition time from the active theme rather than
 * frozen as hex constants, so they follow dynamic color.
 */
object MetricConfigRegistry {

    const val SKIN_TEMPERATURE = "skin_temp"
    const val HRV = "hrv"
    const val BREATHING_RATE = "breathing_rate"
    const val WEIGHT = "weight"
    const val WEEKLY_CARDIO = "weekly_cardio"

    @Composable
    fun fromKey(key: String): MetricConfig? {
        val healthColors = com.healthcompanion.app.ui.theme.LocalHealthColors.current
        return when (key) {
            SKIN_TEMPERATURE -> MetricConfig(
                title = "Skin temperature",
                unit = "°C",
                icon = Icons.Default.DeviceThermostat,
                iconTint = healthColors.accentOrange,
                baselineDescription = "Variation from baseline",
                clinicalRangeDescription = "-1.0°C to +1.0°C normal overnight fluctuation",
                aboutDescription = "Skin temperature is monitored while you sleep to estimate " +
                    "variations from your baseline body temperature. Shifts can reflect sleep " +
                    "quality, circadian cycle changes, recovery status, or room temperature.",
                isVariation = true
            )

            HRV -> MetricConfig(
                title = "Heart rate variability (HRV)",
                unit = "ms",
                icon = Icons.Default.Timeline,
                iconTint = healthColors.accentPurple,
                baselineDescription = "Typical RMSSD baseline",
                clinicalRangeDescription = "20–100 ms normal adult resting range",
                aboutDescription = "Heart rate variability (HRV) is the variance in time between " +
                    "consecutive heartbeats. Higher baseline HRV generally indicates strong " +
                    "cardiovascular fitness and autonomic recovery."
            )

            BREATHING_RATE -> MetricConfig(
                title = "Breathing rate",
                unit = "brpm",
                icon = Icons.Default.Air,
                iconTint = healthColors.accentTeal,
                baselineDescription = "Overnight average",
                clinicalRangeDescription = "12–20 breaths per minute at rest",
                aboutDescription = "Breathing rate measures the average number of breaths you take " +
                    "per minute during sleep. Significant changes from your baseline can indicate " +
                    "physical fatigue or respiratory shifts."
            )

            WEIGHT -> MetricConfig(
                title = "Weight",
                unit = "kg",
                icon = Icons.Default.Accessibility,
                iconTint = healthColors.accentBlue,
                baselineDescription = "Current weight",
                clinicalRangeDescription = "Normal BMI: 18.5 – 24.9 kg/m²",
                aboutDescription = "Tracks body weight changes over time to monitor fitness " +
                    "progress and body composition. Weight is measured weekly or monthly, with " +
                    "daily points plotted smoothly."
            )

            WEEKLY_CARDIO -> MetricConfig(
                title = "Weekly cardio",
                unit = "active zone minutes",
                icon = Icons.Default.Favorite,
                iconTint = healthColors.accentTeal,
                baselineDescription = "Active zone minutes this week",
                clinicalRangeDescription = "World Health Organization recommends at least 150 " +
                    "minutes of moderate or 75 minutes of vigorous physical activity weekly.",
                aboutDescription = "Active Zone Minutes reward time spent in fat burn, cardio, " +
                    "or peak heart rate zones.",
                isSumMetric = true
            )

            else -> null
        }
    }

    /** Maps a catalog metric id onto a registered config, if one exists. */
    fun routeForMetric(metricId: String): String? =
        when (metricId) {
            SKIN_TEMPERATURE, HRV, BREATHING_RATE, WEIGHT, WEEKLY_CARDIO -> metricId
            else -> null
        }
}

/**
 * Resolves weight (and any future simple scalar metric) on demand, so the screen
 * gets real history rather than whatever the Activity last happened to load.
 */
@Composable
fun GenericMetricRoute(
    manager: HealthConnectManager,
    config: MetricConfig,
    goals: HealthGoals,
    onBack: () -> Unit
) {
    val isWeight = config.title == "Weight"
    var latest by remember { mutableStateOf<Float?>(null) }
    var history by remember {
        mutableStateOf<List<Pair<LocalDate, Float?>>>(emptyList())
    }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(config.title) {
        loading = true
        try {
            if (isWeight) {
                val realLatest = manager.readLatestWeight().getOrNull()
                val realHistory: List<Pair<LocalDate, Float?>> = manager
                    .readWeightPeriods(days = 365)
                    .getOrDefault(emptyList())
                    .map { (date, value) -> date to value as Float? }
                latest = realLatest
                history = realHistory
            } else {
                latest = null
                history = emptyList()
            }
        } finally {
            loading = false
        }
    }

    GenericMetricDetailScreen(
        config = config,
        recordedValue = latest,
        historyData = history,
        availableRanges = if (isWeight) {
            listOf(
                com.healthcompanion.app.ui.components.TimeRange.WEEK,
                com.healthcompanion.app.ui.components.TimeRange.MONTH,
                com.healthcompanion.app.ui.components.TimeRange.THREE_MONTHS,
                com.healthcompanion.app.ui.components.TimeRange.YEAR
            )
        } else {
            com.healthcompanion.app.ui.components.TimeRange.entries
        },
        showDateNavigator = true,
        isLoading = loading,
        onBack = onBack
    )
}

/**
 * Weekly cardio, derived from real workouts.
 *
 * The previous version keyed a `LaunchedEffect` on a list rebuilt on every
 * recomposition (`workouts.ifEmpty { SampleHealthDataProvider.getSampleWorkouts() }`),
 * so the effect cancelled and restarted constantly and the loading flag flickered.
 */
@Composable
fun WeeklyCardioRoute(
    manager: HealthConnectManager,
    workouts: List<ExerciseWorkout>,
    goalMinutes: Int,
    config: MetricConfig,
    onBack: () -> Unit
) {
    var cardioMinutes by remember { mutableStateOf<Int?>(null) }
    var dailyHistory by remember {
        mutableStateOf<List<Pair<LocalDate, Float?>>>(emptyList())
    }
    var loading by remember { mutableStateOf(true) }

    // Keyed on the identity of the input list, not on a freshly allocated one.
    LaunchedEffect(workouts) {
        loading = true
        try {
            val readMinutes = manager.readWeeklyActiveZoneMinutes().getOrNull()
            val today = LocalDate.now()
            val zone = ZoneId.systemDefault()

            val workoutMinsByDay = workouts.groupBy { it.startTime.atZone(zone).toLocalDate() }
                .mapValues { (_, dayWorkouts) ->
                    dayWorkouts.sumOf { workout ->
                        val zoneMins = workout.zones.filter { it.minBpm >= 110 }.sumOf { it.minutesSpent }
                        if (zoneMins > 0) {
                            zoneMins
                        } else {
                            workout.cardioLoad.coerceAtLeast((workout.durationMinutes * 0.7f).toInt())
                        }
                    }.toFloat()
                }

            val history = (84 downTo 0).map { offset ->
                val date = today.minusDays(offset.toLong())
                date to (workoutMinsByDay[date] ?: 0f)
            }
            dailyHistory = history

            val weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            val weekTotal = history
                .filter { !it.first.isBefore(weekStart) && !it.first.isAfter(today) }
                .sumOf { it.second?.toDouble() ?: 0.0 }
                .toInt()
            cardioMinutes = if (readMinutes != null && readMinutes > 0) readMinutes else weekTotal
        } finally {
            loading = false
        }
    }

    GenericMetricDetailScreen(
        config = config.copy(
            baselineDescription = cardioMinutes
                ?.let { "$it of $goalMinutes active zone minutes" }
                ?: "No cardio recorded this week",
            targetGoal = goalMinutes
        ),
        recordedValue = null,
        historyData = dailyHistory,
        availableRanges = listOf(
            com.healthcompanion.app.ui.components.TimeRange.WEEK,
            com.healthcompanion.app.ui.components.TimeRange.MONTH,
            com.healthcompanion.app.ui.components.TimeRange.THREE_MONTHS
        ),
        showDateNavigator = true,
        isLoading = loading,
        onBack = onBack
    )
}
