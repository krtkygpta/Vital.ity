package com.healthcompanion.app.data.healthconnect

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.healthcompanion.app.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class HealthConnectStatus {
    AVAILABLE,
    NOT_INSTALLED,
    UPDATE_REQUIRED
}

class HealthConnectManager(private val context: Context) {

    fun getStatus(): HealthConnectStatus {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectStatus.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectStatus.UPDATE_REQUIRED
            else -> HealthConnectStatus.NOT_INSTALLED
        }
    }

    val healthConnectClient: HealthConnectClient? by lazy {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(SleepSessionRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(OxygenSaturationRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(BloodPressureRecord::class),
        HealthPermission.getWritePermission(HydrationRecord::class)
    )

    suspend fun getGrantedPermissions(): Set<String> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext emptySet()
        try {
            client.permissionController.getGrantedPermissions()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Failed to get granted permissions", e)
            emptySet()
        }
    }

    suspend fun hasAllPermissions(): Boolean = withContext(Dispatchers.IO) {
        val granted = getGrantedPermissions()
        permissions.all { it in granted }
    }

    suspend fun hasAnyPermissions(): Boolean = withContext(Dispatchers.IO) {
        val granted = getGrantedPermissions()
        permissions.any { it in granted }
    }

    suspend fun <T : Record> readAllRecords(
        recordType: kotlin.reflect.KClass<T>,
        timeRangeFilter: TimeRangeFilter
    ): List<T> {
        val client = healthConnectClient ?: return emptyList()
        val allRecords = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val request = ReadRecordsRequest(
                recordType = recordType,
                timeRangeFilter = timeRangeFilter,
                pageToken = pageToken
            )
            val response = client.readRecords(request)
            allRecords.addAll(response.records)
            pageToken = response.pageToken
        } while (pageToken != null)
        return allRecords
    }

    // ==========================================
    // SLEEP: Read across historical range (e.g. past 90 days)
    // ==========================================
    suspend fun readSleepSessions(
        startTime: Instant = Instant.now().minus(90, ChronoUnit.DAYS),
        endTime: Instant = Instant.now()
    ): Result<List<SleepSession>> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available on this device"))

        try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

            val sessions = response.records.map { record ->
                val origin = record.metadata.dataOrigin.packageName
                val displayApp = when {
                    origin.contains("nothing", ignoreCase = true) -> "Nothing X"
                    origin.contains("fitbit", ignoreCase = true) -> "Fitbit"
                    origin.contains("pixel", ignoreCase = true) -> "Pixel Watch"
                    origin.contains("samsung", ignoreCase = true) -> "Samsung Health"
                    origin.contains("whoop", ignoreCase = true) -> "WHOOP"
                    origin.contains("oura", ignoreCase = true) -> "Oura Ring"
                    origin.contains("garmin", ignoreCase = true) -> "Garmin"
                    else -> origin.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                }

                val stageIntervals = record.stages.map { stage ->
                    val type = when (stage.stage) {
                        SleepSessionRecord.STAGE_TYPE_AWAKE,
                        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> SleepStageType.AWAKE
                        SleepSessionRecord.STAGE_TYPE_REM -> SleepStageType.REM
                        SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStageType.LIGHT
                        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStageType.DEEP
                        else -> SleepStageType.LIGHT
                    }
                    SleepStageInterval(type, stage.startTime, stage.endTime)
                }

                val totalDuration = ChronoUnit.MINUTES.between(record.startTime, record.endTime)
                val asleepMinutes = stageIntervals.filter { it.stage.isSleep }.sumOf { it.durationMinutes }
                val efficiency = if (totalDuration > 0 && asleepMinutes > 0) {
                    ((asleepMinutes.toFloat() / totalDuration.toFloat()) * 100).toInt().coerceIn(1, 100)
                } else {
                    100
                }

                SleepSession(
                    id = record.metadata.id,
                    sourceApp = displayApp,
                    startTime = record.startTime,
                    endTime = record.endTime,
                    stages = stageIntervals,
                    sleepEfficiency = efficiency,
                    isMainSleep = totalDuration >= 90 // Sessions under 90 mins are categorized as naps
                )
            }.sortedByDescending { it.startTime }

            Result.success(sessions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun computeHeartRateZones(samples: List<HeartRateSample>, age: Int = 30): List<HeartRateZone> {
        val maxHr = (220 - age).coerceIn(120, 220)
        val lightMin = (maxHr * 0.50).toInt()
        val moderateMin = (maxHr * 0.60).toInt()
        val vigorousMin = (maxHr * 0.70).toInt()
        val peakMin = (maxHr * 0.85).toInt()

        if (samples.isEmpty()) {
            return listOf(
                HeartRateZone("Peak (≥$peakMin BPM)", peakMin, maxHr, 0, 0, type = HeartRateZoneType.PEAK),
                HeartRateZone("Vigorous ($vigorousMin–${peakMin - 1} BPM)", vigorousMin, peakMin - 1, 0, 0, type = HeartRateZoneType.VIGOROUS),
                HeartRateZone("Moderate ($moderateMin–${vigorousMin - 1} BPM)", moderateMin, vigorousMin - 1, 0, 0, type = HeartRateZoneType.MODERATE),
                HeartRateZone("Light ($lightMin–${moderateMin - 1} BPM)", lightMin, moderateMin - 1, 0, 0, type = HeartRateZoneType.LIGHT)
            )
        }

        var peakSeconds = 0L
        var vigorousSeconds = 0L
        var moderateSeconds = 0L
        var lightSeconds = 0L

        val sortedSamples = samples.sortedBy { it.timestamp }
        for (i in sortedSamples.indices) {
            val current = sortedSamples[i]
            val deltaSeconds = if (i < sortedSamples.size - 1) {
                val gap = Duration.between(current.timestamp, sortedSamples[i + 1].timestamp).seconds
                if (gap in 1..600) gap else 60L
            } else {
                60L
            }

            when {
                current.bpm >= peakMin -> peakSeconds += deltaSeconds
                current.bpm >= vigorousMin -> vigorousSeconds += deltaSeconds
                current.bpm >= moderateMin -> moderateSeconds += deltaSeconds
                else -> lightSeconds += deltaSeconds
            }
        }

        val totalSeconds = (peakSeconds + vigorousSeconds + moderateSeconds + lightSeconds).coerceAtLeast(1)

        val peakMinutes = (peakSeconds / 60).toInt()
        val vigorousMinutes = (vigorousSeconds / 60).toInt()
        val moderateMinutes = (moderateSeconds / 60).toInt()
        val lightMinutes = (lightSeconds / 60).toInt()

        return listOf(
            HeartRateZone("Peak (≥$peakMin BPM)", peakMin, maxHr, peakMinutes, ((peakSeconds.toFloat() / totalSeconds) * 100).toInt(), type = HeartRateZoneType.PEAK),
            HeartRateZone("Vigorous ($vigorousMin–${peakMin - 1} BPM)", vigorousMin, peakMin - 1, vigorousMinutes, ((vigorousSeconds.toFloat() / totalSeconds) * 100).toInt(), type = HeartRateZoneType.VIGOROUS),
            HeartRateZone("Moderate ($moderateMin–${vigorousMin - 1} BPM)", moderateMin, vigorousMin - 1, moderateMinutes, ((moderateSeconds.toFloat() / totalSeconds) * 100).toInt(), type = HeartRateZoneType.MODERATE),
            HeartRateZone("Light ($lightMin–${moderateMin - 1} BPM)", lightMin, moderateMin - 1, lightMinutes, ((lightSeconds.toFloat() / totalSeconds) * 100).toInt(), type = HeartRateZoneType.LIGHT)
        )
    }

    // ==========================================
    // HEART RATE: 24h Daily series for specific date
    // ==========================================
    suspend fun readDailyHeartRate(date: LocalDate = LocalDate.now(), age: Int = 30): Result<DailyHeartRateSummary?> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))

        try {
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)

            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                )
            )

            val samples = response.records.flatMap { record ->
                record.samples.map { sample ->
                    HeartRateSample(sample.time, sample.beatsPerMinute.toInt())
                }
            }.sortedBy { it.timestamp }

            if (samples.isEmpty()) {
                return@withContext Result.success(null)
            }

            val minBpm = samples.minOf { it.bpm }
            val maxBpm = samples.maxOf { it.bpm }

            val zones = computeHeartRateZones(samples, age)
            val peakZone = zones.firstOrNull()
            val peakZoneDesc = when {
                peakZone != null && maxBpm >= peakZone.minBpm -> "High of $maxBpm BPM in peak zone"
                maxBpm >= (220 - age) * 0.70 -> "High of $maxBpm BPM in vigorous zone"
                maxBpm >= (220 - age) * 0.60 -> "High of $maxBpm BPM in moderate zone"
                else -> "High of $maxBpm BPM in light zone"
            }

            Result.success(
                DailyHeartRateSummary(
                    date = date,
                    minBpm = minBpm,
                    maxBpm = maxBpm,
                    peakZoneSummary = peakZoneDesc,
                    samples = samples,
                    zones = zones
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readWeeklyHeartRate(endDate: LocalDate): Result<List<Triple<LocalDate, Int, Int>>> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
        try {
            val monday = weekStartMon(endDate)
            val sunday = monday.plusDays(6)
            val today = LocalDate.now()
            val startOfMonday = monday.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endOfSunday = sunday.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfMonday, endOfSunday)
                )
            )
            val samplesByDay = response.records.flatMap { r ->
                r.samples.map { s ->
                    s.time.atZone(ZoneId.systemDefault()).toLocalDate() to s.beatsPerMinute.toInt()
                }
            }.groupBy({ it.first }, { it.second })

            val result = (0..6).map { i ->
                val d = monday.plusDays(i.toLong())
                val bpms = samplesByDay[d].orEmpty()
                if (bpms.isNotEmpty() && !d.isAfter(today)) {
                    Triple(d, bpms.min(), bpms.max())
                } else {
                    Triple(d, 0, 0)
                }
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // RESTING HEART RATE: Past 30 to 90 days
    // ==========================================
    suspend fun readRestingHeartRateHistory(daysBack: Long = 30): Result<List<RestingHeartRateDay>> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))

        try {
            val startTime = Instant.now().minus(daysBack, ChronoUnit.DAYS)
            val endTime = Instant.now()

            val explicit = try {
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = RestingHeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                    )
                ).records.map { record ->
                    val date = record.time.atZone(ZoneId.systemDefault()).toLocalDate()
                    RestingHeartRateDay(date, record.beatsPerMinute.toInt(), sparklineSamples = emptyList())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("HealthConnectManager", "Resting HR read failed", e)
                emptyList()
            }

            // Fallback: the lowest heart rate recorded each day.
            val derived = try {
                val zone = ZoneId.systemDefault()
                client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(HeartRateRecord.BPM_MIN),
                        timeRangeFilter = TimeRangeFilter.between(
                            startTime.atZone(zone).toLocalDate().atStartOfDay(),
                            endTime.atZone(zone).toLocalDateTime()
                        ),
                        timeRangeSlicer = java.time.Period.ofDays(1)
                    )
                ).mapNotNull { bucket ->
                    val bpm = bucket.result[HeartRateRecord.BPM_MIN]?.toInt()
                    if (bpm != null && bpm > 0) {
                        RestingHeartRateDay(bucket.startTime.toLocalDate(), bpm, sparklineSamples = emptyList())
                    } else null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("HealthConnectManager", "Derived resting HR failed", e)
                emptyList()
            }

            val daily = (explicit.filter { it.bpm > 0 }
                .groupBy { it.date }
                .map { (date, list) ->
                    RestingHeartRateDay(date, list.map { it.bpm }.average().toInt(), sparklineSamples = emptyList())
                } + derived)
                .distinctBy { it.date }
                .sortedByDescending { it.date }

            Result.success(daily)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readRestingHeartRatePeriod(
        days: Int,
        endDate: LocalDate = LocalDate.now()
    ): Result<RestingHeartRatePeriodSummary?> = withContext(Dispatchers.IO) {
        val daysBack = (days * 2L).coerceAtLeast(30L)
        val historyResult = readRestingHeartRateHistory(daysBack = daysBack)
        if (historyResult.isFailure) return@withContext Result.failure(historyResult.exceptionOrNull() ?: Exception())
        val records = historyResult.getOrNull() ?: emptyList()
        val periodRecords = records.filter { !it.date.isAfter(endDate) && !it.date.isBefore(endDate.minusDays((days - 1).toLong())) }
        if (periodRecords.isEmpty()) {
            return@withContext Result.success(null)
        }
        val avgBpm = periodRecords.map { it.bpm }.average().toInt()
        val prevEndDate = endDate.minusDays(days.toLong())
        val prevStartDate = prevEndDate.minusDays((days - 1).toLong())
        val prevRecords = records.filter { !it.date.isAfter(prevEndDate) && !it.date.isBefore(prevStartDate) }
        val prevAvgBpm = if (prevRecords.isNotEmpty()) prevRecords.map { it.bpm }.average().toInt() else avgBpm
        val diff = avgBpm - prevAvgBpm
        val compText = when {
            diff > 0 -> "Your average was $diff bpm higher than the previous period"
            diff < 0 -> "Your average was ${-diff} bpm lower than the previous period"
            else -> "Your average was unchanged compared to the previous period"
        }
        Result.success(
            RestingHeartRatePeriodSummary(
                averageBpm = avgBpm,
                comparisonText = compText,
                dailyRecords = periodRecords.sortedByDescending { it.date }
            )
        )
    }

    suspend fun readRestingHeartRateWeekly(endDate: LocalDate = LocalDate.now()): Result<RestingHeartRatePeriodSummary?> =
        readRestingHeartRatePeriod(7, endDate)

    // ==========================================
    // EXERCISES: Workouts with embedded GPS routes and synced HR
    // ==========================================
    /** Re-reads one session's route; returns null when consent is still required or no route exists. */
    suspend fun readWorkoutRoute(sessionId: String): List<GeoPoint>? = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext null
        try {
            val record = client.readRecord(ExerciseSessionRecord::class, sessionId).record
            val result = record.exerciseRouteResult
            if (result is ExerciseRouteResult.Data) {
                result.exerciseRoute.route.map { loc ->
                    GeoPoint(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        altitudeMeters = loc.altitude?.inMeters ?: 0.0,
                        timestamp = loc.time
                    )
                }.takeIf { it.isNotEmpty() }
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("HealthConnectManager", "Route re-read failed", e)
            null
        }
    }

    suspend fun readWorkouts(daysBack: Long = 365, age: Int = 30): Result<List<ExerciseWorkout>> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))

        try {
            val startTime = Instant.now().minus(daysBack, ChronoUnit.DAYS)
            val endTime = Instant.now().plus(30, ChronoUnit.DAYS)

            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

            val workouts = response.records.map { record ->
                val titleLower = (record.title ?: "").lowercase()
                val notesLower = (record.notes ?: "").lowercase()

                val type = when {
                    record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON || titleLower.contains("badminton") || notesLower.contains("badminton") -> ExerciseType.BADMINTON
                    record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_WALKING || titleLower.contains("walk") -> ExerciseType.WALK
                    record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING || titleLower.contains("run") -> ExerciseType.RUN
                    record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_BIKING || titleLower.contains("bike") || titleLower.contains("cycling") -> ExerciseType.CYCLING
                    record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL || record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER || titleLower.contains("swim") -> ExerciseType.SWIMMING
                    record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING || titleLower.contains("hiit") -> ExerciseType.HIIT
                    record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING || record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING || titleLower.contains("strength") || titleLower.contains("lift") -> ExerciseType.STRENGTH_TRAINING
                    record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_YOGA || titleLower.contains("yoga") -> ExerciseType.YOGA
                    record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_SOCCER || titleLower.contains("soccer") || titleLower.contains("football") -> ExerciseType.SOCCER
                    record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL || titleLower.contains("basketball") -> ExerciseType.BASKETBALL
                    record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_TENNIS || titleLower.contains("tennis") -> ExerciseType.TENNIS
                    else -> ExerciseType.WORKOUT
                }

                val duration = ChronoUnit.MINUTES.between(record.startTime, record.endTime).toInt().coerceAtLeast(1)

                // Read embedded GPS route from session record
                val routePoints = mutableListOf<GeoPoint>()
                val routeResult = record.exerciseRouteResult
                if (routeResult is ExerciseRouteResult.Data) {
                    routeResult.exerciseRoute.route.forEach { loc ->
                        routePoints.add(
                            GeoPoint(
                                latitude = loc.latitude,
                                longitude = loc.longitude,
                                altitudeMeters = loc.altitude?.inMeters ?: 0.0,
                                timestamp = loc.time
                            )
                        )
                    }
                }

                // Query synchronized Heart Rate during workout window
                val hrResponse = try {
                    client.readRecords(
                        ReadRecordsRequest(
                            recordType = HeartRateRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(record.startTime, record.endTime)
                        )
                    )
                } catch (e: Exception) {
                    null
                }

                val hrSamples = hrResponse?.records?.flatMap { hrRec ->
                    hrRec.samples.map { HeartRateSample(it.time, it.beatsPerMinute.toInt()) }
                } ?: emptyList()

                // Calculate workout zones
                val workoutZones = computeHeartRateZones(hrSamples, age)

                // Query real distance if recorded
                val distanceResponse = try {
                    client.readRecords(
                        ReadRecordsRequest(
                            recordType = DistanceRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(record.startTime, record.endTime)
                        )
                    )
                } catch (e: Exception) {
                    null
                }
                var totalDistanceKm = distanceResponse?.records?.sumOf { it.distance.inKilometers }?.let {
                    if (it > 0.0) Math.round(it * 100.0) / 100.0 else null
                }

                // If distance wasn't logged separately but GPS points exist, compute physical distance
                if (totalDistanceKm == null && routePoints.size > 1) {
                    var totalMeters = 0.0
                    for (i in 1 until routePoints.size) {
                        val p1 = routePoints[i - 1]
                        val p2 = routePoints[i]
                        val dLat = Math.toRadians(p2.latitude - p1.latitude)
                        val dLng = Math.toRadians(p2.longitude - p1.longitude)
                        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                                Math.cos(Math.toRadians(p1.latitude)) * Math.cos(Math.toRadians(p2.latitude)) *
                                Math.sin(dLng / 2) * Math.sin(dLng / 2)
                        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
                        totalMeters += 6371000.0 * c
                    }
                    totalDistanceKm = Math.round((totalMeters / 1000.0) * 100.0) / 100.0
                }

                // Query real active calories during workout (Health Connect aggregate or workout package)
                val workoutAppPackage = record.metadata.dataOrigin.packageName
                val calories = try {
                    val agg = client.aggregate(
                        AggregateRequest(
                            metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(record.startTime, record.endTime)
                        )
                    )
                    agg[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.toInt()
                } catch (e: Exception) {
                    null
                } ?: run {
                    val calResp = try {
                        client.readRecords(
                            ReadRecordsRequest(
                                recordType = ActiveCaloriesBurnedRecord::class,
                                timeRangeFilter = TimeRangeFilter.between(record.startTime, record.endTime)
                            )
                        )
                    } catch (e: Exception) { null }
                    val sourceMatch = calResp?.records?.filter { it.metadata.dataOrigin.packageName == workoutAppPackage }
                    if (!sourceMatch.isNullOrEmpty()) {
                        sourceMatch.sumOf { it.energy.inKilocalories }.toInt()
                    } else {
                        calResp?.records?.groupBy { it.metadata.dataOrigin.packageName }
                            ?.values?.maxOfOrNull { list -> list.sumOf { it.energy.inKilocalories }.toInt() }
                            ?: (duration * 6)
                    }
                }

                // Query real steps during workout (Health Connect aggregate or workout package - NEVER sum multiple apps)
                val realWorkoutSteps = try {
                    val agg = client.aggregate(
                        AggregateRequest(
                            metrics = setOf(StepsRecord.COUNT_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(record.startTime, record.endTime)
                        )
                    )
                    agg[StepsRecord.COUNT_TOTAL]?.toInt()
                } catch (e: Exception) {
                    null
                } ?: run {
                    val stepsResp = try {
                        client.readRecords(
                            ReadRecordsRequest(
                                recordType = StepsRecord::class,
                                timeRangeFilter = TimeRangeFilter.between(record.startTime, record.endTime)
                            )
                        )
                    } catch (e: Exception) { null }
                    val sourceMatch = stepsResp?.records?.filter { it.metadata.dataOrigin.packageName == workoutAppPackage }
                    if (!sourceMatch.isNullOrEmpty()) {
                        sourceMatch.sumOf { it.count }.toInt()
                    } else {
                        stepsResp?.records?.groupBy { it.metadata.dataOrigin.packageName }
                            ?.values?.maxOfOrNull { list -> list.sumOf { it.count }.toInt() }
                    }
                }

                // Calculate real elevation gain from route
                val realElevationGain = if (routePoints.size > 1) {
                    var gain = 0.0
                    for (i in 1 until routePoints.size) {
                        val diff = routePoints[i].altitudeMeters - routePoints[i - 1].altitudeMeters
                        if (diff > 0) gain += diff
                    }
                    if (gain > 1.0) gain.toInt() else null
                } else null

                // Compute real pace
                val paceStr = if (totalDistanceKm != null && totalDistanceKm > 0.05) {
                    val totalSecs = ChronoUnit.SECONDS.between(record.startTime, record.endTime).toDouble()
                    val paceSecsPerKm = (totalSecs / totalDistanceKm).toInt()
                    val minsPart = paceSecsPerKm / 60
                    val secsPart = paceSecsPerKm % 60
                    "${minsPart}'${String.format("%02d", secsPart)}\""
                } else null

                // Real cardio load
                val avgBpm = if (hrSamples.isNotEmpty()) hrSamples.map { it.bpm }.average().toInt() else 100
                val computedCardioLoad = when {
                    avgBpm >= 150 -> (duration * 0.35).toInt()
                    avgBpm >= 120 -> (duration * 0.25).toInt()
                    avgBpm >= 100 -> (duration * 0.20).toInt()
                    else -> (duration * 0.15).toInt()
                }.coerceAtLeast(1)

                val rTitle = record.title
                val resolvedTitle = when {
                    type == ExerciseType.BADMINTON -> "Badminton"
                    !rTitle.isNullOrBlank() && rTitle != "Workout" -> rTitle
                    else -> type.label
                }

                ExerciseWorkout(
                    id = record.metadata.id,
                    type = type,
                    title = resolvedTitle,
                    startTime = record.startTime,
                    durationMinutes = duration,
                    cardioLoad = computedCardioLoad,
                    distanceKm = totalDistanceKm,
                    caloriesBurnedKcal = calories,
                    route = if (routePoints.isNotEmpty()) routePoints else null,
                    heartRateSamples = hrSamples,
                    zones = workoutZones,
                    avgPaceMinKm = paceStr,
                    elevationGainMeters = realElevationGain,
                    steps = realWorkoutSteps ?: (duration * 105),
                    routeConsentRequired = routeResult is ExerciseRouteResult.ConsentRequired
                )
            }.sortedByDescending { it.startTime }

            Result.success(workouts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // STEPS: Daily & Hourly step querying
    // ==========================================
    suspend fun readStepsForDate(date: LocalDate): Result<Int> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
        try {
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)

            // 1. Health Connect Aggregation (uses system priority & auto-deduplication)
            val agg = try {
                val resp = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                    )
                )
                resp[StepsRecord.COUNT_TOTAL]
            } catch (e: Exception) {
                null
            }

            if (agg != null) {
                return@withContext Result.success(agg.toInt())
            }

            // 2. Fallback: Deduplicate raw records by source package (never sum multiple apps together)
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                )
            )
            val maxPackageSteps = response.records.groupBy { it.metadata.dataOrigin.packageName }
                .values.maxOfOrNull { list -> list.sumOf { it.count } } ?: 0L
            Result.success(maxPackageSteps.toInt())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readHourlyStepsForDate(date: LocalDate): Result<List<Pair<Int, Int>>> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
        try {
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)
            val zone = ZoneId.systemDefault()

            // 1. Try aggregateGroupByDuration (aggregates per hour using system priority)
            val aggList = try {
                client.aggregateGroupByDuration(
                    AggregateGroupByDurationRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay),
                        timeRangeSlicer = Duration.ofHours(1)
                    )
                )
            } catch (e: Exception) {
                null
            }

            if (!aggList.isNullOrEmpty()) {
                val hourlyMap = mutableMapOf<Int, Int>()
                for (bucket in aggList) {
                    val h = bucket.startTime.atZone(zone).hour
                    val s = bucket.result[StepsRecord.COUNT_TOTAL]?.toInt() ?: 0
                    hourlyMap[h] = s
                }
                val result = (0..23).map { h -> h to (hourlyMap[h] ?: 0) }
                return@withContext Result.success(result)
            }

            // 2. Fallback: Identify primary credible source package, and only sum that package
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                )
            )
            val primaryPackage = response.records.groupBy { it.metadata.dataOrigin.packageName }
                .maxByOrNull { it.value.sumOf { r -> r.count } }?.key

            val filteredRecords = if (primaryPackage != null) {
                response.records.filter { it.metadata.dataOrigin.packageName == primaryPackage }
            } else {
                response.records
            }

            val hourlyMap = mutableMapOf<Int, Long>()
            filteredRecords.forEach { record ->
                val hour = record.startTime.atZone(zone).hour
                hourlyMap[hour] = (hourlyMap[hour] ?: 0L) + record.count
            }
            val result = (0..23).map { h -> h to (hourlyMap[h]?.toInt() ?: 0) }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readWeeklySteps(endDate: LocalDate): Result<List<Pair<LocalDate, Int>>> {
        val monday = weekStartMon(endDate)
        val today = LocalDate.now()
        val list = mutableListOf<Pair<LocalDate, Int>>()
        for (i in 0..6) {
            val d = monday.plusDays(i.toLong())
            val count = if (d.isAfter(today)) 0 else readStepsForDate(d).getOrDefault(0)
            list.add(d to count)
        }
        return Result.success(list)
    }

    suspend fun readPeriodSteps(days: Int, endDate: LocalDate): Result<List<Pair<LocalDate, Int>>> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Pair<LocalDate, Int>>()
        for (i in (days - 1) downTo 0) {
            val d = endDate.minusDays(i.toLong())
            val count = readStepsForDate(d).getOrDefault(0)
            list.add(d to count)
        }
        Result.success(list)
    }

    suspend fun readDistanceForDate(date: LocalDate): Result<Double> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
        try {
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)

            // 1. Read raw DistanceRecord and deduplicate by primary source package
            val response = try {
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = DistanceRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                    )
                )
            } catch (e: Exception) {
                null
            }

            var directKm = 0.0
            if (response != null && response.records.isNotEmpty()) {
                val primaryPackage = response.records.groupBy { it.metadata.dataOrigin.packageName }
                    .maxByOrNull { it.value.sumOf { r -> r.distance.inMeters } }?.key
                val totalMeters = response.records
                    .filter { primaryPackage == null || it.metadata.dataOrigin.packageName == primaryPackage }
                    .sumOf { it.distance.inMeters }
                directKm = Math.round((totalMeters / 1000.0) * 100.0) / 100.0
            }

            if (directKm > 0.0) {
                return@withContext Result.success(directKm)
            }

            // 2. Try aggregate if raw records didn't yield positive distance
            val aggMeters = try {
                val resp = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                    )
                )
                resp[DistanceRecord.DISTANCE_TOTAL]?.inMeters
            } catch (e: Exception) {
                null
            }

            if (aggMeters != null && aggMeters > 0.0) {
                val totalKm = Math.round((aggMeters / 1000.0) * 100.0) / 100.0
                return@withContext Result.success(totalKm)
            }

            // 3. Fallback: Exercise sessions distance for that day
            val sessionsDistance = try {
                val sessionsResp = client.readRecords(
                    ReadRecordsRequest(
                        recordType = ExerciseSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                    )
                )
                val sessMeters = sessionsResp.records.sumOf { sess ->
                    val distResp = client.readRecords(
                        ReadRecordsRequest(
                            recordType = DistanceRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(sess.startTime, sess.endTime)
                        )
                    )
                    distResp.records.sumOf { it.distance.inMeters }
                }
                if (sessMeters > 0.0) Math.round((sessMeters / 1000.0) * 100.0) / 100.0 else 0.0
            } catch (e: Exception) {
                0.0
            }

            if (sessionsDistance > 0.0) {
                return@withContext Result.success(sessionsDistance)
            }

            // 4. Fallback: Derive from steps (~0.78m per step)
            val steps = readStepsForDate(date).getOrDefault(0)
            if (steps > 0) {
                val derivedKm = Math.round((steps * 0.00078) * 100.0) / 100.0
                return@withContext Result.success(derivedKm)
            }

            Result.success(0.0)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readHourlyDistanceForDate(date: LocalDate): Result<List<Pair<Int, Double>>> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
        try {
            val zone = ZoneId.systemDefault()
            val startOfDay = date.atStartOfDay(zone).toInstant()
            val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)

            // 1. Read DistanceRecord and bucket by start hour
            val response = try {
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = DistanceRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                    )
                )
            } catch (e: Exception) {
                null
            }

            val hourlyMap = mutableMapOf<Int, Double>()
            if (response != null && response.records.isNotEmpty()) {
                val primaryPackage = response.records.groupBy { it.metadata.dataOrigin.packageName }
                    .maxByOrNull { it.value.sumOf { r -> r.distance.inMeters } }?.key
                val filtered = if (primaryPackage != null) {
                    response.records.filter { it.metadata.dataOrigin.packageName == primaryPackage }
                } else {
                    response.records
                }
                filtered.forEach { record ->
                    val hour = record.startTime.atZone(zone).hour
                    val km = record.distance.inMeters / 1000.0
                    hourlyMap[hour] = (hourlyMap[hour] ?: 0.0) + km
                }
            }

            if (hourlyMap.values.sum() > 0.0) {
                val result = (0..23).map { h ->
                    val raw = hourlyMap[h] ?: 0.0
                    h to (Math.round(raw * 100.0) / 100.0)
                }
                return@withContext Result.success(result)
            }

            // 2. Fallback: Derive hourly distance from hourly steps (~0.00078 km per step)
            val hourlyStepsRes = readHourlyStepsForDate(date)
            if (hourlyStepsRes.isSuccess) {
                val stepsList = hourlyStepsRes.getOrThrow()
                if (stepsList.any { it.second > 0 }) {
                    val derived = stepsList.map { (h, s) ->
                        val km = Math.round((s * 0.00078) * 100.0) / 100.0
                        h to km
                    }
                    return@withContext Result.success(derived)
                }
            }

            Result.success((0..23).map { it to 0.0 })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readWeeklyDistance(endDate: LocalDate): Result<List<Pair<LocalDate, Double>>> {
        val monday = weekStartMon(endDate)
        val today = LocalDate.now()
        val list = mutableListOf<Pair<LocalDate, Double>>()
        for (i in 0..6) {
            val d = monday.plusDays(i.toLong())
            val km = if (d.isAfter(today)) 0.0 else readDistanceForDate(d).getOrDefault(0.0)
            list.add(d to km)
        }
        return Result.success(list)
    }

    suspend fun readPeriodDistance(days: Int, endDate: LocalDate): Result<List<Pair<LocalDate, Double>>> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Pair<LocalDate, Double>>()
        for (i in (days - 1) downTo 0) {
            val d = endDate.minusDays(i.toLong())
            val km = readDistanceForDate(d).getOrDefault(0.0)
            list.add(d to km)
        }
        Result.success(list)
    }

    // ==========================================
    // CALORIES / ENERGY BURNED: Active & General (Total)
    // ==========================================
    suspend fun readActiveCaloriesForDate(date: LocalDate): Result<Int> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
        try {
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)

            val agg = try {
                val resp = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                    )
                )
                resp[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
            } catch (e: Exception) {
                null
            }

            if (agg != null) {
                return@withContext Result.success(agg.toInt())
            }

            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ActiveCaloriesBurnedRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                )
            )
            val primaryPackage = response.records.groupBy { it.metadata.dataOrigin.packageName }
                .maxByOrNull { it.value.sumOf { r -> r.energy.inKilocalories } }?.key
            val total = response.records.filter { primaryPackage == null || it.metadata.dataOrigin.packageName == primaryPackage }
                .sumOf { it.energy.inKilocalories }.toInt()
            Result.success(total)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readTotalCaloriesForDate(date: LocalDate): Result<Int> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
        try {
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)

            val agg = try {
                val resp = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                    )
                )
                resp[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
            } catch (e: Exception) {
                null
            }

            if (agg != null) {
                return@withContext Result.success(agg.toInt())
            }

            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = TotalCaloriesBurnedRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                )
            )
            val primaryPackage = response.records.groupBy { it.metadata.dataOrigin.packageName }
                .maxByOrNull { it.value.sumOf { r -> r.energy.inKilocalories } }?.key
            val total = response.records.filter { primaryPackage == null || it.metadata.dataOrigin.packageName == primaryPackage }
                .sumOf { it.energy.inKilocalories }.toInt()
            Result.success(total)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readHourlyActiveCaloriesForDate(date: LocalDate): Result<List<Pair<Int, Int>>> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
        try {
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)
            val zone = ZoneId.systemDefault()

            val aggList = try {
                client.aggregateGroupByDuration(
                    AggregateGroupByDurationRequest(
                        metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay),
                        timeRangeSlicer = Duration.ofHours(1)
                    )
                )
            } catch (e: Exception) { null }

            if (!aggList.isNullOrEmpty()) {
                val hourlyMap = mutableMapOf<Int, Int>()
                for (bucket in aggList) {
                    val h = bucket.startTime.atZone(zone).hour
                    val cal = bucket.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.toInt() ?: 0
                    hourlyMap[h] = cal
                }
                val result = (0..23).map { h -> h to (hourlyMap[h] ?: 0) }
                return@withContext Result.success(result)
            }

            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ActiveCaloriesBurnedRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                )
            )
            val primaryPackage = response.records.groupBy { it.metadata.dataOrigin.packageName }
                .maxByOrNull { it.value.sumOf { r -> r.energy.inKilocalories } }?.key
            val filtered = response.records.filter { primaryPackage == null || it.metadata.dataOrigin.packageName == primaryPackage }

            val hourlyMap = mutableMapOf<Int, Double>()
            filtered.forEach { record ->
                val hour = record.startTime.atZone(zone).hour
                hourlyMap[hour] = (hourlyMap[hour] ?: 0.0) + record.energy.inKilocalories
            }
            val result = (0..23).map { h -> h to (hourlyMap[h]?.toInt() ?: 0) }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readHourlyTotalCaloriesForDate(date: LocalDate): Result<List<Pair<Int, Int>>> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
        try {
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)
            val zone = ZoneId.systemDefault()

            val aggList = try {
                client.aggregateGroupByDuration(
                    AggregateGroupByDurationRequest(
                        metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay),
                        timeRangeSlicer = Duration.ofHours(1)
                    )
                )
            } catch (e: Exception) { null }

            if (!aggList.isNullOrEmpty()) {
                val hourlyMap = mutableMapOf<Int, Int>()
                for (bucket in aggList) {
                    val h = bucket.startTime.atZone(zone).hour
                    val cal = bucket.result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.toInt() ?: 0
                    hourlyMap[h] = cal
                }
                val result = (0..23).map { h -> h to (hourlyMap[h] ?: 0) }
                return@withContext Result.success(result)
            }

            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = TotalCaloriesBurnedRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                )
            )
            val primaryPackage = response.records.groupBy { it.metadata.dataOrigin.packageName }
                .maxByOrNull { it.value.sumOf { r -> r.energy.inKilocalories } }?.key
            val filtered = response.records.filter { primaryPackage == null || it.metadata.dataOrigin.packageName == primaryPackage }

            val hourlyMap = mutableMapOf<Int, Double>()
            filtered.forEach { record ->
                val hour = record.startTime.atZone(zone).hour
                hourlyMap[hour] = (hourlyMap[hour] ?: 0.0) + record.energy.inKilocalories
            }
            val result = (0..23).map { h -> h to (hourlyMap[h]?.toInt() ?: 0) }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readWeeklyActiveCalories(endDate: LocalDate): Result<List<Pair<LocalDate, Int>>> {
        val monday = weekStartMon(endDate)
        val today = LocalDate.now()
        val list = mutableListOf<Pair<LocalDate, Int>>()
        for (i in 0..6) {
            val d = monday.plusDays(i.toLong())
            val c = if (d.isAfter(today)) 0 else readActiveCaloriesForDate(d).getOrDefault(0)
            list.add(d to c)
        }
        return Result.success(list)
    }

    suspend fun readPeriodActiveCalories(days: Int, endDate: LocalDate): Result<List<Pair<LocalDate, Int>>> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Pair<LocalDate, Int>>()
        for (i in (days - 1) downTo 0) {
            val d = endDate.minusDays(i.toLong())
            val c = readActiveCaloriesForDate(d).getOrDefault(0)
            list.add(d to c)
        }
        Result.success(list)
    }

    suspend fun readWeeklyTotalCalories(endDate: LocalDate): Result<List<Pair<LocalDate, Int>>> {
        val monday = weekStartMon(endDate)
        val today = LocalDate.now()
        val list = mutableListOf<Pair<LocalDate, Int>>()
        for (i in 0..6) {
            val d = monday.plusDays(i.toLong())
            val c = if (d.isAfter(today)) 0 else readTotalCaloriesForDate(d).getOrDefault(0)
            list.add(d to c)
        }
        return Result.success(list)
    }

    suspend fun readPeriodTotalCalories(days: Int, endDate: LocalDate): Result<List<Pair<LocalDate, Int>>> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Pair<LocalDate, Int>>()
        for (i in (days - 1) downTo 0) {
            val d = endDate.minusDays(i.toLong())
            val c = readTotalCaloriesForDate(d).getOrDefault(0)
            list.add(d to c)
        }
        Result.success(list)
    }

    // Backward-compatible delegates
    suspend fun readCaloriesForDate(date: LocalDate): Result<Int> = readTotalCaloriesForDate(date)
    suspend fun readWeeklyCalories(endDate: LocalDate): Result<List<Pair<LocalDate, Int>>> = readWeeklyTotalCalories(endDate)
    suspend fun readHourlyCaloriesForDate(date: LocalDate): Result<List<Pair<Int, Int>>> = readHourlyTotalCaloriesForDate(date)

    // ==========================================
    // BLOOD OXYGEN (SpO2)
    // ==========================================
    suspend fun readBloodOxygenHistory(daysBack: Long = 30): Result<List<Pair<LocalDate, Int>>> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
        try {
            val startTime = Instant.now().minus(daysBack, ChronoUnit.DAYS)
            val endTime = Instant.now()
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = OxygenSaturationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val list = response.records.map {
                val d = it.time.atZone(ZoneId.systemDefault()).toLocalDate()
                d to it.percentage.value.toInt()
            }.distinctBy { it.first }.sortedByDescending { it.first }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readOxygenSaturationForDate(date: LocalDate): Result<Int?> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
        try {
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = OxygenSaturationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                )
            )
            if (response.records.isEmpty()) {
                Result.success(null)
            } else {
                val avg = response.records.map { it.percentage.value.toInt() }.average().toInt()
                Result.success(avg)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readLatestOxygenSaturation(daysBack: Long = 7): Result<Int?> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
        try {
            val startTime = Instant.now().minus(daysBack, ChronoUnit.DAYS)
            val endTime = Instant.now()
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = OxygenSaturationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val latest = response.records.maxByOrNull { it.time }
            Result.success(latest?.percentage?.value?.toInt())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readWeeklyOxygenSaturation(endDate: LocalDate): Result<List<Pair<LocalDate, Int?>>> {
        val monday = weekStartMon(endDate)
        val today = LocalDate.now()
        val list = mutableListOf<Pair<LocalDate, Int?>>()
        for (i in 0..6) {
            val d = monday.plusDays(i.toLong())
            val pct = if (d.isAfter(today)) null else readOxygenSaturationForDate(d).getOrNull()
            list.add(d to pct)
        }
        return Result.success(list)
    }

    suspend fun readPeriodOxygenSaturation(days: Int, endDate: LocalDate): Result<List<Pair<LocalDate, Int?>>> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Pair<LocalDate, Int?>>()
        for (i in (days - 1) downTo 0) {
            val d = endDate.minusDays(i.toLong())
            val pct = readOxygenSaturationForDate(d).getOrNull()
            list.add(d to pct)
        }
        Result.success(list)
    }

    // ==========================================
    // PERIOD BUCKETING
    // ==========================================
    /**
     * Collapses a daily series into chart-friendly buckets so a month shows ~30
     * points, a quarter ~13 weekly points and a year 12 monthly points — instead
     * of the same handful of values stretched across every bar.
     *
     * [average] is applied within each bucket, so a weekly bar means "average
     * per day that week" rather than "the same weekly total copied 12 times".
     */
    private fun <T> bucketDailySeries(
        daily: List<Pair<LocalDate, T>>,
        selectedRange: String,
        average: ((List<T>) -> T)?
    ): List<Pair<LocalDate, T>> {
        if (daily.isEmpty()) return emptyList()

        val ordered = daily.sortedBy { it.first }
        val groupFn: (LocalDate) -> LocalDate = when (selectedRange) {
            // A WEEK and a MONTH already fit a readable number of points, so
            // they stay per-day. Only the long ranges are collapsed.
            "THREE_MONTHS" -> { d: LocalDate -> weekStartMon(d) }
            "YEAR" -> { d: LocalDate -> YearMonth.from(d).atDay(1) }
            else -> return ordered
        }

        val grouped = ordered.groupBy { groupFn(it.first) }
        return grouped.entries
            .sortedBy { it.key }
            .map { (bucketDate, rows) ->
                val values = rows.map { it.second }
                bucketDate to (average?.invoke(values) ?: values.first())
            }
    }

    fun weekStartMon(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Weekly steps averaged per day, for the 3M chart. */
    suspend fun readPeriodStepsBucketed(
        range: String,
        days: Int,
        endDate: LocalDate
    ): Result<List<Pair<LocalDate, Int>>> = withContext(Dispatchers.IO) {
        val daily = readPeriodSteps(days, endDate).getOrDefault(emptyList())
        Result.success(bucketDailySeries(daily, range) { vals -> (vals.average()).toInt() })
    }

    /** Daily distance averaged per day within each bucket. */
    suspend fun readPeriodDistanceBucketed(
        range: String,
        days: Int,
        endDate: LocalDate
    ): Result<List<Pair<LocalDate, Double>>> = withContext(Dispatchers.IO) {
        val daily = readPeriodDistance(days, endDate).getOrDefault(emptyList())
        Result.success(bucketDailySeries(daily, range) { vals -> vals.average() })
    }

    /** Total calories averaged per day within each bucket. */
    suspend fun readPeriodTotalCaloriesBucketed(
        range: String,
        days: Int,
        endDate: LocalDate
    ): Result<List<Pair<LocalDate, Int>>> = withContext(Dispatchers.IO) {
        val daily = readPeriodTotalCalories(days, endDate).getOrDefault(emptyList())
        Result.success(bucketDailySeries(daily, range) { vals -> vals.average().toInt() })
    }

    /** Active calories averaged per day within each bucket. */
    suspend fun readPeriodActiveCaloriesBucketed(
        range: String,
        days: Int,
        endDate: LocalDate
    ): Result<List<Pair<LocalDate, Int>>> = withContext(Dispatchers.IO) {
        val daily = readPeriodActiveCalories(days, endDate).getOrDefault(emptyList())
        Result.success(bucketDailySeries(daily, range) { vals -> vals.average().toInt() })
    }

    // ==========================================
    // METABOLIC (BASAL) CALORIES — from BasalMetabolicRateRecord
    // ==========================================
    private suspend fun aggregateBasalKcal(start: Instant, end: Instant): Int {
        val client = healthConnectClient ?: return 0
        return try {
            val resp = client.aggregate(
                AggregateRequest(
                    metrics = setOf(BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            resp[BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL]?.inKilocalories?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    suspend fun readBasalCaloriesForDate(date: LocalDate): Result<Int> = withContext(Dispatchers.IO) {
        if (healthConnectClient == null) {
            return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
        }
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        // For today, only count energy up to now (BMR records can extend into the future).
        val end = minOf(start.plus(1, ChronoUnit.DAYS), Instant.now()).let {
            if (it.isBefore(start)) start else it
        }
        Result.success(if (end == start) 0 else aggregateBasalKcal(start, end))
    }

    suspend fun readHourlyBasalCaloriesForDate(date: LocalDate): Result<List<Pair<Int, Int>>> = withContext(Dispatchers.IO) {
        val client = healthConnectClient
            ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
        try {
            val zone = ZoneId.systemDefault()
            val start = date.atStartOfDay(zone).toInstant()
            val end = start.plus(1, ChronoUnit.DAYS)
            val buckets = client.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(
                    metrics = setOf(BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    timeRangeSlicer = Duration.ofHours(1)
                )
            )
            val map = buckets.associate {
                it.startTime.atZone(zone).hour to
                    (it.result[BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL]?.inKilocalories?.toInt() ?: 0)
            }
            Result.success((0..23).map { h -> h to (map[h] ?: 0) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readWeeklyBasalCalories(endDate: LocalDate): Result<List<Pair<LocalDate, Int>>> = withContext(Dispatchers.IO) {
        val monday = weekStartMon(endDate)
        val today = LocalDate.now()
        Result.success((0..6).map { i ->
            val d = monday.plusDays(i.toLong())
            d to if (d.isAfter(today)) 0 else readBasalCaloriesForDate(d).getOrDefault(0)
        })
    }

    suspend fun readPeriodBasalCalories(days: Int, endDate: LocalDate): Result<List<Pair<LocalDate, Int>>> = withContext(Dispatchers.IO) {
        Result.success((days - 1 downTo 0).map { i ->
            val d = endDate.minusDays(i.toLong())
            d to readBasalCaloriesForDate(d).getOrDefault(0)
        })
    }

    /** Metabolic calories averaged per day within each bucket. */
    suspend fun readPeriodBasalCaloriesBucketed(
        range: String,
        days: Int,
        endDate: LocalDate
    ): Result<List<Pair<LocalDate, Int>>> = withContext(Dispatchers.IO) {
        val daily = readPeriodBasalCalories(days, endDate).getOrDefault(emptyList())
        Result.success(bucketDailySeries(daily, range) { vals -> vals.average().toInt() })
    }

    /** SpO2 averaged per reading within each bucket; days with no data stay null. */
    suspend fun readPeriodOxygenSaturationBucketed(
        range: String,
        days: Int,
        endDate: LocalDate
    ): Result<List<Pair<LocalDate, Int?>>> = withContext(Dispatchers.IO) {
        val daily = readPeriodOxygenSaturation(days, endDate).getOrDefault(emptyList())
        Result.success(bucketDailySeries(daily, range) { vals ->
            vals.filterNotNull().takeIf { it.isNotEmpty() }?.average()?.toInt()
        })
    }

    /** Resting heart rate averaged per day within each bucket. */
    suspend fun readRestingHeartRateBucketed(
        range: String,
        days: Int,
        endDate: LocalDate
    ): Result<RestingHeartRatePeriodSummary?> = withContext(Dispatchers.IO) {
        val daily = readRestingHeartRateHistory(daysBack = days.toLong() * 2L + 14L)
            .getOrDefault(emptyList())
            .map { it.date to it.bpm }

        // The period is anchored to real calendar boundaries, so a week always
        // covers Monday–Sunday regardless of which day the cursor is on.
        val (periodStart, periodEnd) = when (range) {
            "WEEK" -> weekStartMon(endDate) to weekStartMon(endDate).plusDays(6)
            "MONTH" -> endDate.minusDays(29) to endDate
            "THREE_MONTHS" -> endDate.minusWeeks(11).minusDays(6) to endDate
            else -> endDate.withDayOfMonth(1).minusMonths(11) to endDate
        }
        // Never read past today: the tail of the current period is simply missing.
        val visibleEnd = minOf(periodEnd, LocalDate.now())
        val inRange = daily.filter { !it.first.isAfter(visibleEnd) && !it.first.isBefore(periodStart) }
        if (inRange.isEmpty()) return@withContext Result.success(null)

        val avgBpm = inRange.map { it.second }.average().toInt()

        // One point per day across the whole period, so a week always has a
        // column for each of its seven days. Days with no reading are simply
        // absent from the series rather than being filled with a fake value.
        val byDay = inRange.toMap()
        val records = (0..(visibleEnd.toEpochDay() - periodStart.toEpochDay()).toInt())
            .mapNotNull { offset ->
                val d = periodStart.plusDays(offset.toLong())
                byDay[d]?.let { RestingHeartRateDay(d, it) }
            }

        // Personal band is computed from trailing 90 days, so it reflects the
        // user's normal resting rate and is not skewed by the visible period.
        val baseline = readRestingHeartRateHistory(daysBack = 90L)
            .getOrDefault(emptyList())
            .filter { !it.date.isAfter(visibleEnd) }
            .map { it.bpm }

        Result.success(
            RestingHeartRatePeriodSummary(
                averageBpm = avgBpm,
                comparisonText = compareRestingPeriods(periodStart, visibleEnd, avgBpm),
                dailyRecords = records,
                personalRangeMin = baseline.minOrNull(),
                personalRangeMax = baseline.maxOrNull()
            )
        )
    }

    /** Human sentence comparing this period's average with the one before it. */
    private suspend fun compareRestingPeriods(
        start: LocalDate,
        end: LocalDate,
        avgBpm: Int
    ): String {
        val span = (end.toEpochDay() - start.toEpochDay() + 1).toInt()
        val prevStart = start.minusDays(span.toLong())
        val prevEnd = start.minusDays(1)

        val previous = readRestingHeartRateHistory(daysBack = span.toLong() * 2L + 14L)
            .getOrDefault(emptyList())
            .filter { !it.date.isAfter(prevEnd) && !it.date.isBefore(prevStart) }

        if (previous.isEmpty()) return "No earlier readings to compare against"
        val prevAvg = previous.map { it.bpm }.average().toInt()
        val diff = avgBpm - prevAvg
        return when {
            diff == 0 -> "Same average as the previous period"
            diff > 0 -> "Your average was $diff bpm higher than the previous period"
            else -> "Your average was ${-diff} bpm lower than the previous period"
        }
    }

    // ==========================================
    // WEIGHT
    // ==========================================
    suspend fun readWeightHistory(daysBack: Long = 180): Result<List<Pair<LocalDate, Double>>> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
        try {
            val startTime = Instant.now().minus(daysBack, ChronoUnit.DAYS)
            val endTime = Instant.now()
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val list = response.records.map {
                val d = it.time.atZone(ZoneId.systemDefault()).toLocalDate()
                d to Math.round(it.weight.inKilograms * 10.0) / 10.0
            }.distinctBy { it.first }.sortedByDescending { it.first }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Weight is a slow-moving metric measured in weeks or months, not days.
     * Buckets the raw history into one averaged point per period so the chart
     * has a single meaningful bar per column instead of a near-flat line.
     */
    suspend fun readWeightPeriods(
        days: Int,
        endDate: LocalDate = LocalDate.now()
    ): Result<List<Pair<LocalDate, Float>>> = withContext(Dispatchers.IO) {
        val history = readWeightHistory(daysBack = days.toLong() + 45L)
        if (history.isFailure) return@withContext Result.failure(history.exceptionOrNull() ?: Exception())
        val records = history.getOrDefault(emptyList())

        val start = endDate.minusDays((days - 1).toLong())
        val inRange = records.filter { !it.first.isAfter(endDate) && !it.first.isBefore(start) }
        if (inRange.isEmpty()) return@withContext Result.success(emptyList())

        val result: List<Pair<LocalDate, Float>> = when {
            // Daily buckets (week view)
            days <= 7 -> inRange
                .sortedBy { it.first }
                .map { (d, kg) -> d to kg.toFloat() }

            // One point per month (month / 3-month / year views)
            else -> inRange
                .groupBy { YearMonth.from(it.first) }
                .toSortedMap()
                .map { (ym, entries) ->
                    val avg = entries.map { it.second }.average()
                    ym.atDay(1) to Math.round(avg * 10.0).div(10.0).toFloat()
                }
        }
        Result.success(result)
    }

    /** Weight recorded on or before [endDate], for the headline value. */
    suspend fun readLatestWeight(endDate: LocalDate = LocalDate.now()): Result<Float?> = withContext(Dispatchers.IO) {
        val history = readWeightHistory(daysBack = 90L)
        val latest = history.getOrNull()
            ?.filter { !it.first.isAfter(endDate) }
            ?.maxByOrNull { it.first }
        Result.success(latest?.second?.toFloat())
    }

    // ==========================================
    // HOURLY ACTIVITY & WEEKLY CARDIO
    // ==========================================
    /**
     * Steps bucketed by day and hour in a single pass, so a whole period costs
     * one query instead of one per day.
     */
    suspend fun readHourlyStepsRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<Map<LocalDate, List<Pair<Int, Int>>>> = withContext(Dispatchers.IO) {
        val client = healthConnectClient
            ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
        try {
            val zone = ZoneId.systemDefault()
            val start = startDate.atStartOfDay(zone).toInstant()
            val end = endDate.plusDays(1).atStartOfDay(zone).toInstant()

            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )

            // Only the highest-volume source app is used, matching the rest of the app.
            val primary = response.records.groupBy { it.metadata.dataOrigin.packageName }
                .maxByOrNull { it.value.sumOf { r -> r.count } }?.key
            val filtered = response.records.filter {
                primary == null || it.metadata.dataOrigin.packageName == primary
            }

            val buckets = mutableMapOf<LocalDate, IntArray>()
            filtered.forEach { record ->
                val local = record.startTime.atZone(zone)
                val day = local.toLocalDate()
                val arr = buckets.getOrPut(day) { IntArray(24) }
                arr[local.hour] = (arr[local.hour] + record.count.toInt()).coerceAtLeast(0)
            }
            Result.success(buckets.mapValues { (_, arr) -> (0..23).map { it to arr[it] } })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Counts distinct hours in [dayStartHour]..[dayEndHour] where the user
     * logged at least [stepThreshold] steps — the WHO "active hours" idea
     * (250 steps within 9 distinct hours).
     */
    suspend fun readActiveHoursForDate(
        date: LocalDate,
        stepThreshold: Int = 250,
        dayStartHour: Int = 9,
        dayEndHour: Int = 18
    ): Result<Int> = withContext(Dispatchers.IO) {
        val hourly = readHourlyStepsForDate(date).getOrDefault(emptyList())
        if (hourly.isEmpty()) return@withContext Result.success(0)
        val active = hourly.count { (hour, steps) -> hour in dayStartHour..dayEndHour && steps >= stepThreshold }
        Result.success(active)
    }

    /** Active hours per day for the last [days] days, oldest first. */
    suspend fun readActiveHoursHistory(days: Int, endDate: LocalDate = LocalDate.now()): Result<List<Pair<LocalDate, Float>>> =
        withContext(Dispatchers.IO) {
            val start = endDate.minusDays((days - 1).toLong())
            val buckets = readHourlyStepsRange(start, endDate).getOrDefault(emptyMap())
            val list = (0 until days).map { offset ->
                val d = start.plusDays(offset.toLong())
                val hourly = buckets[d].orEmpty()
                val active = hourly.count { (hour, steps) -> hour in 9..18 && steps >= 250 }
                d to active.toFloat()
            }
            Result.success(list)
        }

    /**
     * Cardio load / Active zone minutes for the week ending [endDate].
     * Combines:
     * 1. Exercises/workouts: minutes in moderate/vigorous/peak zones or cardio load from exercise duration & calories.
     * 2. Steps with elevated heart rate: hours with higher step cadence contribute additional cardio load.
     */
    suspend fun readWeeklyActiveZoneMinutes(endDate: LocalDate = LocalDate.now()): Result<Int> =
        withContext(Dispatchers.IO) {
            val weekStart = endDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            val workouts = readWorkouts(daysBack = 45L).getOrDefault(emptyList())

            val workoutCardio = workouts
                .filter { workout ->
                    val d = workout.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
                    !d.isBefore(weekStart) && !d.isAfter(endDate)
                }
                .sumOf { workout ->
                    val zoneMins = workout.zones
                        .filter { zone -> zone.minBpm >= 110 }
                        .sumOf { it.minutesSpent }
                    if (zoneMins > 0) zoneMins
                    else workout.cardioLoad.coerceAtLeast((workout.durationMinutes * 0.7f).toInt())
                }

            // Steps cardio contribution: brisk hours generate cardio points
            var stepsCardio = 0
            var curDate = weekStart
            while (!curDate.isAfter(endDate)) {
                val dailySteps = readStepsForDate(curDate).getOrDefault(0)
                if (dailySteps >= 3000) {
                    val hourlySteps = readHourlyStepsForDate(curDate).getOrDefault(emptyList())
                    for ((_, steps) in hourlySteps) {
                        if (steps >= 1200) {
                            stepsCardio += (steps / 250).coerceIn(3, 12)
                        } else if (steps >= 600) {
                            stepsCardio += (steps / 350).coerceIn(1, 6)
                        }
                    }
                }
                curDate = curDate.plusDays(1)
            }

            val totalCardio = (workoutCardio + stepsCardio).coerceAtLeast(if (workouts.isNotEmpty()) 14 else 0)
            Result.success(totalCardio)
        }

    /** Weekly active zone minutes for the last [weeks] weeks, oldest first. */
    suspend fun readActiveZoneMinutesHistory(
        weeks: Int,
        endDate: LocalDate = LocalDate.now()
    ): Result<List<Pair<LocalDate, Float>>> = withContext(Dispatchers.IO) {
        val list = (weeks - 1 downTo 0).map { offset ->
            val weekEnd = endDate.minusWeeks(offset.toLong())
            val minutes = readWeeklyActiveZoneMinutes(weekEnd).getOrDefault(0)
            weekEnd to minutes.toFloat()
        }
        Result.success(list)
    }

    // ==========================================
    // DATA WRITES / LOGGING (Health Connect Inserts)
    // ==========================================
    suspend fun insertWeight(weightKg: Double, time: Instant = Instant.now()): Result<Unit> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
        try {
            val record = WeightRecord(
                time = time,
                zoneOffset = ZoneId.systemDefault().rules.getOffset(time),
                weight = androidx.health.connect.client.units.Mass.kilograms(weightKg)
            )
            client.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Failed to insert weight", e)
            Result.failure(e)
        }
    }

    suspend fun insertHydration(volumeMl: Double, time: Instant = Instant.now()): Result<Unit> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
        try {
            val startTime = time.minusSeconds(60)
            val record = HydrationRecord(
                startTime = startTime,
                startZoneOffset = ZoneId.systemDefault().rules.getOffset(startTime),
                endTime = time,
                endZoneOffset = ZoneId.systemDefault().rules.getOffset(time),
                volume = androidx.health.connect.client.units.Volume.liters(volumeMl / 1000.0)
            )
            client.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Failed to insert hydration", e)
            Result.failure(e)
        }
    }

    suspend fun insertHeartRate(bpm: Int, time: Instant = Instant.now()): Result<Unit> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
        try {
            val record = HeartRateRecord(
                startTime = time,
                startZoneOffset = ZoneId.systemDefault().rules.getOffset(time),
                endTime = time.plusSeconds(1),
                endZoneOffset = ZoneId.systemDefault().rules.getOffset(time),
                samples = listOf(HeartRateRecord.Sample(time = time, beatsPerMinute = bpm.toLong()))
            )
            client.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Failed to insert heart rate", e)
            Result.failure(e)
        }
    }

    suspend fun insertBloodPressure(systolicMmHg: Double, diastolicMmHg: Double, time: Instant = Instant.now()): Result<Unit> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
        try {
            val record = BloodPressureRecord(
                time = time,
                zoneOffset = ZoneId.systemDefault().rules.getOffset(time),
                systolic = androidx.health.connect.client.units.Pressure.millimetersOfMercury(systolicMmHg),
                diastolic = androidx.health.connect.client.units.Pressure.millimetersOfMercury(diastolicMmHg)
            )
            client.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Failed to insert blood pressure", e)
            Result.failure(e)
        }
    }

    suspend fun insertSleepSession(startTime: Instant, endTime: Instant): Result<Unit> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
        try {
            val record = SleepSessionRecord(
                startTime = startTime,
                startZoneOffset = ZoneId.systemDefault().rules.getOffset(startTime),
                endTime = endTime,
                endZoneOffset = ZoneId.systemDefault().rules.getOffset(endTime),
                title = "Sleep"
            )
            client.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Failed to insert sleep session", e)
            Result.failure(e)
        }
    }

    suspend fun insertWorkout(workout: ExerciseWorkout): Result<Unit> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
        try {
            val hcExerciseType = when (workout.type) {
                ExerciseType.RUN -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
                ExerciseType.WALK -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
                ExerciseType.CYCLING -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
                ExerciseType.SWIMMING -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL
                ExerciseType.YOGA -> ExerciseSessionRecord.EXERCISE_TYPE_YOGA
                ExerciseType.HIIT -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
                ExerciseType.STRENGTH_TRAINING -> ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING
                ExerciseType.BADMINTON -> ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON
                ExerciseType.SOCCER -> ExerciseSessionRecord.EXERCISE_TYPE_SOCCER
                ExerciseType.BASKETBALL -> ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL
                ExerciseType.TENNIS -> ExerciseSessionRecord.EXERCISE_TYPE_TENNIS
                else -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
            }
            val endTime = workout.startTime.plus(workout.durationMinutes.toLong(), ChronoUnit.MINUTES)
            val record = ExerciseSessionRecord(
                startTime = workout.startTime,
                startZoneOffset = ZoneId.systemDefault().rules.getOffset(workout.startTime),
                endTime = endTime,
                endZoneOffset = ZoneId.systemDefault().rules.getOffset(endTime),
                exerciseType = hcExerciseType,
                title = workout.title
            )
            client.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Failed to insert workout", e)
            Result.failure(e)
        }
    }
}
