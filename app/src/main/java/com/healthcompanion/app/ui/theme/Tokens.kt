package com.healthcompanion.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Spacing tokens (4 dp base grid).
 */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val grid40 = 40.dp
    val grid48 = 48.dp
    val grid64 = 64.dp

    // Standard card & container paddings
    val cardPaddingStandard = 16.dp
    val cardPaddingHero = 24.dp
}

/**
 * Material 3 Elevation levels.
 */
object Elevation {
    val Level0 = 0.dp
    val Level1 = 1.dp
    val Level2 = 3.dp
    val Level3 = 6.dp
}

/**
 * Standardized Alpha tokens for state layers and charts.
 */
object Alpha {
    const val Hover = 0.08f
    const val Focus = 0.12f
    const val Pressed = 0.12f
    const val Dragged = 0.16f
    const val DisabledContainer = 0.12f
    const val DisabledContent = 0.38f
    const val Scrim = 0.32f

    // Chart-specific opacity tokens
    const val ChartFill = 0.15f
    const val ChartGrid = 0.08f
    const val MutedText = 0.60f
}

/**
 * Chart height tokens.
 */
object ChartHeight {
    val Small: Dp = 160.dp
    val Medium: Dp = 200.dp
    val Large: Dp = 240.dp
}
