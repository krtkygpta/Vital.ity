package com.healthcompanion.app.model

import java.time.Instant

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class ExerciseType(val label: String, val iconName: String) {
    WALK("Walk", "directions_walk"),
    RUN("Run", "directions_run"),
    CYCLING("Cycling", "directions_bike"),
    HIIT("HIIT", "fitness_center"),
    STRENGTH_TRAINING("Strength training", "fitness_center"),
    SWIMMING("Swimming", "pool"),
    BADMINTON("Badminton", "sports_tennis"),
    TENNIS("Tennis", "sports_tennis"),
    YOGA("Yoga", "self_improvement"),
    SOCCER("Football", "sports_soccer"),
    BASKETBALL("Basketball", "sports_basketball"),
    WORKOUT("Workout", "fitness_center");

    fun getIcon(): ImageVector = when (this) {
        WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
        RUN -> Icons.AutoMirrored.Filled.DirectionsRun
        CYCLING -> Icons.AutoMirrored.Filled.DirectionsBike
        SWIMMING -> Icons.Default.Pool
        BADMINTON, TENNIS -> Icons.Default.SportsTennis
        YOGA -> Icons.Default.SelfImprovement
        SOCCER -> Icons.Default.SportsSoccer
        BASKETBALL -> Icons.Default.SportsBasketball
        HIIT, STRENGTH_TRAINING, WORKOUT -> Icons.Default.FitnessCenter
    }

    /**
     * Only outdoor locomotion is actually tracked over distance, so only these
     * activities can meaningfully carry a GPS polyline route.
     */
    val isGpsBased: Boolean
        get() = this == WALK || this == RUN || this == CYCLING

    companion object {
        fun fromSport(sport: String): ExerciseType = when (sport.lowercase()) {
            "running", "run" -> RUN
            "walking", "walk" -> WALK
            "cycling", "spinning" -> CYCLING
            "swimming" -> SWIMMING
            "yoga", "pilates" -> YOGA
            "hiit" -> HIIT
            "strength training", "strength" -> STRENGTH_TRAINING
            "badminton" -> BADMINTON
            "soccer", "football" -> SOCCER
            "basketball" -> BASKETBALL
            "tennis" -> TENNIS
            else -> WORKOUT
        }
    }
}

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double = 0.0,
    val timestamp: Instant = Instant.EPOCH
)

data class ExerciseWorkout(
    val id: String,
    val type: ExerciseType,
    val title: String,
    val startTime: Instant,
    val durationMinutes: Int,
    val cardioLoad: Int,
    val distanceKm: Double?,
    val caloriesBurnedKcal: Int,
    val route: List<GeoPoint>?,
    val heartRateSamples: List<HeartRateSample>,
    val zones: List<HeartRateZone>,
    val avgPaceMinKm: String? = null,
    val elevationGainMeters: Int? = null,
    val avgSpeedKmH: Double? = null,
    val steps: Int? = null,
    val routeConsentRequired: Boolean = false
)
