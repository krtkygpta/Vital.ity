package com.healthcompanion.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Time range selector. Delegates to the shared [M3EConnectedButtonGroup] so the
 * selected accent pill / check treatment is consistent with every other group.
 */
@Composable
fun M3ETimeRangeSelector(
    selectedRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    TimeRangePills(
        selectedRange = selectedRange,
        onRangeSelected = onRangeSelected,
        modifier = modifier
    )
}
