package com.healthcompanion.app.model

data class UserHealthProfile(
    val age: Int = 26,
    val weightKg: Double = 68.0,
    val heightCm: Double = 175.0,
    val restingHeartRateBaseline: Int = 58
) {
    val maxHeartRate: Int
        get() = (208 - 0.7 * age).toInt()

    val heartRateReserve: Int
        get() = (maxHeartRate - restingHeartRateBaseline).coerceAtLeast(40)

    val lightThreshold: Int
        get() = (restingHeartRateBaseline + heartRateReserve * 0.50f).toInt()
    val moderateThreshold: Int
        get() = (restingHeartRateBaseline + heartRateReserve * 0.60f).toInt()
    val vigorousThreshold: Int
        get() = (restingHeartRateBaseline + heartRateReserve * 0.75f).toInt()
    val peakThreshold: Int
        get() = (restingHeartRateBaseline + heartRateReserve * 0.88f).toInt()
}

object HealthBenchmarkEngine {
    val defaultProfile = UserHealthProfile()

    const val CLINICAL_MIN_SPO2 = 95
    const val CLINICAL_MAX_SPO2 = 100

    const val NORMAL_RESTING_HR_MIN = 50
    const val NORMAL_RESTING_HR_MAX = 80

    const val DAILY_STEP_GOAL = 10000
    const val WEEKLY_EXERCISE_DAYS_GOAL = 5

    fun isSpO2InRange(percent: Int): Boolean = percent in CLINICAL_MIN_SPO2..CLINICAL_MAX_SPO2

    fun isRestingHrInRange(bpm: Int, baseline: Int = defaultProfile.restingHeartRateBaseline): Boolean {
        return bpm in (baseline - 8)..(baseline + 8)
    }
}
