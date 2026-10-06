package com.healthcompanion.app.data.usecase

import com.healthcompanion.app.data.healthconnect.HealthConnectManager
import com.healthcompanion.app.model.ExerciseType
import com.healthcompanion.app.model.ExerciseWorkout
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Everything needed to log a workout, in one named type instead of four loose parameters. */
data class WorkoutDraft(
    val sport: String,
    val durationMinutes: Int,
    val calories: Int,
    val distanceKm: Double?
)

data class SleepDraft(
    val bedHour: Int,
    val bedMinute: Int,
    val wakeHour: Int,
    val wakeMinute: Int
)

/**
 * The single write path for every manually logged value.
 *
 * Previously the Today tab and the Fitness tab each had their own copy of this
 * logic. The Fitness copy discarded the `Result` from `insertWeight`,
 * `insertHydration`, `insertSleepSession`, `insertHeartRate` and
 * `insertBloodPressure`, so a user logging into a denied permission saw no
 * feedback at all and believed it had saved. Both paths now return the result,
 * and neither one writes invented data when Health Connect is unavailable.
 */
class LogHealthUseCase(
    private val healthConnectManager: HealthConnectManager,
    private val now: () -> Instant = Instant::now,
    private val today: () -> LocalDate = LocalDate::now
) {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    suspend fun logWorkout(draft: WorkoutDraft): Result<Unit> {
        val exerciseType = ExerciseType.fromSport(draft.sport)
        val start = now().minus(draft.durationMinutes.toLong(), ChronoUnit.MINUTES)
        val workout = ExerciseWorkout(
            id = UUID.randomUUID().toString(),
            type = exerciseType,
            title = draft.sport,
            startTime = start,
            durationMinutes = draft.durationMinutes,
            cardioLoad = cardioLoad(draft.durationMinutes, draft.calories),
            distanceKm = draft.distanceKm,
            caloriesBurnedKcal = draft.calories,
            route = null,
            heartRateSamples = emptyList(),
            zones = emptyList()
        )
        return healthConnectManager.insertWorkout(workout)
    }

    suspend fun logWeight(weightKg: Double): Result<Unit> =
        healthConnectManager.insertWeight(weightKg)

    suspend fun logWater(amountMl: Int): Result<Unit> =
        healthConnectManager.insertHydration(amountMl.toDouble())

    suspend fun logHeartRate(bpm: Int): Result<Unit> =
        healthConnectManager.insertHeartRate(bpm)

    suspend fun logBloodPressure(systolic: Int, diastolic: Int): Result<Unit> =
        healthConnectManager.insertBloodPressure(systolic.toDouble(), diastolic.toDouble())

    /**
     * Bedtime is anchored to the previous evening and wake-up to today, so a
     * 23:00 → 07:00 session is eight hours rather than a negative duration.
     */
    suspend fun logSleep(draft: SleepDraft): Result<Unit> {
        val date = today()
        val bedInstant = date.minusDays(1)
            .atTime(draft.bedHour, draft.bedMinute)
            .atZone(zone)
            .toInstant()
        val wakeInstant = date.atTime(draft.wakeHour, draft.wakeMinute)
            .atZone(zone)
            .toInstant()
        if (!wakeInstant.isAfter(bedInstant)) {
            return Result.failure(
                IllegalArgumentException("Wake-up time must be after bedtime")
            )
        }
        return healthConnectManager.insertSleepSession(bedInstant, wakeInstant)
    }

    /**
     * An estimate of cardiovascular load, used only for the in-memory optimistic
     * row. It is never written to Health Connect as a measurement.
     */
    private fun cardioLoad(durationMinutes: Int, calories: Int): Int =
        ((durationMinutes * 0.6) + (calories / 25.0)).toInt().coerceAtLeast(3)
}
