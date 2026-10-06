package com.healthcompanion.app.ui.navigation

/**
 * Stable identifiers for the category browse destinations.
 *
 * The route used to carry a URL-encoded *display label* — callers passed strings
 * like "Upload records", "Health goals" and "Profile". Anything not matching one
 * of the `when` branches fell through to an "else" row that rendered
 * "No records found in Health Connect", which is how a tap on **Profile** ended
 * up on a screen claiming the user had no records.
 *
 * A stable id makes an unrecognised route an explicit "unknown" state instead.
 */
enum class CategoryId(val key: String, val displayName: String) {
    HEART("heart", "Heart"),
    FITNESS("fitness", "Activity"),
    VITALS("vitals", "Body vitals"),
    RESPIRATORY("respiratory", "Respiratory"),
    SLEEP("sleep", "Sleep"),
    METABOLIC("metabolic", "Metabolic"),
    TEMPERATURE("temperature", "Temperature"),
    MENTAL_WELLBEING("mental_wellbeing", "Mental wellbeing"),
    NUTRITION("nutrition", "Nutrition");

    companion object {
        private val byKey = entries.associateBy(CategoryId::key)

        /** Returns null for an unknown id, which callers surface as an explicit state. */
        fun fromKey(key: String?): CategoryId? = key?.let(byKey::get)
    }
}
