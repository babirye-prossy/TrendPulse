package com.trendpulse.trendpulse.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.trendpulse.trendpulse.core.model.SentimentTrendPoint

/**
 * Custom composable for rendering a sentiment trend line chart.
 *
 * Enhanced for Accessibility:
 * - Uses distinct shapes (circles for positive, squares for negative) to assist color-blind users.
 * - Provides semantic descriptions for screen readers.
 * - Uses a color-blind-safe palette.
 */
@Composable
fun SentimentLineChart(
    dataPoints: List<SentimentTrendPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF2196F3), // Safe Blue instead of pure green
    strokeWidth: Dp = 2.dp
) {
    if (dataPoints.size < 2) {
        Box(modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(
                text = "Collect more comments to see the trend",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
            )
        }
        return
    }

    // Generate accessibility summary
    val avgScore = dataPoints.map { it.score }.average()
    val trendSummary = when {
        avgScore > 0.6 -> "Overall sentiment is positive."
        avgScore < 0.4 -> "Overall sentiment is negative."
        else -> "Overall sentiment is neutral."
    }

    Canvas(
        modifier = modifier.semantics {
            contentDescription = "Sentiment trend chart over ${dataPoints.size} comments. $trendSummary"
        }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // ✅ Padding so the line doesn't touch the edges
        val paddingPx = 24.dp.toPx()
        val chartWidth = canvasWidth - paddingPx * 2
        val chartHeight = canvasHeight - paddingPx * 2

        val count = dataPoints.size

        // ✅ Map each data point to pixel coordinates
        // x: spread evenly across chart width
        // y: normalized score (0.0=bottom negative, 1.0=top positive)
        fun xOf(index: Int) = paddingPx + (index.toFloat() / (count - 1)) * chartWidth
        fun yOf(score: Float) = paddingPx + (1f - score) * chartHeight

        // --- Draw grid lines ---
        drawGridLines(paddingPx, chartWidth, chartHeight)

        // --- Build the line path ---
        val linePath = Path().apply {
            dataPoints.forEachIndexed { i, point ->
                val x = xOf(i)
                val y = yOf(point.score)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }

        // --- Build gradient fill path (line + close to bottom) ---
        val fillPath = Path().apply {
            addPath(linePath)
            // Close down to the bottom of the chart
            lineTo(xOf(count - 1), paddingPx + chartHeight)
            lineTo(xOf(0), paddingPx + chartHeight)
            close()
        }

        // ✅ Draw gradient fill below the line
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.4f),
                    lineColor.copy(alpha = 0.0f)
                ),
                startY = paddingPx,
                endY = paddingPx + chartHeight
            )
        )

        // ✅ Draw the line itself
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // ✅ Draw shapes at each data point
        dataPoints.forEachIndexed { i, point ->
            val x = xOf(i)
            val y = yOf(point.score)
            val isPositive = point.score >= 0.5f
            
            if (isPositive) {
                // Circle for positive sentiment
                drawCircle(
                    color = Color(0xFF4CAF50), // Standard Green
                    radius = 4.dp.toPx(),
                    center = Offset(x, y)
                )
            } else {
                // Square for negative sentiment (easier to distinguish for color-blind users)
                val size = 7.dp.toPx()
                drawRect(
                    color = Color(0xFFE91E63), // Pink/Red
                    topLeft = Offset(x - size/2, y - size/2),
                    size = androidx.compose.ui.geometry.Size(size, size)
                )
            }
        }

        // ✅ Draw neutral midline (0.5 score)
        val midY = yOf(0.5f)
        drawLine(
            color = Color.Gray.copy(alpha = 0.3f),
            start = Offset(paddingPx, midY),
            end = Offset(paddingPx + chartWidth, midY),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        )
    }
}

// ✅ Light horizontal grid lines at 0.25, 0.5, 0.75
private fun DrawScope.drawGridLines(
    paddingPx: Float,
    chartWidth: Float,
    chartHeight: Float
) {
    listOf(0.25f, 0.5f, 0.75f).forEach { level ->
        val y = paddingPx + (1f - level) * chartHeight
        drawLine(
            color = Color.Gray.copy(alpha = 0.15f),
            start = Offset(paddingPx, y),
            end = Offset(paddingPx + chartWidth, y)
        )
    }
}