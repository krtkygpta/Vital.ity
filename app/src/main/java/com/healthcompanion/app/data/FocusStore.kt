package com.healthcompanion.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class FocusTileSize {
    SMALL,
    LARGE
}

enum class TileTone {
    TEAL,
    SLATE,
    BLUE,
    PURPLE,
    GREEN
}

data class FocusTileDef(
    val id: String,
    val title: String,
    val iconKey: String = "circle",
    val tone: TileTone = TileTone.TEAL,
    val defaultVal: String = "No data",
    val defaultSubtitle: String? = null,
    val size: FocusTileSize = FocusTileSize.SMALL
)

object FocusTileRegistry {
    val allTiles: List<FocusTileDef> = listOf(
        // Small Tiles
        FocusTileDef(
            id = "steps",
            title = "Steps",
            iconKey = "walk",
            tone = TileTone.TEAL,
            defaultVal = "0"
        ),
        FocusTileDef(
            id = "exercise_days",
            title = "Exercise days",
            iconKey = "fitness",
            tone = TileTone.TEAL,
            defaultVal = "0 of 5"
        ),
        FocusTileDef(
            id = "run_distance",
            title = "Run distance",
            iconKey = "run",
            tone = TileTone.SLATE,
            defaultVal = "No data"
        ),
        FocusTileDef(
            id = "zone_min",
            title = "Zone min",
            iconKey = "trending_up",
            tone = TileTone.TEAL,
            defaultVal = "0"
        ),
        FocusTileDef(
            id = "distance",
            title = "Distance",
            iconKey = "location",
            tone = TileTone.TEAL,
            defaultVal = "0 km"
        ),
        FocusTileDef(
            id = "calories_burned",
            title = "Cal burned",
            iconKey = "fire",
            tone = TileTone.TEAL,
            defaultVal = "0"
        ),
        FocusTileDef(
            id = "floors",
            title = "Floors",
            iconKey = "terrain",
            tone = TileTone.TEAL,
            defaultVal = "0"
        ),
        FocusTileDef(
            id = "weekly_cardio",
            title = "Weekly cardio",
            iconKey = "favorite",
            tone = TileTone.BLUE,
            defaultVal = "0%"
        ),
        FocusTileDef(
            id = "readiness",
            title = "Readiness",
            iconKey = "self_improvement",
            tone = TileTone.SLATE,
            defaultVal = "Calibrating"
        ),
        FocusTileDef(
            id = "sleep",
            title = "Sleep",
            iconKey = "bedtime",
            tone = TileTone.PURPLE,
            defaultVal = "No data"
        ),
        FocusTileDef(
            id = "heart_rate",
            title = "Heart rate",
            iconKey = "favorite",
            tone = TileTone.SLATE,
            defaultVal = "No data"
        ),
        FocusTileDef(
            id = "resting_heart_rate",
            title = "Resting heart rate",
            iconKey = "monitor_heart",
            tone = TileTone.GREEN,
            defaultVal = "No data"
        ),
        FocusTileDef(
            id = "spo2",
            title = "SpO2",
            iconKey = "water_drop",
            tone = TileTone.GREEN,
            defaultVal = "No data"
        ),
        FocusTileDef(
            id = "breathing_rate",
            title = "Breathing rate",
            iconKey = "air",
            tone = TileTone.GREEN,
            defaultVal = "No data"
        ),
        FocusTileDef(
            id = "skin_temp",
            title = "Skin temp",
            iconKey = "device_thermostat",
            tone = TileTone.GREEN,
            defaultVal = "No data"
        ),
        FocusTileDef(
            id = "heart_rate_variability",
            title = "Heart rate variab..",
            iconKey = "timeline",
            tone = TileTone.GREEN,
            defaultVal = "No data"
        ),
        FocusTileDef(
            id = "glucose",
            title = "Glucose",
            iconKey = "bloodtype",
            tone = TileTone.GREEN,
            defaultVal = "No data"
        ),
        FocusTileDef(
            id = "weight",
            title = "Weight",
            iconKey = "scale",
            tone = TileTone.BLUE,
            defaultVal = "No data"
        ),
        FocusTileDef(
            id = "mindfulness",
            title = "Mindfulness",
            iconKey = "psychology",
            tone = TileTone.SLATE,
            defaultVal = "0 m"
        ),
        FocusTileDef(
            id = "mindful_days",
            title = "Mindful days",
            iconKey = "self_improvement",
            tone = TileTone.SLATE,
            defaultVal = "No data"
        ),
        FocusTileDef(
            id = "resilience",
            title = "Resilience",
            iconKey = "spa",
            tone = TileTone.SLATE,
            defaultVal = "No data"
        ),
        FocusTileDef(
            id = "hydration",
            title = "Hydration",
            iconKey = "local_drink",
            tone = TileTone.SLATE,
            defaultVal = "No data"
        ),
        FocusTileDef(
            id = "cardio_leaderboard",
            title = "Cardio leaderbo..",
            iconKey = "emoji_events",
            tone = TileTone.SLATE,
            defaultVal = "Tap to set up"
        ),
        FocusTileDef(
            id = "second_place",
            title = "2nd place",
            iconKey = "military_tech",
            tone = TileTone.TEAL,
            defaultVal = "11999 steps to 1st"
        ),
        FocusTileDef(
            id = "fat",
            title = "Fat",
            iconKey = "pie_chart",
            tone = TileTone.SLATE,
            defaultVal = "No data"
        )
    )

    fun getDef(id: String): FocusTileDef {
        return allTiles.find { it.id == id } ?: FocusTileDef(
            id = id,
            title = id.replace('_', ' ').replaceFirstChar { it.uppercase() },
            iconKey = "circle",
            tone = TileTone.SLATE
        )
    }
}

class FocusStore(context: Context) {
    private val prefs = context.getSharedPreferences("focus_settings", Context.MODE_PRIVATE)

    var page0TilesState by mutableStateOf(loadPageTiles(0))
        private set

    var page1TilesState by mutableStateOf(loadPageTiles(1))
        private set

    var page2TilesState by mutableStateOf(loadPageTiles(2))
        private set

    var selectedTileIdsState by mutableStateOf(loadSelectedTileIds())
        private set

    var isExpandedViewState by mutableStateOf(isExpandedView())
        private set

    fun getPageTiles(page: Int): List<String> {
        return when (page) {
            0 -> page0TilesState
            1 -> page1TilesState
            2 -> page2TilesState
            else -> emptyList()
        }
    }

    fun loadPageTiles(page: Int): List<String> {
        val key = "focus_page_${page}_tiles"
        val defaultTiles = when (page) {
            0 -> listOf("distance", "resting_heart_rate", "sleep")
            1 -> listOf("steps", "calories_burned", "exercise_days")
            2 -> listOf("weekly_cardio", "floors", "zone_min")
            else -> emptyList()
        }
        val raw = prefs.getString(key, null)
        if (raw == null) {
            if (page == 0) {
                val legacy = prefs.getString(KEY_TILES, null)
                if (legacy != null) {
                    return legacy.split(",").filter { it.isNotBlank() }
                }
            }
            return defaultTiles
        }
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun savePageTiles(page: Int, ids: List<String>) {
        val key = "focus_page_${page}_tiles"
        prefs.edit().putString(key, ids.joinToString(",")).apply()
        when (page) {
            0 -> {
                page0TilesState = ids
                prefs.edit().putString(KEY_TILES, ids.joinToString(",")).apply()
                selectedTileIdsState = ids
            }
            1 -> page1TilesState = ids
            2 -> page2TilesState = ids
        }
    }

    fun loadSelectedTileIds(): List<String> {
        return loadPageTiles(0)
    }

    fun saveSelectedTileIds(ids: List<String>) {
        savePageTiles(0, ids)
    }

    fun isExpandedView(): Boolean {
        return prefs.getBoolean(KEY_EXPANDED, false)
    }

    fun setExpandedView(expanded: Boolean) {
        prefs.edit().putBoolean(KEY_EXPANDED, expanded).apply()
        isExpandedViewState = expanded
    }

    private companion object {
        const val KEY_TILES = "focus_tile_ids"
        const val KEY_EXPANDED = "focus_expanded_view"
    }
}
