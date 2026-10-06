package com.healthcompanion.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.healthcompanion.app.data.FocusStore
import com.healthcompanion.app.data.GoalsStore
import com.healthcompanion.app.data.healthconnect.HealthConnectManager
import com.healthcompanion.app.data.healthconnect.HealthConnectStatus
import com.healthcompanion.app.data.usecase.LogHealthUseCase
import com.healthcompanion.app.data.usecase.SleepDraft
import com.healthcompanion.app.data.usecase.WorkoutDraft
import com.healthcompanion.app.model.DailyHeartRateSummary
import com.healthcompanion.app.model.ExerciseWorkout
import com.healthcompanion.app.model.RestingHeartRatePeriodSummary
import com.healthcompanion.app.model.SleepSession
import com.healthcompanion.app.ui.components.LogCategory
import com.healthcompanion.app.ui.components.LogDataBottomSheet
import com.healthcompanion.app.ui.components.TimeRange
import com.healthcompanion.app.ui.navigation.CategoryId
import com.healthcompanion.app.ui.navigation.GenericMetricRoute
import com.healthcompanion.app.ui.navigation.MetricConfigRegistry
import com.healthcompanion.app.ui.navigation.Screen
import com.healthcompanion.app.ui.navigation.WeeklyCardioRoute
import com.healthcompanion.app.ui.screens.activity.HourlyActivityScreen
import com.healthcompanion.app.ui.screens.catalog.HealthCatalogScreen
import com.healthcompanion.app.ui.screens.distance.DistanceDetailScreen
import com.healthcompanion.app.ui.screens.energy.EnergyBurnedDetailScreen
import com.healthcompanion.app.ui.screens.exercise.ExerciseDetailScreen
import com.healthcompanion.app.ui.screens.exercise.ExerciseListScreen
import com.healthcompanion.app.ui.screens.exercise.FitnessScreen
import com.healthcompanion.app.ui.screens.focus.EditFocusScreen
import com.healthcompanion.app.ui.screens.health.CategoryMetricItem
import com.healthcompanion.app.ui.screens.health.CategoryMetricsListScreen
import com.healthcompanion.app.ui.screens.health.HealthStatusScreen
import com.healthcompanion.app.ui.screens.health.VitalsListScreen
import com.healthcompanion.app.ui.screens.heartrate.HeartRateDetailScreen
import com.healthcompanion.app.ui.screens.heartrate.HeartRateMode
import com.healthcompanion.app.ui.screens.metric.GenericMetricDetailScreen
import com.healthcompanion.app.ui.screens.profile.ProfileScreen
import com.healthcompanion.app.ui.screens.sleep.SleepDashboardScreen
import com.healthcompanion.app.ui.screens.sleep.SleepDetailScreen
import com.healthcompanion.app.ui.screens.spo2.SpO2DetailScreen
import com.healthcompanion.app.ui.screens.steps.StepsDetailScreen
import com.healthcompanion.app.ui.screens.today.TodayDashboardScreen
import com.healthcompanion.app.ui.state.AppMessage
import com.healthcompanion.app.ui.state.PermissionState
import com.healthcompanion.app.ui.state.duration
import com.healthcompanion.app.ui.theme.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class MainActivity : ComponentActivity() {

    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var goalsStore: GoalsStore
    private lateinit var focusStore: FocusStore
    private lateinit var logHealth: LogHealthUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        healthConnectManager = HealthConnectManager(this)
        goalsStore = GoalsStore(this)
        focusStore = FocusStore(this)
        logHealth = LogHealthUseCase(healthConnectManager)

        val themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)

        var onPermissionsResolved: (() -> Unit)? = null
        val requestPermissionLauncher = registerForActivityResult(
            androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
        ) { _ ->
            onPermissionsResolved?.invoke()
        }

        var pendingRouteWorkoutId: String? = null
        var onRouteResult: ((String, androidx.health.connect.client.records.ExerciseRoute?) -> Unit)? = null
        val routeLauncher = registerForActivityResult(
            androidx.health.connect.client.contracts.ExerciseRouteRequestContract()
        ) { route ->
            pendingRouteWorkoutId?.let { onRouteResult?.invoke(it, route) }
        }

        setContent {
            var themeModeName by rememberSaveable {
                mutableStateOf(themePrefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
            }
            var useDynamicColor by rememberSaveable {
                mutableStateOf(themePrefs.getBoolean("use_dynamic_color", true))
            }

            val themeMode = try { ThemeMode.valueOf(themeModeName) } catch (e: Exception) { ThemeMode.SYSTEM }

            fun updateThemeMode(newMode: ThemeMode) {
                themeModeName = newMode.name
                themePrefs.edit().putString("theme_mode", newMode.name).apply()
            }

            fun updateDynamicColor(dynamic: Boolean) {
                useDynamicColor = dynamic
                themePrefs.edit().putBoolean("use_dynamic_color", dynamic).apply()
            }

            var goals by remember { mutableStateOf(goalsStore.load()) }

            HealthCompanionTheme(themeMode = themeMode, dynamicColor = useDynamicColor) {
                val healthColors = LocalHealthColors.current
                val navController = rememberNavController()
                val coroutineScope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }
                val messages = remember { Channel<AppMessage>(Channel.BUFFERED) }

                // §2.8: re-tap counters, one per tab route, used to request a
                // scroll-to-top without needing a reference to each list state.
                var tabScrollRequests by remember {
                    mutableStateOf<Map<String, Int>>(emptyMap())
                }

                LaunchedEffect(Unit) {
                    for (message in messages) {
                        val result = snackbarHostState.showSnackbar(
                            message = message.text,
                            actionLabel = (message as? AppMessage.Retryable)?.actionLabel,
                            duration = message.duration
                        )
                        if (result == SnackbarResult.ActionPerformed && message is AppMessage.Retryable) {
                            message.onRetry()
                        }
                    }
                }

                fun notify(message: AppMessage) {
                    messages.trySend(message)
                }

                // ---- Health Connect availability & permission state -----------
                var availability by remember { mutableStateOf(HealthConnectStatus.AVAILABLE) }
                var permissionState by remember { mutableStateOf(PermissionState.Unknown) }

                // Real data only. There is no synthetic fallback anywhere: an
                // empty read produces an empty state, never an invented number.
                var sleepSessions by remember { mutableStateOf<List<SleepSession>>(emptyList()) }
                var dailyHeartRate by remember { mutableStateOf<DailyHeartRateSummary?>(null) }
                var restingHeartRate by remember { mutableStateOf<RestingHeartRatePeriodSummary?>(null) }
                var workouts by remember { mutableStateOf<List<ExerciseWorkout>>(emptyList()) }
                var todaySteps by remember { mutableStateOf<Int?>(null) }
                var todayDistance by remember { mutableStateOf<Double?>(null) }
                var weeklySteps by remember { mutableStateOf<List<Pair<LocalDate, Int>>>(emptyList()) }
                var weeklyCalories by remember { mutableStateOf<List<Pair<LocalDate, Int>>>(emptyList()) }
                var todaySpO2 by remember { mutableStateOf<Int?>(null) }
                var latestWeight by remember { mutableStateOf<Float?>(null) }
                var isRefreshing by remember { mutableStateOf(false) }
                var hasLoadedOnce by remember { mutableStateOf(false) }

                val hasPermissions = permissionState.canReadAnything

                fun loadData() {
                    coroutineScope.launch {
                        isRefreshing = true
                        try {
                            sleepSessions = healthConnectManager.readSleepSessions().getOrNull() ?: emptyList()
                            dailyHeartRate = healthConnectManager.readDailyHeartRate(LocalDate.now(), goals.age).getOrNull()
                            restingHeartRate = healthConnectManager.readRestingHeartRateWeekly(LocalDate.now()).getOrNull()
                            workouts = healthConnectManager.readWorkouts(365, goals.age).getOrNull() ?: emptyList()
                            todaySteps = healthConnectManager.readStepsForDate(LocalDate.now()).getOrNull()
                            todayDistance = healthConnectManager.readDistanceForDate(LocalDate.now()).getOrNull()
                            weeklySteps = healthConnectManager.readWeeklySteps(LocalDate.now()).getOrNull() ?: emptyList()
                            weeklyCalories = healthConnectManager.readWeeklyCalories(LocalDate.now()).getOrNull() ?: emptyList()
                            todaySpO2 = healthConnectManager.readOxygenSaturationForDate(LocalDate.now()).getOrNull()
                                ?: healthConnectManager.readLatestOxygenSaturation().getOrNull()
                            latestWeight = healthConnectManager.readLatestWeight().getOrNull()
                            hasLoadedOnce = true
                        } finally {
                            isRefreshing = false
                        }
                    }
                }

                fun refreshPermissions() {
                    coroutineScope.launch {
                        val granted = healthConnectManager.getGrantedPermissions()
                        permissionState = PermissionState(
                            grantedCount = granted.size,
                            requestedCount = healthConnectManager.permissions.size
                        )
                    }
                }

                onPermissionsResolved = {
                    refreshPermissions()
                    loadData()
                }

                LaunchedEffect(Unit) {
                    val status = healthConnectManager.getStatus()
                    availability = status
                    refreshPermissions()
                    loadData()
                    // No implicit permission prompt: the user is asked in-context
                    // with a stated reason first.
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Pressing back on a top-level tab exits the app instead of
                // silently returning to Today.
                val isTopLevelTab = currentRoute in Screen.topLevelRoutes
                BackHandler(enabled = isTopLevelTab && navController.previousBackStackEntry == null) {
                    finish()
                }

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        if (availability == HealthConnectStatus.AVAILABLE && !permissionState.isFullyGranted) {
                            HealthConnectBanner(
                                permissionState = permissionState,
                                onConnect = {
                                    onPermissionsResolved = {
                                        refreshPermissions()
                                        loadData()
                                    }
                                    requestPermissionLauncher.launch(healthConnectManager.permissions)
                                }
                            )
                        } else if (availability != HealthConnectStatus.AVAILABLE) {
                            HealthConnectUnavailableBanner(availability = availability)
                        }
                    },
                    bottomBar = {
                        val tabs = Screen.topLevelDestinations
                        val showBottomBar = currentRoute in tabs.map { it.route }
                        if (showBottomBar) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                windowInsets = WindowInsets.navigationBars
                            ) {
                                tabs.forEach { screen ->
                                    val isSelected = currentRoute == screen.route
                                    NavigationBarItem(
                                        icon = {
                                            // The label already names the tab, so an
                                            // icon description would be announced twice.
                                            Icon(
                                                imageVector = screen.icon,
                                                contentDescription = null
                                            )
                                        },
                                        label = { Text(screen.title) },
                                        selected = isSelected,
                                        onClick = {
                                            if (isSelected) {
                                                // §2.8: re-tap the active tab to return to the top.
                                                val nextRequest =
                                                    (tabScrollRequests[screen.route] ?: 0) + 1
                                                tabScrollRequests =
                                                    tabScrollRequests + (screen.route to nextRequest)
                                            } else {
                                                navController.navigate(screen.route) {
                                                    // Pop to the graph's start destination
                                                    // rather than a hardcoded route, so a
                                                    // back press from a non-Today tab exits
                                                    // instead of silently landing on Today.
                                                    popUpTo(Screen.Today.route) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    // §3.2: twelve of the twenty-one screens had no loading
                    // affordance and rendered an empty frame on first launch.
                    // The first read is gated here so every tab shows a spinner
                    // rather than an ambiguous blank screen.
                    if (!hasLoadedOnce) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = innerPadding.calculateBottomPadding()),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Today.route,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding()),
                        enterTransition = {
                            slideInHorizontally(
                                animationSpec = ExpressiveMotion.emphasizedDecelerateSpec(),
                                initialOffsetX = { it / 4 }
                            ) + fadeIn(ExpressiveMotion.emphasizedDecelerateSpec())
                        },
                        exitTransition = {
                            slideOutHorizontally(
                                animationSpec = ExpressiveMotion.emphasizedAccelerateSpec(),
                                targetOffsetX = { -it / 8 }
                            ) + fadeOut(ExpressiveMotion.emphasizedAccelerateSpec())
                        },
                        popEnterTransition = {
                            slideInHorizontally(
                                animationSpec = ExpressiveMotion.emphasizedDecelerateSpec(),
                                initialOffsetX = { -it / 8 }
                            ) + fadeIn(ExpressiveMotion.emphasizedDecelerateSpec())
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                animationSpec = ExpressiveMotion.emphasizedAccelerateSpec(),
                                targetOffsetX = { it / 4 }
                            ) + fadeOut(ExpressiveMotion.emphasizedAccelerateSpec())
                        }
                    ) {
                        composable(Screen.Today.route) {
                            TodayDashboardScreen(
                                sleepSessions = sleepSessions,
                                dailyHeartRate = dailyHeartRate,
                                restingHeartRate = restingHeartRate,
                                weeklySteps = weeklySteps,
                                todaySteps = todaySteps,
                                todayDistanceKm = todayDistance,
                                workouts = workouts,
                                hasPermissions = hasPermissions,
                                isConnected = permissionState.isFullyGranted,
                                grantedCount = permissionState.grantedCount,
                                requestedCount = permissionState.requestedCount,
                                onRequestPermissions = {
                                    onPermissionsResolved = {
                                        refreshPermissions()
                                        loadData()
                                    }
                                    requestPermissionLauncher.launch(healthConnectManager.permissions)
                                },
                                onNavigateToSleep = { navController.navigate(Screen.SleepDetail.route) },
                                onNavigateToHeartRate = { navController.navigate(Screen.HeartRate.route) },
                                onNavigateToExercise = { navController.navigate(Screen.Fitness.route) },
                                onNavigateToWorkout = { w -> navController.navigate(Screen.ExerciseDetail.createRoute(w.id)) },
                                onNavigateToSteps = { navController.navigate(Screen.Steps.route) },
                                onNavigateToDistance = { navController.navigate(Screen.Distance.route) },
                                onNavigateToEditFocus = { navController.navigate(Screen.EditFocus.route) },
                                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                                focusStore = focusStore,
                                goals = goals,
                                onToggleTheme = {
                                    val nextMode = when (themeMode) {
                                        ThemeMode.SYSTEM -> ThemeMode.LIGHT
                                        ThemeMode.LIGHT -> ThemeMode.DARK
                                        ThemeMode.DARK -> ThemeMode.SYSTEM
                                    }
                                    updateThemeMode(nextMode)
                                },
                                isDynamicColor = useDynamicColor,
                                onToggleDynamicColor = { updateDynamicColor(!useDynamicColor) },
                                onLogWorkout = { sport, durationMinutes, calories, distanceKm ->
                                    coroutineScope.launch {
                                        val result = logHealth.logWorkout(
                                            WorkoutDraft(sport, durationMinutes, calories, distanceKm)
                                        )
                                        result.fold(
                                            onSuccess = {
                                                notify(AppMessage.Success("Logged $sport ($durationMinutes min)"))
                                                loadData()
                                            },
                                            onFailure = { error ->
                                                notify(AppMessage.Failure("Couldn't log $sport", error))
                                            }
                                        )
                                    }
                                },
                                onLogWeight = { weightKg ->
                                    coroutineScope.launch {
                                        logHealth.logWeight(weightKg).fold(
                                            onSuccess = {
                                                notify(AppMessage.Success("Logged weight: ${weightKg} kg"))
                                                loadData()
                                            },
                                            onFailure = { error ->
                                                notify(AppMessage.Failure("Couldn't save weight", error))
                                            }
                                        )
                                    }
                                },
                                onLogWater = { amountMl ->
                                    coroutineScope.launch {
                                        logHealth.logWater(amountMl).fold(
                                            onSuccess = {
                                                notify(AppMessage.Success("Logged $amountMl ml of water"))
                                                loadData()
                                            },
                                            onFailure = { error ->
                                                notify(AppMessage.Failure("Couldn't save water intake", error))
                                            }
                                        )
                                    }
                                },
                                onLogSleep = { bedHour, bedMin, wakeHour, wakeMin ->
                                    coroutineScope.launch {
                                        logHealth.logSleep(SleepDraft(bedHour, bedMin, wakeHour, wakeMin)).fold(
                                            onSuccess = {
                                                notify(AppMessage.Success("Logged sleep session"))
                                                loadData()
                                            },
                                            onFailure = { error ->
                                                notify(AppMessage.Failure("Couldn't save sleep session", error))
                                            }
                                        )
                                    }
                                },
                                onLogHeartRate = { bpm ->
                                    coroutineScope.launch {
                                        logHealth.logHeartRate(bpm).fold(
                                            onSuccess = {
                                                notify(AppMessage.Success("Logged heart rate: $bpm bpm"))
                                                loadData()
                                            },
                                            onFailure = { error ->
                                                notify(AppMessage.Failure("Couldn't save heart rate", error))
                                            }
                                        )
                                    }
                                },
                                onLogBloodPressure = { sys, dia ->
                                    coroutineScope.launch {
                                        logHealth.logBloodPressure(sys, dia).fold(
                                            onSuccess = {
                                                notify(AppMessage.Success("Logged blood pressure: $sys/$dia mmHg"))
                                                loadData()
                                            },
                                            onFailure = { error ->
                                                notify(AppMessage.Failure("Couldn't save blood pressure", error))
                                            }
                                        )
                                    }
                                },
                                scrollToTopSignal = tabScrollRequests[Screen.Today.route] ?: 0,
                                isRefreshing = isRefreshing,
                                onRefresh = { loadData() }
                            )
                        }

                        composable(Screen.Fitness.route) {
                            var showFitnessLogSheet by rememberSaveable { mutableStateOf(false) }
                            var fitnessLogSport by rememberSaveable { mutableStateOf("Running") }

                            FitnessScreen(
                                workouts = workouts,
                                scrollToTopSignal = tabScrollRequests[Screen.Fitness.route] ?: 0,
                                onWorkoutClick = { workout ->
                                    navController.navigate(Screen.ExerciseDetail.createRoute(workout.id))
                                },
                                onNavigateToAllActivities = { navController.navigate(Screen.ExerciseList.route) },
                                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                                onNavigateToSteps = { navController.navigate(Screen.Steps.route) },
                                onNavigateToDistance = { navController.navigate(Screen.Distance.route) },
                                onNavigateToHourlyActivity = { navController.navigate(Screen.HourlyActivity.route) },
                                onNavigateToWeeklyCardio = { navController.navigate(Screen.WeeklyCardio.createRoute()) },
                                onStartWorkout = { sport ->
                                    fitnessLogSport = sport
                                    showFitnessLogSheet = true
                                }
                            )

                            if (showFitnessLogSheet) {
                                LogDataBottomSheet(
                                    onDismiss = { showFitnessLogSheet = false },
                                    initialCategory = LogCategory.WORKOUT,
                                    initialSport = fitnessLogSport,
                                    onLogWorkout = { sport, durationMinutes, calories, distanceKm ->
                                        showFitnessLogSheet = false
                                        coroutineScope.launch {
                                            // The result is surfaced here too: the
                                            // previous Fitness path discarded it.
                                            logHealth.logWorkout(
                                                WorkoutDraft(sport, durationMinutes, calories, distanceKm)
                                            ).fold(
                                                onSuccess = {
                                                    notify(AppMessage.Success("Logged $sport ($durationMinutes min)"))
                                                    loadData()
                                                },
                                                onFailure = { error ->
                                                    notify(AppMessage.Failure("Couldn't log $sport", error))
                                                }
                                            )
                                        }
                                    },
                                    onLogWeight = { w ->
                                        coroutineScope.launch {
                                            logHealth.logWeight(w).fold(
                                                onSuccess = {
                                                    notify(AppMessage.Success("Logged weight: $w kg"))
                                                    loadData()
                                                },
                                                onFailure = { error ->
                                                    notify(AppMessage.Failure("Couldn't save weight", error))
                                                }
                                            )
                                        }
                                    },
                                    onLogWater = { ml ->
                                        coroutineScope.launch {
                                            logHealth.logWater(ml).fold(
                                                onSuccess = {
                                                    notify(AppMessage.Success("Logged $ml ml of water"))
                                                    loadData()
                                                },
                                                onFailure = { error ->
                                                    notify(AppMessage.Failure("Couldn't save water intake", error))
                                                }
                                            )
                                        }
                                    },
                                    onLogSleep = { bh, bm, wh, wm ->
                                        coroutineScope.launch {
                                            logHealth.logSleep(SleepDraft(bh, bm, wh, wm)).fold(
                                                onSuccess = {
                                                    notify(AppMessage.Success("Logged sleep session"))
                                                    loadData()
                                                },
                                                onFailure = { error ->
                                                    notify(AppMessage.Failure("Couldn't save sleep session", error))
                                                }
                                            )
                                        }
                                    },
                                    onLogHeartRate = { bpm ->
                                        coroutineScope.launch {
                                            logHealth.logHeartRate(bpm).fold(
                                                onSuccess = {
                                                    notify(AppMessage.Success("Logged heart rate: $bpm bpm"))
                                                    loadData()
                                                },
                                                onFailure = { error ->
                                                    notify(AppMessage.Failure("Couldn't save heart rate", error))
                                                }
                                            )
                                        }
                                    },
                                    onLogBloodPressure = { sys, dia ->
                                        coroutineScope.launch {
                                            logHealth.logBloodPressure(sys, dia).fold(
                                                onSuccess = {
                                                    notify(AppMessage.Success("Logged blood pressure: $sys/$dia mmHg"))
                                                    loadData()
                                                },
                                                onFailure = { error ->
                                                    notify(AppMessage.Failure("Couldn't save blood pressure", error))
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        composable(Screen.SleepTab.route) {
                            SleepDashboardScreen(
                                sessions = sleepSessions,
                                onNavigateToHeartRate = { navController.navigate(Screen.HeartRate.route) },
                                onNavigateToHrv = { navController.navigate(Screen.Hrv.createRoute()) },
                                onNavigateToSleepDetail = { navController.navigate(Screen.SleepDetail.route) }
                            )
                        }

                        composable(Screen.Health.route) {
                            HealthStatusScreen(
                                dailyHeartRate = dailyHeartRate,
                                restingHeartRate = restingHeartRate,
                                weeklySteps = weeklySteps,
                                weeklyCalories = weeklyCalories,
                                workouts = workouts,
                                sleepSessions = sleepSessions,
                                todaySteps = todaySteps,
                                todayDistanceKm = todayDistance,
                                currentSpO2 = todaySpO2,
                                latestWeight = latestWeight,
                                scrollToTopSignal = tabScrollRequests[Screen.Health.route] ?: 0,
                                onNavigateToSleep = { navController.navigate(Screen.SleepDetail.route) },
                                onNavigateToHeartRate = { navController.navigate(Screen.HeartRate.route) },
                                onNavigateToEnergyBurned = { navController.navigate(Screen.EnergyBurned.route) },
                                onNavigateToSteps = { navController.navigate(Screen.Steps.route) },
                                onNavigateToExercise = { navController.navigate(Screen.ExerciseList.route) },
                                onNavigateToSpO2 = { navController.navigate(Screen.SpO2.route) },
                                onNavigateToVitalsList = { navController.navigate(Screen.VitalsList.route) },
                                onNavigateToDistance = { navController.navigate(Screen.Distance.route) },
                                onNavigateToWeight = { navController.navigate(Screen.Weight.createRoute()) },
                                onNavigateToHourlyActivity = { navController.navigate(Screen.HourlyActivity.route) },
                                onNavigateToWeeklyCardio = { navController.navigate(Screen.WeeklyCardio.createRoute()) },
                                onNavigateToCategory = { category ->
                                    navController.navigate(Screen.CategoryMetrics.createRoute(category.name))
                                },
                                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                                onNavigateToCatalog = { navController.navigate(Screen.Catalog.route) },
                                onEditGoals = { navController.navigate(Screen.Profile.route) }
                            )
                        }

                        composable(Screen.SleepDetail.route) {
                            SleepDetailScreen(
                                sessions = sleepSessions,
                                onBack = { navController.popBackStack() },
                                initialRange = TimeRange.WEEK
                            )
                        }

                        composable(Screen.HeartRate.route) {
                            HeartRateDetailScreen(
                                dailySummary = dailyHeartRate,
                                restingSummary = restingHeartRate,
                                onQueryDailyHeartRate = { date, age -> healthConnectManager.readDailyHeartRate(date, age) },
                                onQueryWeeklyHeartRate = { date -> healthConnectManager.readWeeklyHeartRate(date) },
                                onQueryRestingHeartRate = { date -> healthConnectManager.readRestingHeartRateWeekly(date) },
                                onQueryRestingHeartRatePeriod = { range, days, date ->
                                    healthConnectManager.readRestingHeartRateBucketed(range, days, date)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.RestingHeartRate.route) {
                            HeartRateDetailScreen(
                                dailySummary = dailyHeartRate,
                                restingSummary = restingHeartRate,
                                initialRange = TimeRange.WEEK,
                                initialMode = HeartRateMode.RESTING_HEART_RATE,
                                onQueryDailyHeartRate = { date, age -> healthConnectManager.readDailyHeartRate(date, age) },
                                onQueryWeeklyHeartRate = { date -> healthConnectManager.readWeeklyHeartRate(date) },
                                onQueryRestingHeartRate = { date -> healthConnectManager.readRestingHeartRateWeekly(date) },
                                onQueryRestingHeartRatePeriod = { range, days, date ->
                                    healthConnectManager.readRestingHeartRateBucketed(range, days, date)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.EnergyBurned.route) {
                            EnergyBurnedDetailScreen(
                                onQueryDailyCalories = { date -> healthConnectManager.readTotalCaloriesForDate(date) },
                                onQueryHourlyCalories = { date -> healthConnectManager.readHourlyTotalCaloriesForDate(date) },
                                onQueryWeeklyCalories = { date -> healthConnectManager.readWeeklyTotalCalories(date) },
                                onQueryDailyActiveCalories = { date -> healthConnectManager.readActiveCaloriesForDate(date) },
                                onQueryHourlyActiveCalories = { date -> healthConnectManager.readHourlyActiveCaloriesForDate(date) },
                                onQueryWeeklyActiveCalories = { date -> healthConnectManager.readWeeklyActiveCalories(date) },
                                onQueryPeriodCalories = { range, days, date ->
                                    healthConnectManager.readPeriodTotalCaloriesBucketed(range, days, date)
                                },
                                onQueryPeriodActiveCalories = { range, days, date ->
                                    healthConnectManager.readPeriodActiveCaloriesBucketed(range, days, date)
                                },
                                onQueryDailyBasalCalories = { date -> healthConnectManager.readBasalCaloriesForDate(date) },
                                onQueryHourlyBasalCalories = { date -> healthConnectManager.readHourlyBasalCaloriesForDate(date) },
                                onQueryWeeklyBasalCalories = { date -> healthConnectManager.readWeeklyBasalCalories(date) },
                                onQueryPeriodBasalCalories = { range, days, date ->
                                    healthConnectManager.readPeriodBasalCaloriesBucketed(range, days, date)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.Steps.route) {
                            StepsDetailScreen(
                                onQueryDailySteps = { date -> healthConnectManager.readStepsForDate(date).getOrDefault(0) },
                                onQueryHourlySteps = { date -> healthConnectManager.readHourlyStepsForDate(date).getOrDefault(emptyList()) },
                                onQueryWeeklySteps = { date -> healthConnectManager.readWeeklySteps(date).getOrDefault(emptyList()) },
                                onQueryPeriodSteps = { range, days, date ->
                                    healthConnectManager.readPeriodStepsBucketed(range, days, date).getOrNull()
                                },
                                dailyStepGoal = goals.dailyStepGoal,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.SpO2.route) {
                            SpO2DetailScreen(
                                onQueryDailySpO2 = { date -> healthConnectManager.readOxygenSaturationForDate(date) },
                                onQueryWeeklySpO2 = { date -> healthConnectManager.readWeeklyOxygenSaturation(date) },
                                onQueryPeriodSpO2 = { range, days, date ->
                                    healthConnectManager.readPeriodOxygenSaturationBucketed(range, days, date)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.Distance.route) {
                            DistanceDetailScreen(
                                onQueryDailyDistance = { date -> healthConnectManager.readDistanceForDate(date).getOrDefault(0.0) },
                                onQueryHourlyDistance = { date -> healthConnectManager.readHourlyDistanceForDate(date).getOrDefault(emptyList()) },
                                onQueryWeeklyDistance = { date -> healthConnectManager.readWeeklyDistance(date).getOrDefault(emptyList()) },
                                onQueryPeriodDistance = { range, days, date ->
                                    healthConnectManager.readPeriodDistanceBucketed(range, days, date).getOrNull()
                                },
                                dailyDistanceGoalKm = goals.dailyDistanceGoalKm,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.VitalsList.route) {
                            VitalsListScreen(
                                onBack = { navController.popBackStack() },
                                onNavigateToSpO2 = { navController.navigate(Screen.SpO2.route) },
                                onNavigateToHeartRate = { navController.navigate(Screen.RestingHeartRate.route) },
                                onNavigateToSkinTemp = { navController.navigate(Screen.SkinTemp.createRoute()) },
                                onNavigateToHrv = { navController.navigate(Screen.Hrv.createRoute()) },
                                onNavigateToBreathingRate = { navController.navigate(Screen.BreathingRate.createRoute()) },
                                currentSpO2 = todaySpO2,
                                currentRestingBpm = restingHeartRate?.dailyRecords?.firstOrNull()?.bpm
                            )
                        }

                        // One destination per screen: the config is resolved from
                        // the registry instead of being inlined five times.
                        composable(
                            route = Screen.GenericMetric.route,
                            arguments = listOf(
                                navArgument(Screen.ARG_CONFIG_ID) { type = NavType.StringType }
                            )
                        ) { entry ->
                            val configId = entry.arguments?.getString(Screen.ARG_CONFIG_ID)
                                ?: MetricConfigRegistry.SKIN_TEMPERATURE
                            val config = MetricConfigRegistry.fromKey(configId)
                                ?: MetricConfigRegistry.fromKey(MetricConfigRegistry.SKIN_TEMPERATURE)!!

                            if (configId == MetricConfigRegistry.WEEKLY_CARDIO) {
                                WeeklyCardioRoute(
                                    manager = healthConnectManager,
                                    workouts = workouts,
                                    goalMinutes = goals.weeklyCardioMinutesGoal,
                                    config = config,
                                    onBack = { navController.popBackStack() }
                                )
                            } else {
                                GenericMetricRoute(
                                    manager = healthConnectManager,
                                    config = config,
                                    goals = goals,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }

                        composable(Screen.HourlyActivity.route) {
                            HourlyActivityScreen(
                                loadRange = { start, end -> healthConnectManager.readHourlyStepsRange(start, end).getOrDefault(emptyMap()) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Screen.ExerciseDetail.route,
                            arguments = listOf(
                                navArgument(Screen.ARG_WORKOUT_ID) { type = NavType.StringType }
                            )
                        ) { entry ->
                            val workoutId = entry.arguments?.getString(Screen.ARG_WORKOUT_ID)
                            val routeOverrides = remember { mutableStateMapOf<String, List<com.healthcompanion.app.model.GeoPoint>>() }
                            val baseWorkout = remember(workoutId, workouts) {
                                workouts.firstOrNull { it.id == workoutId }
                            }
                            LaunchedEffect(baseWorkout?.id) {
                                val w = baseWorkout
                                if (w != null && w.routeConsentRequired && w.route == null) {
                                    healthConnectManager.readWorkoutRoute(w.id)?.let { routeOverrides[w.id] = it }
                                }
                            }
                            val workout = baseWorkout?.let { w ->
                                routeOverrides[w.id]?.let { w.copy(route = it, routeConsentRequired = false) } ?: w
                            }
                            if (workout != null) {
                                ExerciseDetailScreen(
                                    workout = workout,
                                    onBack = { navController.popBackStack() },
                                    onRequestRoute = {
                                        pendingRouteWorkoutId = workout.id
                                        onRouteResult = { id, route ->
                                            if (route != null) {
                                                routeOverrides[id] = route.route.map { loc ->
                                                    com.healthcompanion.app.model.GeoPoint(
                                                        latitude = loc.latitude,
                                                        longitude = loc.longitude,
                                                        altitudeMeters = loc.altitude?.inMeters ?: 0.0,
                                                        timestamp = loc.time
                                                    )
                                                }
                                            }
                                        }
                                        routeLauncher.launch(workout.id)
                                    }
                                )
                            } else {
                                // Resolved from the repository rather than a global
                                // object, so a process restart or a deep link lands
                                // here honestly instead of rendering stale content.
                                WorkoutMissingState(
                                    reason = if (workoutId == null) {
                                        "No workout was specified."
                                    } else {
                                        "This workout isn't in your Health Connect records."
                                    },
                                    onBack = { navController.popBackStack() },
                                    onBrowse = { navController.navigate(Screen.ExerciseList.route) }
                                )
                            }
                        }

                        composable(Screen.ExerciseList.route) {
                            ExerciseListScreen(
                                workouts = workouts,
                                onWorkoutClick = { workout ->
                                    navController.navigate(Screen.ExerciseDetail.createRoute(workout.id))
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Screen.CategoryMetrics.route,
                            arguments = listOf(
                                navArgument(Screen.ARG_CATEGORY_ID) { type = NavType.StringType }
                            )
                        ) { entry ->
                            val rawId = entry.arguments?.getString(Screen.ARG_CATEGORY_ID)
                            // A stable id is parsed, not a free-text display label,
                            // so a rename cannot break an existing route.
                            val category = CategoryId.fromKey(rawId)
                            if (category == null) {
                                UnknownCategoryState(
                                    onBack = { navController.popBackStack() }
                                )
                            } else {
                                val metrics = buildCategoryMetrics(
                                    category = category,
                                    dailyHeartRate = dailyHeartRate,
                                    restingHeartRate = restingHeartRate,
                                    todaySteps = todaySteps,
                                    todayDistance = todayDistance,
                                    weeklyCalories = weeklyCalories,
                                    workouts = workouts,
                                    sleepSessions = sleepSessions,
                                    todaySpO2 = todaySpO2,
                                    latestWeight = latestWeight,
                                    navController = navController
                                )
                                CategoryMetricsListScreen(
                                    categoryName = category.displayName,
                                    onBack = { navController.popBackStack() },
                                    metrics = metrics
                                )
                            }
                        }

                        composable(Screen.Profile.route) {
                            ProfileScreen(
                                goals = goals,
                                onGoalsChange = { updated ->
                                    goalsStore.save(updated)
                                    goals = updated
                                },
                                themeMode = themeMode,
                                onThemeModeChange = { updateThemeMode(it) },
                                isDynamicColor = useDynamicColor,
                                onToggleDynamicColor = { updateDynamicColor(!useDynamicColor) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.EditFocus.route) {
                            val today = LocalDate.now()
                            val weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                            val zone = ZoneId.systemDefault()
                            val weeklyCardio = workouts.filter {
                                val d = it.startTime.atZone(zone).toLocalDate()
                                !d.isBefore(weekStart) && !d.isAfter(today)
                            }.sumOf { it.cardioLoad }

                            EditFocusScreen(
                                focusStore = focusStore,
                                todaySteps = todaySteps ?: 0,
                                dailyStepGoal = goals.dailyStepGoal,
                                todayDistanceKm = todayDistance ?: 0.0,
                                dailyDistanceGoalKm = goals.dailyDistanceGoalKm,
                                weeklyCardioMinutes = weeklyCardio,
                                weeklyCardioGoal = goals.weeklyCardioMinutesGoal,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.Catalog.route) {
                            HealthCatalogScreen(
                                onSelectMetric = { metricId ->
                                    MetricConfigRegistry.routeForMetric(metricId)?.let { key ->
                                        navController.navigate(Screen.GenericMetric.createRoute(key))
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                    }
                }
            }
        }
    }
}

/**
 * Honest permission banner.
 *
 * The previous banner read "Demo Data Mode — Connect Health Connect" whenever
 * permissions were missing, which told a user who had deliberately declined that
 * they were looking at synthetic data while the dashboard quietly filled with
 * invented numbers. It now states the real counts and never implies sample data
 * is in play.
 */
@Composable
private fun HealthConnectBanner(
    permissionState: PermissionState,
    onConnect: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    permissionState.isNoneGranted ->
                        "Health Connect is off — no data is being shown"
                    else ->
                        "${permissionState.grantedCount} of ${permissionState.requestedCount} " +
                            "permissions granted — connect the rest"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f, fill = false)
            )
            TextButton(
                onClick = onConnect,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text("Connect", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HealthConnectUnavailableBanner(availability: HealthConnectStatus) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = when (availability) {
                HealthConnectStatus.NOT_INSTALLED ->
                    "Health Connect isn't installed — install it to see your data"
                HealthConnectStatus.UPDATE_REQUIRED ->
                    "Health Connect needs an update to show your data"
                HealthConnectStatus.AVAILABLE -> ""
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}
