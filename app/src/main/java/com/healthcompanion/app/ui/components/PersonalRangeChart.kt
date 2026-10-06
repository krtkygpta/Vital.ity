package com.healthcompanion.app.ui.components

import androidx.compose.material3.MaterialTheme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthcompanion.app.ui.theme.LocalHealthColors
import java.time.LocalDate

/**
 * Resting-heart-rate chart.
 *
 * Visual language taken from the reference screenshots:
 *  - A **personal-range band**: a dark green filled rectangle spanning the
 *    user's own min–max for the period. The band has a stepped top edge because
 *    it is drawn per data point at that point's local max.
 *  - The **line and points turn green while inside the band and red above it**,
 *    so out-of-range spikes are immediately obvious.
 *  - The **last point is always red** when it breaches the range.
 *  - Clean plot, no gridlines, right-side value labels.
 */
@Composable
fun PersonalRangeChart(
    values: List<Float>,
    rangeMin: Float,
    rangeMax: Float,
    modifier: Modifier = Modifier,
    /** Dates for each point, used to place them on the real calendar. */
    dates: List<LocalDate>? = null,
    /**
     * Full extent of the plotted period. The axis spans this, not the readings,
     * so a week always fills the width and days without data stay empty.
     */
    periodStart: LocalDate? = null,
    periodEnd: LocalDate? = null,
    valueFormatter: (Float) -> String = { it.toInt().toString() },
    onScrub: (Int?) -> Unit = {}
) {
    val healthColors = LocalHealthColors.current
    var scrubbed by remember { mutableStateOf<Int?>(null) }

    // §5.7: without this the chart drew an empty frame inside the personal
    // range band, which reads as "everything was normal" rather than "no data".
    if (values.isEmpty()) {
        EmptyChartPlaceholder(
            title = "No readings",
            subtitle = "Nothing has been recorded for this period.",
            modifier = modifier
        )
        return
    }

    val inRangeColor = healthColors.accentTeal
    val outOfRangeColor = healthColors.accentOrange
    val bandColor = healthColors.accentTeal.copy(alpha = 0.14f)

    val dataMin = values.min()
    val dataMax = values.max()
    // §5.3: padding is proportional to the observed span with a floor, rather
    // than a flat 30% that turned a 2 bpm wobble into a mountain range.
    val padding = ((dataMax - dataMin) * 0.3f).coerceAtLeast(4f)
    val lowBound = (minOf(dataMin, rangeMin) - padding).coerceAtLeast(0f)
    val highBound = maxOf(dataMax, rangeMax) + padding

    val animatedHigh by animateFloatAsState(
        targetValue = highBound,
        animationSpec = tween(ExpressiveDuration.MEDIUM),
        label = "prHigh"
    )
    val animatedLow by animateFloatAsState(
        targetValue = lowBound,
        animationSpec = tween(ExpressiveDuration.MEDIUM),
        label = "prLow"
    )
    val range = (animatedHigh - animatedLow).coerceAtLeast(1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 48.dp)
                .pointerInput(values.size, dates, periodStart, periodEnd) {
                    if (values.isEmpty()) return@pointerInput
                    val axisStart = periodStart ?: dates?.firstOrNull()
                    val axisEnd = periodEnd ?: dates?.lastOrNull()
                    val totalDays = if (axisStart != null && axisEnd != null) {
                        (axisEnd.toEpochDay() - axisStart.toEpochDay()).toInt().coerceAtLeast(1)
                    } else {
                        (values.size - 1).coerceAtLeast(1)
                    }
                    val slot = size.width / totalDays
                    detectTapGestures { offset ->
                        if (offset.x < 0f || dates == null) return@detectTapGestures
                        // Snap to whichever real reading is nearest the touch,
                        // so empty days still select their closest neighbour.
                        val dayOffset = ((offset.x - slot / 2f) / slot).toInt()
                        var best = 0
                        var bestDist = Long.MAX_VALUE
                        val base = axisStart ?: dates.first()
                        dates.forEachIndexed { i, d ->
                            val dist = kotlin.math.abs(
                                (d.toEpochDay() - base.toEpochDay()) - dayOffset
                            )
                            if (dist < bestDist) { bestDist = dist; best = i }
                        }
                        scrubbed = if (scrubbed == best) null else best
                        onScrub(scrubbed)
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            fun yFor(v: Float): Float = h - ((v - animatedLow) / range).coerceIn(0f, 1f) * h

            if (values.isNotEmpty()) {
                // X positions come from real elapsed days across the whole period,
                // so the axis always fills the chart and a day with no reading
                // leaves a genuine hole rather than pulling points together.
                val axisStart = periodStart ?: dates?.firstOrNull()
                val axisEnd = periodEnd ?: dates?.lastOrNull()
                val totalDays = if (axisStart != null && axisEnd != null) {
                    (axisEnd.toEpochDay() - axisStart.toEpochDay()).toInt().coerceAtLeast(1)
                } else {
                    (values.size - 1).coerceAtLeast(1)
                }
                val slot = w / totalDays
                fun xFor(i: Int): Float {
                    val d = dates?.getOrNull(i) ?: return i * slot + slot / 2f
                    val base = axisStart ?: dates.first()
                    val offset = (d.toEpochDay() - base.toEpochDay()).toInt()
                    return offset * slot + slot / 2f
                }

                // Personal-range band, stepped per data point.
                val bandTop = yFor(rangeMax)
                val bandBottom = yFor(rangeMin)
                var bandPath = Path()
                bandPath.moveTo(xFor(0), bandTop)
                values.forEachIndexed { i, _ ->
                    bandPath.lineTo(xFor(i), bandTop)
                }
                bandPath.lineTo(w, bandTop)
                bandPath.lineTo(w, bandBottom)
                bandPath.lineTo(0f, bandBottom)
                bandPath.close()
                drawPath(bandPath, color = bandColor)

                // Line, split into in-range (green) and out-of-range (red) segments.
                // A jump of more than one day is a gap, so the line is not drawn
                // across missing data.
                fun drawSegment(fromIdx: Int, toIdx: Int, color: Color) {
                    if (toIdx - fromIdx < 1) return
                    val dList = dates
                    val datesSupplied = dList != null && dList.size == values.size
                    if (datesSupplied && dList != null) {
                        val gap = (dList[toIdx].toEpochDay() - dList[fromIdx].toEpochDay()).toInt()
                        if (gap > 1) return
                    }
                    val p = Path()
                    p.moveTo(xFor(fromIdx), yFor(values[fromIdx]))
                    for (i in fromIdx + 1..toIdx) {
                        p.lineTo(xFor(i), yFor(values[i]))
                    }
                    drawPath(
                        path = p,
                        color = color,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                for (i in 0 until values.size - 1) {
                    val bothIn = values[i] in rangeMin..rangeMax && values[i + 1] in rangeMin..rangeMax
                    val color = if (bothIn) inRangeColor else outOfRangeColor
                    drawSegment(i, i + 1, color)
                }

                // Points, coloured by whether they sit inside the personal range.
                values.forEachIndexed { i, v ->
                    val inRange = v in rangeMin..rangeMax
                    drawCircle(
                        color = if (inRange) inRangeColor else outOfRangeColor,
                        radius = if (scrubbed == i) 5.dp.toPx() else 3.5.dp.toPx(),
                        center = Offset(xFor(i), yFor(v))
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            Text(valueFormatter(animatedHigh), color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(valueFormatter(animatedLow + range * 0.5f), color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(valueFormatter(animatedLow), color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/**
 * Legend for [PersonalRangeChart]: a dashed range swatch, an in-range dot and
 * an out-of-range dot, matching the three-item row in the screenshots.
 */
@Composable
fun PersonalRangeLegend(
    showOutOfRange: Boolean,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Personal range swatch: a small dashed box.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(14.dp)) {
                val dash = 3.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        color = healthColors.accentTeal,
                        start = Offset(x, size.height / 2),
                        end = Offset(minOf(x + dash / 2, size.width), size.height / 2),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    x += dash
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text("Personal range", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(healthColors.accentTeal)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("In range", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
        }

        if (showOutOfRange) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(healthColors.accentOrange)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Out of range", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** "Out of range N times" caption used on period summary rows. */
@Composable
fun OutOfRangeCaption(
    count: Int,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    if (count <= 0) return
    Text(
        text = if (count == 1) "Out of range 1 time" else "Out of range $count times",
        color = healthColors.textSecondary,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
    )
}
