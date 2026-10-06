package com.healthcompanion.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.healthcompanion.app.ui.format.FieldError
import com.healthcompanion.app.ui.format.FieldResult
import com.healthcompanion.app.ui.format.HealthFormat
import com.healthcompanion.app.ui.format.HealthInput
import com.healthcompanion.app.ui.format.HealthRanges
import com.healthcompanion.app.ui.format.NumberRange
import com.healthcompanion.app.ui.theme.LocalHealthColors
import com.healthcompanion.app.ui.theme.Spacing

enum class LogCategory(val label: String, val icon: ImageVector) {
    WORKOUT("Workout", Icons.Default.DirectionsRun),
    WEIGHT("Weight", Icons.Default.Scale),
    WATER("Water", Icons.Default.WaterDrop),
    SLEEP("Sleep", Icons.Default.Bedtime),
    HEART_RATE("Heart rate", Icons.Default.Favorite),
    BLOOD_PRESSURE("Blood pressure", Icons.Default.MonitorHeart)
}

/** Human-readable reason for a rejected field, or null when the value is valid. */
fun fieldErrorMessage(result: FieldResult<*>, rangeLabel: String, showErrors: Boolean): String? {
    if (!showErrors) return null
    val invalid = result as? FieldResult.Invalid ?: return null
    return when (invalid.reason) {
        FieldError.REQUIRED -> "Required"
        FieldError.NOT_A_NUMBER -> "Enter a number"
        FieldError.OUT_OF_RANGE -> "Must be $rangeLabel"
        FieldError.INVERTED -> "Diastolic must be below systolic"
    }
}

/**
 * A validated numeric input.
 *
 * Every field in this sheet goes through here so `isError` / `supportingText` /
 * `imeAction` are applied uniformly instead of per-call — previously there were
 * zero `isError`, `supportingText` and `imeAction` occurrences app-wide.
 */
@Composable
private fun NumericField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    range: NumberRange,
    result: FieldResult<*>,
    isDecimal: Boolean,
    showErrors: Boolean,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val focusManager = LocalFocusManager.current
    val error = fieldErrorMessage(result, range.message(label), showErrors)

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        singleLine = true,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = healthColors.accentTeal,
            focusedLabelColor = healthColors.accentTeal
        )
    )
}

/**
 * Manual-entry sheet.
 *
 * Every field starts empty and is validated before anything is written. The
 * previous version pre-filled invented values (45 min, 320 kcal, 72.5 kg,
 * 120/80 mmHg …) and coerced each unparseable field to a plausible default on
 * Save, so clearing the weight field and tapping Save silently persisted 70 kg
 * into the user's real Health Connect record.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDataBottomSheet(
    onDismiss: () -> Unit,
    onLogWorkout: (sport: String, durationMinutes: Int, calories: Int, distanceKm: Double?) -> Unit,
    onLogWeight: (weightKg: Double) -> Unit,
    onLogWater: (amountMl: Int) -> Unit,
    onLogSleep: (bedHour: Int, bedMin: Int, wakeHour: Int, wakeMin: Int) -> Unit,
    onLogHeartRate: (bpm: Int) -> Unit,
    onLogBloodPressure: (systolic: Int, diastolic: Int) -> Unit,
    modifier: Modifier = Modifier,
    initialCategory: LogCategory = LogCategory.WORKOUT,
    initialSport: String = "Running"
) {
    val healthColors = LocalHealthColors.current
    val focusManager = LocalFocusManager.current
    var selectedCategory by remember { mutableStateOf(initialCategory) }

    // All fields start blank: nothing is invented on the user's behalf.
    var durationMinutesText by remember { mutableStateOf("") }
    var caloriesText by remember { mutableStateOf("") }
    var distanceKmText by remember { mutableStateOf("") }
    var weightKgText by remember { mutableStateOf("") }
    var waterMlText by remember { mutableStateOf("") }
    var bedHourText by remember { mutableStateOf("") }
    var bedMinText by remember { mutableStateOf("") }
    var wakeHourText by remember { mutableStateOf("") }
    var wakeMinText by remember { mutableStateOf("") }
    var heartRateText by remember { mutableStateOf("") }
    var systolicText by remember { mutableStateOf("") }
    var diastolicText by remember { mutableStateOf("") }

    // Errors are only surfaced once a field has been touched, so the form does
    // not greet the user with red text before they have typed anything.
    var touched by remember { mutableStateOf(false) }

    val selectedSportState = remember { mutableStateOf(initialSport) }
    val selectedSport = selectedSportState.value

    // ---- Validation -------------------------------------------------------

    val durationResult = HealthInput.int(durationMinutesText, HealthRanges.durationMinutes)
    val caloriesResult = HealthInput.int(caloriesText, HealthRanges.caloriesKcal)
    val distanceResult = HealthInput.optionalDecimal(distanceKmText, HealthRanges.distanceKm)
    val weightResult = HealthInput.decimal(weightKgText, HealthRanges.weightKg)
    val waterResult = HealthInput.int(waterMlText, HealthRanges.waterMl)
    val bedHourResult = HealthInput.int(bedHourText, HealthRanges.hourOfDay)
    val bedMinResult = HealthInput.int(bedMinText, HealthRanges.minuteOfHour)
    val wakeHourResult = HealthInput.int(wakeHourText, HealthRanges.hourOfDay)
    val wakeMinResult = HealthInput.int(wakeMinText, HealthRanges.minuteOfHour)
    val heartRateResult = HealthInput.int(heartRateText, HealthRanges.heartRateBpm)
    val systolicResult = HealthInput.int(systolicText, HealthRanges.systolicMmHg)
    val diastolicResult = HealthInput.int(diastolicText, HealthRanges.diastolicMmHg)

    val sleepDurationMinutes = if (
        bedHourResult is FieldResult.Value && bedMinResult is FieldResult.Value &&
        wakeHourResult is FieldResult.Value && wakeMinResult is FieldResult.Value
    ) {
        HealthInput.sleepSessionMinutes(
            bedHourResult.value, bedMinResult.value,
            wakeHourResult.value, wakeMinResult.value
        )
    } else null

    val isSleepInvalid = bedHourResult is FieldResult.Invalid || bedMinResult is FieldResult.Invalid ||
        wakeHourResult is FieldResult.Invalid || wakeMinResult is FieldResult.Invalid ||
        (sleepDurationMinutes == null &&
            bedHourResult is FieldResult.Value && bedMinResult is FieldResult.Value &&
            wakeHourResult is FieldResult.Value && wakeMinResult is FieldResult.Value)

    // Systolic must exceed diastolic; otherwise the pair is not a measurement.
    val isBloodPressureInverted = systolicResult is FieldResult.Value &&
        diastolicResult is FieldResult.Value &&
        systolicResult.value <= diastolicResult.value

    val formValid = when (selectedCategory) {
        LogCategory.WORKOUT ->
            durationResult is FieldResult.Value &&
                caloriesResult is FieldResult.Value &&
                distanceResult is FieldResult.Value
        LogCategory.WEIGHT -> weightResult is FieldResult.Value
        LogCategory.WATER -> waterResult is FieldResult.Value
        LogCategory.SLEEP -> !isSleepInvalid
        LogCategory.HEART_RATE -> heartRateResult is FieldResult.Value
        LogCategory.BLOOD_PRESSURE ->
            systolicResult is FieldResult.Value &&
                diastolicResult is FieldResult.Value &&
                !isBloodPressureInverted
    }

    fun errorFor(result: FieldResult<*>, rangeLabel: String): String? {
        if (!touched) return null
        val invalid = result as? FieldResult.Invalid ?: return null
        return when (invalid.reason) {
            FieldError.REQUIRED -> "Required"
            FieldError.NOT_A_NUMBER -> "Enter a number"
            FieldError.OUT_OF_RANGE -> "Must be $rangeLabel"
            FieldError.INVERTED -> "Diastolic must be below systolic"
        }
    }

    // The shared numeric field is a top-level composable (`NumericField`), since
    // a local `fun` cannot be @Composable and a composable lambda cannot take
    // named arguments.

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = healthColors.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl, vertical = Spacing.s)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.l)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Log health data",
                    style = MaterialTheme.typography.titleLarge,
                    color = healthColors.textPrimary,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = healthColors.textSecondary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s)
            ) {
                LogCategory.entries.forEach { category ->
                    val isSelected = (category == selectedCategory)
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (isSelected) healthColors.accentTeal else healthColors.cardBackground,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraLarge)
                            .clickable {
                                selectedCategory = category
                                touched = false
                                focusManager.clearFocus()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s)
                        ) {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                tint = if (isSelected) healthColors.pillSelectedText else healthColors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = category.label,
                                color = if (isSelected) healthColors.pillSelectedText else healthColors.textPrimary,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }

            when (selectedCategory) {
                LogCategory.WORKOUT -> {
                    val sports = listOf("Running", "Walking", "Cycling", "Strength training", "Swimming", "Yoga", "HIIT")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s)
                    ) {
                        sports.forEach { sport ->
                            val isSelected = (sport == selectedSport)
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = if (isSelected) healthColors.cardElevated else healthColors.cardBackground,
                                border = if (isSelected) BorderStroke(1.5.dp, healthColors.accentTeal) else null,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.large)
                                    .clickable { selectedSportState.value = sport }
                            ) {
                                Text(
                                    text = sport,
                                    color = if (isSelected) healthColors.accentTeal else healthColors.textPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = Spacing.s)
                                )
                            }
                        }
                    }

                    NumericField(
                        value = durationMinutesText,
                        onValueChange = { durationMinutesText = it },
                        label = "Duration (minutes)",
                        range = HealthRanges.durationMinutes,
                        result = durationResult,
                        isDecimal = false,
                        showErrors = touched,
                        modifier = Modifier
                    )
                    NumericField(
                        value = caloriesText,
                        onValueChange = { caloriesText = it },
                        label = "Calories burned (kcal)",
                        range = HealthRanges.caloriesKcal,
                        result = caloriesResult,
                        isDecimal = false,
                        showErrors = touched,
                        modifier = Modifier
                    )
                    NumericField(
                        value = distanceKmText,
                        onValueChange = { distanceKmText = it },
                        label = "Distance (km, optional)",
                        range = HealthRanges.distanceKm,
                        result = distanceResult,
                        isDecimal = true,
                        showErrors = touched,
                        modifier = Modifier
                    )
                }

                LogCategory.WEIGHT -> {
                    NumericField(
                        value = weightKgText,
                        onValueChange = { weightKgText = it },
                        label = "Weight (kg)",
                        range = HealthRanges.weightKg,
                        result = weightResult,
                        isDecimal = true,
                        showErrors = touched,
                        modifier = Modifier
                    )

                    // Adjustments apply to what the user typed, not to a default.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(-0.5, -0.1, 0.1, 0.5).forEach { delta ->
                            OutlinedButton(
                                onClick = {
                                    val current = (weightResult as? FieldResult.Value)?.value
                                    val base = current ?: return@OutlinedButton
                                    val next = (base + delta).coerceIn(
                                        HealthRanges.weightKg.min,
                                        HealthRanges.weightKg.max
                                    )
                                    weightKgText = HealthFormat.decimal(next, 1)
                                },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(
                                    text = (if (delta > 0) "+" else "") +
                                        com.healthcompanion.app.ui.format.HealthFormat.decimal(delta, 1) + " kg",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                LogCategory.WATER -> {
                    NumericField(
                        value = waterMlText,
                        onValueChange = { waterMlText = it },
                        label = "Water intake (ml)",
                        range = HealthRanges.waterMl,
                        result = waterResult,
                        isDecimal = false,
                        showErrors = touched,
                        modifier = Modifier
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(250, 500, 750).forEach { delta ->
                            OutlinedButton(
                                onClick = {
                                    val current = (waterResult as? FieldResult.Value)?.value ?: 0
                                    val next = (current + delta)
                                        .coerceIn(
                                            HealthRanges.waterMl.min.toInt(),
                                            HealthRanges.waterMl.max.toInt()
                                        )
                                    waterMlText = next.toString()
                                },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(
                                    text = "+$delta ml",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                LogCategory.SLEEP -> {
                    Text(
                        text = "Bedtime",
                        style = MaterialTheme.typography.bodyMedium,
                        color = healthColors.textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        NumericField(
                            value = bedHourText,
                            onValueChange = { bedHourText = it },
                            label = "Hour (0–23)",
                            range = HealthRanges.hourOfDay,
                            result = bedHourResult,
                            isDecimal = false,
                            showErrors = touched,
                            modifier = Modifier.weight(1f)
                        )
                        NumericField(
                            value = bedMinText,
                            onValueChange = { bedMinText = it },
                            label = "Minute (0–59)",
                            range = HealthRanges.minuteOfHour,
                            result = bedMinResult,
                            isDecimal = false,
                            showErrors = touched,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = "Wake-up time",
                        style = MaterialTheme.typography.bodyMedium,
                        color = healthColors.textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        NumericField(
                            value = wakeHourText,
                            onValueChange = { wakeHourText = it },
                            label = "Hour (0–23)",
                            range = HealthRanges.hourOfDay,
                            result = wakeHourResult,
                            isDecimal = false,
                            showErrors = touched,
                            modifier = Modifier.weight(1f)
                        )
                        NumericField(
                            value = wakeMinText,
                            onValueChange = { wakeMinText = it },
                            label = "Minute (0–59)",
                            range = HealthRanges.minuteOfHour,
                            result = wakeMinResult,
                            isDecimal = false,
                            showErrors = touched,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (touched && !isSleepInvalid && sleepDurationMinutes != null) {
                        Text(
                            text = "Sleep duration: " +
                                com.healthcompanion.app.ui.format.HealthFormat.duration(sleepDurationMinutes),
                            style = MaterialTheme.typography.bodySmall,
                            color = healthColors.textSecondary
                        )
                    }
                    if (touched && isSleepInvalid && bedHourResult is FieldResult.Value) {
                        Text(
                            text = "Wake-up time must be after bedtime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                LogCategory.HEART_RATE -> {
                    NumericField(
                        value = heartRateText,
                        onValueChange = { heartRateText = it },
                        label = "Heart rate (bpm)",
                        range = HealthRanges.heartRateBpm,
                        result = heartRateResult,
                        isDecimal = false,
                        showErrors = touched,
                        modifier = Modifier
                    )
                }

                LogCategory.BLOOD_PRESSURE -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        NumericField(
                            value = systolicText,
                            onValueChange = { systolicText = it },
                            label = "Systolic",
                            range = HealthRanges.systolicMmHg,
                            result = systolicResult,
                            isDecimal = false,
                            showErrors = touched,
                            modifier = Modifier.weight(1f)
                        )
                        NumericField(
                            value = diastolicText,
                            onValueChange = { diastolicText = it },
                            label = "Diastolic",
                            range = HealthRanges.diastolicMmHg,
                            result = diastolicResult,
                            isDecimal = false,
                            showErrors = touched,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = "mmHg",
                        style = MaterialTheme.typography.bodySmall,
                        color = healthColors.textSecondary
                    )
                    if (touched &&
                        systolicResult is FieldResult.Value &&
                        diastolicResult is FieldResult.Value &&
                        systolicResult.value <= diastolicResult.value
                    ) {
                        Text(
                            text = "Systolic must be above diastolic",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Button(
                onClick = {
                    touched = true
                    if (!formValid) return@Button
                    when (selectedCategory) {
                        LogCategory.WORKOUT -> {
                            val dur = (durationResult as FieldResult.Value).value
                            val cal = (caloriesResult as FieldResult.Value).value
                            val dist = (distanceResult as FieldResult.Value).value
                            onLogWorkout(selectedSport, dur, cal, dist)
                        }
                        LogCategory.WEIGHT -> onLogWeight((weightResult as FieldResult.Value).value)
                        LogCategory.WATER -> onLogWater((waterResult as FieldResult.Value).value)
                        LogCategory.SLEEP -> {
                            val bh = (bedHourResult as FieldResult.Value).value
                            val bm = (bedMinResult as FieldResult.Value).value
                            val wh = (wakeHourResult as FieldResult.Value).value
                            val wm = (wakeMinResult as FieldResult.Value).value
                            onLogSleep(bh, bm, wh, wm)
                        }
                        LogCategory.HEART_RATE -> onLogHeartRate((heartRateResult as FieldResult.Value).value)
                        LogCategory.BLOOD_PRESSURE -> {
                            val sys = (systolicResult as FieldResult.Value).value
                            val dia = (diastolicResult as FieldResult.Value).value
                            onLogBloodPressure(sys, dia)
                        }
                    }
                    onDismiss()
                },
                enabled = formValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(containerColor = healthColors.pillSelectedBg)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = healthColors.pillSelectedText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.s))
                Text(
                    text = "Save to Health Connect",
                    color = healthColors.pillSelectedText,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(Spacing.l))
        }
    }
}
