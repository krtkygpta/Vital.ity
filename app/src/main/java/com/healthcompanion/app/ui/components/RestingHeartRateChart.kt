package com.healthcompanion.app.ui.components

import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthcompanion.app.model.RestingHeartRateDay
import com.healthcompanion.app.model.RestingHeartRatePeriodSummary
import com.healthcompanion.app.ui.theme.*
import java.time.format.DateTimeFormatter

@Composable
fun RestingHeartRateChart(
    summary: RestingHeartRatePeriodSummary,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val records = summary.dailyRecords.reversed() // Oldest to newest for X-axis

    var scrubbedIndex by remember { mutableStateOf<Int?>(null) }

    // §5.7: an empty frame with axis numbers reads as a flat 0 bpm line, which is
    // a materially different claim from "no readings were recorded".
    if (records.isEmpty()) {
        EmptyChartPlaceholder(
            title = "No resting readings",
            subtitle = "No resting heart rate has been recorded for this period.",
            modifier = modifier
        )
        return
    }

    // §5.3: the window is derived from the data with a floor on the span. It was
    // previously rawMin-4..rawMax+4, which made a flat week look volatile.
    val rawMin = records.minOfOrNull { it.bpm } ?: 52
    val rawMax = records.maxOfOrNull { it.bpm } ?: 76
    val observedSpan = (rawMax - rawMin).coerceAtLeast(8)
    val minBpm = (rawMin - (observedSpan * 0.25f).toInt() - 2).coerceAtLeast(30).toFloat()
    val maxBpm = (rawMax + (observedSpan * 0.25f).toInt() + 2).coerceAtMost(180).toFloat()
    val bpmRange = (maxBpm - minBpm).coerceAtLeast(10f)

    val yAxisLabels = listOf(
        maxBpm.toInt(),
        (minBpm + bpmRange * 0.67f).toInt(),
        (minBpm + bpmRange * 0.33f).toInt(),
        minBpm.toInt()
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Scrubber feedback header
        val idx = scrubbedIndex
        if (idx != null && idx in records.indices) {
            val scrubbed = records[idx]
            val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${scrubbed.bpm}",
                            style = Typography.displaySmall,
                            color = healthColors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "bpm",
                            color = healthColors.textSecondary,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Text(
                        text = scrubbed.date.format(dateFmt),
                        color = healthColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                IconButton(onClick = { scrubbedIndex = null }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = healthColors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(records) {
                        if (records.isEmpty()) return@pointerInput
                        val yAxisWidth = 36.dp.toPx()
                        val chartWidth = size.width - yAxisWidth
                        val count = records.size

                        fun handleTouch(x: Float) {
                            val idx = ((x / chartWidth) * (count - 1)).toInt().coerceIn(0, count - 1)
                            scrubbedIndex = idx
                        }

                        detectTapGestures { offset -> handleTouch(offset.x) }
                    }
                    .pointerInput(records) {
                        if (records.isEmpty()) return@pointerInput
                        val yAxisWidth = 36.dp.toPx()
                        val chartWidth = size.width - yAxisWidth
                        val count = records.size

                        detectDragGestures(
                            onDragStart = { offset ->
                                val idx = ((offset.x / chartWidth) * (count - 1)).toInt().coerceIn(0, count - 1)
                                scrubbedIndex = idx
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val idx = ((change.position.x / chartWidth) * (count - 1)).toInt().coerceIn(0, count - 1)
                                scrubbedIndex = idx
                            }
                        )
                    }
            ) {
                val yAxisWidth = 36.dp.toPx()
                val chartWidth = size.width - yAxisWidth
                val chartHeight = size.height - 10.dp.toPx()

                fun bpmToY(bpm: Float): Float {
                    val norm = (bpm - minBpm) / bpmRange
                    return chartHeight - (norm.coerceIn(0f, 1f) * chartHeight)
                }

                val gridColor = healthColors.cardBorder

                // Grid lines for Y-axis levels
                yAxisLabels.forEach { bpm ->
                    val y = bpmToY(bpm.toFloat())
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1f
                    )
                }

                if (records.isNotEmpty()) {
                    val path = Path()
                    val points = mutableListOf<Offset>()

                    records.forEachIndexed { index, day ->
                        val x = if (records.size > 1) {
                            (index.toFloat() / (records.size - 1)) * chartWidth
                        } else {
                            chartWidth / 2f
                        }
                        val y = bpmToY(day.bpm.toFloat())
                        val pt = Offset(x, y)
                        points.add(pt)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    // Draw connecting line
                    drawPath(
                        path = path,
                        color = healthColors.accentBlue,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw circular dots on points (if not too dense)
                    if (records.size <= 35) {
                        points.forEachIndexed { index, pt ->
                            val isScrubbed = scrubbedIndex == index
                            drawCircle(
                                color = if (isScrubbed) healthColors.accentTeal else healthColors.accentBlue,
                                radius = if (isScrubbed) 6.dp.toPx() else 4.dp.toPx(),
                                center = pt
                            )
                        }
                    }

                    // Draw scrubber line & indicator
                    scrubbedIndex?.let { idx ->
                        if (idx in points.indices) {
                            val pt = points[idx]
                            drawLine(
                                color = healthColors.textPrimary.copy(alpha = 0.5f),
                                start = Offset(pt.x, 0f),
                                end = Offset(pt.x, chartHeight),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                            )
                            drawCircle(
                                color = healthColors.accentTeal,
                                radius = 7.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = healthColors.onAccentTeal,
                                radius = 3.5.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                }
            }

            // Right-aligned Y-axis labels
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                yAxisLabels.forEach { bpm ->
                    Text(
                        text = "$bpm",
                        color = healthColors.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // X-axis date labels
        if (records.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 36.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (records.size <= 7) {
                    records.forEach { day ->
                        val dayInitial = day.date.dayOfWeek.name.take(1)
                        Text(
                            text = dayInitial,
                            color = healthColors.textSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    // Sample 5 dates evenly for Month / 3M / Year
                    val step = (records.size / 4).coerceAtLeast(1)
                    val sampled = listOf(0, step, step * 2, step * 3, records.size - 1).distinct()
                    val fmt = if (records.size > 90) DateTimeFormatter.ofPattern("MMM") else DateTimeFormatter.ofPattern("d MMM")
                    sampled.forEach { i ->
                        if (i in records.indices) {
                            Text(
                                text = records[i].date.format(fmt),
                                color = healthColors.textMuted,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Daily records list with mini sparklines
        Text(
            text = "Resting heart rate",
            color = healthColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        summary.dailyRecords.take(14).forEach { day ->
            RestingHeartRateDayRow(day = day)
        }
    }
}

@Composable
fun RestingHeartRateDayRow(
    day: RestingHeartRateDay,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val formatter = DateTimeFormatter.ofPattern("EEE d MMM")
    val dateStr = day.date.format(formatter)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(healthColors.cardBackground)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dateStr,
                color = healthColors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Mini sparkline
                Canvas(
                    modifier = Modifier
                        .width(60.dp)
                        .height(20.dp)
                ) {
                    if (day.sparklineSamples.isNotEmpty()) {
                        val path = Path()
                        val minS = day.sparklineSamples.minOrNull() ?: 50
                        val maxS = (day.sparklineSamples.maxOrNull() ?: 70).coerceAtLeast(minS + 1)
                        day.sparklineSamples.forEachIndexed { i, s ->
                            val x = (i.toFloat() / (day.sparklineSamples.size - 1)) * size.width
                            val y = size.height - ((s - minS).toFloat() / (maxS - minS)) * size.height
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, healthColors.accentTeal, style = Stroke(2.dp.toPx()))
                    }
                }

                Text(
                    text = "${day.bpm} BPM",
                    color = healthColors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
