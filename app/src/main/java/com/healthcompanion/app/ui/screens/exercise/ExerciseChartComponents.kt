package com.healthcompanion.app.ui.screens.exercise

import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthcompanion.app.model.ExerciseWorkout
import com.healthcompanion.app.ui.components.EmptyChartPlaceholder
import com.healthcompanion.app.ui.theme.LocalHealthColors
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.max

/**
 * Workout start time chart plotted on a 6am to 10pm vertical time scale.
 * Days with workouts display an accent teal badge at the workout's start time.
 */
@Composable
fun WorkoutTimeChart(
    days: List<LocalDate>,
    workoutsByDay: Map<LocalDate, List<ExerciseWorkout>>,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val zone = remember { ZoneId.systemDefault() }

    // §5.7: an empty frame read as "no workouts at all" rather than as no data.
    if (days.isEmpty() || workoutsByDay.isEmpty()) {
        EmptyChartPlaceholder(
            title = "No workouts",
            subtitle = "No exercise sessions have been recorded for this period.",
            modifier = modifier
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 48.dp)
        ) {
            val w = size.width
            val h = size.height

            // Guide lines. §5.3: the window was hardcoded to 6am–10pm, so a 5am
            // run or an 11pm workout was clipped onto the edge of the frame. It
            // is now derived from the actual session times, rounded outwards.
            val sessionHours = workoutsByDay.values
                .flatten()
                .map { it.startTime.atZone(zone).let { z -> z.hour + z.minute / 60f } }
            val observedMin = sessionHours.minOrNull()
            val observedMax = sessionHours.maxOrNull()
            val minHour = ((observedMin ?: 6f) - 1f).coerceAtLeast(0f).toInt().toFloat()
            val maxHour = ((observedMax ?: 22f) + 1f).coerceAtMost(24f).toInt().toFloat()
            val hourRange = (maxHour - minHour).coerceAtLeast(1f)

            val guideHours = listOf(minHour, (minHour + maxHour) / 2f, maxHour).distinct()
            guideHours.forEach { hour ->
                val y = h - ((hour - minHour) / hourRange) * h
                drawLine(
                    color = healthColors.cardBorder.copy(alpha = 0.35f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            if (days.isNotEmpty()) {
                val slot = w / days.size
                days.forEachIndexed { index, d ->
                    val dayWorkouts = workoutsByDay[d].orEmpty()
                    if (dayWorkouts.isNotEmpty()) {
                        val primary = dayWorkouts.maxByOrNull { it.cardioLoad } ?: dayWorkouts.first()
                        val zt = primary.startTime.atZone(zone)
                        val hourFraction = (zt.hour + zt.minute / 60f).coerceIn(minHour, maxHour)
                        val y = h - ((hourFraction - minHour) / hourRange) * h
                        val x = index * slot + slot / 2f

                        // Outer teal circle
                        drawCircle(
                            color = healthColors.accentTeal,
                            radius = 6.dp.toPx(),
                            center = Offset(x, y)
                        )
                        // Inner ring/dot
                        drawCircle(
                            color = healthColors.onAccentTeal,
                            radius = 2.5.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }
            }
        }

        // Y-axis labels on right
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            Text(text = "10pm", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
            Text(text = "6pm", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
            Text(text = "12pm", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
            Text(text = "6am", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Date / weekday indicator labels positioned beneath exercise charts.
 */
@Composable
fun ChartDateLabels(
    days: List<LocalDate>,
    selectedDate: LocalDate,
    isWeek: Boolean,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    if (isWeek) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(end = 48.dp, top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            days.forEach { d ->
                Text(
                    text = d.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()).take(1),
                    color = if (d == selectedDate) healthColors.accentTeal else healthColors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (d == selectedDate) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(end = 48.dp, top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val step = max(1, days.size / 5)
            val sampleIndices = listOf(0, step, step * 2, step * 3, step * 4, days.lastIndex).distinct()
            sampleIndices.forEach { idx ->
                val d = days.getOrNull(idx)
                if (d != null) {
                    Text(
                        text = "${d.dayOfMonth}",
                        color = if (d == selectedDate) healthColors.accentTeal else healthColors.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (d == selectedDate) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
