package com.healthcompanion.app.ui.format

/**
 * Parsing and range rules for every manually entered health value.
 *
 * The point of this file is that a field which cannot be parsed is *never*
 * silently replaced with a plausible default. Previously `weightKgText
 * .toDoubleOrNull() ?: 70.0` meant that clearing the weight field and tapping
 * Save wrote 70 kg into the user's real Health Connect record.
 *
 * Each validator returns `null` when the input is valid, or a message key
 * describing what is wrong when it is not.
 */

data class NumberRange(val min: Double, val max: Double) {
    operator fun contains(value: Double): Boolean = value in min..max

    fun message(label: String): String =
        HealthFormat.decimal(min, if (min % 1.0 == 0.0) 0 else 1) + "–" +
            HealthFormat.decimal(max, if (max % 1.0 == 0.0) 0 else 1) + " $label"
}

/** Clinically plausible bounds, used to reject nonsense before it reaches Health Connect. */
object HealthRanges {
    val weightKg = NumberRange(20.0, 300.0)
    val heartRateBpm = NumberRange(20.0, 250.0)
    val systolicMmHg = NumberRange(50.0, 260.0)
    val diastolicMmHg = NumberRange(30.0, 160.0)
    val waterMl = NumberRange(0.0, 10_000.0)
    val durationMinutes = NumberRange(1.0, 1_440.0)
    val caloriesKcal = NumberRange(0.0, 20_000.0)
    val distanceKm = NumberRange(0.0, 500.0)
    val hourOfDay = NumberRange(0.0, 23.0)
    val minuteOfHour = NumberRange(0.0, 59.0)
}

/** Outcome of validating one field: `Value` to use, or `Invalid` with a reason. */
sealed interface FieldResult<out T> {
    data class Value<T>(val value: T) : FieldResult<T>
    data class Invalid(val reason: FieldError) : FieldResult<Nothing>
}

enum class FieldError { REQUIRED, NOT_A_NUMBER, OUT_OF_RANGE, INVERTED }

object HealthInput {

    /**
     * Parses [text] against [range]. A blank string is `REQUIRED`, an
     * unparseable one is `NOT_A_NUMBER`, and an out-of-bounds one is
     * `OUT_OF_RANGE` — never a silent fallback.
     */
    fun int(
        text: String,
        range: NumberRange,
        required: Boolean = true,
        allowBlankAsZero: Boolean = false
    ): FieldResult<Int> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return if (!required || allowBlankAsZero) FieldResult.Value(0) else FieldResult.Invalid(FieldError.REQUIRED)
        }
        val parsed = trimmed.toIntOrNull() ?: trimmed.toDoubleOrNull()?.toInt()
            ?: return FieldResult.Invalid(FieldError.NOT_A_NUMBER)
        return if (range.contains(parsed.toDouble())) {
            FieldResult.Value(parsed)
        } else {
            FieldResult.Invalid(FieldError.OUT_OF_RANGE)
        }
    }

    fun decimal(
        text: String,
        range: NumberRange,
        required: Boolean = true
    ): FieldResult<Double> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return if (required) FieldResult.Invalid(FieldError.REQUIRED) else FieldResult.Value(0.0)
        }
        // Accept both the locale separator and a plain dot, so a de-DE keyboard
        // cannot make a valid weight unparseable.
        val normalized = normalizeDecimalSeparator(trimmed)
        val parsed = normalized.toDoubleOrNull() ?: return FieldResult.Invalid(FieldError.NOT_A_NUMBER)
        return if (range.contains(parsed)) {
            FieldResult.Value(parsed)
        } else {
            FieldResult.Invalid(FieldError.OUT_OF_RANGE)
        }
    }

    /**
     * Optional decimal field: blank yields `null` rather than a default, so an
     * untouched distance field is omitted from the written record.
     */
    fun optionalDecimal(text: String, range: NumberRange): FieldResult<Double?> {
        if (text.isBlank()) return FieldResult.Value(null)
        return when (val r = decimal(text, range)) {
            is FieldResult.Value -> FieldResult.Value(r.value)
            is FieldResult.Invalid -> r
        }
    }

    /**
     * Replaces the locale's decimal separator with `.` so `String.toDoubleOrNull`
     * succeeds regardless of device locale.
     */
    private fun normalizeDecimalSeparator(text: String): String {
        val localeSeparator = java.text.DecimalFormatSymbols.getInstance().decimalSeparator
        return if (localeSeparator != '.') text.replace(localeSeparator, '.') else text
    }

    /**
     * Sleep must wake after going to bed. Because bedtime is usually on the
     * previous evening, a wake time earlier than the bed time is interpreted as
     * the next morning — but only if that still yields a plausible session.
     */
    fun sleepSessionMinutes(
        bedHour: Int,
        bedMinute: Int,
        wakeHour: Int,
        wakeMinute: Int
    ): Int? {
        val bedMinutes = bedHour * 60 + bedMinute
        val wakeMinutes = wakeHour * 60 + wakeMinute
        val overnight = wakeMinutes - bedMinutes
        val duration = if (overnight > 0) overnight else overnight + 24 * 60
        // A "session" longer than 20 hours is a mistyped time, not a nap.
        return if (duration in 1..(20 * 60)) duration else null
    }
}
