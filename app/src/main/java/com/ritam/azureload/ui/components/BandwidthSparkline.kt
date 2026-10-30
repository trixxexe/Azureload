package com.ritam.azureload.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.ritam.azureload.feature.dashboard.formatSpeed

/**
 * Modern Jetpack Compose Sparkline / Speed Graph with gradient shading,
 * glowing current sample point, and optional peak/average speed markers.
 */
@Composable
fun BandwidthSparkline(
    history: List<Long>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.primary,
    showGridAndLabels: Boolean = false,
    currentSpeed: Long = history.lastOrNull() ?: 0L
) {
    val nonZeroHistory = remember(history) {
        if (history.isEmpty()) listOf(0L) else history
    }
    val maxSpeed = remember(nonZeroHistory) {
        (nonZeroHistory.maxOrNull() ?: 1L).coerceAtLeast(1024L)
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width <= 0f || height <= 0f) return@Canvas

            // Optional subtle dashed horizontal grid lines for full view
            if (showGridAndLabels) {
                val gridColor = lineColor.copy(alpha = 0.12f)
                val halfY = height * 0.5f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, halfY),
                    end = Offset(width, halfY),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, 0f),
                    end = Offset(width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }

            if (nonZeroHistory.size < 2) {
                // Flat zero baseline
                val y = height - 2.dp.toPx()
                drawLine(
                    color = lineColor.copy(alpha = 0.4f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                return@Canvas
            }

            val stepX = width / (nonZeroHistory.size - 1).toFloat()
            val points = nonZeroHistory.mapIndexed { index, value ->
                val ratio = (value.toFloat() / maxSpeed.toFloat()).coerceIn(0f, 1f)
                // Leave a little margin at top and bottom
                val y = height - (ratio * (height - 8.dp.toPx())) - 4.dp.toPx()
                Offset(index * stepX, y)
            }

            // Path for gradient fill under the line
            val fillPath = Path().apply {
                moveTo(points.first().x, height)
                lineTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val p0 = points[i - 1]
                    val p1 = points[i]
                    val cx = (p0.x + p1.x) / 2f
                    cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                }
                lineTo(points.last().x, height)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        fillColor.copy(alpha = 0.35f),
                        fillColor.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = height
                )
            )

            // Path for stroke line
            val strokePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val p0 = points[i - 1]
                    val p1 = points[i]
                    val cx = (p0.x + p1.x) / 2f
                    cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                }
            }

            drawPath(
                path = strokePath,
                color = lineColor,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Glowing dot on latest point
            val lastPoint = points.last()
            drawCircle(
                color = lineColor.copy(alpha = 0.25f),
                radius = 6.dp.toPx(),
                center = lastPoint
            )
            drawCircle(
                color = lineColor,
                radius = 3.dp.toPx(),
                center = lastPoint
            )
        }

        if (showGridAndLabels) {
            // Peak label in top-left
            Text(
                text = "Peak: ${formatSpeed(maxSpeed)}",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = lineColor.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 4.dp, top = 2.dp)
            )

            // Current speed in bottom-right
            Text(
                text = formatSpeed(currentSpeed),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = lineColor,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 2.dp)
            )
        }
    }
}
