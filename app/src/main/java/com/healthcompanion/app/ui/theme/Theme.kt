package com.healthcompanion.app.ui.theme

import android.app.Activity
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

val LocalThemeMode = compositionLocalOf { mutableStateOf(ThemeMode.SYSTEM) }

// Material 3 Expressive Easing Curves
val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
val EmphasizedStandardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
val ExpressiveSpringSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)

/**
 * Material 3 Expressive motion scheme.
 */
object ExpressiveMotion {
    const val DURATION_SHORT = 200
    const val DURATION_MEDIUM = 350
    const val DURATION_LONG = 500
    const val DURATION_EXTRA_LONG = 700

    fun <T> emphasizedDecelerateSpec(): FiniteAnimationSpec<T> = tween(
        durationMillis = DURATION_MEDIUM,
        easing = EmphasizedDecelerateEasing
    )

    fun <T> emphasizedAccelerateSpec(): FiniteAnimationSpec<T> = tween(
        durationMillis = DURATION_SHORT,
        easing = EmphasizedAccelerateEasing
    )

    fun <T> spatialSpring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.8f,
        stiffness = 380f
    )

    fun <T> bouncySpring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium
    )
}

/**
 * Semantic health colors that adapt between Light and Dark mode.
 */
data class HealthColors(
    val background: Color,
    val surface: Color,
    val cardBackground: Color,
    val cardElevated: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val pillSelectedBg: Color,
    val pillSelectedText: Color,
    val pillUnselectedBg: Color,
    val pillUnselectedText: Color,
    val accentTeal: Color,
    val onAccentTeal: Color,
    val accentBlue: Color,
    val onAccentBlue: Color,
    val accentPurple: Color,
    val onAccentPurple: Color,
    val accentOrange: Color,
    val onAccentOrange: Color,
    // Sleep Stage Colors
    val sleepAwake: Color,
    val sleepRem: Color,
    val sleepLight: Color,
    val sleepDeep: Color,
    // Heart Rate Zone Colors
    val heartPeak: Color,
    val heartVigorous: Color,
    val heartModerate: Color,
    val heartLight: Color,
    val heartResting: Color,
    // Status / Benchmark Colors
    val statusGood: Color,
    val statusWarn: Color,
    val statusBad: Color,
    val isDark: Boolean
)

val DarkHealthColors = HealthColors(
    background = BackgroundDark,
    surface = SurfaceDark,
    cardBackground = CardBackgroundDark,
    cardElevated = CardBackgroundElevatedDark,
    cardBorder = CardBorderDark,
    textPrimary = TextPrimaryDark,
    textSecondary = TextSecondaryDark,
    textMuted = TextMutedDark,
    pillSelectedBg = PillSelectedBackgroundDark,
    pillSelectedText = PillSelectedTextDark,
    pillUnselectedBg = PillUnselectedBackgroundDark,
    pillUnselectedText = PillUnselectedTextDark,
    accentTeal = AccentTealDark,
    onAccentTeal = OnAccentTeal,
    accentBlue = AccentBlueDark,
    onAccentBlue = OnAccentBlue,
    accentPurple = AccentPurpleDark,
    onAccentPurple = OnAccentPurple,
    accentOrange = AccentOrangeDark,
    onAccentOrange = OnAccentOrange,
    sleepAwake = SleepAwakeColor,
    sleepRem = SleepRemColor,
    sleepLight = SleepLightColor,
    sleepDeep = SleepDeepColor,
    heartPeak = HrPeakColor,
    heartVigorous = HrVigorousColor,
    heartModerate = HrModerateColor,
    heartLight = HrLightColor,
    heartResting = HrRestingLineColor,
    statusGood = StatusGoodColor,
    statusWarn = StatusWarnColor,
    statusBad = StatusBadColor,
    isDark = true
)

val LightHealthColors = HealthColors(
    background = BackgroundLight,
    surface = SurfaceLight,
    cardBackground = CardBackgroundLight,
    cardElevated = CardBackgroundElevatedLight,
    cardBorder = CardBorderLight,
    textPrimary = TextPrimaryLight,
    textSecondary = TextSecondaryLight,
    textMuted = TextMutedLight,
    pillSelectedBg = PillSelectedBackgroundLight,
    pillSelectedText = PillSelectedTextLight,
    pillUnselectedBg = PillUnselectedBackgroundLight,
    pillUnselectedText = PillUnselectedTextLight,
    accentTeal = AccentTealLight,
    onAccentTeal = Color.White,
    accentBlue = AccentBlueLight,
    onAccentBlue = Color.White,
    accentPurple = AccentPurpleLight,
    onAccentPurple = Color.White,
    accentOrange = AccentOrangeLight,
    onAccentOrange = Color.White,
    sleepAwake = SleepAwakeColor,
    sleepRem = Color(0xFF0288D1),
    sleepLight = Color(0xFF1976D2),
    sleepDeep = Color(0xFF512DA8),
    heartPeak = Color(0xFFD32F2F),
    heartVigorous = Color(0xFFF57C00),
    heartModerate = Color(0xFF388E3C),
    heartLight = Color(0xFF0097A7),
    heartResting = Color(0xFF0288D1),
    statusGood = Color(0xFF388E3C),
    statusWarn = Color(0xFFF57C00),
    statusBad = Color(0xFFD32F2F),
    isDark = false
)

val LocalHealthColors = staticCompositionLocalOf { DarkHealthColors }

/**
 * Material 3 Expressive shapes: 4 / 8 / 12 / 16 / 24 dp (+ 32 dp xxl).
 */
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

val ShapeXxl = RoundedCornerShape(32.dp)

val LocalExpressiveShapes = staticCompositionLocalOf { ExpressiveShapes }

private val ExpressiveDarkColorScheme = darkColorScheme(
    primary = AccentBlueDark,
    onPrimary = Color(0xFF003547),
    primaryContainer = CardBackgroundDark,
    onPrimaryContainer = Color(0xFFBDE9FF),
    secondary = AccentTealDark,
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF0E3836),
    onSecondaryContainer = Color(0xFF73F8E4),
    tertiary = AccentPurpleDark,
    onTertiary = Color(0xFF38006B),
    tertiaryContainer = Color(0xFF2E1C4D),
    onTertiaryContainer = Color(0xFFEADBFF),
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = CardBackgroundDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = CardBackgroundDark,
    surfaceContainerHigh = CardBackgroundElevatedDark,
    surfaceContainerHighest = Color(0xFF2A2C32),
    outline = TextMutedDark,
    outlineVariant = CardBorderDark,
    error = StatusBadColor,
    onError = Color(0xFF600004)
)

private val ExpressiveLightColorScheme = lightColorScheme(
    primary = AccentBlueLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC0E8FF),
    onPrimaryContainer = Color(0xFF001F2B),
    secondary = AccentTealLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF73F8E4),
    onSecondaryContainer = Color(0xFF00201D),
    tertiary = AccentPurpleLight,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEADBFF),
    onTertiaryContainer = Color(0xFF23005B),
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = CardBackgroundElevatedLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = CardBackgroundElevatedLight,
    surfaceContainerHighest = Color(0xFFE1E3E8),
    outline = TextMutedLight,
    outlineVariant = CardBorderLight,
    error = Color(0xFFD32F2F),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun HealthCompanionTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val colorScheme = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> ExpressiveDarkColorScheme
        else -> ExpressiveLightColorScheme
    }
    val healthColors = if (isDark) {
        if (dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            DarkHealthColors.copy(
                background = colorScheme.surface,
                surface = colorScheme.surface,
                cardBackground = colorScheme.surfaceContainer,
                cardElevated = colorScheme.surfaceContainerHigh,
                cardBorder = colorScheme.outlineVariant,
                textPrimary = colorScheme.onSurface,
                textSecondary = colorScheme.onSurfaceVariant,
                textMuted = colorScheme.outline,
                pillSelectedBg = colorScheme.primary,
                pillSelectedText = colorScheme.onPrimary,
                pillUnselectedBg = colorScheme.surfaceContainerHighest,
                pillUnselectedText = colorScheme.onSurfaceVariant,
                accentTeal = colorScheme.primary,
                accentBlue = colorScheme.secondary,
                accentPurple = colorScheme.tertiary,
                accentOrange = colorScheme.tertiary
            )
        } else DarkHealthColors
    } else {
        if (dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            LightHealthColors.copy(
                background = colorScheme.surface,
                surface = colorScheme.surface,
                cardBackground = colorScheme.surfaceContainer,
                cardElevated = colorScheme.surfaceContainerHigh,
                cardBorder = colorScheme.outlineVariant,
                textPrimary = colorScheme.onSurface,
                textSecondary = colorScheme.onSurfaceVariant,
                textMuted = colorScheme.outline,
                pillSelectedBg = colorScheme.primary,
                pillSelectedText = colorScheme.onPrimary,
                pillUnselectedBg = colorScheme.surfaceContainerHighest,
                pillUnselectedText = colorScheme.onSurfaceVariant,
                accentTeal = colorScheme.primary,
                accentBlue = colorScheme.secondary,
                accentPurple = colorScheme.tertiary,
                accentOrange = colorScheme.tertiary
            )
        } else LightHealthColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(
        LocalHealthColors provides healthColors,
        LocalExpressiveShapes provides ExpressiveShapes,
        LocalThemeMode provides remember { mutableStateOf(themeMode) }
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = ExpressiveShapes,
            content = content
        )
    }
}
