package com.healthcompanion.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthcompanion.app.data.HealthGoals
import com.healthcompanion.app.ui.components.ConnectedListItem
import com.healthcompanion.app.ui.theme.LocalHealthColors
import com.healthcompanion.app.ui.theme.Spacing
import com.healthcompanion.app.ui.theme.ThemeMode
import com.healthcompanion.app.ui.theme.Typography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    goals: HealthGoals,
    onGoalsChange: (HealthGoals) -> Unit,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    isDynamicColor: Boolean = true,
    onToggleDynamicColor: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current

    var stepGoalText by remember(goals) { mutableStateOf(goals.dailyStepGoal.toString()) }
    var distanceGoalText by remember(goals) { mutableStateOf(goals.dailyDistanceGoalKm.toString()) }
    var exerciseDaysText by remember(goals) { mutableStateOf(goals.weeklyExerciseDaysGoal.toString()) }
    var caloriesText by remember(goals) { mutableStateOf(goals.dailyCalorieGoal.toString()) }
    var activeCaloriesText by remember(goals) { mutableStateOf(goals.activeCalorieGoal.toString()) }
    var cardioMinutesText by remember(goals) { mutableStateOf(goals.weeklyCardioMinutesGoal.toString()) }
    var sleepMinutesText by remember(goals) { mutableStateOf(goals.sleepDurationGoalMinutes.toString()) }

    var ageText by remember(goals) { mutableStateOf(goals.age.toString()) }
    var heightText by remember(goals) { mutableStateOf(goals.heightCm.toString()) }
    var restingHrText by remember(goals) { mutableStateOf(goals.restingHeartRateBaseline.toString()) }

    fun saveGoals() {
        val updated = goals.copy(
            dailyStepGoal = stepGoalText.toIntOrNull() ?: goals.dailyStepGoal,
            dailyDistanceGoalKm = distanceGoalText.toDoubleOrNull() ?: goals.dailyDistanceGoalKm,
            weeklyExerciseDaysGoal = exerciseDaysText.toIntOrNull() ?: goals.weeklyExerciseDaysGoal,
            dailyCalorieGoal = caloriesText.toIntOrNull() ?: goals.dailyCalorieGoal,
            activeCalorieGoal = activeCaloriesText.toIntOrNull() ?: goals.activeCalorieGoal,
            weeklyCardioMinutesGoal = cardioMinutesText.toIntOrNull() ?: goals.weeklyCardioMinutesGoal,
            sleepDurationGoalMinutes = sleepMinutesText.toIntOrNull() ?: goals.sleepDurationGoalMinutes,
            age = ageText.toIntOrNull() ?: goals.age,
            heightCm = heightText.toDoubleOrNull() ?: goals.heightCm,
            restingHeartRateBaseline = restingHrText.toIntOrNull() ?: goals.restingHeartRateBaseline
        )
        onGoalsChange(updated)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Profile & Goals",
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = healthColors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = healthColors.textPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        saveGoals()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save",
                            tint = healthColors.accentTeal
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = healthColors.background
                )
            )
        },
        containerColor = healthColors.background,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Appearance & Settings",
                    style = Typography.titleSmall,
                    color = healthColors.accentTeal,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }

            item {
                ConnectedListItem(
                    index = 0,
                    count = 2,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Theme mode",
                                color = healthColors.textPrimary,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = when (themeMode) {
                                    ThemeMode.SYSTEM -> "System default"
                                    ThemeMode.DARK -> "Dark theme"
                                    ThemeMode.LIGHT -> "Light theme"
                                },
                                color = healthColors.textMuted,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(
                                ThemeMode.SYSTEM to "System",
                                ThemeMode.DARK to "Dark",
                                ThemeMode.LIGHT to "Light"
                            ).forEach { (mode, label) ->
                                val isSelected = themeMode == mode
                                Box(
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(if (isSelected) healthColors.accentTeal else healthColors.pillUnselectedBg)
                                        .clickable { onThemeModeChange(mode) }
                                        .padding(horizontal = Spacing.s, vertical = Spacing.xs)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) healthColors.onAccentTeal else healthColors.textSecondary,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                ConnectedListItem(
                    index = 1,
                    count = 2,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dynamic color (Material You)",
                                color = healthColors.textPrimary,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Theme colours adapt to your system wallpaper",
                                color = healthColors.textMuted,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Switch(
                            checked = isDynamicColor,
                            onCheckedChange = { onToggleDynamicColor() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = healthColors.onAccentTeal,
                                checkedTrackColor = healthColors.accentTeal
                            )
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Activity Goals",
                    style = Typography.titleSmall,
                    color = healthColors.accentTeal,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }

            item {
                ConnectedListItem(
                    index = 0,
                    count = 3,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    GoalInputField(
                        label = "Daily steps",
                        value = stepGoalText,
                        onValueChange = { stepGoalText = it },
                        suffix = "steps"
                    )
                }
            }

            item {
                ConnectedListItem(
                    index = 1,
                    count = 3,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    GoalInputField(
                        label = "Daily distance",
                        value = distanceGoalText,
                        onValueChange = { distanceGoalText = it },
                        suffix = "km",
                        allowDecimal = true
                    )
                }
            }

            item {
                ConnectedListItem(
                    index = 2,
                    count = 3,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    GoalInputField(
                        label = "Weekly exercise days",
                        value = exerciseDaysText,
                        onValueChange = { exerciseDaysText = it },
                        suffix = "days"
                    )
                }
            }

            item {
                Text(
                    text = "Energy & Cardio",
                    style = Typography.titleSmall,
                    color = healthColors.accentTeal,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }

            item {
                ConnectedListItem(
                    index = 0,
                    count = 4,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    GoalInputField(
                        label = "Total calories",
                        value = caloriesText,
                        onValueChange = { caloriesText = it },
                        suffix = "kcal"
                    )
                }
            }

            item {
                ConnectedListItem(
                    index = 1,
                    count = 4,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    GoalInputField(
                        label = "Active calories",
                        value = activeCaloriesText,
                        onValueChange = { activeCaloriesText = it },
                        suffix = "kcal"
                    )
                }
            }

            item {
                ConnectedListItem(
                    index = 2,
                    count = 4,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    GoalInputField(
                        label = "Weekly cardio",
                        value = cardioMinutesText,
                        onValueChange = { cardioMinutesText = it },
                        suffix = "min"
                    )
                }
            }

            item {
                ConnectedListItem(
                    index = 3,
                    count = 4,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    GoalInputField(
                        label = "Nightly sleep target",
                        value = sleepMinutesText,
                        onValueChange = { sleepMinutesText = it },
                        suffix = "min"
                    )
                }
            }

            item {
                Text(
                    text = "Body Profile",
                    style = Typography.titleSmall,
                    color = healthColors.accentTeal,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }

            item {
                ConnectedListItem(
                    index = 0,
                    count = 3,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    GoalInputField(
                        label = "Age",
                        value = ageText,
                        onValueChange = { ageText = it },
                        suffix = "years"
                    )
                }
            }

            item {
                ConnectedListItem(
                    index = 1,
                    count = 3,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    GoalInputField(
                        label = "Height",
                        value = heightText,
                        onValueChange = { heightText = it },
                        suffix = "cm",
                        allowDecimal = true
                    )
                }
            }

            item {
                ConnectedListItem(
                    index = 2,
                    count = 3,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    GoalInputField(
                        label = "Resting heart rate baseline",
                        value = restingHrText,
                        onValueChange = { restingHrText = it },
                        suffix = "BPM"
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun GoalInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suffix: String,
    /**
     * Distance and height are parsed with `toDoubleOrNull()`, so a digit-only
     * keypad made entering `5.5` km or `175.5` cm impossible.
     */
    allowDecimal: Boolean = false,
    errorText: String? = null
) {
    val healthColors = LocalHealthColors.current
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = healthColors.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            isError = errorText != null,
            supportingText = errorText?.let { message -> { Text(message) } },
            singleLine = true,
            trailingIcon = {
                Text(
                    text = suffix,
                    color = healthColors.textMuted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
            },
            modifier = Modifier.width(130.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = healthColors.textPrimary,
                unfocusedTextColor = healthColors.textPrimary,
                focusedBorderColor = healthColors.accentTeal,
                unfocusedBorderColor = healthColors.cardBorder
            )
        )
    }
}
