package com.healthcompanion.app.ui.components

import androidx.compose.material3.MaterialTheme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthcompanion.app.ui.format.HealthFormat
import com.healthcompanion.app.ui.theme.LocalHealthColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Bar chart with a clean, goal-anchored plot.
 *
 * Reference behaviour:
 *  - **No gridlines.** A clean plot area with only the bars and the goal line.
 *  - **Goal-anchored axis.** When a goal exists, the top of the scale IS the
 *    goal, labelled in the accent colour, with 2/3 and 1/3 marks beneath it.
 *  - **Fully rounded bars** — the corner radius is half the bar width, so a
 *    short bar is a lozenge and a tall one is a capsule.
 *  - **Goal line only spans the plot width**, drawn in a lighter accent tint.
 *  - **Scrub handle**: a vertical line with a ring at the baseline and short
 *    horizontal caps at the top and bottom.
 *  - **Bars animate upward** on first composition and re-animate on range change.
 */
@Composable
fun GoogleStyleBarChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    goalValue: Float? = null,
    goalLabel: String? = null,
    valueFormatter: (Float) -> String = { HealthFormat.compact(it.toDouble()) },
    onScrub: (Int?) -> Unit = {}
) {
    val healthColors = LocalHealthColors.current
    var scrubbed by remember { mutableStateOf<Int?>(null) }

    val maxValue = values.maxOrNull() ?: 0f
    // When a goal is set, the goal defines the top of the chart so the user can
    // see at a glance how close each bar is. Otherwise scale to the data.
    val topOfScale = when {
        goalValue != null && goalValue > 0f -> max(goalValue, maxValue)
        else -> maxValue
    }.coerceAtLeast(1f)

    val animatedTop by animateFloatAsState(
        targetValue = topOfScale,
        animationSpec = tween(ExpressiveDuration.MEDIUM),
        label = "chartTop"
    )
    val growth by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(ExpressiveDuration.LONG),
        label = "chartGrowth"
    )

    val scale = if (animatedTop <= 0f) 1f else animatedTop
    val barColor = healthColors.accentTeal
    val goalLineColor = healthColors.accentTeal.copy(alpha = 0.75f)
    val handleColor = healthColors.textPrimary

    val slotWidth = if (values.isNotEmpty()) 1f / values.size else 1f
    val targetCenterXFraction = scrubbed?.let { (it + 0.5f) * slotWidth } ?: 0.5f
    val animatedCenterXFraction by animateFloatAsState(
        targetValue = targetCenterXFraction,
        animationSpec = tween(150),
        label = "scrubberGlideX"
    )

    val targetValue = scrubbed?.let { values.getOrElse(it) { 0f } } ?: 0f
    val animatedScrubbedValue by animateFloatAsState(
        targetValue = targetValue,
        animationSpec = tween(150),
        label = "scrubberGlideY"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 48.dp)
                .pointerInput(values.size) {
                    if (values.isEmpty()) return@pointerInput
                    val slot = size.width / values.size
                    detectTapGestures { offset ->
                        if (offset.x < 0f) return@detectTapGestures
                        val idx = (offset.x / slot).toInt().coerceIn(0, values.lastIndex)
                        scrubbed = if (scrubbed == idx) null else idx
                        onScrub(scrubbed)
                    }
                }
                .pointerInput(values.size) {
                    if (values.isEmpty()) return@pointerInput
                    val slot = size.width / values.size
                    detectDragGestures(
                        onDragStart = { offset ->
                            val idx = (offset.x / slot).toInt().coerceIn(0, values.lastIndex)
                            scrubbed = idx
                            onScrub(scrubbed)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val idx = (change.position.x / slot).toInt().coerceIn(0, values.lastIndex)
                            if (scrubbed != idx) {
                                scrubbed = idx
                                onScrub(scrubbed)
                            }
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height

            // Goal line: spans the plot width only, in a lighter tint.
            if (goalValue != null && goalValue > 0f && goalValue <= scale) {
                val goalY = h - (goalValue / scale) * h
                drawLine(
                    color = goalLineColor,
                    start = Offset(0f, goalY),
                    end = Offset(w, goalY),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            if (values.isNotEmpty()) {
                val slot = w / values.size
                val barWidth = slot * 0.56f
                val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

                values.forEachIndexed { index, raw ->
                    val fraction = (raw / scale).coerceIn(0f, 1f)
                    val full = fraction * h * growth
                    val barHeight = full.coerceAtLeast(if (raw > 0f) barWidth else 0f)
                    if (barHeight <= 0f) return@forEachIndexed
                    val x = index * slot + (slot - barWidth) / 2f
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, h - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = radius
                    )
                }

                // Scrub handle: smoothly gliding vertical line + ring at baseline + end caps.
                scrubbed?.let { _ ->
                    val centerX = animatedCenterXFraction * w
                    val knobY = h - (animatedScrubbedValue / scale).coerceIn(0f, 1f) * h

                    drawLine(
                        color = handleColor,
                        start = Offset(centerX, knobY),
                        end = Offset(centerX, h),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    // Ring sitting on the baseline.
                    drawCircle(
                        color = Color.Transparent,
                        radius = 5.dp.toPx(),
                        center = Offset(centerX, h),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    // Short horizontal caps.
                    val cap = 7.dp.toPx()
                    drawLine(
                        color = handleColor,
                        start = Offset(centerX - cap, knobY),
                        end = Offset(centerX + cap, knobY),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    drawLine(
                        color = handleColor,
                        start = Offset(centerX - cap, h),
                        end = Offset(centerX + cap, h),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }
        }

        // Right-hand value labels: top of scale (accent if goal matches top), then 2/3 and 1/3, then 0.
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            val isGoalAtTop = goalValue != null && goalValue > 0f && scale == goalValue
            Text(
                text = valueFormatter(scale),
                color = if (isGoalAtTop) barColor else healthColors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isGoalAtTop) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
            Text(
                text = valueFormatter(scale * 0.667f),
                color = healthColors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
            Text(
                text = valueFormatter(scale * 0.333f),
                color = healthColors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
            Text(
                text = "0",
                color = healthColors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

/**
 * Line chart for resting-heart-rate and heart-rate views:
 * a clean plot with no gridlines, right-side value labels, and round markers on
 * each data point. Used for resting HR (7 points) and other sparse series.
 */
@Composable
fun GoogleStyleLineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    valueFormatter: (Float) -> String = { HealthFormat.compact(it.toDouble()) },
    lineColor: Color? = null,
    showMarkers: Boolean = true,
    goalValue: Float? = null,
    goalLabel: String? = null,
    onScrub: (Int?) -> Unit = {}
) {
    val healthColors = LocalHealthColors.current
    var scrubbed by remember { mutableStateOf<Int?>(null) }
    val stroke = lineColor ?: healthColors.accentBlue

    val dataMin = values.minOrNull() ?: 0f
    val dataMax = values.maxOrNull() ?: 1f
    val padding = ((dataMax - dataMin) * 0.25f).coerceAtLeast(2f)
    val lowBound = (dataMin - padding).coerceAtLeast(0f)
    val highBound = dataMax + padding

    val animatedHigh by animateFloatAsState(
        targetValue = highBound,
        animationSpec = tween(ExpressiveDuration.MEDIUM),
        label = "lineHigh"
    )
    val animatedLow by animateFloatAsState(
        targetValue = lowBound,
        animationSpec = tween(ExpressiveDuration.MEDIUM),
        label = "lineLow"
    )

    val range = (animatedHigh - animatedLow).coerceAtLeast(1f)

    val slotWidth = if (values.isNotEmpty()) 1f / values.size else 1f
    val targetCenterXFraction = scrubbed?.let { if (values.size > 1) (it + 0.5f) * slotWidth else 0.5f } ?: 0.5f
    val animatedCenterXFraction by animateFloatAsState(
        targetValue = targetCenterXFraction,
        animationSpec = tween(150),
        label = "lineScrubGlideX"
    )

    val targetVal = scrubbed?.let { values.getOrElse(it) { animatedLow } } ?: animatedLow
    val animatedScrubVal by animateFloatAsState(
        targetValue = targetVal,
        animationSpec = tween(150),
        label = "lineScrubGlideY"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 48.dp)
                .pointerInput(values.size) {
                    if (values.isEmpty()) return@pointerInput
                    val slot = size.width / values.size
                    detectTapGestures { offset ->
                        if (offset.x < 0f) return@detectTapGestures
                        val idx = (offset.x / slot).toInt().coerceIn(0, values.lastIndex)
                        scrubbed = if (scrubbed == idx) null else idx
                        onScrub(scrubbed)
                    }
                }
                .pointerInput(values.size) {
                    if (values.isEmpty()) return@pointerInput
                    val slot = size.width / values.size
                    detectDragGestures(
                        onDragStart = { offset ->
                            val idx = (offset.x / slot).toInt().coerceIn(0, values.lastIndex)
                            scrubbed = idx
                            onScrub(scrubbed)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val idx = (change.position.x / slot).toInt().coerceIn(0, values.lastIndex)
                            if (scrubbed != idx) {
                                scrubbed = idx
                                onScrub(scrubbed)
                            }
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height

            fun yFor(value: Float): Float =
                h - ((value - animatedLow) / range).coerceIn(0f, 1f) * h

            if (values.isNotEmpty()) {
                val slot = w / values.size
                val points = values.mapIndexed { i, v ->
                    val x = if (values.size > 1) i * slot + slot / 2f else w / 2f
                    Offset(x, yFor(v))
                }

                // Smooth polyline through the points.
                val path = Path()
                points.forEachIndexed { i, p ->
                    if (i == 0) path.moveTo(p.x, p.y) else {
                        val prev = points[i - 1]
                        val midX = (prev.x + p.x) / 2f
                        path.cubicTo(midX, prev.y, midX, p.y, p.x, p.y)
                    }
                }
                drawPath(
                    path = path,
                    color = stroke,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )

                if (showMarkers) {
                    points.forEachIndexed { i, p ->
                        drawCircle(
                            color = stroke,
                            radius = if (scrubbed == i) 5.dp.toPx() else 3.5.dp.toPx(),
                            center = p
                        )
                    }
                }

                // Smooth animated scrubber vertical line and glowing knob
                scrubbed?.let { _ ->
                    val centerX = animatedCenterXFraction * w
                    val knobY = yFor(animatedScrubVal)

                    drawLine(
                        color = healthColors.textPrimary.copy(alpha = 0.6f),
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, h),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                    drawCircle(
                        color = stroke,
                        radius = 6.dp.toPx(),
                        center = Offset(centerX, knobY)
                    )
                    drawCircle(
                        color = healthColors.cardBackground,
                        radius = 3.dp.toPx(),
                        center = Offset(centerX, knobY)
                    )
                }
            }
        }

        // Right-hand labels: high, mid, low.
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            Text(valueFormatter(animatedHigh), color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(valueFormatter(animatedLow + range * 0.33f), color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(valueFormatter(animatedLow), color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/**
 * Point / line chart styled for metrics like blood oxygen (SpO2) without continuous fills.
 */
@Composable
fun GoogleStylePointChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    goalValue: Float? = null,
    goalLabel: String? = null,
    valueFormatter: (Float) -> String = { HealthFormat.compact(it.toDouble()) },
    lineColor: Color? = null,
    onScrub: (Int?) -> Unit = {}
) {
    GoogleStyleLineChart(
        values = values,
        modifier = modifier,
        valueFormatter = valueFormatter,
        lineColor = lineColor,
        showMarkers = true,
        goalValue = goalValue,
        goalLabel = goalLabel,
        onScrub = onScrub
    )
}

/**
 * Placeholder displayed when Health Connect has no data recorded for the selected period.
 */
@Composable
fun EmptyChartPlaceholder(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = healthColors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = healthColors.textMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Evenly spaced x-axis labels for a sparse date row. */
@Composable
fun GoogleStyleXAxis(
    labels: List<String>,
    modifier: Modifier = Modifier,
    maxLabels: Int = 7
) {
    val healthColors = LocalHealthColors.current
    if (labels.isEmpty()) return

    if (labels.size <= maxLabels) {
        // If count <= maxLabels (e.g. 7 days in WEEK), assign each item an equal slot (weight = 1f)
        // so its center matches the bar's center: index * slot + slot / 2
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 6.dp, end = 48.dp)
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    color = healthColors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        val step = (labels.size - 1).toFloat() / (maxLabels - 1)
        val indices = (0 until maxLabels).map { Math.round(it * step).coerceAtMost(labels.lastIndex) }.distinct()

        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 6.dp, end = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            indices.forEach { i ->
                Text(
                    text = labels.getOrElse(i) { "" },
                    color = healthColors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Generates formatted x-axis labels for a list of dates based on the active [TimeRange].
 */
fun xAxisLabels(dates: List<java.time.LocalDate>, range: TimeRange): List<String> = when (range) {
    TimeRange.DAY -> dates.map { it.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) }
    TimeRange.WEEK -> dates.map { it.dayOfWeek.name.take(1) }
    TimeRange.MONTH -> dates.map { it.dayOfMonth.toString() }
    TimeRange.THREE_MONTHS -> dates.map { it.format(java.time.format.DateTimeFormatter.ofPattern("d MMM")) }
    TimeRange.YEAR -> dates.map { it.format(java.time.format.DateTimeFormatter.ofPattern("MMM")) }
}

/** Durations used by chart animations, kept in one place. */
object ExpressiveDuration {
    const val MEDIUM = 350
    const val LONG = 500
}

