package com.healthcompanion.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthcompanion.app.model.SleepSession
import com.healthcompanion.app.model.SleepStageInterval
import com.healthcompanion.app.model.SleepStageType
import com.healthcompanion.app.ui.theme.*
import kotlin.math.abs
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HypnogramChart(
    session: SleepSession,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val totalDurationSeconds = Duration.between(session.startTime, session.endTime).seconds.coerceAtLeast(1)
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    val startTimeStr = timeFormatter.format(session.startTime)
    val midTimeStr = timeFormatter.format(session.startTime.plusSeconds(totalDurationSeconds / 2))
    val endTimeStr = timeFormatter.format(session.endTime)

    val awakeMins = session.totalAwakeMinutes
    val remMins = session.stages.filter { it.stage == SleepStageType.REM }.sumOf { it.durationMinutes }
    val lightMins = session.stages.filter { it.stage == SleepStageType.LIGHT }.sumOf { it.durationMinutes }
    val deepMins = session.stages.filter { it.stage == SleepStageType.DEEP }.sumOf { it.durationMinutes }

    fun formatDuration(minutes: Long): String {
        return if (minutes >= 60) {
            "${minutes / 60} h ${minutes % 60} m"
        } else {
            "$minutes min"
        }
    }

    val stageRows = listOf(
        SleepStageType.AWAKE to "Total awake • $awakeMins min",
        SleepStageType.REM to "REM • $remMins min",
        SleepStageType.LIGHT to "Light • ${formatDuration(lightMins)}",
        SleepStageType.DEEP to "Deep • ${formatDuration(deepMins)}"
    )

    var scrubOffsetSeconds by remember { mutableStateOf<Long?>(null) }

    val scrubbedInstant = scrubOffsetSeconds?.let { session.startTime.plusSeconds(it) }
    val scrubbedInterval = scrubbedInstant?.let { inst ->
        session.stages.firstOrNull { inst >= it.startTime && inst <= it.endTime }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Scrubber Info Pill when user taps or drags
        if (scrubOffsetSeconds != null && scrubbedInterval != null) {
            val scrubTimeStr = timeFormatter.format(scrubbedInstant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.s)
                    .clip(MaterialTheme.shapes.medium)
                    .background(healthColors.cardElevated)
                    .padding(horizontal = Spacing.m, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(scrubbedInterval.stage.color(healthColors))
                    )
                    Text(
                        text = "$scrubTimeStr • ${scrubbedInterval.stage.label} (${scrubbedInterval.durationMinutes} min)",
                        color = healthColors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(healthColors.pillUnselectedBg)
                        .clickable { scrubOffsetSeconds = null },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = healthColors.textPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        val trackHeightDp = 22.dp

        fun stageIndex(of: SleepStageType): Int = when (of) {
            SleepStageType.AWAKE -> 0
            SleepStageType.REM -> 1
            SleepStageType.LIGHT -> 2
            SleepStageType.DEEP -> 3
        }

        // Row positions are measured from the laid-out tracks so the label and
        // its track cannot drift apart, while the connector bands span between
        // rows without a second hand-maintained layout.
        val trackRects = remember { mutableStateListOf(*Array(4) { Rect.Zero }) }
        var chartOrigin by remember { mutableStateOf(Offset.Zero) }

        Box(modifier = Modifier.fillMaxWidth()) {
            // Layer 1 (bottom): connector bands between stage transitions.
            // Same color as track background so they blend in. Drawn behind
            // the per-row canvases so the colored pills sit on top.
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .onGloballyPositioned { chartOrigin = it.boundsInRoot().topLeft }
            ) {
                val yOf = { rect: Rect -> rect.center.y - chartOrigin.y }
                val connectorWidth = 2.dp.toPx()

                var previous: SleepStageInterval? = null
                session.stages.forEach { current ->
                    val prev = previous
                    if (prev != null && prev.stage != current.stage) {
                        val from = trackRects[stageIndex(prev.stage)]
                        val to = trackRects[stageIndex(current.stage)]
                        if (from != Rect.Zero && to != Rect.Zero) {
                            val transitionSec = Duration
                                .between(session.startTime, prev.endTime)
                                .seconds.coerceAtLeast(0)
                            val x = (transitionSec.toFloat() / totalDurationSeconds) * size.width
                            val y1 = yOf(from)
                            val y2 = yOf(to)
                            if (abs(y2 - y1) > 1f) {
                                drawRect(
                                    color = current.stage.color(healthColors).copy(alpha = 0.45f),
                                    topLeft = Offset(
                                        (x - connectorWidth / 2)
                                            .coerceIn(0f, size.width - connectorWidth),
                                        minOf(y1, y2)
                                    ),
                                    size = Size(connectorWidth, abs(y2 - y1))
                                )
                            }
                        }
                    }
                    previous = current
                }
            }

            // Layer 2 (middle): per-row canvases with labels and colored pills.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                stageRows.forEachIndexed { index, (stage, label) ->
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(stage.color(healthColors))
                            )
                            Text(
                                text = label,
                                color = healthColors.textPrimary,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(trackHeightDp)
                                .onGloballyPositioned { coords ->
                                    trackRects[index] = coords.boundsInRoot()
                                }
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(session) {
                                        detectTapGestures { offset ->
                                            val ratio =
                                                (offset.x / size.width).coerceIn(0f, 1f)
                                            scrubOffsetSeconds =
                                                (ratio * totalDurationSeconds).toLong()
                                        }
                                    }
                                    .pointerInput(session) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                val ratio =
                                                    (offset.x / size.width).coerceIn(0f, 1f)
                                                scrubOffsetSeconds =
                                                    (ratio * totalDurationSeconds).toLong()
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                val ratio = (change.position.x / size.width)
                                                    .coerceIn(0f, 1f)
                                                scrubOffsetSeconds =
                                                    (ratio * totalDurationSeconds).toLong()
                                            }
                                        )
                                    }
                            ) {
                                val trackH = size.height

                                // Background track.
                                drawRoundRect(
                                    color = healthColors.cardBackground,
                                    topLeft = Offset(0f, 0f),
                                    size = Size(size.width, trackH),
                                    cornerRadius = CornerRadius(trackH / 2, trackH / 2)
                                )

                                // Coloured segments for this stage only.
                                session.stages
                                    .filter { it.stage == stage }
                                    .forEach { interval ->
                                        val startSec = Duration
                                            .between(session.startTime, interval.startTime)
                                            .seconds.coerceAtLeast(0)
                                        val endSec = Duration
                                            .between(session.startTime, interval.endTime)
                                            .seconds.coerceAtMost(totalDurationSeconds)

                                        val startX = (startSec.toFloat() / totalDurationSeconds) *
                                            size.width
                                        val endX = (endSec.toFloat() / totalDurationSeconds) *
                                            size.width
                                        val pillWidth = (endX - startX).coerceAtLeast(3.dp.toPx())
                                        val r = 5.dp.toPx()

                                        drawRoundRect(
                                            color = interval.stage.color(healthColors),
                                            topLeft = Offset(
                                                startX.coerceIn(
                                                    0f,
                                                    (size.width - pillWidth).coerceAtLeast(0f)
                                                ),
                                                0f
                                            ),
                                            size = Size(pillWidth, trackH),
                                            cornerRadius = CornerRadius(
                                                minOf(r, pillWidth / 2),
                                                minOf(r, trackH / 2)
                                            )
                                        )
                                    }

                                // Knob marks the stage currently scrubbed.
                                scrubOffsetSeconds?.let { sec ->
                                    if (scrubbedInterval?.stage == stage) {
                                        val scrubX = (sec.toFloat() / totalDurationSeconds) *
                                            size.width
                                        val knobColor = stage.color(healthColors)
                                        drawCircle(
                                            color = knobColor.copy(alpha = 0.35f),
                                            radius = 10.dp.toPx(),
                                            center = Offset(scrubX, trackH / 2)
                                        )
                                        drawCircle(
                                            color = knobColor,
                                            radius = 5.dp.toPx(),
                                            center = Offset(scrubX, trackH / 2)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Layer 3 (top): cross-row dashed scrub guide.
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(session) {
                        detectTapGestures { offset ->
                            val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                            scrubOffsetSeconds = (ratio * totalDurationSeconds).toLong()
                        }
                    }
                    .pointerInput(session) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                                scrubOffsetSeconds = (ratio * totalDurationSeconds).toLong()
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val ratio = (change.position.x / size.width)
                                    .coerceIn(0f, 1f)
                                scrubOffsetSeconds = (ratio * totalDurationSeconds).toLong()
                            }
                        )
                    }
            ) {
                scrubOffsetSeconds?.let { sec ->
                    val scrubX = (sec.toFloat() / totalDurationSeconds) * size.width
                    drawLine(
                        color = healthColors.textPrimary.copy(alpha = 0.5f),
                        start = Offset(scrubX, 0f),
                        end = Offset(scrubX, size.height),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.l))

        // Time ticks below hypnogram
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(text = startTimeStr, color = healthColors.textPrimary, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                Text(text = "Time that you went to bed", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = midTimeStr, color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = endTimeStr, color = healthColors.textPrimary, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                Text(text = "Time out of bed", color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
