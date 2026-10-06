package com.healthcompanion.app.model

enum class HealthCategory(val title: String) {
    SLEEP("Sleep"),
    VITALS("Vitals"),
    ACTIVITY("Activity & Exercise"),
    BODY_MEASUREMENTS("Body Measurements"),
    NUTRITION("Nutrition & Hydration"),
    CYCLE_TRACKING("Cycle Tracking")
}

data class HealthMetricDefinition(
    val id: String,
    val name: String,
    val category: HealthCategory,
    val permission: String,
    val description: String,
    val units: String,
    val isSupportedInApp: Boolean = true
)

object HealthConnectCatalog {
    val allMetrics = listOf(
        // Sleep
        HealthMetricDefinition(
            id = "sleep_session",
            name = "Sleep Sessions & Stages",
            category = HealthCategory.SLEEP,
            permission = "android.permission.health.READ_SLEEP",
            description = "Sleep intervals with Awake, REM, Light, and Deep stages, sleep efficiency, and nap tracking.",
            units = "hours, minutes"
        ),

        // Vitals
        HealthMetricDefinition(
            id = "heart_rate",
            name = "Heart Rate (Series)",
            category = HealthCategory.VITALS,
            permission = "android.permission.health.READ_HEART_RATE",
            description = "Continuous time-series heart rate readings with zone distributions.",
            units = "BPM"
        ),
        HealthMetricDefinition(
            id = "resting_heart_rate",
            name = "Resting Heart Rate",
            category = HealthCategory.VITALS,
            permission = "android.permission.health.READ_RESTING_HEART_RATE",
            description = "Daily baseline resting heart rate when sitting or at waking.",
            units = "BPM"
        ),
        HealthMetricDefinition(
            id = "hrv_rmssd",
            name = "Heart Rate Variability (HRV)",
            category = HealthCategory.VITALS,
            permission = "android.permission.health.READ_HEART_RATE_VARIABILITY",
            description = "RMSSD variance between consecutive heartbeats reflecting autonomic nervous recovery.",
            units = "ms"
        ),
        HealthMetricDefinition(
            id = "spo2",
            name = "Blood Oxygen (SpO2)",
            category = HealthCategory.VITALS,
            permission = "android.permission.health.READ_OXYGEN_SATURATION",
            description = "Percentage of oxygen-saturated hemoglobin in the blood.",
            units = "%"
        ),
        HealthMetricDefinition(
            id = "blood_pressure",
            name = "Blood Pressure",
            category = HealthCategory.VITALS,
            permission = "android.permission.health.READ_BLOOD_PRESSURE",
            description = "Systolic and diastolic arterial pressure.",
            units = "mmHg"
        ),
        HealthMetricDefinition(
            id = "respiratory_rate",
            name = "Respiratory Rate",
            category = HealthCategory.VITALS,
            permission = "android.permission.health.READ_RESPIRATORY_RATE",
            description = "Breathing rate during sleep or rest.",
            units = "breaths/min"
        ),
        HealthMetricDefinition(
            id = "skin_temperature",
            name = "Skin Temperature",
            category = HealthCategory.VITALS,
            permission = "android.permission.health.READ_SKIN_TEMPERATURE",
            description = "Skin temperature variation relative to personal baseline during sleep.",
            units = "°C / °F"
        ),

        // Activity
        HealthMetricDefinition(
            id = "exercise_session",
            name = "Exercise & GPS Route",
            category = HealthCategory.ACTIVITY,
            permission = "android.permission.health.READ_EXERCISE",
            description = "Workout sessions with embedded GPS polyline routes, altitude, pace, and synchronized HR.",
            units = "duration, route, km"
        ),
        HealthMetricDefinition(
            id = "steps",
            name = "Steps",
            category = HealthCategory.ACTIVITY,
            permission = "android.permission.health.READ_STEPS",
            description = "Daily step counts and interval pacing.",
            units = "steps"
        ),
        HealthMetricDefinition(
            id = "distance",
            name = "Distance",
            category = HealthCategory.ACTIVITY,
            permission = "android.permission.health.READ_DISTANCE",
            description = "Distance walked, run, or cycled.",
            units = "km / miles"
        ),
        HealthMetricDefinition(
            id = "total_calories",
            name = "Energy Burned (Total)",
            category = HealthCategory.ACTIVITY,
            permission = "android.permission.health.READ_TOTAL_CALORIES_BURNED",
            description = "Combined basal and active calories burned across the day.",
            units = "kcal"
        ),
        HealthMetricDefinition(
            id = "active_calories",
            name = "Active Calories",
            category = HealthCategory.ACTIVITY,
            permission = "android.permission.health.READ_ACTIVE_CALORIES_BURNED",
            description = "Energy expended strictly through physical workouts and active movement.",
            units = "kcal"
        ),
        HealthMetricDefinition(
            id = "elevation_gained",
            name = "Elevation Gained",
            category = HealthCategory.ACTIVITY,
            permission = "android.permission.health.READ_ELEVATION_GAINED",
            description = "Total vertical elevation climbed during outdoor activities.",
            units = "meters / feet"
        ),
        HealthMetricDefinition(
            id = "vo2_max",
            name = "Cardio Fitness (VO2 Max)",
            category = HealthCategory.ACTIVITY,
            permission = "android.permission.health.READ_VO2_MAX",
            description = "Maximum rate of oxygen consumption during maximum exercise effort.",
            units = "ml/kg/min"
        ),
        HealthMetricDefinition(
            id = "speed",
            name = "Speed & Pace",
            category = HealthCategory.ACTIVITY,
            permission = "android.permission.health.READ_SPEED",
            description = "Instantaneous and continuous movement speed during activity.",
            units = "km/h, min/km"
        ),

        // Body Measurements
        HealthMetricDefinition(
            id = "weight",
            name = "Weight",
            category = HealthCategory.BODY_MEASUREMENTS,
            permission = "android.permission.health.READ_WEIGHT",
            description = "Body mass trends over time.",
            units = "kg / lbs"
        ),
        HealthMetricDefinition(
            id = "body_fat",
            name = "Body Fat Percentage",
            category = HealthCategory.BODY_MEASUREMENTS,
            permission = "android.permission.health.READ_BODY_FAT",
            description = "Percentage of total body weight composed of fat tissue.",
            units = "%"
        ),
        HealthMetricDefinition(
            id = "bmi",
            name = "Body Mass Index (BMI)",
            category = HealthCategory.BODY_MEASUREMENTS,
            permission = "android.permission.health.READ_BODY_MASS_INDEX",
            description = "Standard ratio of weight to height squared.",
            units = "kg/m²"
        ),

        // Nutrition
        HealthMetricDefinition(
            id = "hydration",
            name = "Hydration",
            category = HealthCategory.NUTRITION,
            permission = "android.permission.health.READ_HYDRATION",
            description = "Daily fluid and water consumption volume.",
            units = "liters / ml"
        ),
        HealthMetricDefinition(
            id = "nutrition",
            name = "Macronutrients & Micronutrients",
            category = HealthCategory.NUTRITION,
            permission = "android.permission.health.READ_NUTRITION",
            description = "Carbohydrates, protein, fats, dietary fiber, vitamins A-K, and essential minerals.",
            units = "grams / mg"
        ),

        // Cycle Tracking
        HealthMetricDefinition(
            id = "menstruation",
            name = "Menstruation & Cycle",
            category = HealthCategory.CYCLE_TRACKING,
            permission = "android.permission.health.READ_MENSTRUATION",
            description = "Period flow intensity, cycle prediction, and ovulation indicators.",
            units = "flow stages, dates"
        )
    )
}
