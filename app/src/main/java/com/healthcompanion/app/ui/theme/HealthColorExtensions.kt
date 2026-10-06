package com.healthcompanion.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.healthcompanion.app.model.BenchmarkStatus
import com.healthcompanion.app.model.HeartRateZone
import com.healthcompanion.app.model.HeartRateZoneType
import com.healthcompanion.app.model.SleepStageType

fun SleepStageType.color(colors: HealthColors): Color = when (this) {
    SleepStageType.AWAKE -> colors.sleepAwake
    SleepStageType.REM -> colors.sleepRem
    SleepStageType.LIGHT -> colors.sleepLight
    SleepStageType.DEEP -> colors.sleepDeep
}

@Composable
fun SleepStageType.color(): Color = color(LocalHealthColors.current)

fun BenchmarkStatus.color(colors: HealthColors): Color = when (this) {
    BenchmarkStatus.LOWER_THAN_USUAL -> colors.statusWarn
    BenchmarkStatus.TYPICAL -> colors.statusGood
    BenchmarkStatus.HIGHER_THAN_USUAL -> colors.accentTeal
}

@Composable
fun BenchmarkStatus.color(): Color = color(LocalHealthColors.current)

fun BenchmarkStatus.containerColor(colors: HealthColors): Color = when (this) {
    BenchmarkStatus.LOWER_THAN_USUAL -> if (colors.isDark) Color(0xFF382819) else Color(0xFFFFECB3)
    BenchmarkStatus.TYPICAL -> if (colors.isDark) Color(0xFF1B3624) else Color(0xFFC8E6C9)
    BenchmarkStatus.HIGHER_THAN_USUAL -> if (colors.isDark) Color(0xFF15333F) else Color(0xFFB2EBF2)
}

@Composable
fun BenchmarkStatus.containerColor(): Color = containerColor(LocalHealthColors.current)

fun HeartRateZoneType.color(colors: HealthColors): Color = when (this) {
    HeartRateZoneType.PEAK -> colors.heartPeak
    HeartRateZoneType.VIGOROUS -> colors.heartVigorous
    HeartRateZoneType.MODERATE -> colors.heartModerate
    HeartRateZoneType.LIGHT -> colors.heartLight
}

@Composable
fun HeartRateZoneType.color(): Color = color(LocalHealthColors.current)

fun HeartRateZone.color(colors: HealthColors): Color = type.color(colors)

@Composable
fun HeartRateZone.color(): Color = color(LocalHealthColors.current)
