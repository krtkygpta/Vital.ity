package com.healthcompanion.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthcompanion.app.model.DailyHeartRateSummary
import com.healthcompanion.app.model.HeartRateSample
import com.healthcompanion.app.ui.theme.*
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun HeartRateChart(
    summary: DailyHeartRateSummary?,
    scrubbedSample: HeartRateSample? = null,
    onScrub: (HeartRateSample?) -> Unit = {},
    isRelativeTime: Boolean = false,
    workoutStartTime: Instant? = null,
    workoutDurationMinutes: Int? = null,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current

    if (summary == null || summary.samples.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No heart rate data",
                color = healthColors.textMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    // Theme-aware overlay colors hoisted out of the Canvas draw lambda
    val sampleStemColor = healthColors.textMuted
    val scrubGuideColor = healthColors.textPrimary
    val scrubKnobCenterColor = healthColors.cardBackground

    // The axis is derived from the data. It used to be pinned to 30–180 bpm
    // regardless of the user, so a resting rate of ~50 occupied the bottom 20%
    // of the frame and a 70 bpm day looked flat. The window is clamped to a
    // span that keeps ordinary variation legible, and widened to cover the
    // highest sample when someone is genuinely near their ceiling.
    val chartSamples = summary.samples
    val observedMax = (chartSamples.maxOfOrNull { it.bpm } ?: 0).toFloat()
    val observedMin = (chartSamples.minOfOrNull { it.bpm } ?: 0).toFloat()
    val dataSpan = (observedMax - observedMin).coerceAtLeast(20f)
    val paddedSpan = dataSpan * 1.3f
    val maxChartBpm = maxOf(observedMax + paddedSpan * 0.15f, 120f)
    val minChartBpm = (observedMin - paddedSpan * 0.25f).coerceAtLeast(20f)
    val bpmRange = (maxChartBpm - minChartBpm).coerceAtLeast(10f)

    // Zone thresholds matching media_1790630580526.png
    val yLabels = listOf(
        166 to healthColors.heartPeak,
        135 to healthColors.heartVigorous,
        110 to healthColors.heartModerate,
        71 to healthColors.accentTeal,
        30 to healthColors.heartResting
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Chart Canvas with right Y-axis and Interactive Scrubber
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(summary.samples, isRelativeTime, workoutDurationMinutes) {
                        val yAxisWidth = 36.dp.toPx()
                        val chartWidth = size.width - yAxisWidth
                        val zone = ZoneId.systemDefault()
                        val totalDurationSec = if (isRelativeTime) {
                            ((workoutDurationMinutes ?: 45) * 60).toFloat().coerceAtLeast(60f)
                        } else 86400f
                        val workoutStart = workoutStartTime ?: summary.samples.firstOrNull()?.timestamp ?: Instant.now()

                        fun updateScrubAt(touchX: Float) {
                            if (summary.samples.isEmpty()) return
                            val clampedX = touchX.coerceIn(0f, chartWidth)
                            val fraction = clampedX / chartWidth.coerceAtLeast(1f)

                            val nearest = if (isRelativeTime) {
                                val targetInstant = workoutStart.plusSeconds((fraction * totalDurationSec).toLong())
                                summary.samples.minByOrNull { s ->
                                    Math.abs(Duration.between(s.timestamp, targetInstant).toMillis())
                                }
                            } else {
                                val targetMinute = fraction * 1440f
                                summary.samples.minByOrNull { s ->
                                    val zdt = s.timestamp.atZone(zone)
                                    val m = zdt.hour * 60 + zdt.minute
                                    Math.abs(m - targetMinute)
                                }
                            }
                            onScrub(nearest)
                        }

                        detectTapGestures(
                            onTap = { offset ->
                                updateScrubAt(offset.x)
                            }
                        )
                    }
                    .pointerInput(summary.samples, isRelativeTime, workoutDurationMinutes) {
                        val yAxisWidth = 36.dp.toPx()
                        val chartWidth = size.width - yAxisWidth
                        val zone = ZoneId.systemDefault()
                        val totalDurationSec = if (isRelativeTime) {
                            ((workoutDurationMinutes ?: 45) * 60).toFloat().coerceAtLeast(60f)
                        } else 86400f
                        val workoutStart = workoutStartTime ?: summary.samples.firstOrNull()?.timestamp ?: Instant.now()

                        fun updateScrubAt(touchX: Float) {
                            if (summary.samples.isEmpty()) return
                            val clampedX = touchX.coerceIn(0f, chartWidth)
                            val fraction = clampedX / chartWidth.coerceAtLeast(1f)

                            val nearest = if (isRelativeTime) {
                                val targetInstant = workoutStart.plusSeconds((fraction * totalDurationSec).toLong())
                                summary.samples.minByOrNull { s ->
                                    Math.abs(Duration.between(s.timestamp, targetInstant).toMillis())
                                }
                            } else {
                                val targetMinute = fraction * 1440f
                                summary.samples.minByOrNull { s ->
                                    val zdt = s.timestamp.atZone(zone)
                                    val m = zdt.hour * 60 + zdt.minute
                                    Math.abs(m - targetMinute)
                                }
                            }
                            onScrub(nearest)
                        }

                        detectDragGestures(
                            onDragStart = { offset ->
                                updateScrubAt(offset.x)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                updateScrubAt(change.position.x)
                            },
                            onDragEnd = {
                                // Keep the scrubbed sample highlighted until user taps elsewhere or dismisses
                            }
                        )
                    }
            ) {
                val yAxisWidth = 36.dp.toPx()
                val chartWidth = size.width - yAxisWidth
                val chartHeight = size.height - 12.dp.toPx()

                fun bpmToY(bpm: Float): Float {
                    val norm = (bpm - minChartBpm) / bpmRange
                    return chartHeight - (norm.coerceIn(0f, 1f) * chartHeight)
                }

                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 5f), 0f)

                // Reference zone horizontal dotted lines
                yLabels.forEach { (bpm, color) ->
                    val y = bpmToY(bpm.toFloat())
                    drawLine(
                        color = color.copy(alpha = 0.7f),
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = dashEffect
                    )
                }

                if (summary.samples.isNotEmpty()) {
                    val zone = ZoneId.systemDefault()
                    val totalDurationSec = if (isRelativeTime) {
                        ((workoutDurationMinutes ?: 45) * 60).toFloat().coerceAtLeast(60f)
                    } else 86400f
                    val workoutStart = workoutStartTime ?: summary.samples.first().timestamp

                    val points = mutableListOf<Pair<Offset, Int>>()

                    summary.samples.forEach { sample ->
                        val x = if (isRelativeTime) {
                            val elapsedSec = Duration.between(workoutStart, sample.timestamp).seconds.toFloat().coerceAtLeast(0f)
                            (elapsedSec / totalDurationSec).coerceIn(0f, 1f) * chartWidth
                        } else {
                            val zdt = sample.timestamp.atZone(zone)
                            val minuteOfDay = zdt.hour * 60 + zdt.minute
                            (minuteOfDay.toFloat() / 1440f) * chartWidth
                        }
                        val y = bpmToY(sample.bpm.toFloat())
                        points.add(Offset(x, y) to sample.bpm)
                    }


                    // 2. Multi-zone colored thin line segments (1.5 dp stroke)
                    for (i in 0 until points.size - 1) {
                        val p1 = points[i].first
                        val bpm1 = points[i].second
                        val p2 = points[i + 1].first
                        val bpm2 = points[i + 1].second

                        val maxBpmInSeg = maxOf(bpm1, bpm2)
                        val segmentColor = when {
                            maxBpmInSeg >= 166 -> healthColors.heartPeak
                            maxBpmInSeg >= 135 -> healthColors.heartVigorous
                            maxBpmInSeg >= 110 -> healthColors.heartModerate
                            else -> healthColors.heartLight
                        }

                        drawLine(
                            color = segmentColor,
                            start = p1,
                            end = p2,
                            strokeWidth = 1.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // 3. Draw Interactive Scrubber if user is scrubbing/clicking
                    scrubbedSample?.let { scrubbed ->
                        val scrubX = if (isRelativeTime) {
                            val elapsedSec = Duration.between(workoutStart, scrubbed.timestamp).seconds.toFloat().coerceAtLeast(0f)
                            (elapsedSec / totalDurationSec).coerceIn(0f, 1f) * chartWidth
                        } else {
                            val zdt = scrubbed.timestamp.atZone(zone)
                            val minuteOfDay = zdt.hour * 60 + zdt.minute
                            (minuteOfDay.toFloat() / 1440f) * chartWidth
                        }
                        val scrubY = bpmToY(scrubbed.bpm.toFloat())

                        val zoneColor = when {
                            scrubbed.bpm >= 166 -> healthColors.heartPeak
                            scrubbed.bpm >= 135 -> healthColors.heartVigorous
                            scrubbed.bpm >= 110 -> healthColors.heartModerate
                            else -> healthColors.heartLight
                        }

                        // Vertical scrubber guide line
                        drawLine(
                            color = scrubGuideColor.copy(alpha = 0.6f),
                            start = Offset(scrubX, 0f),
                            end = Offset(scrubX, chartHeight),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )

                        // Glowing scrubber knob
                        drawCircle(
                            color = zoneColor.copy(alpha = 0.35f),
                            radius = 11.dp.toPx(),
                            center = Offset(scrubX, scrubY)
                        )
                        drawCircle(
                            color = zoneColor,
                            radius = 6.dp.toPx(),
                            center = Offset(scrubX, scrubY)
                        )
                        drawCircle(
                            color = scrubKnobCenterColor,
                            radius = 3.dp.toPx(),
                            center = Offset(scrubX, scrubY)
                        )
                    }
                }
            }

            // Right-aligned Y-Axis Labels matching media_1790630580526.png
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                yLabels.forEach { (bpm, _) ->
                    Text(
                        text = "$bpm",
                        color = healthColors.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Ticks and labels (Relative workout minutes e.g. 0 min, 10 min, ... OR 24-Hour 0, 4, 8...)
        val xTicks = if (isRelativeTime) {
            val totalMin = (workoutDurationMinutes ?: 45).coerceAtLeast(1)
            val step = when {
                totalMin <= 20 -> 5
                totalMin <= 45 -> 10
                totalMin <= 90 -> 15
                else -> 30
            }
            val ticks = mutableListOf<String>()
            var m = 0
            while (m < totalMin) {
                ticks.add("${m}m")
                m += step
            }
            ticks.add("${totalMin}m")
            ticks
        } else {
            listOf("0", "4", "8", "12", "16", "20", "23")
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 36.dp, top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            xTicks.forEach { tick ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(4.dp)
                            .background(healthColors.textMuted)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = tick, color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Zone Legend matching media_1790630580526.png: Light (Cyan), Moderate (Green), Vigorous (Amber), Peak (Red)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(
                "Light" to healthColors.heartLight,
                "Moderate" to healthColors.heartModerate,
                "Vigorous" to healthColors.heartVigorous,
                "Peak" to healthColors.heartPeak
            ).forEach { (label, color) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(color, CircleShape)
                    )
                    Text(text = label, color = healthColors.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
