package com.healthcompanion.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthcompanion.app.model.GeoPoint
import com.healthcompanion.app.ui.theme.*

@Composable
fun ExerciseRouteMap(
    route: List<GeoPoint>,
    modifier: Modifier = Modifier
) {
    val healthColors = LocalHealthColors.current
    val routeColor = healthColors.accentTeal
    val markerHaloColor = healthColors.cardBackground
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(MaterialTheme.shapes.large)
            .background(healthColors.cardBackground)
    ) {
        if (route.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No GPS route recorded for this workout", color = healthColors.textMuted, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                val minLat = route.minOf { it.latitude }
                val maxLat = route.maxOf { it.latitude }
                val minLng = route.minOf { it.longitude }
                val maxLng = route.maxOf { it.longitude }

                val avgLat = (minLat + maxLat) / 2.0
                val cosLat = Math.cos(Math.toRadians(avgLat)).coerceAtLeast(0.1)

                val deltaLat = (maxLat - minLat).coerceAtLeast(0.0001)
                val deltaLngEquiv = ((maxLng - minLng) * cosLat).coerceAtLeast(0.0001)

                // Preserve true geographic aspect ratio
                val availableWidth = size.width
                val availableHeight = size.height

                val scaleX = availableWidth / deltaLngEquiv.toFloat()
                val scaleY = availableHeight / deltaLat.toFloat()
                val uniformScale = minOf(scaleX, scaleY) * 0.90f

                val drawnWidth = deltaLngEquiv.toFloat() * uniformScale
                val drawnHeight = deltaLat.toFloat() * uniformScale

                val offsetX = (availableWidth - drawnWidth) / 2f
                val offsetY = (availableHeight - drawnHeight) / 2f

                fun geoToOffset(point: GeoPoint): Offset {
                    val xRel = ((point.longitude - minLng) * cosLat).toFloat() * uniformScale
                    val yRel = (maxLat - point.latitude).toFloat() * uniformScale
                    return Offset(offsetX + xRel, offsetY + yRel)
                }

                val offsets = route.map { geoToOffset(it) }
                val routePath = Path()

                offsets.forEachIndexed { index, pt ->
                    if (index == 0) {
                        routePath.moveTo(pt.x, pt.y)
                    } else {
                        val prev = offsets[index - 1]
                        val midX = (prev.x + pt.x) / 2f
                        val midY = (prev.y + pt.y) / 2f
                        routePath.quadraticTo(prev.x, prev.y, midX, midY)
                    }
                }
                if (offsets.isNotEmpty()) {
                    routePath.lineTo(offsets.last().x, offsets.last().y)
                }

                // Draw clean route path in cyan matching media_1790630580391.png
                drawPath(
                    path = routePath,
                    color = routeColor,
                    style = Stroke(
                        width = 3.8.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Start marker
                val startPt = offsets.first()
                drawCircle(color = markerHaloColor, radius = 7.dp.toPx(), center = startPt)
                drawCircle(color = healthColors.statusGood, radius = 5.dp.toPx(), center = startPt)

                // End marker
                val endPt = offsets.last()
                drawCircle(color = markerHaloColor, radius = 7.dp.toPx(), center = endPt)
                drawCircle(color = healthColors.accentPurple, radius = 5.dp.toPx(), center = endPt)
            }
        }
    }
}
