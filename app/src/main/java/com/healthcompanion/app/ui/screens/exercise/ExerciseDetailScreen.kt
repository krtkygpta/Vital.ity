package com.healthcompanion.app.ui.screens.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthcompanion.app.model.DailyHeartRateSummary
import com.healthcompanion.app.model.ExerciseWorkout
import com.healthcompanion.app.model.HeartRateSample
import com.healthcompanion.app.model.HeartRateZone
import com.healthcompanion.app.ui.components.ExerciseRouteMap
import com.healthcompanion.app.ui.components.HeartRateChart
import com.healthcompanion.app.ui.format.HealthFormat
import com.healthcompanion.app.ui.theme.*
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "1 h 05 m" / "45 min". */
private fun formatDuration(minutes: Int): String = when {
    minutes <= 0 -> "0 min"
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/**
 * Recomputes zone shares so they always sum to exactly 100.
 *
 * The stored `percentage` on each zone is trusted nowhere: truncating
 * `minutes / total` per zone leaves the shares summing to 96 or 104 whenever the
 * fractions do not divide evenly. Largest-remainder rounding fixes that — floor
 * every share, then hand the leftover points to the zones with the biggest
 * fractional parts, so the total is exactly 100 by construction.
 */
internal fun largestRemainderPercentages(weights: List<Int>): List<Int> {
    if (weights.isEmpty()) return emptyList()
    val total = weights.sum()
    if (total <= 0) return weights.map { 0 }

    val exact = weights.map { it.toDouble() / total * 100.0 }
    val floors = exact.map { it.toInt() }
    var remainder = 100 - floors.sum()

    // Rank by fractional part, descending; ties broken by index so the result is
    // deterministic for the same input.
    val order = exact.indices.sortedWith(
        compareByDescending<Int> { exact[it] - floors[it] }.thenBy { it }
    )

    val result = floors.toMutableList()
    var idx = 0
    while (remainder > 0 && order.isNotEmpty()) {
        result[order[idx % order.size]] += 1
        remainder--
        idx++
    }
    return result
}

/** Zone with a share that is guaranteed to be a whole number out of 100. */
internal data class ZoneShare(
    val zone: HeartRateZone,
    val percentage: Int,
    val minutes: Int
)

/** Pairs each zone with a largest-remainder share; the shares total 100. */
internal fun zoneShares(zones: List<HeartRateZone>): List<ZoneShare> {
    if (zones.isEmpty()) return emptyList()
    val percentages = largestRemainderPercentages(zones.map { it.minutesSpent.coerceAtLeast(0) })
    return zones.mapIndexed { index, zone ->
        ZoneShare(
            zone = zone,
            percentage = percentages.getOrElse(index) { 0 },
            minutes = zone.minutesSpent
        )
    }
}

/** Adapts a bare HR sample list to the shape [HeartRateChart] expects. */
private fun workoutHeartRateSummary(workout: ExerciseWorkout): DailyHeartRateSummary? {
    if (workout.heartRateSamples.isEmpty()) return null
    val zone = ZoneId.systemDefault()
    val date = workout.startTime.atZone(zone).toLocalDate()
    val bpmValues = workout.heartRateSamples.map { it.bpm }
    return DailyHeartRateSummary(
        date = date,
        minBpm = bpmValues.minOrNull() ?: 0,
        maxBpm = bpmValues.maxOrNull() ?: 0,
        peakZoneSummary = "Peak of ${bpmValues.maxOrNull() ?: 0} BPM during this workout",
        samples = workout.heartRateSamples,
        zones = workout.zones
    )
}

/**
 * Exercise / workout detail.
 *
 * Header (title, type, start time, duration), a stat grid, the heart-rate
 * chart built from the workout's own samples, a heart-rate-zone breakdown whose
 * shares are recomputed to total 100, and — only for GPS-based activity that
 * actually recorded one — the route map.
 *
 * No route is ever fabricated: a GPS activity without a recorded polyline falls
 * back to an explicit "no route recorded" note rather than drawing a synthetic
 * path. Calories are not shown because the model exposes them only as a
 * constructor argument.
 */
@Composable
fun ExerciseDetailScreen(
    workout: ExerciseWorkout,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRequestRoute: (() -> Unit)? = null
) {
    val healthColors = LocalHealthColors.current
    val zone = remember { ZoneId.systemDefault() }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE d MMM") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm").withZone(zone) }

    val heartRateSummary = remember(workout) { workoutHeartRateSummary(workout) }
    var scrubbedSample by remember { mutableStateOf<HeartRateSample?>(null) }
    val shares = remember(workout.zones) { zoneShares(workout.zones) }
    val route = workout.route
    val showRoute = workout.type.isGpsBased

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(healthColors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = healthColors.textPrimary
                )
            }
            Text(
                text = "Workout",
                style = Typography.titleLarge,
                color = healthColors.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.size(48.dp))
        }

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(healthColors.accentTeal.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = workout.type.getIcon(),
                    contentDescription = null,
                    tint = healthColors.accentTeal,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workout.title.ifBlank { workout.type.label },
                    style = Typography.titleLarge,
                    color = healthColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = workout.type.label,
                    color = healthColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${workout.startTime.atZone(zone).toLocalDate().format(dateFormatter)} • ${timeFormatter.format(workout.startTime)}",
                    color = healthColors.textMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Primary stats
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailStatTile(
                label = "Duration",
                value = formatDuration(workout.durationMinutes),
                modifier = Modifier.weight(1f)
            )
            DetailStatTile(
                label = "Cardio load",
                value = "${workout.cardioLoad}",
                modifier = Modifier.weight(1f)
            )
        }

        // Distance / pace / speed / elevation / steps, each only when recorded.
        val secondaryStats = buildList {
            workout.distanceKm?.let { add("Distance" to String.format(Locale.getDefault(), "%.2f km", it)) }
            workout.avgPaceMinKm?.let { add("Avg pace" to "$it /km") }
            workout.avgSpeedKmH?.let { add("Avg speed" to String.format(Locale.getDefault(), "%.1f km/h", it)) }
            workout.elevationGainMeters?.let { add("Elevation" to "$it m") }
            workout.steps?.let { add("Steps" to HealthFormat.count(it)) }
        }

        if (secondaryStats.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                secondaryStats.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (label, value) ->
                            DetailStatTile(
                                label = label,
                                value = value,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Keep the last odd tile at half width instead of
                        // stretching it across the row.
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Route — only for GPS activity that recorded a polyline.
        if (showRoute) {
            Text(
                text = "Route",
                style = Typography.titleMedium,
                color = healthColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!route.isNullOrEmpty()) {
                ExerciseRouteMap(route = route)
                val ctx = androidx.compose.ui.platform.LocalContext.current
                androidx.compose.material3.TextButton(
                    onClick = {
                        val pts = route
                        val step = (pts.size / 8).coerceAtLeast(1)
                        val mid = pts.filterIndexed { i, _ -> i % step == 0 }
                            .drop(1).dropLast(1).take(8)
                        fun p(g: com.healthcompanion.app.model.GeoPoint) = "${g.latitude},${g.longitude}"
                        val url = buildString {
                            append("https://www.google.com/maps/dir/?api=1")
                            append("&origin=").append(p(pts.first()))
                            append("&destination=").append(p(pts.last()))
                            if (mid.isNotEmpty()) append("&waypoints=").append(mid.joinToString("%7C") { p(it) })
                            append("&travelmode=walking")
                        }
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url)
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { ctx.startActivity(intent) }
                    }
                ) {
                    Text("Open route in Maps")
                }
            } else if (workout.routeConsentRequired && onRequestRoute != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(healthColors.cardBackground)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Button(onClick = onRequestRoute) {
                        Text("Allow access to GPS route")
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(healthColors.cardBackground)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No GPS route was recorded for this workout",
                        color = healthColors.textMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Heart rate
        Text(
            text = "Heart rate",
            style = Typography.titleMedium,
            color = healthColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (heartRateSummary == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(healthColors.cardBackground)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No heart rate data for this workout",
                    color = healthColors.textMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
            ) {
                HeartRateChart(
                    summary = heartRateSummary,
                    scrubbedSample = scrubbedSample,
                    onScrub = { scrubbedSample = it },
                    isRelativeTime = true,
                    workoutStartTime = workout.startTime,
                    workoutDurationMinutes = workout.durationMinutes
                )
            }
        }

        // Heart rate zones
        if (shares.isNotEmpty()) {
            Text(
                text = "Heart rate zones",
                style = Typography.titleMedium,
                color = healthColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val totalZoneMinutes = shares.sumOf { it.minutes }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                shares.forEach { share ->
                    ZoneRow(
                        share = share,
                        totalZoneMinutes = totalZoneMinutes
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

/** Fixed-width stat tile so a long value can never reflow its neighbour. */
@Composable
private fun DetailStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(healthColors.cardBackground)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            color = healthColors.textMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = healthColors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** One zone: colour, name, bpm band, minutes and its recomputed share. */
@Composable
private fun ZoneRow(
    share: ZoneShare,
    totalZoneMinutes: Int,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val zoneColor = share.zone.color()
    val band = remember(share.zone) {
        if (share.zone.maxBpm > 0) "${share.zone.minBpm}–${share.zone.maxBpm} BPM"
        else "≥${share.zone.minBpm} BPM"
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(zoneColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = share.zone.name,
                    color = healthColors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${share.percentage}%",
                color = healthColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Proportional bar.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(healthColors.cardElevated)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = (share.percentage.coerceIn(0, 100) / 100f))
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(zoneColor)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (totalZoneMinutes > 0) {
                "$band • ${formatDuration(share.minutes)} of ${formatDuration(totalZoneMinutes)}"
            } else {
                band
            },
            color = healthColors.textMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
