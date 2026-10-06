package com.healthcompanion.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.healthcompanion.app.model.SleepStageStat
import com.healthcompanion.app.model.SleepStageType
import com.healthcompanion.app.ui.theme.*

@Composable
fun StageBreakdownBar(
    stat: SleepStageStat,
    showBenchmarkBadge: Boolean = true,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val stageColor = stat.stage.color()
    val formattedDuration = if (stat.durationMinutes >= 60) {
        val hours = stat.durationMinutes / 60
        val mins = stat.durationMinutes % 60
        "${hours} h ${mins} m"
    } else {
        "${stat.durationMinutes} min"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.s)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Swatch + label. The stage name was previously rendered in textPrimary with
            // no colour key, so the reader had to memorise which track colour
            // belonged to which stage.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(stageColor)
                )
                Spacer(modifier = Modifier.width(Spacing.s))
                Text(
                    text = "${stat.stage.label} • ${stat.percentage}% • $formattedDuration",
                    color = healthColors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
            }

            if (showBenchmarkBadge && stat.stage != SleepStageType.AWAKE) {
                val badgeBg = stat.benchmarkStatus.containerColor()
                val badgeText = stat.benchmarkStatus.label
                val badgeColor = stat.benchmarkStatus.color()

                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(badgeBg)
                        .padding(horizontal = Spacing.s, vertical = Spacing.xs)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.s))

        // Progress track with benchmark indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(MaterialTheme.shapes.small)
                .background(healthColors.cardBackground)
        ) {
            // Actual user fill
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = (stat.percentage.coerceIn(0, 100) / 100f))
                    .clip(MaterialTheme.shapes.small)
                    .background(stageColor)
            )

            // Typical-range marker.
            //
            // This was previously a fixed `offset(x = 100.dp)` box whose own
            // comment admitted it was decorative: the position ignored the
            // track's actual pixel width, so the "typical range" indicator sat
            // in the wrong place on every screen but one width. Drawing both
            // edges as width fractions makes it correct at any size.
            if (showBenchmarkBadge && stat.stage != SleepStageType.AWAKE) {
                TypicalRangeMarker(
                    startFraction = stat.benchmarkRange.first / 100f,
                    endFraction = stat.benchmarkRange.second / 100f,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
    }
}

/** Outlined band plus end ticks showing the typical range across a track. */
@Composable
fun TypicalRangeMarker(
    startFraction: Float,
    endFraction: Float,
    modifier: Modifier = Modifier
) {
    val color = LocalHealthColors.current.textPrimary
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val s = startFraction.coerceIn(0f, 1f) * size.width
        val e = endFraction.coerceIn(startFraction.coerceIn(0f, 1f), 1f) * size.width
        val stroke = 1.5.dp.toPx()
        drawRoundRect(
            color = color.copy(alpha = 0.9f),
            topLeft = androidx.compose.ui.geometry.Offset(s, stroke / 2),
            size = androidx.compose.ui.geometry.Size((e - s).coerceAtLeast(stroke), size.height - stroke),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )
    }
}
