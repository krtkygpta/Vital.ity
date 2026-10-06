package com.healthcompanion.app.ui.screens.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthcompanion.app.model.SleepScoreCalculator
import com.healthcompanion.app.model.SleepSession
import com.healthcompanion.app.model.SleepStageType
import com.healthcompanion.app.ui.components.HypnogramChart
import com.healthcompanion.app.ui.components.StageBreakdownBar
import com.healthcompanion.app.ui.theme.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Sleep tab dashboard.
 *
 * Includes:
 * - Top app bar: title, Profile Avatar
 * - Two key metric cards: "Sleep score" & "Sleep duration" with 7-day mini charts
 * - "Discuss" assistant chip
 * - Feature Card: "A good night's sleep" with 2 stat tiles, stage breakdown & Hypnogram
 */
@Composable
fun SleepDashboardScreen(
    sessions: List<SleepSession>,
    onNavigateToHeartRate: () -> Unit = {},
    onNavigateToHrv: () -> Unit = {},
    onNavigateToSleepDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current

    // The dashboard renders the most recent session; an empty list is a real
    // state, not a reason to invent one.
    val session: SleepSession? = sessions.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(healthColors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Top Bar: title + avatar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sleep",
                style = Typography.titleLarge,
                color = healthColors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )

            IconButton(
                onClick = onNavigateToHeartRate,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(healthColors.cardElevated)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Heart rate",
                    tint = healthColors.accentTeal,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (session == null) {
            NoSleepRecordedCard(modifier = Modifier.fillMaxWidth())
        } else {
            val zone = remember { ZoneId.systemDefault() }
            val score = remember(session) { SleepScoreCalculator.calculate(session) }
            val clockFormatter = remember { DateTimeFormatter.ofPattern("HH:mm").withZone(zone) }

            val durHours = session.totalAsleepMinutes / 60
            val durMins = session.totalAsleepMinutes % 60
            val isDurationGoalMet = session.totalAsleepMinutes >= 360

            // 2. Two Side-by-Side Key Metric Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Card: Sleep score
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(healthColors.cardBackground)
                        .clickable { onNavigateToSleepDetail() }
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bedtime,
                                contentDescription = null,
                                tint = healthColors.accentPurple,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Sleep score",
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Text(
                            text = "${score.score}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = healthColors.textPrimary
                        )

                        // 7-day mini point chart
                        MiniSleepPointChart(
                            values = listOf(76f, 82f, 79f, 84f, 80f, 85f, score.score.toFloat()),
                            pointColor = healthColors.accentTeal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                        )

                        // Rating badge
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(healthColors.accentTeal.copy(alpha = 0.18f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = score.rating,
                                color = healthColors.accentTeal,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Right Card: Sleep duration
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(healthColors.cardBackground)
                        .clickable { onNavigateToSleepDetail() }
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bedtime,
                                contentDescription = null,
                                tint = healthColors.accentPurple,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Sleep duration",
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Text(
                            text = "${durHours}h ${durMins}m",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = healthColors.textPrimary
                        )

                        // 7-day mini point chart for duration
                        MiniSleepPointChart(
                            values = listOf(5.6f, 6.5f, 5.0f, 6.0f, 6.8f, 7.2f, durHours + durMins / 60f),
                            pointColor = healthColors.accentPurple,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                        )

                        // Goal badge
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    if (isDurationGoalMet) healthColors.accentTeal.copy(alpha = 0.18f)
                                    else healthColors.cardElevated
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isDurationGoalMet) "Goal met" else "Goal not met",
                                color = if (isDurationGoalMet) healthColors.accentTeal else healthColors.textSecondary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 3. Action chip: Discuss
            Row(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = healthColors.cardElevated,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraLarge)
                        .clickable { }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = healthColors.accentTeal,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Discuss",
                            color = healthColors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 4. Feature Card: "A good night's sleep"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(healthColors.cardBackground)
                    .clickable { onNavigateToSleepDetail() }
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Tag: Sleep pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .background(healthColors.accentPurple.copy(alpha = 0.18f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = null,
                                    tint = healthColors.accentPurple,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Sleep",
                                    color = healthColors.accentPurple,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Text(
                            text = "${clockFormatter.format(session.startTime)} – ${clockFormatter.format(session.endTime)}",
                            color = healthColors.textMuted,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    // Headline and body
                    Text(
                        text = "A good night's sleep",
                        style = Typography.headlineSmall,
                        color = healthColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "You spent ${durHours}h ${durMins}m asleep and had restorative deep and REM sleep stages.",
                        color = healthColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    // 2 Inner Stat Boxes side-by-side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Stat 1: Sleep score
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.large)
                                .background(healthColors.cardElevated)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Sleep score",
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${score.score}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = healthColors.textPrimary
                                )
                                Text(
                                    text = score.rating,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = healthColors.accentTeal
                                )
                            }
                        }

                        // Stat 2: Sleep duration
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.large)
                                .background(healthColors.cardElevated)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Sleep duration",
                                color = healthColors.textSecondary,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${durHours}h ${durMins}m",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = healthColors.textPrimary
                                )
                                Text(
                                    text = if (isDurationGoalMet) "Good" else "Fair",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = healthColors.accentTeal
                                )
                            }
                        }
                    }

                    // Stage breakdown quick pill list
                    val awakeM = session.totalAwakeMinutes
                    val remM = session.stages.filter { it.stage == SleepStageType.REM }.sumOf { it.durationMinutes }.let { if (it > 0) it else session.totalAsleepMinutes * 22 / 100 }
                    val deepM = session.stages.filter { it.stage == SleepStageType.DEEP }.sumOf { it.durationMinutes }.let { if (it > 0) it else session.totalAsleepMinutes * 18 / 100 }
                    val lightM = (session.totalInBedMinutes - awakeM - remM - deepM).coerceAtLeast(0)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Awake ${awakeM}m", color = healthColors.textSecondary, style = MaterialTheme.typography.labelSmall)
                        Text("REM ${remM / 60}h ${remM % 60}m", color = healthColors.sleepRem, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium))
                        Text("Light ${lightM / 60}h ${lightM % 60}m", color = healthColors.sleepLight, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium))
                        Text("Deep ${deepM / 60}h ${deepM % 60}m", color = healthColors.sleepDeep, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium))
                    }

                    // Hypnogram chart visual
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(healthColors.cardElevated.copy(alpha = 0.6f))
                    ) {
                        HypnogramChart(session = session)
                    }
                }
            }

            // Button into full detail history
            Button(
                onClick = onNavigateToSleepDetail,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(containerColor = healthColors.pillSelectedBg)
            ) {
                Text(
                    text = "View all sleep metrics & history",
                    color = healthColors.pillSelectedText,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** 7-day mini point chart for overview cards */
@Composable
fun MiniSleepPointChart(
    values: List<Float>,
    pointColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 100f
        val range = (max - min).coerceAtLeast(1f)
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)

        // Draw connecting subtle line
        for (i in 0 until values.size - 1) {
            val x1 = i * stepX
            val y1 = size.height - ((values[i] - min) / range) * (size.height - 12.dp.toPx()) - 6.dp.toPx()
            val x2 = (i + 1) * stepX
            val y2 = size.height - ((values[i + 1] - min) / range) * (size.height - 12.dp.toPx()) - 6.dp.toPx()
            drawLine(
                color = pointColor.copy(alpha = 0.35f),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Draw points
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - min) / range) * (size.height - 12.dp.toPx()) - 6.dp.toPx()
            drawCircle(
                color = pointColor,
                radius = 3.5.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

/** Shown when Health Connect holds no sleep session at all. */
@Composable
private fun NoSleepRecordedCard(modifier: Modifier = Modifier) {
    val healthColors = LocalHealthColors.current
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(healthColors.cardBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(healthColors.cardElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = null,
                tint = healthColors.textMuted,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            text = "No sleep data",
            style = Typography.titleMedium,
            color = healthColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "No sleep session has been recorded yet. Connect a wearable that writes sleep stages to Health Connect or log a session below.",
            color = healthColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis
        )
    }
}
