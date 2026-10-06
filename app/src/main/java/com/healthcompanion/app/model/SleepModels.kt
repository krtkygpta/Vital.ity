package com.healthcompanion.app.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

enum class SleepStageType(val label: String) {
    AWAKE("Total awake"),
    REM("REM"),
    LIGHT("Light"),
    DEEP("Deep");

    val isSleep: Boolean
        get() = this != AWAKE
}

enum class BenchmarkStatus(val label: String) {
    LOWER_THAN_USUAL("Lower than usual"),
    TYPICAL("Typical"),
    HIGHER_THAN_USUAL("Higher than usual")
}

data class SleepStageInterval(
    val stage: SleepStageType,
    val startTime: Instant,
    val endTime: Instant
) {
    val durationMinutes: Long
        get() = Duration.between(startTime, endTime).toMinutes()
}

data class SleepStageStat(
    val stage: SleepStageType,
    val durationMinutes: Long,
    val percentage: Int,
    val benchmarkStatus: BenchmarkStatus,
    val benchmarkRange: Pair<Int, Int> = 10 to 30 // typical percentage range
)

data class SleepSession(
    val id: String,
    val sourceApp: String, // e.g. "Nothing X", "Pixel Watch", "Fitbit"
    val startTime: Instant,
    val endTime: Instant,
    val stages: List<SleepStageInterval>,
    val sleepEfficiency: Int = 100, // e.g. 100%
    val isMainSleep: Boolean = true
) {
    val totalInBedMinutes: Long
        get() = Duration.between(startTime, endTime).toMinutes()

    val totalAsleepMinutes: Long
        get() = stages.filter { it.stage.isSleep }
            .sumOf { it.durationMinutes }
            .let { if (it > 0) it else totalInBedMinutes }

    val totalAwakeMinutes: Long
        get() = stages.filter { it.stage == SleepStageType.AWAKE }.sumOf { it.durationMinutes }

    val sleepScore: Int
        get() {
            val durationScore = ((totalAsleepMinutes.toFloat() / 480f).coerceIn(0f, 1f) * 50).toInt()
            val efficiencyScore = (sleepEfficiency.toFloat() * 0.3f).toInt()
            val deepRemMins = stages.filter { it.stage == SleepStageType.DEEP || it.stage == SleepStageType.REM }.sumOf { it.durationMinutes }
            val qualityScore = ((deepRemMins.toFloat() / 120f).coerceIn(0f, 1f) * 20).toInt()
            return (durationScore + efficiencyScore + qualityScore).coerceIn(50, 100)
        }

    val stageStats: List<SleepStageStat>
        get() {
            val total = if (totalInBedMinutes > 0) totalInBedMinutes else 1
            return SleepStageType.entries.map { stage ->
                val mins = stages.filter { it.stage == stage }.sumOf { it.durationMinutes }
                val pct = ((mins.toFloat() / total) * 100).toInt()
                val status = when (stage) {
                    SleepStageType.AWAKE -> if (pct > 15) BenchmarkStatus.HIGHER_THAN_USUAL else BenchmarkStatus.LOWER_THAN_USUAL
                    SleepStageType.REM -> if (pct < 15) BenchmarkStatus.LOWER_THAN_USUAL else if (pct > 25) BenchmarkStatus.HIGHER_THAN_USUAL else BenchmarkStatus.TYPICAL
                    SleepStageType.LIGHT -> if (pct in 40..60) BenchmarkStatus.TYPICAL else if (pct < 40) BenchmarkStatus.LOWER_THAN_USUAL else BenchmarkStatus.HIGHER_THAN_USUAL
                    SleepStageType.DEEP -> if (pct > 25) BenchmarkStatus.HIGHER_THAN_USUAL else if (pct < 10) BenchmarkStatus.LOWER_THAN_USUAL else BenchmarkStatus.TYPICAL
                }
                SleepStageStat(
                    stage = stage,
                    durationMinutes = mins,
                    percentage = pct,
                    benchmarkStatus = status
                )
            }
        }
}

data class DaySleepRecord(
    val date: LocalDate,
    val sessions: List<SleepSession>
) {
    val primarySession: SleepSession?
        get() = sessions.maxByOrNull { it.totalInBedMinutes }

    val totalDurationMinutes: Long
        get() = sessions.sumOf { it.totalInBedMinutes }
}
