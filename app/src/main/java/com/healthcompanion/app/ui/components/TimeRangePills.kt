package com.healthcompanion.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class TimeRange(val label: String, val contentDescription: String) {
    DAY("D", "Day"),
    WEEK("W", "Week"),
    MONTH("M", "Month"),
    THREE_MONTHS("3M", "3 Months"),
    YEAR("Y", "Year")
}

@Composable
fun TimeRangePills(
    selectedRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit,
    ranges: List<TimeRange> = TimeRange.entries,
    modifier: Modifier = Modifier
) {
    M3EConnectedButtonGroup(
        items = ranges,
        selectedItem = selectedRange,
        onItemSelected = onRangeSelected,
        labelProvider = { it.label },
        modifier = modifier
    )
}
