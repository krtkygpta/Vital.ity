package com.healthcompanion.app.ui.screens.heartrate

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthcompanion.app.model.DailyHeartRateSummary
import com.healthcompanion.app.model.HeartRateSample
import com.healthcompanion.app.model.RestingHeartRateDay
import com.healthcompanion.app.model.RestingHeartRatePeriodSummary
import com.healthcompanion.app.ui.components.*
import com.healthcompanion.app.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

enum class HeartRateMode {
    RESTING_HEART_RATE,
    TIME_IN_ZONES
}

data class RestingPoint(
    val date: LocalDate,
    val bpm: Int?
)

data class DayZoneMinutes(
    val date: LocalDate,
    val moderateMinutes: Int,
    val vigorousMinutes: Int,
    val peakMinutes: Int
) {
    val totalMinutes: Int get() = moderateMinutes + vigorousMinutes + peakMinutes
}

@Composable
fun HeartRateDetailScreen(
    dailySummary: DailyHeartRateSummary?,
    restingSummary: RestingHeartRatePeriodSummary?,
    onBack: () -> Unit,
    initialRange: TimeRange = TimeRange.DAY,
    initialMode: HeartRateMode = HeartRateMode.RESTING_HEART_RATE,
    onQueryDailyHeartRate: (suspend (LocalDate, Int) -> Result<DailyHeartRateSummary?>)? = null,
    onQueryWeeklyHeartRate: (suspend (LocalDate) -> Result<List<Triple<LocalDate, Int, Int>>>)? = null,
    onQueryRestingHeartRate: (suspend (LocalDate) -> Result<RestingHeartRatePeriodSummary?>)? = null,
    onQueryRestingHeartRatePeriod: (suspend (String, Int, LocalDate) -> Result<RestingHeartRatePeriodSummary?>)? = null,
    goalsAge: Int = 30,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    var selectedRange by rememberSaveable(stateSaver = enumSaver<TimeRange>()) { mutableStateOf(initialRange) }
    var selectedMode by rememberSaveable(stateSaver = enumSaver<HeartRateMode>()) { mutableStateOf(initialMode) }
    var selectedDate by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(LocalDate.now()) }
    var showAccuracyCard by rememberSaveable { mutableStateOf(true) }

    var dynamicDailySummary by remember { mutableStateOf<DailyHeartRateSummary?>(null) }
    var dynamicRestingSummary by remember { mutableStateOf<RestingHeartRatePeriodSummary?>(null) }
    var weeklyHeartRate by remember { mutableStateOf<List<Triple<LocalDate, Int, Int>>>(emptyList()) }

    /**
     * Real per-day summaries for the visible period, keyed by date. Outside a
     * week the screen no longer invents a min/max pair or zone minutes from the
     * day of the month; it queries the days it actually shows.
     */
    var dailySummaryCache by remember { mutableStateOf<Map<LocalDate, DailyHeartRateSummary>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }

    var scrubbedSample by remember { mutableStateOf<HeartRateSample?>(null) }
    var scrubbedPointIndex by remember { mutableStateOf<Int?>(null) }
    var scrubbedZoneBarIndex by remember { mutableStateOf<Int?>(null) }
    var selectedZoneFilter by remember { mutableStateOf("Time in zones") }

    val daysCount = when (selectedRange) {
        TimeRange.DAY -> 1
        TimeRange.WEEK -> 7
        TimeRange.MONTH -> 30
        TimeRange.THREE_MONTHS -> 90
        TimeRange.YEAR -> 365
    }

    LaunchedEffect(selectedDate, selectedRange) {
        scrubbedSample = null
        scrubbedPointIndex = null
        scrubbedZoneBarIndex = null
        loading = true
        try {
            // Real reads only. A failed read yields null, which renders as an
            // empty state — never a synthetic summary.
            dynamicDailySummary = onQueryDailyHeartRate
                ?.invoke(selectedDate, goalsAge)
                ?.getOrNull()

            if (selectedRange == TimeRange.WEEK && onQueryWeeklyHeartRate != null) {
                weeklyHeartRate = onQueryWeeklyHeartRate(selectedDate)?.getOrNull().orEmpty()
            }

            val periodDays = daysCount
            dynamicRestingSummary = if (onQueryRestingHeartRatePeriod != null) {
                onQueryRestingHeartRatePeriod(selectedRange.name, periodDays, selectedDate)?.getOrNull()
            } else if (onQueryRestingHeartRate != null) {
                onQueryRestingHeartRate(selectedDate)?.getOrNull()
            } else null

            // Fetch the per-day summaries the visible bars need. This runs for
            // every range — including WEEK — because the zone-minute bars read
            // from this cache even when the min/max pairs came from
            // `weeklyHeartRate`.
            if (onQueryDailyHeartRate != null) {
                val start = when (selectedRange) {
                    TimeRange.DAY -> selectedDate
                    TimeRange.WEEK -> weekStartMon(selectedDate)
                    TimeRange.MONTH -> selectedDate.withDayOfMonth(1)
                    TimeRange.THREE_MONTHS -> selectedDate.minusWeeks(11)
                    TimeRange.YEAR -> selectedDate.withDayOfMonth(1).minusMonths(11)
                }
                val today = LocalDate.now()
                val queried = mutableMapOf<LocalDate, DailyHeartRateSummary>()
                var cursor = start
                var guard = 0
                while (!cursor.isAfter(selectedDate) && guard < 400) {
                    if (!cursor.isAfter(today)) {
                        onQueryDailyHeartRate(cursor, goalsAge).getOrNull()?.let { queried[cursor] = it }
                    }
                    cursor = cursor.plusDays(1)
                    guard++
                }
                dailySummaryCache = queried
            } else {
                dailySummaryCache = emptyMap()
            }
        } finally {
            loading = false
        }
    }

    val currentDailySummary = dynamicDailySummary
        ?: dailySummary?.takeIf { it.date == selectedDate }

    val currentRestingSummary = dynamicRestingSummary ?: restingSummary

    val hasHeartRateData = currentDailySummary != null ||
        (currentRestingSummary?.dailyRecords?.isNotEmpty() == true) ||
        weeklyHeartRate.any { it.second > 0 }

    // Build points for resting heart rate chart
    val today = LocalDate.now()
    val restingRecords = currentRestingSummary?.dailyRecords.orEmpty()
    val restingPoints = remember(selectedRange, selectedDate, currentRestingSummary) {
        // A day with no reading is `null`; it is not back-filled with a
        // value derived from the day of the month, which made an empty week
        // look like a real 58–68 bpm series.
        val recordsMap = restingRecords.associateBy({ it.date }, { it.bpm })
        fun bpmForDate(d: LocalDate): Int? = recordsMap[d]
        fun bpmForWindow(from: LocalDate, to: LocalDate): Int? {
            val vals = restingRecords.filter { !it.date.isBefore(from) && !it.date.isAfter(to) && it.bpm > 0 }.map { it.bpm }
            return if (vals.isEmpty()) null else vals.average().toInt()
        }

        when (selectedRange) {
            TimeRange.DAY -> {
                listOf(RestingPoint(selectedDate, if (selectedDate.isAfter(today)) null else bpmForDate(selectedDate)))
            }
            TimeRange.WEEK -> {
                val weekStart = weekStartMon(selectedDate)
                (0..6).map { i ->
                    val d = weekStart.plusDays(i.toLong())
                    RestingPoint(d, if (d.isAfter(today)) null else bpmForDate(d))
                }
            }
            TimeRange.MONTH -> {
                val monthStart = selectedDate.minusDays(29)
                (0..29).map { i ->
                    val d = monthStart.plusDays(i.toLong())
                    RestingPoint(d, if (d.isAfter(today)) null else bpmForDate(d))
                }
            }
            TimeRange.THREE_MONTHS -> {
                (11 downTo 0).map { w ->
                    val d = selectedDate.minusWeeks(w.toLong())
                    RestingPoint(d, if (d.isAfter(today)) null else bpmForWindow(d.minusDays(6), d))
                }
            }
            TimeRange.YEAR -> {
                (11 downTo 0).map { m ->
                    val first = selectedDate.minusMonths(m.toLong()).withDayOfMonth(1)
                    RestingPoint(first, if (first.isAfter(today)) null else bpmForWindow(first, first.plusMonths(1).minusDays(1)))
                }
            }
        }
    }

    val validRestingPoints = restingPoints.filter { it.bpm != null }
    val avgRestingBpm: Int? = currentRestingSummary?.averageBpm?.takeIf { it > 0 }
        ?: if (validRestingPoints.isNotEmpty()) {
            validRestingPoints.mapNotNull { it.bpm }.average().toInt()
        } else null

    // Period min/max ranges for Time in zones
    val periodRanges = remember(currentDailySummary, weeklyHeartRate, selectedDate, selectedRange) {
        if (selectedRange == TimeRange.WEEK) {
            weeklyHeartRate
        } else {
            val count = when (selectedRange) {
                TimeRange.DAY -> 1
                TimeRange.MONTH -> 30
                TimeRange.THREE_MONTHS -> 12
                TimeRange.YEAR -> 12
                else -> 7
            }
            val stepDays = if (selectedRange == TimeRange.THREE_MONTHS || selectedRange == TimeRange.YEAR) 7 else 1
            val periodStart = when (selectedRange) {
                TimeRange.DAY -> selectedDate
                TimeRange.MONTH -> selectedDate.withDayOfMonth(1)
                else -> selectedDate.minusDays(((count - 1) * stepDays).toLong())
            }
            (0 until count).map { i ->
                val d = periodStart.plusDays((i * stepDays).toLong())
                // Outside a week there is no per-day query, so days outside the
                // fetched set carry no range rather than an invented one.
                if (d.isAfter(today)) {
                    Triple(d, 0, 0)
                } else {
                    val summary = dailySummaryCache[d]
                    Triple(d, summary?.minBpm ?: 0, summary?.maxBpm ?: 0)
                }
            }
        }
    }

    val periodZoneMinutes = remember(selectedRange, selectedDate, periodRanges) {
        // Zone minutes come from real zone data only. The previous version
        // seeded them from the day of the month whenever all three were zero,
        // which drew plausible-looking bars for days with no heart-rate record.
        periodRanges.map { (d, minBpm, maxBpm) ->
            if (d.isAfter(today) || (minBpm == 0 && maxBpm == 0)) {
                DayZoneMinutes(d, 0, 0, 0)
            } else {
                val zones = dailySummaryCache[d]?.zones.orEmpty()
                fun zoneMinutes(predicate: (String) -> Boolean): Int =
                    zones.firstOrNull { predicate(it.name) }?.minutesSpent ?: 0
                DayZoneMinutes(
                    date = d,
                    moderateMinutes = zoneMinutes { it.contains("Moderate", true) || it.contains("Fat burn", true) },
                    vigorousMinutes = zoneMinutes { it.contains("Vigorous", true) || it.contains("Cardio", true) },
                    peakMinutes = zoneMinutes { it.contains("Peak", true) }
                )
            }
        }
    }

    // Date formatting
    val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
    val monthFmt = DateTimeFormatter.ofPattern("MMM")
    val dateLabel = when (selectedRange) {
        TimeRange.DAY -> {
            when (selectedDate) {
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> selectedDate.format(dateFormatter)
            }
        }
        TimeRange.WEEK -> {
            val start = weekStartMon(selectedDate)
            val end = weekEndMon(selectedDate)
            if (start.monthValue == end.monthValue) {
                "${start.dayOfMonth}–${end.dayOfMonth} ${end.format(monthFmt)}"
            } else {
                "${start.dayOfMonth} ${start.format(monthFmt)}–${end.dayOfMonth} ${end.format(monthFmt)}"
            }
        }
        TimeRange.MONTH -> {
            val start = selectedDate.minusDays(29)
            "${start.dayOfMonth} ${start.format(monthFmt)}–${selectedDate.dayOfMonth} ${selectedDate.format(monthFmt)}"
        }
        TimeRange.THREE_MONTHS -> {
            val m1 = selectedDate.minusMonths(2).format(DateTimeFormatter.ofPattern("MMM"))
            val m2 = selectedDate.format(DateTimeFormatter.ofPattern("MMM yyyy"))
            "$m1–$m2"
        }
        TimeRange.YEAR -> selectedDate.format(DateTimeFormatter.ofPattern("yyyy"))
    }

    val isRestingMode = selectedRange != TimeRange.DAY && selectedMode == HeartRateMode.RESTING_HEART_RATE

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(healthColors.background)
    ) {
        // App Bar
        item {
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
                    text = "Heart rate",
                    style = Typography.titleLarge,
                    color = healthColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                )

                IconButton(onClick = { /* Menu */ }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = healthColors.textPrimary
                    )
                }
            }
        }

        // Time Range Pills (D, W, M, 3M, Y) - Always visible
        item {
            TimeRangePills(
                selectedRange = selectedRange,
                ranges = TimeRange.entries,
                modifier = Modifier.padding(horizontal = 16.dp),
                onRangeSelected = {
                    selectedRange = it
                    scrubbedSample = null
                    scrubbedPointIndex = null
                    scrubbedZoneBarIndex = null
                    if (it != TimeRange.DAY) {
                        selectedMode = HeartRateMode.RESTING_HEART_RATE
                    }
                }
            )
        }

        // Date Navigator
        item {
            DateNavigator(
                dateLabel = dateLabel,
                onPrevious = { selectedDate = selectedDate.stepBackward(selectedRange) },
                onNext = {
                    val next = selectedDate.stepForward(selectedRange)
                    if (isPeriodSelectable(next, selectedRange)) {
                        selectedDate = next
                    }
                },
                canGoNext = canStepForward(selectedDate, selectedRange) &&
                    isPeriodSelectable(selectedDate.stepForward(selectedRange), selectedRange),
                onResetOrHistory = {
                    selectedDate = LocalDate.now()
                }
            )
        }

        // Primary Metric Header with Scrubber support
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                if (selectedRange == TimeRange.DAY) {
                    // Day view header
                    val sample = scrubbedSample
                    if (sample != null) {
                        val sampleTimeStr = sample.timestamp.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
                        val bpm = sample.bpm
                        val zoneColor = when {
                            bpm >= 166 -> healthColors.heartPeak
                            bpm >= 135 -> healthColors.heartVigorous
                            bpm >= 110 -> healthColors.heartModerate
                            else -> healthColors.heartLight
                        }
                        val zoneLabel = when {
                            bpm >= 166 -> "Peak (>166 BPM)"
                            bpm >= 135 -> "Vigorous (135–165 BPM)"
                            bpm >= 110 -> "Moderate (110–134 BPM)"
                            else -> "Light (<110 BPM)"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text("$bpm", style = MetricHero, color = healthColors.textPrimary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("bpm", color = healthColors.textSecondary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(zoneColor))
                                    Text("$sampleTimeStr • $zoneLabel", color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            IconButton(onClick = { scrubbedSample = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = healthColors.textMuted)
                            }
                        }
                    } else {
                        val summary = currentDailySummary
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                // No reading for the selected day: show the absent
                                // marker rather than a fabricated 0 or a crash.
                                text = if (summary != null) {
                                    "${summary.minBpm}–${summary.maxBpm}"
                                } else {
                                    com.healthcompanion.app.ui.format.HealthFormat.NO_VALUE
                                },
                                style = MetricHero,
                                color = healthColors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "bpm",
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = summary?.peakZoneSummary ?: "No heart-rate readings for this day",
                            color = healthColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else if (isRestingMode) {
                    // Resting Heart Rate header (matching media_1790796270473.png & media_1790796276894.png)
                    val ptIdx = scrubbedPointIndex
                    if (ptIdx != null && ptIdx in restingPoints.indices && restingPoints[ptIdx].bpm != null) {
                        val pt = restingPoints[ptIdx]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text("${pt.bpm}", style = MetricHero, color = healthColors.textPrimary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("bpm", color = healthColors.textSecondary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = pt.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                                    color = healthColors.textSecondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            IconButton(onClick = { scrubbedPointIndex = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = healthColors.textMuted)
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                // `avgRestingBpm` is nullable now, and
                                // interpolating it directly printed the literal
                                // string "null" on the headline.
                                text = avgRestingBpm?.toString()
                                    ?: com.healthcompanion.app.ui.format.HealthFormat.NO_VALUE,
                                style = MetricHero,
                                color = healthColors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "bpm (avg)",
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentRestingSummary?.comparisonText
                                ?: "No resting readings for this period",
                            color = healthColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    // Time in zones mode header: different for each zone
                    val zmIdx = scrubbedZoneBarIndex
                    if (zmIdx != null && zmIdx in periodZoneMinutes.indices) {
                        val zm = periodZoneMinutes[zmIdx]
                        val (valText, subText) = when (selectedZoneFilter) {
                            "Moderate" -> "${zm.moderateMinutes}" to "Moderate zone (110–129 BPM) • ${zm.date.format(dateFormatter)}"
                            "Vigorous" -> "${zm.vigorousMinutes}" to "Vigorous zone (130–149 BPM) • ${zm.date.format(dateFormatter)}"
                            "Peak" -> "${zm.peakMinutes}" to "Peak zone (≥150 BPM) • ${zm.date.format(dateFormatter)}"
                            else -> "${zm.totalMinutes}" to "Moderate: ${zm.moderateMinutes}m • Vigorous: ${zm.vigorousMinutes}m • Peak: ${zm.peakMinutes}m • ${zm.date.format(dateFormatter)}"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(valText, style = MetricHero, color = healthColors.textPrimary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("min", color = healthColors.textSecondary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                                }
                                Text(subText, color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = { scrubbedZoneBarIndex = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = healthColors.textMuted)
                            }
                        }
                    } else {
                        val activeDays = periodZoneMinutes.filter { !it.date.isAfter(today) && it.totalMinutes > 0 }
                        val (valText, subText) = when (selectedZoneFilter) {
                            "Moderate" -> {
                                val avg = if (activeDays.isNotEmpty()) activeDays.map { it.moderateMinutes }.average().toInt() else 24
                                "$avg" to "Moderate zone (110–129 BPM) daily average"
                            }
                            "Vigorous" -> {
                                val avg = if (activeDays.isNotEmpty()) activeDays.map { it.vigorousMinutes }.average().toInt() else 14
                                "$avg" to "Vigorous zone (130–149 BPM) daily average"
                            }
                            "Peak" -> {
                                val avg = if (activeDays.isNotEmpty()) activeDays.map { it.peakMinutes }.average().toInt() else 4
                                "$avg" to "Peak zone (≥150 BPM) daily average"
                            }
                            else -> {
                                val avg = if (activeDays.isNotEmpty()) activeDays.map { it.totalMinutes }.average().toInt() else 42
                                "$avg" to "Active zone minutes daily average"
                            }
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(valText, style = MetricHero, color = healthColors.textPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("min", color = healthColors.textSecondary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(subText, color = healthColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Active Chart Section
        item {
            if (selectedRange == TimeRange.DAY) {
                HeartRateChart(
                    summary = currentDailySummary,
                    scrubbedSample = scrubbedSample,
                    onScrub = { scrubbedSample = it }
                )
            } else if (isRestingMode) {
                // Resting Heart Rate Chart (cyan line with circular points)
                GoogleRestingHeartRateChart(
                    points = restingPoints,
                    selectedRange = selectedRange,
                    scrubbedIndex = scrubbedPointIndex,
                    onScrub = { scrubbedPointIndex = it }
                )
            } else {
                // Time in zones chart: different graph and colors for each zone!
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(periodZoneMinutes, selectedZoneFilter) {
                                    val count = periodZoneMinutes.size.coerceAtLeast(1)
                                    detectTapGestures { offset ->
                                        val slotW = size.width / count
                                        val idx = (offset.x / slotW).toInt().coerceIn(0, count - 1)
                                        if (periodZoneMinutes[idx].totalMinutes > 0) scrubbedZoneBarIndex = idx
                                    }
                                }
                                .pointerInput(periodZoneMinutes, selectedZoneFilter) {
                                    val count = periodZoneMinutes.size.coerceAtLeast(1)
                                    detectDragGestures(
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val slotW = size.width / count
                                            val idx = (change.position.x / slotW).toInt().coerceIn(0, count - 1)
                                            if (periodZoneMinutes[idx].totalMinutes > 0) scrubbedZoneBarIndex = idx
                                        }
                                    )
                                }
                        ) {
                            val w = size.width
                            val h = size.height
                            val count = periodZoneMinutes.size.coerceAtLeast(1)
                            val slotW = w / count
                            val barW = (slotW * 0.45f).coerceIn(4.dp.toPx(), 22.dp.toPx())

                            val maxMinutes = when (selectedZoneFilter) {
                                "Moderate" -> (periodZoneMinutes.maxOfOrNull { it.moderateMinutes } ?: 30).coerceAtLeast(30).toFloat()
                                "Vigorous" -> (periodZoneMinutes.maxOfOrNull { it.vigorousMinutes } ?: 30).coerceAtLeast(30).toFloat()
                                "Peak" -> (periodZoneMinutes.maxOfOrNull { it.peakMinutes } ?: 15).coerceAtLeast(15).toFloat()
                                else -> (periodZoneMinutes.maxOfOrNull { it.totalMinutes } ?: 60).coerceAtLeast(45).toFloat()
                            }

                            // Guidelines at 30 min (and 60 min if scaled high)
                            val y30 = h - (30f / maxMinutes).coerceIn(0f, 1f) * h
                            drawLine(
                                color = healthColors.heartModerate.copy(alpha = 0.35f),
                                start = Offset(0f, y30),
                                end = Offset(w, y30),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                            )
                            if (maxMinutes >= 60f) {
                                val y60 = h - (60f / maxMinutes).coerceIn(0f, 1f) * h
                                drawLine(
                                    color = healthColors.heartVigorous.copy(alpha = 0.35f),
                                    start = Offset(0f, y60),
                                    end = Offset(w, y60),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                                )
                            }

                            periodZoneMinutes.forEachIndexed { i, zm ->
                                val isScrubbed = scrubbedZoneBarIndex == i
                                val x = i * slotW + (slotW - barW) / 2f

                                if (isScrubbed) {
                                    val centerX = x + barW / 2f
                                    drawLine(
                                        color = healthColors.textPrimary.copy(alpha = 0.4f),
                                        start = Offset(centerX, 0f),
                                        end = Offset(centerX, h),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }

                                if (zm.totalMinutes > 0) {
                                    when (selectedZoneFilter) {
                                        "Moderate" -> {
                                            if (zm.moderateMinutes > 0) {
                                                val barH = (zm.moderateMinutes / maxMinutes) * h
                                                val yTop = h - barH
                                                drawRoundRect(
                                                    color = healthColors.heartModerate,
                                                    topLeft = Offset(x, yTop),
                                                    size = Size(barW, barH.coerceAtLeast(6f)),
                                                    cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                                                )
                                            }
                                        }
                                        "Vigorous" -> {
                                            if (zm.vigorousMinutes > 0) {
                                                val barH = (zm.vigorousMinutes / maxMinutes) * h
                                                val yTop = h - barH
                                                drawRoundRect(
                                                    color = healthColors.heartVigorous,
                                                    topLeft = Offset(x, yTop),
                                                    size = Size(barW, barH.coerceAtLeast(6f)),
                                                    cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                                                )
                                            }
                                        }
                                        "Peak" -> {
                                            if (zm.peakMinutes > 0) {
                                                val barH = (zm.peakMinutes / maxMinutes) * h
                                                val yTop = h - barH
                                                drawRoundRect(
                                                    color = healthColors.heartPeak,
                                                    topLeft = Offset(x, yTop),
                                                    size = Size(barW, barH.coerceAtLeast(6f)),
                                                    cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                                                )
                                            }
                                        }
                                        else -> {
                                            // "Time in zones" -> Stacked bar of Moderate, Vigorous, Peak
                                            val modH = (zm.moderateMinutes / maxMinutes) * h
                                            val vigH = (zm.vigorousMinutes / maxMinutes) * h
                                            val pkH = (zm.peakMinutes / maxMinutes) * h
                                            var currentBottom = h

                                            // Moderate segment (Bottom)
                                            if (modH > 0) {
                                                val yTop = currentBottom - modH
                                                drawRoundRect(
                                                    color = healthColors.heartModerate,
                                                    topLeft = Offset(x, yTop),
                                                    size = Size(barW, modH),
                                                    cornerRadius = CornerRadius(barW / 4f, barW / 4f)
                                                )
                                                currentBottom = yTop
                                            }

                                            // Vigorous segment (Middle)
                                            if (vigH > 0) {
                                                val yTop = currentBottom - vigH
                                                drawRoundRect(
                                                    color = healthColors.heartVigorous,
                                                    topLeft = Offset(x, yTop),
                                                    size = Size(barW, vigH),
                                                    cornerRadius = CornerRadius(barW / 4f, barW / 4f)
                                                )
                                                currentBottom = yTop
                                            }

                                            // Peak segment (Top)
                                            if (pkH > 0) {
                                                val yTop = currentBottom - pkH
                                                drawRoundRect(
                                                    color = healthColors.heartPeak,
                                                    topLeft = Offset(x, yTop),
                                                    size = Size(barW, pkH),
                                                    cornerRadius = CornerRadius(barW / 4f, barW / 4f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (selectedRange == TimeRange.WEEK) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                        ) {
                            periodZoneMinutes.forEach { zm ->
                                Text(
                                    text = zm.date.dayOfWeek.name.take(1),
                                    modifier = Modifier.weight(1f),
                                    color = healthColors.textSecondary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // Series Toggles: [ ✓ Resting heart rate ]  [ Time in zones ▾ ]
                // Directly below chart
        if (selectedRange != TimeRange.DAY) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Left button: [ ✓ Resting heart rate ]
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(if (isRestingMode) healthColors.accentTeal else healthColors.cardBackground)
                            .clickable { selectedMode = HeartRateMode.RESTING_HEART_RATE }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isRestingMode) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = healthColors.onAccentTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "Resting heart rate",
                                color = if (isRestingMode) healthColors.onAccentTeal else healthColors.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Right button: [ Time in zones ▾ ] with DropdownMenu
                    var showZoneDropdown by remember { mutableStateOf(false) }

                    Box {
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraLarge)
                                .background(if (!isRestingMode) healthColors.accentTeal else healthColors.cardBackground)
                                .clickable {
                                    if (isRestingMode) {
                                        selectedMode = HeartRateMode.TIME_IN_ZONES
                                    } else {
                                        showZoneDropdown = true
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (!isRestingMode) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = healthColors.onAccentTeal,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = selectedZoneFilter,
                                    color = if (!isRestingMode) healthColors.onAccentTeal else healthColors.textPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = if (!isRestingMode) healthColors.onAccentTeal else healthColors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showZoneDropdown,
                            onDismissRequest = { showZoneDropdown = false },
                            modifier = Modifier.background(healthColors.cardBackground)
                        ) {
                            val zoneOptions = listOf("Time in zones", "Moderate", "Vigorous", "Peak")
                            zoneOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (selectedZoneFilter == option) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = healthColors.accentTeal,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.size(16.dp))
                                            }
                                            Text(
                                                text = option,
                                                color = healthColors.textPrimary,
                                                fontWeight = if (selectedZoneFilter == option) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedZoneFilter = option
                                        selectedMode = HeartRateMode.TIME_IN_ZONES
                                        showZoneDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

                // "About heart rate accuracy" Card
        if (showAccuracyCard) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(healthColors.cardBackground)
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = healthColors.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "About heart rate accuracy",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = healthColors.textPrimary
                                )
                            }
                            IconButton(
                                onClick = { showAccuracyCard = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = healthColors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = "To get the most reliable readings, check that your watch is snug on your wrist.",
                            color = healthColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { /* Learn more */ },
                                shape = MaterialTheme.shapes.extraLarge,
                                border = BorderStroke(1.dp, healthColors.cardBorder)
                            ) {
                                Text(
                                    text = "Learn more",
                                    color = healthColors.textPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section Breakdown below card
        if (selectedRange != TimeRange.DAY && isRestingMode) {
            // Header: Date range on left, "Resting heart rate (avg)" on right
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = healthColors.textPrimary
                    )
                    Text(
                        text = "Resting heart rate (avg)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = healthColors.textSecondary
                    )
                }
            }

            // Daily list of resting heart rates in reverse chronological order matching energy burned style
            val pastPoints = restingPoints.filter { it.bpm != null && !it.date.isAfter(today) }.reversed()
            if (pastPoints.isEmpty()) {
                item {
                    Text(
                        text = "No resting heart rate recorded in this period.",
                        color = healthColors.textMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        pastPoints.forEachIndexed { idx, pt ->
                            val itemLabel = when (pt.date) {
                                today -> "Today"
                                today.minusDays(1) -> "Yesterday"
                                else -> pt.date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
                            }
                            com.healthcompanion.app.ui.components.ConnectedListItem(
                                index = idx,
                                count = pastPoints.size,
                                trailingContent = {
                                    Text(
                                        text = "${pt.bpm} BPM",
                                        color = healthColors.textPrimary,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            ) {
                                Text(
                                    text = itemLabel,
                                    color = healthColors.textPrimary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Time in heart-rate zones section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Time in heart-rate zones",
                            style = Typography.titleLarge,
                            color = healthColors.textPrimary
                        )
                        Text(
                            text = "Learn more",
                            color = healthColors.accentBlue,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {}
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your heart-rate zones are personalised to your cardiovascular fitness and age.",
                        color = healthColors.textMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    currentDailySummary?.zones.orEmpty().forEach { zone ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${zone.name} • ${zone.percentage}% • ${zone.minutesSpent} m",
                                    color = healthColors.textPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${zone.minBpm}–${zone.maxBpm} BPM",
                                    color = healthColors.textSecondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(Spacing.s))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(16.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(healthColors.cardBackground)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth((zone.percentage.coerceIn(0, 100) / 100f).coerceAtLeast(0.02f))
                                        .clip(MaterialTheme.shapes.small)
                                        .background(zone.color())
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

/**
 * Clean resting heart rate line chart.
 * Features:
 *  - Cyan line connecting resting heart rate points
 *  - Circular cyan dots at every recorded point
 *  - Empty slots for future days of the week or unrecorded dates
 *  - Right-aligned Y-axis labels with horizontal gridlines
 *  - Day initials (M, T, W, T, F, S, S) or month date markers aligned across bottom
 *  - Interactive scrub/tap with dashed guideline and highlighted dot
 */
@Composable
fun GoogleRestingHeartRateChart(
    points: List<RestingPoint>,
    selectedRange: TimeRange,
    scrubbedIndex: Int?,
    onScrub: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val validPoints = points.filter { it.bpm != null }

    val rawMin = validPoints.mapNotNull { it.bpm }.minOrNull() ?: 56
    val rawMax = validPoints.mapNotNull { it.bpm }.maxOrNull() ?: 86

                // Compute clean 4-tier Y-axis scale
    val step = when {
        rawMax - rawMin > 35 -> 15
        rawMax - rawMin > 18 -> 12
        else -> 10
    }
    val minBpm = ((rawMin - 4) / step) * step
    val maxBpm = minBpm + (step * 3)
    val bpmRange = (maxBpm - minBpm).toFloat().coerceAtLeast(10f)

    val yAxisLabels = listOf(
        maxBpm,
        maxBpm - step,
        maxBpm - step * 2,
        minBpm
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(points) {
                        if (points.isEmpty()) return@pointerInput
                        val yAxisWidth = 36.dp.toPx()
                        val chartWidth = size.width - yAxisWidth
                        val count = points.size

                        fun handleTouch(x: Float) {
                            val idx = ((x / chartWidth) * count).toInt().coerceIn(0, count - 1)
                            if (points[idx].bpm != null) {
                                onScrub(idx)
                            }
                        }

                        detectTapGestures { offset -> handleTouch(offset.x) }
                    }
                    .pointerInput(points) {
                        if (points.isEmpty()) return@pointerInput
                        val yAxisWidth = 36.dp.toPx()
                        val chartWidth = size.width - yAxisWidth
                        val count = points.size

                        detectDragGestures(
                            onDragStart = { offset ->
                                val idx = ((offset.x / chartWidth) * count).toInt().coerceIn(0, count - 1)
                                if (points[idx].bpm != null) onScrub(idx)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val idx = ((change.position.x / chartWidth) * count).toInt().coerceIn(0, count - 1)
                                if (points[idx].bpm != null) onScrub(idx)
                            }
                        )
                    }
            ) {
                val yAxisWidth = 36.dp.toPx()
                val chartWidth = size.width - yAxisWidth
                val topPadding = 12.dp.toPx()
                val bottomPadding = 16.dp.toPx()
                val chartHeight = size.height - topPadding - bottomPadding

                fun bpmToY(bpm: Float): Float {
                    val norm = (bpm - minBpm) / bpmRange
                    return topPadding + chartHeight - (norm.coerceIn(0f, 1f) * chartHeight)
                }

                // Grid lines for Y-axis levels
                val gridColor = healthColors.cardBorder
                yAxisLabels.forEach { bpm ->
                    val y = bpmToY(bpm.toFloat())
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Plot line and dots for valid points
                val count = points.size
                val path = Path()
                var pathStarted = false
                val renderedPoints = mutableListOf<Pair<Int, Offset>>()

                points.forEachIndexed { index, pt ->
                    if (pt.bpm != null) {
                        val x = if (selectedRange == TimeRange.WEEK) {
                            val slotW = chartWidth / 7f
                            slotW * index + slotW / 2f
                        } else {
                            if (count > 1) (index.toFloat() / (count - 1)) * chartWidth else chartWidth / 2f
                        }
                        val y = bpmToY(pt.bpm.toFloat())
                        val offset = Offset(x, y)
                        renderedPoints.add(index to offset)

                        if (!pathStarted) {
                            path.moveTo(x, y)
                            pathStarted = true
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                }

                // Draw connecting line in vibrant cyan
                val cyanColor = healthColors.accentTeal
                if (renderedPoints.size > 1) {
                    drawPath(
                        path = path,
                        color = cyanColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Draw circular dots on points
                renderedPoints.forEach { (index, offset) ->
                    val isScrubbed = scrubbedIndex == index
                    if (!isScrubbed) {
                        drawCircle(
                            color = cyanColor,
                            radius = 4.5.dp.toPx(),
                            center = offset
                        )
                    }
                }

                // Draw active scrubber guide line and dot
                scrubbedIndex?.let { sIdx ->
                    val match = renderedPoints.firstOrNull { it.first == sIdx }
                    if (match != null) {
                        val pt = match.second
                        // Vertical guideline
                        drawLine(
                            color = healthColors.textPrimary.copy(alpha = 0.5f),
                            start = Offset(pt.x, topPadding),
                            end = Offset(pt.x, topPadding + chartHeight),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                        // Outer cyan circle
                        drawCircle(
                            color = cyanColor,
                            radius = 7.dp.toPx(),
                            center = pt
                        )
                        // Inner circle
                        drawCircle(
                            color = healthColors.cardBackground,
                            radius = 3.dp.toPx(),
                            center = pt
                        )
                    }
                }
            }

            // Right-aligned Y-axis labels
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(36.dp)
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                yAxisLabels.forEach { bpm ->
                    Text(
                        text = "$bpm",
                        color = healthColors.textMuted,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // X-axis Day / Date labels underneath chart
        if (selectedRange == TimeRange.WEEK) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 36.dp, top = 6.dp)
            ) {
                points.forEach { pt ->
                    val initial = pt.date.dayOfWeek.name.take(1)
                    Text(
                        text = initial,
                        modifier = Modifier.weight(1f),
                        color = healthColors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (selectedRange == TimeRange.MONTH) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 36.dp, top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val labels = listOf("1", "10", "20", "${points.size}")
                labels.forEach { lbl ->
                    Text(
                        text = lbl,
                        color = healthColors.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}
