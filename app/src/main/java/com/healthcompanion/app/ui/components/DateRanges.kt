package com.healthcompanion.app.ui.components

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Sunday-anchored start of the week containing [date]. */
fun weekStartSun(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

/** Sunday-anchored end of the week containing [date] (Saturday, inclusive). */
fun weekEndSun(date: LocalDate): LocalDate = weekStartSun(date).plusDays(6)

/** Monday-anchored start of the week containing [date]. */
fun weekStartMon(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Monday-anchored end of the week containing [date] (Sunday, inclusive). */
fun weekEndMon(date: LocalDate): LocalDate = weekStartMon(date).plusDays(6)

/** Start of the calendar quarter containing [date]. */
fun quarterStart(date: LocalDate): LocalDate {
    val month = ((date.monthValue - 1) / 3) * 3 + 1
    return LocalDate.of(date.year, month, 1)
}

/** End of the calendar quarter containing [date]. */
fun quarterEnd(date: LocalDate): LocalDate =
    quarterStart(date).plusMonths(3).minusDays(1)

/** Start date of the period containing [date] according to [range]. */
fun periodStart(date: LocalDate, range: TimeRange): LocalDate = when (range) {
    TimeRange.DAY -> date
    TimeRange.WEEK -> weekStartSun(date)
    TimeRange.MONTH -> date.withDayOfMonth(1)
    TimeRange.THREE_MONTHS -> quarterStart(date)
    TimeRange.YEAR -> LocalDate.of(date.year, 1, 1)
}

/** End date (inclusive) of the period containing [date] according to [range]. */
fun periodEnd(date: LocalDate, range: TimeRange): LocalDate = when (range) {
    TimeRange.DAY -> date
    TimeRange.WEEK -> weekEndSun(date)
    TimeRange.MONTH -> date.withDayOfMonth(date.lengthOfMonth())
    TimeRange.THREE_MONTHS -> quarterEnd(date)
    TimeRange.YEAR -> LocalDate.of(date.year, 12, 31)
}

/** Canonical bounds [start, end] for [range] containing [date]. */
fun periodBounds(date: LocalDate, range: TimeRange): Pair<LocalDate, LocalDate> =
    periodStart(date, range) to periodEnd(date, range)

/**
 * Period end clamped to [today] so future days in the current period aren't queried.
 */
fun periodEndClamped(date: LocalDate, range: TimeRange, today: LocalDate = LocalDate.now()): LocalDate {
    val end = periodEnd(date, range)
    return if (end.isAfter(today)) today else end
}

/** Total number of days spanning the period (inclusive). */
fun periodDayCount(date: LocalDate, range: TimeRange): Long {
    val (start, end) = periodBounds(date, range)
    return ChronoUnit.DAYS.between(start, end) + 1
}

/** List of all dates in the canonical period. */
fun daysInPeriod(date: LocalDate, range: TimeRange): List<LocalDate> {
    val (start, end) = periodBounds(date, range)
    val total = ChronoUnit.DAYS.between(start, end)
    return (0..total).map { start.plusDays(it) }
}

/** Advances [date] by one whole step of [range]. */
fun LocalDate.stepForward(range: TimeRange): LocalDate = when (range) {
    TimeRange.DAY -> plusDays(1)
    TimeRange.WEEK -> plusWeeks(1)
    TimeRange.MONTH -> plusMonths(1)
    TimeRange.THREE_MONTHS -> plusMonths(3)
    TimeRange.YEAR -> plusYears(1)
}

/** Steps [date] back by one whole step of [range]. */
fun LocalDate.stepBackward(range: TimeRange): LocalDate = when (range) {
    TimeRange.DAY -> minusDays(1)
    TimeRange.WEEK -> minusWeeks(1)
    TimeRange.MONTH -> minusMonths(1)
    TimeRange.THREE_MONTHS -> minusMonths(3)
    TimeRange.YEAR -> minusYears(1)
}

/**
 * True when the period containing [date] has already begun.
 */
fun isPeriodSelectable(date: LocalDate, range: TimeRange, today: LocalDate = LocalDate.now()): Boolean {
    val start = periodStart(date, range)
    return start <= today
}

/** True when stepping forward from [date] would move into a period that hasn't begun. */
fun canStepForward(date: LocalDate, range: TimeRange, today: LocalDate = LocalDate.now()): Boolean {
    val next = date.stepForward(range)
    return isPeriodSelectable(next, range, today)
}
