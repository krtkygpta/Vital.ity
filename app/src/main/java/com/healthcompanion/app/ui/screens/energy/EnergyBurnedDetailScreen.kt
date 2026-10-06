package com.healthcompanion.app.ui.screens.energy

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.healthcompanion.app.ui.components.DateNavigator
import com.healthcompanion.app.ui.components.EmptyState
import com.healthcompanion.app.ui.components.M3EConnectedButtonGroup
import com.healthcompanion.app.ui.components.TimeRange
import com.healthcompanion.app.ui.components.TimeRangePills
import com.healthcompanion.app.ui.components.canStepForward
import com.healthcompanion.app.ui.components.isPeriodSelectable
import com.healthcompanion.app.ui.components.stepBackward
import com.healthcompanion.app.ui.components.stepForward
import com.healthcompanion.app.ui.components.weekEndMon
import com.healthcompanion.app.ui.components.weekStartMon
import com.healthcompanion.app.ui.components.LocalDateSaver
import com.healthcompanion.app.ui.components.enumSaver
import com.healthcompanion.app.ui.format.HealthFormat
import com.healthcompanion.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class CalorieMode(val label: String) {
    TOTAL("Total energy"),
    ACTIVE("Active calories"),
    NATURAL("Metabolic calories")
}

@Composable
fun EnergyBurnedDetailScreen(
    onBack: () -> Unit,
    onQueryDailyCalories: (suspend (LocalDate) -> Result<Int>)? = null,
    onQueryHourlyCalories: (suspend (LocalDate) -> Result<List<Pair<Int, Int>>>)? = null,
    onQueryWeeklyCalories: (suspend (LocalDate) -> Result<List<Pair<LocalDate, Int>>>)? = null,
    onQueryPeriodCalories: (suspend (String, Int, LocalDate) -> Result<List<Pair<LocalDate, Int>>>)? = null,
    onQueryDailyActiveCalories: (suspend (LocalDate) -> Result<Int>)? = null,
    onQueryHourlyActiveCalories: (suspend (LocalDate) -> Result<List<Pair<Int, Int>>>)? = null,
    onQueryWeeklyActiveCalories: (suspend (LocalDate) -> Result<List<Pair<LocalDate, Int>>>)? = null,
    onQueryPeriodActiveCalories: (suspend (String, Int, LocalDate) -> Result<List<Pair<LocalDate, Int>>>)? = null,
    onQueryDailyBasalCalories: (suspend (LocalDate) -> Result<Int>)? = null,
    onQueryHourlyBasalCalories: (suspend (LocalDate) -> Result<List<Pair<Int, Int>>>)? = null,
    onQueryWeeklyBasalCalories: (suspend (LocalDate) -> Result<List<Pair<LocalDate, Int>>>)? = null,
    onQueryPeriodBasalCalories: (suspend (String, Int, LocalDate) -> Result<List<Pair<LocalDate, Int>>>)? = null,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    var calorieMode by rememberSaveable(stateSaver = enumSaver<CalorieMode>()) { mutableStateOf(CalorieMode.TOTAL) }
    var selectedRange by rememberSaveable(stateSaver = enumSaver<TimeRange>()) { mutableStateOf(TimeRange.WEEK) }
    var selectedDate by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(LocalDate.now()) }
    var scrubbedHour by rememberSaveable { mutableStateOf<Int?>(null) }
    var scrubbedPeriodIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
    val weekStart = weekStartMon(selectedDate)
    val weekFormatter = DateTimeFormatter.ofPattern("d MMM")
    val weekRangeLabel = "${weekStart.dayOfMonth}–${weekEndMon(selectedDate).format(weekFormatter)}"

    // Real reads only. A failed read leaves the value absent rather than
    // substituting a sample figure.
    var dayCalories by remember { mutableStateOf<Int?>(null) }
    var hourlyCalories by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    var weeklyCalories by remember { mutableStateOf<List<Pair<LocalDate, Int>>>(emptyList()) }
    var periodTotalCalories by remember { mutableStateOf<List<Pair<LocalDate, Int>>>(emptyList()) }

    var dayActiveCalories by remember { mutableStateOf<Int?>(null) }
    var hourlyActiveCalories by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    var weeklyActiveCalories by remember { mutableStateOf<List<Pair<LocalDate, Int>>>(emptyList()) }
    var periodActiveCalories by remember { mutableStateOf<List<Pair<LocalDate, Int>>>(emptyList()) }

    var dayBasalCalories by remember { mutableStateOf<Int?>(null) }
    var hourlyBasalCalories by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    var weeklyBasalCalories by remember { mutableStateOf<List<Pair<LocalDate, Int>>>(emptyList()) }
    var periodBasalCalories by remember { mutableStateOf<List<Pair<LocalDate, Int>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val periodDays = when (selectedRange) {
        TimeRange.DAY -> 1
        TimeRange.WEEK -> 7
        TimeRange.MONTH -> 30
        TimeRange.THREE_MONTHS -> 90
        TimeRange.YEAR -> 365
    }

    LaunchedEffect(selectedDate, selectedRange) {
        scrubbedHour = null
        scrubbedPeriodIndex = null
        isLoading = true
        try {
            dayCalories = onQueryDailyCalories?.invoke(selectedDate)?.getOrNull()
            hourlyCalories = onQueryHourlyCalories?.invoke(selectedDate)?.getOrDefault(emptyList()).orEmpty()
            weeklyCalories = onQueryWeeklyCalories?.invoke(selectedDate)?.getOrDefault(emptyList()).orEmpty()
            periodTotalCalories = onQueryPeriodCalories?.invoke(selectedRange.name, periodDays, selectedDate)
                ?.getOrDefault(emptyList()).orEmpty()

            dayActiveCalories = onQueryDailyActiveCalories?.invoke(selectedDate)?.getOrNull()
            hourlyActiveCalories = onQueryHourlyActiveCalories?.invoke(selectedDate)?.getOrDefault(emptyList()).orEmpty()
            weeklyActiveCalories = onQueryWeeklyActiveCalories?.invoke(selectedDate)?.getOrDefault(emptyList()).orEmpty()
            periodActiveCalories = onQueryPeriodActiveCalories?.invoke(selectedRange.name, periodDays, selectedDate)
                ?.getOrDefault(emptyList()).orEmpty()

            dayBasalCalories = onQueryDailyBasalCalories?.invoke(selectedDate)?.getOrNull()
            hourlyBasalCalories = onQueryHourlyBasalCalories?.invoke(selectedDate)?.getOrDefault(emptyList()).orEmpty()
            weeklyBasalCalories = onQueryWeeklyBasalCalories?.invoke(selectedDate)?.getOrDefault(emptyList()).orEmpty()
            periodBasalCalories = onQueryPeriodBasalCalories?.invoke(selectedRange.name, periodDays, selectedDate)
                ?.getOrDefault(emptyList()).orEmpty()
        } finally {
            isLoading = false
        }
    }

    val effectiveDayActive = dayActiveCalories ?: 0
    val effectiveDayTotal = dayCalories ?: 0

    // Hoisted out of the Canvas draw scope: MaterialTheme is @Composable and
    // cannot be read while drawing.
    val overGoalColor = MaterialTheme.colorScheme.error

    val hasAnyData = when (calorieMode) {
        CalorieMode.TOTAL -> ((dayCalories ?: 0) > 0) ||
            hourlyCalories.any { it.second > 0 } ||
            weeklyCalories.any { it.second > 0 } ||
            periodTotalCalories.any { it.second > 0 }
        CalorieMode.ACTIVE -> ((dayActiveCalories ?: 0) > 0) ||
            hourlyActiveCalories.any { it.second > 0 } ||
            weeklyActiveCalories.any { it.second > 0 } ||
            periodActiveCalories.any { it.second > 0 }
        CalorieMode.NATURAL -> ((dayBasalCalories ?: 0) > 0) ||
            hourlyBasalCalories.any { it.second > 0 } ||
            weeklyBasalCalories.any { it.second > 0 } ||
            periodBasalCalories.any { it.second > 0 }
    }

    val currentDayCal = when (calorieMode) {
        CalorieMode.TOTAL -> effectiveDayTotal
        CalorieMode.ACTIVE -> effectiveDayActive
        CalorieMode.NATURAL -> dayBasalCalories ?: 0
    }

    val today = LocalDate.now()
    val isSelectedToday = (selectedDate == today)
    val currentHour = if (isSelectedToday) java.time.LocalTime.now().hour else 24
    val isFutureDate = selectedDate.isAfter(today)

    val currentHourlyCal = when (calorieMode) {
        CalorieMode.TOTAL -> hourlyCalories.map { (h, c) ->
            h to if (isFutureDate || (isSelectedToday && h > currentHour)) 0 else c
        }
        CalorieMode.ACTIVE -> hourlyActiveCalories.map { (h, c) ->
            h to if (isFutureDate || (isSelectedToday && h > currentHour)) 0 else c
        }
        CalorieMode.NATURAL -> hourlyBasalCalories.map { (h, c) ->
            h to if (isFutureDate || (isSelectedToday && h > currentHour)) 0 else c
        }
    }

    val currentWeeklyCal = when (calorieMode) {
        CalorieMode.TOTAL -> weeklyCalories.map { (d, c) ->
            d to if (d.isAfter(today)) 0 else c
        }
        CalorieMode.ACTIVE -> weeklyActiveCalories.map { (d, c) ->
            d to if (d.isAfter(today)) 0 else c
        }
        CalorieMode.NATURAL -> weeklyBasalCalories.map { (d, c) ->
            d to if (d.isAfter(today)) 0 else c
        }
    }

    val currentPeriodBars: List<Pair<LocalDate, Int>> = when (selectedRange) {
        TimeRange.DAY -> emptyList()
        TimeRange.WEEK -> currentWeeklyCal
        else -> when (calorieMode) {
            CalorieMode.ACTIVE -> periodActiveCalories
            CalorieMode.NATURAL -> periodBasalCalories
            CalorieMode.TOTAL -> periodTotalCalories
        }
    }

    val periodAvg = if (currentPeriodBars.isNotEmpty()) {
        currentPeriodBars.sumOf { it.second } / currentPeriodBars.size
    } else {
        currentDayCal
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(healthColors.background)
            .padding(horizontal = 16.dp)
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
                    text = "Energy burned",
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
        }

        // Time Range Pills (D, W, M, 3M, Y)
        item {
            TimeRangePills(
                selectedRange = selectedRange,
                onRangeSelected = {
                    selectedRange = it
                    scrubbedHour = null
                    scrubbedPeriodIndex = null
                }
            )
        }

        // Calorie Mode: Total energy vs Active calories vs Resting calories
        item {
            M3EConnectedButtonGroup(
                items = listOf(CalorieMode.TOTAL, CalorieMode.ACTIVE, CalorieMode.NATURAL),
                selectedItem = calorieMode,
                onItemSelected = {
                    calorieMode = it
                    scrubbedHour = null
                    scrubbedPeriodIndex = null
                },
                labelProvider = { it.label }
            )
        }

        // Date Navigator
        item {
            val dateLabel = when (selectedRange) {
                TimeRange.DAY -> {
                    when (selectedDate) {
                        LocalDate.now() -> "Today"
                        LocalDate.now().minusDays(1) -> "Yesterday"
                        else -> selectedDate.format(dateFormatter)
                    }
                }
                TimeRange.WEEK -> weekRangeLabel
                TimeRange.MONTH -> selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                TimeRange.THREE_MONTHS -> {
                    val m1 = selectedDate.minusMonths(2).format(DateTimeFormatter.ofPattern("MMM"))
                    val m2 = selectedDate.format(DateTimeFormatter.ofPattern("MMM yyyy"))
                    "$m1–$m2"
                }
                TimeRange.YEAR -> selectedDate.format(DateTimeFormatter.ofPattern("yyyy"))
            }

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

        // Big headline value with (X) button
        item {
            val isScrubbed = scrubbedHour != null || scrubbedPeriodIndex != null

            val pIdx = scrubbedPeriodIndex
            val displayCal: Int = when {
                selectedRange == TimeRange.DAY && scrubbedHour != null -> {
                    currentHourlyCal.firstOrNull { it.first == scrubbedHour }?.second ?: 0
                }
                selectedRange == TimeRange.DAY -> currentDayCal
                pIdx != null && pIdx in currentPeriodBars.indices -> {
                    currentPeriodBars[pIdx].second
                }
                else -> periodAvg
            }

            val modeNoun = when (calorieMode) {
                CalorieMode.TOTAL -> "total"
                CalorieMode.ACTIVE -> "active"
                CalorieMode.NATURAL -> "metabolic"
            }
            val hour = scrubbedHour
            val subtitleText: String = when {
                selectedRange == TimeRange.DAY && hour != null -> {
                    String.format(Locale.getDefault(), "%02d:00–%02d:00", hour, hour + 1)
                }
                selectedRange == TimeRange.DAY -> when (calorieMode) {
                    CalorieMode.TOTAL -> {
                        "Active: ${HealthFormat.count(dayActiveCalories ?: 0)} kcal • " +
                            "Metabolic: ${HealthFormat.count(dayBasalCalories ?: 0)} kcal"
                    }
                    CalorieMode.ACTIVE -> "Burned through exercise & movement"
                    CalorieMode.NATURAL -> "Burned by your body at rest"
                }
                pIdx != null && pIdx in currentPeriodBars.indices -> {
                    currentPeriodBars[pIdx].first.format(dateFormatter)
                }
                selectedRange == TimeRange.WEEK -> {
                    "${HealthFormat.count(currentWeeklyCal.sumOf { it.second })} kcal $modeNoun this week"
                }
                selectedRange == TimeRange.MONTH -> "Daily $modeNoun average for this month"
                selectedRange == TimeRange.THREE_MONTHS -> "Daily $modeNoun average over 3 months"
                selectedRange == TimeRange.YEAR -> "Daily $modeNoun average over the year"
                else -> ""
            }

            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (hasAnyData) HealthFormat.compact(displayCal) else HealthFormat.NO_VALUE,
                            // MetricHero uses tabular figures, so scrubbing the
                            // chart no longer makes the headline digits re-flow.
                            style = MetricHero,
                            color = healthColors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "kcal",
                            color = healthColors.textSecondary,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    if (isScrubbed) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(healthColors.pillUnselectedBg)
                                .clickable {
                                    scrubbedHour = null
                                    scrubbedPeriodIndex = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Selection",
                                tint = healthColors.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Text(
                    text = subtitleText,
                    color = healthColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Chart View (Day: hourly bars; Week: 7 bars; Month: 30 bars)
        item {
            val currentHourly = currentHourlyCal
            val chartDataAvailable = when {
                isLoading -> false
                selectedRange == TimeRange.DAY -> currentHourly.any { it.second > 0 }
                else -> currentPeriodBars.any { it.second > 0 }
            }

            if (!chartDataAvailable && !isLoading) {
                // A blank frame with axis numbers reads as "0 calories"; this
                // says there is no data instead.
                EmptyState(
                    title = "No energy data",
                    body = "No calories were recorded for this period.",
                    icon = Icons.Default.LocalFireDepartment,
                    actionLabel = "Connect Health Connect",
                    onAction = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            } else if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .padding(vertical = 8.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(selectedRange, calorieMode, currentHourly, currentPeriodBars) {
                            val yAxisWidth = 44.dp.toPx()
                            val chartWidth = size.width - yAxisWidth

                            fun handleTouch(touchX: Float) {
                                if (selectedRange == TimeRange.DAY) {
                                    val slotW = chartWidth / 24f
                                    scrubbedHour = (touchX / slotW).toInt().coerceIn(0, 23)
                                } else if (currentPeriodBars.isNotEmpty()) {
                                    val slotW = chartWidth / currentPeriodBars.size
                                    scrubbedPeriodIndex = (touchX / slotW).toInt().coerceIn(0, currentPeriodBars.size - 1)
                                }
                            }

                            detectTapGestures(onTap = { offset -> handleTouch(offset.x) })
                        }
                        .pointerInput(selectedRange, calorieMode, currentHourly, currentPeriodBars) {
                            val yAxisWidth = 44.dp.toPx()
                            val chartWidth = size.width - yAxisWidth

                            fun handleTouch(touchX: Float) {
                                if (selectedRange == TimeRange.DAY) {
                                    val slotW = chartWidth / 24f
                                    scrubbedHour = (touchX / slotW).toInt().coerceIn(0, 23)
                                } else if (currentPeriodBars.isNotEmpty()) {
                                    val slotW = chartWidth / currentPeriodBars.size
                                    scrubbedPeriodIndex = (touchX / slotW).toInt().coerceIn(0, currentPeriodBars.size - 1)
                                }
                            }

                            detectDragGestures(
                                onDragStart = { offset -> handleTouch(offset.x) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    handleTouch(change.position.x)
                                }
                            )
                        }
                ) {
                    val yAxisWidth = 44.dp.toPx()
                    val chartWidth = size.width - yAxisWidth
                    val chartHeight = size.height - 24.dp.toPx()

                    // The axis is derived from the data. It used to be pinned to
                    // a fixed 2600 kcal ceiling, so a 4000 kcal day rendered a bar
                    // identical to a 2600 kcal day and the goal line was the only
                    // thing distinguishing them.
                    val targetGoal = when (calorieMode) {
                        CalorieMode.TOTAL -> 2252
                        CalorieMode.ACTIVE -> 600
                        CalorieMode.NATURAL -> 0
                    }

                    val plottedValues = if (selectedRange == TimeRange.DAY) {
                        currentHourlyCal.map { it.second }
                    } else {
                        currentPeriodBars.map { it.second }
                    }
                    val peakValue = plottedValues.maxOrNull() ?: 0
                    val scaleMax = maxOf(
                        peakValue,
                        targetGoal,
                        1
                    )
                    // Headroom so the tallest bar doesn't touch the top edge.
                    val axisMax = (scaleMax * 1.15f).toInt().coerceAtLeast(1)
                    val valueToY = { value: Int ->
                        chartHeight - ((value.toFloat() / axisMax) * chartHeight)
                    }

                    // Goal line drawn only when it falls inside the plotted range.
                    if (targetGoal in 1 until axisMax) {
                        val targetY = valueToY(targetGoal)
                        drawLine(
                            color = healthColors.accentTeal,
                            start = Offset(0f, targetY),
                            end = Offset(chartWidth, targetY),
                            strokeWidth = 2f
                        )
                    }

                    if (selectedRange == TimeRange.DAY) {
                        // 24 Hourly bars
                        val barW = (chartWidth / 24f) * 0.65f
                        val slotW = chartWidth / 24f
                        currentHourly.forEach { (hour, cal) ->
                            val x = hour * slotW + (slotW - barW) / 2f
                            val barH = ((cal.toFloat() / axisMax) * chartHeight)
                                .coerceIn(0f, chartHeight)
                            val y = chartHeight - barH
                            if (barH > 0) {
                                drawRoundRect(
                                    color = healthColors.accentTeal,
                                    topLeft = Offset(x, y),
                                    size = Size(barW, barH.coerceAtLeast(4f)),
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                )
                            }
                        }

                        // Scrubber for Day
                        scrubbedHour?.let { h ->
                            val cal = currentHourly.firstOrNull { it.first == h }?.second ?: 0
                            val x = h * slotW + (slotW - barW) / 2f
                            val centerX = x + barW / 2f
                            val barH = ((cal.toFloat() / axisMax) * chartHeight)
                                .coerceIn(0f, chartHeight)
                            val knobY = if (cal > 0) chartHeight - barH else chartHeight

                            drawLine(
                                color = healthColors.textPrimary.copy(alpha = Alpha.ChartFill + 0.7f),
                                start = Offset(centerX, 0f),
                                end = Offset(centerX, chartHeight),
                                strokeWidth = 1.5.dp.toPx()
                            )
                            drawCircle(
                                color = healthColors.textPrimary,
                                radius = 5.dp.toPx(),
                                center = Offset(centerX, knobY)
                            )
                            drawCircle(
                                color = healthColors.accentTeal,
                                radius = 3.dp.toPx(),
                                center = Offset(centerX, knobY)
                            )
                        }
                    } else {
                        // Multi-period bars (Week: 7, Month: 30, 3M: 12, Year: 12)
                        val count = currentPeriodBars.size.coerceAtLeast(1)
                        val slotW = chartWidth / count
                        val barW = when {
                            count <= 7 -> 24.dp.toPx()
                            count <= 14 -> 14.dp.toPx()
                            count <= 31 -> (slotW * 0.7f).coerceIn(4.dp.toPx(), 8.dp.toPx())
                            else -> 4.dp.toPx()
                        }
                        val cornerRadius = CornerRadius(barW / 2f, barW / 2f)

                        currentPeriodBars.forEachIndexed { i, (_, cal) ->
                            if (cal > 0) {
                                val x = i * slotW + (slotW - barW) / 2f
                                val barH = ((cal.toFloat() / axisMax) * chartHeight)
                                    .coerceIn(0f, chartHeight)
                                val y = chartHeight - barH

                                // A bar above the goal is drawn in the error
                                // colour so "over goal" is visible rather than
                                // implied by a bar touching the top of the frame.
                                val barColor = if (targetGoal > 0 && cal > targetGoal) overGoalColor
                                else healthColors.accentTeal
                                drawRoundRect(
                                    color = barColor,
                                    topLeft = Offset(x, y),
                                    size = Size(barW, barH.coerceAtLeast(4f)),
                                    cornerRadius = cornerRadius
                                )
                            }
                        }

                        // Scrubber for multi-period
                        scrubbedPeriodIndex?.let { idx ->
                            if (idx in currentPeriodBars.indices) {
                                val x = idx * slotW + (slotW - barW) / 2f
                                val centerX = x + barW / 2f
                                val cal = currentPeriodBars[idx].second
                                val barH = ((cal.toFloat() / axisMax) * chartHeight)
                                    .coerceIn(0f, chartHeight)
                                val knobY = chartHeight - barH

                                drawLine(
                                    color = healthColors.textPrimary.copy(alpha = Alpha.ChartFill + 0.7f),
                                    start = Offset(centerX, 0f),
                                    end = Offset(centerX, chartHeight),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                                drawCircle(
                                    color = healthColors.textPrimary,
                                    radius = 5.dp.toPx(),
                                    center = Offset(centerX, knobY)
                                )
                                drawCircle(
                                    color = healthColors.accentTeal,
                                    radius = 3.dp.toPx(),
                                    center = Offset(centerX, knobY)
                                )
                            }
                        }
                    }
                }

                // Y-axis labels, derived from the same scale the bars use.
                // These were previously fixed strings (2,252 / 1,500 / 750 / 0)
                // that never changed regardless of the data.
                val axisScaleMax = {
                    val goal = when (calorieMode) {
                        CalorieMode.TOTAL -> 2252
                        CalorieMode.ACTIVE -> 600
                        CalorieMode.NATURAL -> 0
                    }
                    val peak = when (selectedRange) {
                        TimeRange.DAY -> currentHourlyCal.maxOfOrNull { it.second } ?: 0
                        else -> currentPeriodBars.maxOfOrNull { it.second } ?: 0
                    }
                    (maxOf(peak, goal, 1) * 1.15f).toInt()
                }()

                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = HealthFormat.compact(axisScaleMax),
                        color = healthColors.textMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = HealthFormat.compact(axisScaleMax * 2 / 3),
                        color = healthColors.textMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = HealthFormat.compact(axisScaleMax / 3),
                        color = healthColors.textMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "0",
                        color = healthColors.textMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // X-axis labels below bars
            if (selectedRange == TimeRange.DAY) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 44.dp, top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("12am", "4am", "8am", "12pm", "4pm", "8pm", "12am").forEach {
                        Text(it, color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else if (selectedRange == TimeRange.WEEK) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 44.dp, top = 2.dp)
                ) {
                    currentPeriodBars.forEach { (date, _) ->
                        val letter = date.dayOfWeek.name.take(1)
                        Text(
                            letter,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = healthColors.textSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else if (selectedRange == TimeRange.MONTH) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 44.dp, top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("1", "7", "14", "21", "28").forEach {
                        Text(it, color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else if (selectedRange == TimeRange.THREE_MONTHS) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 44.dp, top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val m1 = selectedDate.minusMonths(2).format(DateTimeFormatter.ofPattern("MMM"))
                    val m2 = selectedDate.minusMonths(1).format(DateTimeFormatter.ofPattern("MMM"))
                    val m3 = selectedDate.format(DateTimeFormatter.ofPattern("MMM"))
                    listOf(m1, m2, m3).forEach {
                        Text(it, color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 44.dp, top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("Jan", "Mar", "May", "Jul", "Sep", "Nov").forEach {
                        Text(it, color = healthColors.textMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // Daily Breakdown list
        item {
            Text(
                text = if (selectedRange == TimeRange.DAY) "Hourly breakdown" else "Period breakdown",
                style = Typography.titleLarge,
                color = healthColors.textPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        val breakdownList = if (selectedRange == TimeRange.DAY) {
            currentHourlyCal.filter { it.second > 0 }.reversed().map { (hour, cal) ->
                String.format(Locale.getDefault(), "%02d:00–%02d:00", hour, (hour + 1) % 24) to cal
            }
        } else {
            currentPeriodBars.reversed().take(30).map { (date, cal) ->
                val title = when (date) {
                    LocalDate.now() -> "Today"
                    LocalDate.now().minusDays(1) -> "Yesterday"
                    else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
                }
                title to cal
            }
        }

        if (breakdownList.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    breakdownList.forEachIndexed { idx, (title, cal) ->
                        com.healthcompanion.app.ui.components.ConnectedListItem(
                            index = idx,
                            count = breakdownList.size,
                            trailingContent = {
                                Text(
                                    text = "${HealthFormat.count(cal)} kcal",
                                    color = healthColors.textPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        ) {
                            Text(
                                text = title,
                                color = healthColors.textPrimary,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
