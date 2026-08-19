package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.model.TrafficSample
import com.example.ui.theme.NaturalBorder
import com.example.ui.theme.NaturalGreenSuccess
import com.example.ui.theme.NaturalOrangeUpload

@Composable
fun TrafficGraph(
    history: List<TrafficSample>,
    modifier: Modifier = Modifier,
    maxHistoryPoints: Int = 40
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width <= 0 || height <= 0) return@Canvas

            // Baseline middle grid line
            val midY = height / 2f
            drawLine(
                color = NaturalBorder,
                start = Offset(0f, midY),
                end = Offset(width, midY),
                strokeWidth = 1.dp.toPx()
            )

            // Top and bottom guide lines
            drawLine(
                color = NaturalBorder.copy(alpha = 0.5f),
                start = Offset(0f, height * 0.15f),
                end = Offset(width, height * 0.15f),
                strokeWidth = 0.8.dp.toPx()
            )
            drawLine(
                color = NaturalBorder.copy(alpha = 0.5f),
                start = Offset(0f, height * 0.85f),
                end = Offset(width, height * 0.85f),
                strokeWidth = 0.8.dp.toPx()
            )

            if (history.isEmpty()) {
                // Flat line
                drawLine(
                    color = NaturalGreenSuccess.copy(alpha = 0.6f),
                    start = Offset(0f, midY),
                    end = Offset(width, midY),
                    strokeWidth = 2.dp.toPx()
                )
                return@Canvas
            }

            // Calculate peak values for scaling
            val maxUp = history.maxOfOrNull { it.upBps }?.coerceAtLeast(1024L) ?: 1024L
            val maxDown = history.maxOfOrNull { it.downBps }?.coerceAtLeast(1024L) ?: 1024L
            val peak = Math.max(maxUp, maxDown).toFloat()

            val stepX = width / (maxHistoryPoints - 1).coerceAtLeast(1)
            val paddingPoints = (maxHistoryPoints - history.size).coerceAtLeast(0)

            // 1. Draw Upload Curve (Sent - Natural Warm Orange)
            val upPath = Path()
            val upFillPath = Path()
            val startX = paddingPoints * stepX

            upPath.moveTo(0f, midY)
            upFillPath.moveTo(0f, midY)

            if (paddingPoints > 0) {
                upPath.lineTo(startX, midY)
                upFillPath.lineTo(startX, midY)
            }

            history.forEachIndexed { index, sample ->
                val x = (paddingPoints + index) * stepX
                val upRatio = (sample.upBps.toFloat() / peak).coerceIn(0f, 1f)
                val y = midY - (upRatio * (midY - 8.dp.toPx()))
                upPath.lineTo(x, y)
                upFillPath.lineTo(x, y)
            }

            val lastX = width
            upFillPath.lineTo(lastX, midY)
            upFillPath.close()

            // Draw Upload Fill Gradient
            drawPath(
                path = upFillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(NaturalOrangeUpload.copy(alpha = 0.25f), Color.Transparent),
                    startY = 0f,
                    endY = midY
                )
            )

            // Draw Upload Stroke
            drawPath(
                path = upPath,
                color = NaturalOrangeUpload,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 2. Draw Download Curve (Received - Natural Green)
            val downPath = Path()
            val downFillPath = Path()

            downPath.moveTo(0f, midY)
            downFillPath.moveTo(0f, midY)

            if (paddingPoints > 0) {
                downPath.lineTo(startX, midY)
                downFillPath.lineTo(startX, midY)
            }

            history.forEachIndexed { index, sample ->
                val x = (paddingPoints + index) * stepX
                val downRatio = (sample.downBps.toFloat() / peak).coerceIn(0f, 1f)
                val y = midY + (downRatio * (height - midY - 8.dp.toPx()))
                downPath.lineTo(x, y)
                downFillPath.lineTo(x, y)
            }

            downFillPath.lineTo(lastX, midY)
            downFillPath.close()

            // Draw Download Fill Gradient
            drawPath(
                path = downFillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, NaturalGreenSuccess.copy(alpha = 0.25f)),
                    startY = midY,
                    endY = height
                )
            )

            // Draw Download Stroke
            drawPath(
                path = downPath,
                color = NaturalGreenSuccess,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Pulsing live indicator dots
            val lastSample = history.lastOrNull()
            if (lastSample != null) {
                val currentUpRatio = (lastSample.upBps.toFloat() / peak).coerceIn(0f, 1f)
                val currentUpY = midY - (currentUpRatio * (midY - 8.dp.toPx()))
                drawCircle(
                    color = NaturalOrangeUpload.copy(alpha = pulseAlpha),
                    radius = 4.dp.toPx(),
                    center = Offset(width, currentUpY)
                )

                val currentDownRatio = (lastSample.downBps.toFloat() / peak).coerceIn(0f, 1f)
                val currentDownY = midY + (currentDownRatio * (height - midY - 8.dp.toPx()))
                drawCircle(
                    color = NaturalGreenSuccess.copy(alpha = pulseAlpha),
                    radius = 4.dp.toPx(),
                    center = Offset(width, currentDownY)
                )
            }
        }
    }
}
