package com.healthcompanion.app.model

import java.time.Duration
import kotlin.math.roundToInt

data class SleepScoreResult(
    val score: Int,             // 0 to 100
    val durationScore: Int,     // 0 to 50
    val deepSleepScore: Int,    // 0 to 20
    val remSleepScore: Int,     // 0 to 15
    val restorationScore: Int,  // 0 to 15
    val rating: String,         // "Excellent", "Good", "Fair", "Poor"
    val summaryText: String
)

object SleepScoreCalculator {

    /**
     * Evidence-based sleep scoring model grounded in National Sleep Foundation & sleep medicine standards.
     * Total score = 100 points maximum:
     * - Total sleep duration: 50 pts (7h-9h ideal)
     * - Deep sleep proportion: 20 pts (15%-25% ideal)
     * - REM sleep proportion: 15 pts (20%-25% ideal)
     * - Sleep restoration / awake penalty: 15 pts (<10% awake ideal)
     */
    fun calculate(
        session: SleepSession,
        historicalSessions: List<SleepSession> = emptyList()
    ): SleepScoreResult {
        val totalAsleepMinutes = session.totalAsleepMinutes.toFloat()
        val totalInBedMinutes = session.totalInBedMinutes.toFloat().coerceAtLeast(1f)

        // 1. Duration Score (0 to 50 points)
        // 7 to 9 hours (420 to 540 min) is optimal (50 pts)
        val durationScore = when {
            totalAsleepMinutes >= 420f && totalAsleepMinutes <= 540f -> 50
            totalAsleepMinutes < 420f -> {
                val ratio = (totalAsleepMinutes - 180f) / (420f - 180f)
                (10f + (ratio.coerceIn(0f, 1f) * 40f)).roundToInt()
            }
            else -> {
                val excess = (totalAsleepMinutes - 540f) / 120f
                (50f - (excess.coerceIn(0f, 1f) * 10f)).roundToInt()
            }
        }.coerceIn(10, 50)

        // 2. Deep Sleep Score (0 to 20 points)
        // 15% to 25% of sleep time is optimal
        val deepMins = session.stages.filter { it.stage == SleepStageType.DEEP }.sumOf { it.durationMinutes }.toFloat()
        val deepPct = (deepMins / totalInBedMinutes) * 100f
        val deepScore = when {
            deepPct in 15f..25f -> 20
            deepPct < 15f -> ((deepPct / 15f) * 20f).roundToInt()
            else -> 18
        }.coerceIn(0, 20)

        // 3. REM Sleep Score (0 to 15 points)
        // 20% to 25% of sleep time is optimal
        val remMins = session.stages.filter { it.stage == SleepStageType.REM }.sumOf { it.durationMinutes }.toFloat()
        val remPct = (remMins / totalInBedMinutes) * 100f
        val remScore = when {
            remPct in 20f..28f -> 15
            remPct < 20f -> ((remPct / 20f) * 15f).roundToInt()
            else -> 14
        }.coerceIn(0, 15)

        // 4. Restoration / Awake penalty (0 to 15 points)
        val awakeMins = session.totalAwakeMinutes.toFloat()
        val awakePct = (awakeMins / totalInBedMinutes) * 100f
        val restorationScore = when {
            awakePct <= 5f -> 15
            awakePct <= 10f -> 12
            awakePct <= 15f -> 8
            awakePct <= 20f -> 4
            else -> 0
        }

        val totalScore = (durationScore + deepScore + remScore + restorationScore).coerceIn(0, 100)

        val rating = when {
            totalScore >= 90 -> "Excellent"
            totalScore >= 80 -> "Good"
            totalScore >= 70 -> "Fair"
            else -> "Poor"
        }

        val summaryText = when {
            totalScore >= 90 -> "You got high quality, restorative sleep across all stages."
            totalScore >= 80 -> "A good night's sleep needed to build a strong foundation for your day."
            totalScore >= 70 -> "Fair sleep quality. Consider getting to bed slightly earlier tonight."
            else -> "Short or interrupted sleep. Give yourself extra recovery time today."
        }

        return SleepScoreResult(
            score = totalScore,
            durationScore = durationScore,
            deepSleepScore = deepScore,
            remSleepScore = remScore,
            restorationScore = restorationScore,
            rating = rating,
            summaryText = summaryText
        )
    }
}
