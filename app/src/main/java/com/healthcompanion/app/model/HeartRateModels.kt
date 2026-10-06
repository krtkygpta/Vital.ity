package com.healthcompanion.app.model

import java.time.Instant
import java.time.LocalDate

data class HeartRateSample(
    val timestamp: Instant,
    val bpm: Int
)

enum class HeartRateZoneType {
    PEAK,
    VIGOROUS,
    MODERATE,
    LIGHT
}

data class HeartRateZone(
    val name: String,
    val minBpm: Int,
    val maxBpm: Int,
    val minutesSpent: Int,
    val percentage: Int,
    val type: HeartRateZoneType = HeartRateZoneType.LIGHT
)

data class DailyHeartRateSummary(
    val date: LocalDate,
    val minBpm: Int,
    val maxBpm: Int,
    val peakZoneSummary: String, // e.g. "High of 111 BPM in moderate zone"
    val samples: List<HeartRateSample>,
    val zones: List<HeartRateZone>
) {
    val latestBpm: Int
        get() = samples.lastOrNull()?.bpm ?: 71
}

data class RestingHeartRateDay(
    val date: LocalDate,
    val bpm: Int,
    val sparklineSamples: List<Int> = emptyList()
)

data class RestingHeartRatePeriodSummary(
    val averageBpm: Int,
    val comparisonText: String, // e.g. "Your average was 2 bpm higher than the previous period"
    val dailyRecords: List<RestingHeartRateDay>,
    /**
     * The user's own typical band, taken from trailing history rather than from
     * the visible period. Deriving it from the period's own min/max would make
     * every point "in range" by definition, so the band has to be independent
     * of what is currently on screen.
     */
    val personalRangeMin: Int? = null,
    val personalRangeMax: Int? = null
)
