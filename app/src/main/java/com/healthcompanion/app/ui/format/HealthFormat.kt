package com.healthcompanion.app.ui.format

import java.text.NumberFormat
import java.time.Duration
import java.util.Locale

/**
 * Every user-visible number in the app is formatted here so that locale, casing
 * and abbreviation rules stay consistent across screens.
 *
 * `String.format("%.1f", …)` is deliberately never used directly in UI code: it
 * ignores the locale unless an explicit `Locale` is passed, so the same metric
 * used to render differently on two screens. Everything below is locale-aware
 * by construction.
 */
object HealthFormat {

    private fun intFormat(): NumberFormat = NumberFormat.getIntegerInstance()

    private fun decimalFormat(digits: Int): NumberFormat =
        NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }

    /** `12,345` — grouped, locale-aware, no abbreviation. */
    fun count(value: Int): String = intFormat().format(value)

    fun count(value: Long): String = intFormat().format(value)

    /** `12.3k`, `4.5M` — compact, for hero values and axis labels that must not wrap. */
    fun compact(value: Int): String {
        val abs = kotlin.math.abs(value)
        return when {
            abs >= 1_000_000 -> trimTrailingZero(decimalFormat(1).format(value / 1_000_000.0)) + "M"
            abs >= 10_000 -> trimTrailingZero(decimalFormat(1).format(value / 1_000.0)) + "k"
            else -> intFormat().format(value)
        }
    }

    fun compact(value: Double): String {
        val abs = kotlin.math.abs(value)
        return when {
            abs >= 1_000_000 -> trimTrailingZero(decimalFormat(1).format(value / 1_000_000.0)) + "M"
            abs >= 10_000 -> trimTrailingZero(decimalFormat(1).format(value / 1_000.0)) + "k"
            else -> decimalFormat(0).format(value)
        }
    }

    /** `72.5` — fixed decimals, locale-aware decimal separator. */
    fun decimal(value: Double, digits: Int = 1): String = decimalFormat(digits).format(value)

    fun decimal(value: Float, digits: Int = 1): String = decimalFormat(digits).format(value.toDouble())

    /** Drops a trailing `.0` so whole numbers read as `5` rather than `5.0`. */
    private fun trimTrailingZero(text: String): String =
        if (text.endsWith(".0")) text.dropLast(2) else text

    /** `8h 20m`, `45m`, `—` for zero/absent durations. */
    fun duration(totalMinutes: Int?): String {
        if (totalMinutes == null || totalMinutes <= 0) return NO_VALUE
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    fun duration(from: Duration): String = duration(from.toMinutes().toInt())

    /** `00:30` for compact clock-style readings (chart axis, scrub bubbles). */
    fun clock(totalMinutes: Int): String {
        val safe = totalMinutes.coerceAtLeast(0)
        val hours = safe / 60
        val minutes = safe % 60
        return String.format(Locale.getDefault(), "%d:%02d", hours, minutes)
    }

    /** `+5%` / `-3%` / `0%` — sign is always explicit. */
    fun signedPercent(value: Int): String =
        if (value > 0) "+$value%" else if (value < 0) "$value%" else "0%"

    const val NO_VALUE = "—"
}
