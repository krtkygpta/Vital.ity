package com.healthcompanion.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Route table.
 *
 * Routes that carry a parameter use a named argument rather than a positional
 * `{id}` placeholder plus a hand-rolled `createRoute`. `exercise_detail` used to
 * be registered twice — once parameterised and once not — and the unparameterised
 * copy had no inbound navigation at all.
 */
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {

    /** The route to navigate to. Aliases override this to point at a shared destination. */
    open fun createRoute(): String = route

    data object Today : Screen("today", "Today", Icons.Default.WbSunny)
    data object Fitness : Screen("fitness", "Exercise", Icons.AutoMirrored.Filled.DirectionsRun)
    data object SleepTab : Screen("sleep_tab", "Sleep", Icons.Default.Bedtime)
    data object Health : Screen("health", "Vitals", Icons.Default.MonitorHeart)

    data object SleepDetail : Screen("sleep_detail", "Sleep Detail", Icons.Default.Bedtime)
    data object HeartRate : Screen("heart_rate", "Heart Rate", Icons.Default.Favorite)
    data object RestingHeartRate : Screen("resting_heart_rate", "Resting Heart Rate", Icons.Default.Favorite)
    data object EnergyBurned : Screen("energy_burned", "Energy Burned", Icons.Default.LocalFireDepartment)
    data object Steps : Screen("steps", "Steps", Icons.AutoMirrored.Filled.DirectionsWalk)
    data object Distance : Screen("distance", "Distance", Icons.AutoMirrored.Filled.DirectionsRun)
    data object SpO2 : Screen("spo2", "Blood Oxygen", Icons.Default.Air)
    data object HourlyActivity : Screen("hourly_activity", "Hourly Activity", Icons.Default.Timer)
    data object ExerciseList : Screen("exercise_list", "Exercise Days", Icons.AutoMirrored.Filled.DirectionsRun)
    data object VitalsList : Screen("vitals_list", "Body Vitals", Icons.Default.MonitorHeart)
    data object Catalog : Screen("catalog", "Browse", Icons.Default.Search)
    data object Profile : Screen("profile", "Profile", Icons.Default.Person)
    data object EditFocus : Screen("edit_focus", "Edit focus", Icons.Default.Tune)

    // Metrics rendered by GenericMetricDetailScreen. Each of these is a
    // convenience alias that resolves to the single `metric/{configId}`
    // destination, so there is still exactly one registration per screen.
    data object SkinTemp : Screen(MetricConfigRegistry.SKIN_TEMPERATURE, "Skin Temperature", Icons.Default.DeviceThermostat) {
        override fun createRoute(): String = GenericMetric.createRoute(MetricConfigRegistry.SKIN_TEMPERATURE)
    }
    data object Hrv : Screen(MetricConfigRegistry.HRV, "Heart Rate Variability", Icons.Default.Timeline) {
        override fun createRoute(): String = GenericMetric.createRoute(MetricConfigRegistry.HRV)
    }
    data object BreathingRate : Screen(MetricConfigRegistry.BREATHING_RATE, "Breathing Rate", Icons.Default.Air) {
        override fun createRoute(): String = GenericMetric.createRoute(MetricConfigRegistry.BREATHING_RATE)
    }
    data object Weight : Screen(MetricConfigRegistry.WEIGHT, "Weight", Icons.Default.Accessibility) {
        override fun createRoute(): String = GenericMetric.createRoute(MetricConfigRegistry.WEIGHT)
    }
    data object WeeklyCardio : Screen(MetricConfigRegistry.WEEKLY_CARDIO, "Weekly Cardio", Icons.Default.Favorite) {
        override fun createRoute(): String = GenericMetric.createRoute(MetricConfigRegistry.WEEKLY_CARDIO)
    }

    /** Exercise detail, resolved from its id rather than a global selected object. */
    data object ExerciseDetail : Screen("exercise_detail/{$ARG_WORKOUT_ID}", "Workout Detail", Icons.AutoMirrored.Filled.DirectionsRun) {
        const val ARG_WORKOUT_ID = "workoutId"
        fun createRoute(workoutId: String): String = "exercise_detail/$workoutId"
    }

    /**
     * One destination for every metric rendered by `GenericMetricDetailScreen`.
     * Previously that screen was registered under five routes, each with its own
     * inlined `MetricConfig`.
     */
    data object GenericMetric : Screen("metric/{$ARG_CONFIG_ID}", "Metric", Icons.Default.ShowChart) {
        const val ARG_CONFIG_ID = "configId"
        fun createRoute(configId: String): String = "metric/$configId"
    }

    /** Category browse takes a stable id, not a user-facing display string. */
    data object CategoryMetrics : Screen("category_metrics/{$ARG_CATEGORY_ID}", "Category Metrics", Icons.Default.Folder) {
        const val ARG_CATEGORY_ID = "categoryId"
        fun createRoute(categoryId: String): String = "category_metrics/$categoryId"
    }

    companion object {
        const val ARG_WORKOUT_ID = "workoutId"
        const val ARG_CONFIG_ID = "configId"
        const val ARG_CATEGORY_ID = "categoryId"

        /** The four bottom-bar tabs, in order. */
        val topLevelDestinations: List<Screen> = listOf(Today, Fitness, SleepTab, Health)

        val topLevelRoutes: Set<String> = topLevelDestinations.map { it.route }.toSet()
    }
}
