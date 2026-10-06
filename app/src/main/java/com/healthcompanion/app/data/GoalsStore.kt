package com.healthcompanion.app.data

import android.content.Context

/**
 * User-editable goals and body profile.
 *
 * Values are persisted with SharedPreferences so they survive process death
 * and configuration changes. Defaults mirror the previous hardcoded constants.
 */
data class HealthGoals(
    // Activity
    val dailyStepGoal: Int = 10_000,
    val dailyDistanceGoalKm: Double = 8.05,
    val weeklyExerciseDaysGoal: Int = 5,
    // Calories
    val dailyCalorieGoal: Int = 2252,
    val activeCalorieGoal: Int = 600,
    // Cardio & sleep
    val weeklyCardioMinutesGoal: Int = 150,
    val sleepDurationGoalMinutes: Int = 420,
    // Body profile, used for heart-rate zone and BMI benchmarks
    val age: Int = 26,
    val heightCm: Double = 175.0,
    val restingHeartRateBaseline: Int = 58
)

class GoalsStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("health_goals", Context.MODE_PRIVATE)

    fun load(): HealthGoals {
        val defaults = HealthGoals()
        return HealthGoals(
            dailyStepGoal = prefs.getInt(KEY_STEPS, defaults.dailyStepGoal),
            dailyDistanceGoalKm = Double.fromBits(prefs.getLong(KEY_DISTANCE, defaults.dailyDistanceGoalKm.toRawBits())),
            weeklyExerciseDaysGoal = prefs.getInt(KEY_EXERCISE_DAYS, defaults.weeklyExerciseDaysGoal),
            dailyCalorieGoal = prefs.getInt(KEY_CALORIES, defaults.dailyCalorieGoal),
            activeCalorieGoal = prefs.getInt(KEY_ACTIVE_CALORIES, defaults.activeCalorieGoal),
            weeklyCardioMinutesGoal = prefs.getInt(KEY_CARDIO, defaults.weeklyCardioMinutesGoal),
            sleepDurationGoalMinutes = prefs.getInt(KEY_SLEEP, defaults.sleepDurationGoalMinutes),
            age = prefs.getInt(KEY_AGE, defaults.age),
            heightCm = Double.fromBits(prefs.getLong(KEY_HEIGHT, defaults.heightCm.toRawBits())),
            restingHeartRateBaseline = prefs.getInt(KEY_RESTING_HR, defaults.restingHeartRateBaseline)
        )
    }

    fun save(goals: HealthGoals) {
        prefs.edit()
            .putInt(KEY_STEPS, goals.dailyStepGoal)
            .putLong(KEY_DISTANCE, goals.dailyDistanceGoalKm.toRawBits())
            .putInt(KEY_EXERCISE_DAYS, goals.weeklyExerciseDaysGoal)
            .putInt(KEY_CALORIES, goals.dailyCalorieGoal)
            .putInt(KEY_ACTIVE_CALORIES, goals.activeCalorieGoal)
            .putInt(KEY_CARDIO, goals.weeklyCardioMinutesGoal)
            .putInt(KEY_SLEEP, goals.sleepDurationGoalMinutes)
            .putInt(KEY_AGE, goals.age)
            .putLong(KEY_HEIGHT, goals.heightCm.toRawBits())
            .putInt(KEY_RESTING_HR, goals.restingHeartRateBaseline)
            .apply()
    }

    private companion object {
        const val KEY_STEPS = "daily_steps"
        const val KEY_DISTANCE = "daily_distance_km"
        const val KEY_EXERCISE_DAYS = "weekly_exercise_days"
        const val KEY_CALORIES = "daily_calories"
        const val KEY_ACTIVE_CALORIES = "active_calories"
        const val KEY_CARDIO = "weekly_cardio_minutes"
        const val KEY_SLEEP = "sleep_minutes"
        const val KEY_AGE = "age"
        const val KEY_HEIGHT = "height_cm"
        const val KEY_RESTING_HR = "resting_hr"
    }
}
