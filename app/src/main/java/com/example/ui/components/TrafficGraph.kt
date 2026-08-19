package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
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
import kotlin.math.sin

@Composable
fun TrafficGraph(
    history: List<TrafficSample>,
    modifier: Modifier = Modifier,
    maxHistoryPoints: Int = 35
) {
    val infiniteTransition = rememberInfiniteTransition(label = "traffic_wave")
    
    // Continuous wave motion phase (ensures the waveform is always moving and alive)
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
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

            val midY = height / 2f

            // Baseline middle grid line
            drawLine(
                color = NaturalBorder.copy(alpha = 0.8f),
                start = Offset(0f, midY),
                end = Offset(width, midY),
                strokeWidth = 1.dp.toPx()
            )

            // Top and bottom guide lines
            drawLine(
                color = NaturalBorder.copy(alpha = 0.4f),
                start = Offset(0f, height * 0.18f),
                end = Offset(width, height * 0.18f),
                strokeWidth = 0.8.dp.toPx()
            )
            drawLine(
                color = NaturalBorder.copy(alpha = 0.4f),
                start = Offset(0f, height * 0.82f),
                end = Offset(width, height * 0.82f),
                strokeWidth = 0.8.dp.toPx()
            )

            val effectiveHistory = if (history.isEmpty()) {
                val now = System.currentTimeMillis()
                List(maxHistoryPoints) { i -> TrafficSample(now - (maxHistoryPoints - i) * 800L, 0L, 0L) }
            } else {
                history
            }

            // Calculate peak values for scaling
            val maxUp = effectiveHistory.maxOfOrNull { it.upBps }?.coerceAtLeast(1024L) ?: 1024L
            val maxDown = effectiveHistory.maxOfOrNull { it.downBps }?.coerceAtLeast(1024L) ?: 1024L
            val peak = Math.max(maxUp, maxDown).toFloat()
            val hasActiveTraffic = effectiveHistory.any { it.upBps > 0 || it.downBps > 0 }

            val totalPoints = effectiveHistory.size.coerceAtLeast(2)
            val stepX = width / (totalPoints - 1).coerceAtLeast(1)

            // 1. Draw Upload Curve (Sent - Natural Warm Orange)
            val upPath = Path()
            val upFillPath = Path()

            effectiveHistory.forEachIndexed { index, sample ->
                val x = index * stepX
                val upRatio = (sample.upBps.toFloat() / peak).coerceIn(0f, 1f)
                
                // Subtle moving wave ripple when active or idle
                val idleRipple = if (!hasActiveTraffic) {
                    (sin(wavePhase + index * 0.35f) * 2.5f).toFloat()
                } else {
                    (sin(wavePhase + index * 0.5f) * (upRatio * 4f)).toFloat()
                }
                
                val y = (midY - (upRatio * (midY - 10.dp.toPx())) - idleRipple).coerceIn(4.dp.toPx(), midY)

                if (index == 0) {
                    upPath.moveTo(x, y)
                    upFillPath.moveTo(0f, midY)
                    upFillPath.lineTo(x, y)
                } else {
                    upPath.lineTo(x, y)
                    upFillPath.lineTo(x, y)
                }
            }

            upFillPath.lineTo(width, midY)
            upFillPath.close()

            // Draw Upload Fill Gradient
            drawPath(
                path = upFillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(NaturalOrangeUpload.copy(alpha = 0.28f), Color.Transparent),
                    startY = 0f,
                    endY = midY
                )
            )

            // Draw Upload Stroke
            drawPath(
                path = upPath,
                color = NaturalOrangeUpload,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 2. Draw Download Curve (Received - Natural Green)
            val downPath = Path()
            val downFillPath = Path()

            effectiveHistory.forEachIndexed { index, sample ->
                val x = index * stepX
                val downRatio = (sample.downBps.toFloat() / peak).coerceIn(0f, 1f)
                
                // Subtle moving wave ripple
                val idleRipple = if (!hasActiveTraffic) {
                    (sin(wavePhase + index * 0.35f + Math.PI.toFloat()) * 2.5f).toFloat()
                } else {
                    (sin(wavePhase + index * 0.5f + Math.PI.toFloat()) * (downRatio * 4f)).toFloat()
                }

                val y = (midY + (downRatio * (height - midY - 10.dp.toPx())) + idleRipple).coerceIn(midY, height - 4.dp.toPx())

                if (index == 0) {
                    downPath.moveTo(x, y)
                    downFillPath.moveTo(0f, midY)
                    downFillPath.lineTo(x, y)
                } else {
                    downPath.lineTo(x, y)
                    downFillPath.lineTo(x, y)
                }
            }

            downFillPath.lineTo(width, midY)
            downFillPath.close()

            // Draw Download Fill Gradient
            drawPath(
                path = downFillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, NaturalGreenSuccess.copy(alpha = 0.28f)),
                    startY = midY,
                    endY = height
                )
            )

            // Draw Download Stroke
            drawPath(
                path = downPath,
                color = NaturalGreenSuccess,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Pulsing live indicator dots on the leading edge (right side)
            val lastSample = effectiveHistory.lastOrNull()
            if (lastSample != null) {
                val upRatio = (lastSample.upBps.toFloat() / peak).coerceIn(0f, 1f)
                val currentUpY = (midY - (upRatio * (midY - 10.dp.toPx()))).coerceIn(4.dp.toPx(), midY)
                
                drawCircle(
                    color = NaturalOrangeUpload.copy(alpha = pulseAlpha),
                    radius = 4.5.dp.toPx(),
                    center = Offset(width, currentUpY)
                )

                val downRatio = (lastSample.downBps.toFloat() / peak).coerceIn(0f, 1f)
                val currentDownY = (midY + (downRatio * (height - midY - 10.dp.toPx()))).coerceIn(midY, height - 4.dp.toPx())
                
                drawCircle(
                    color = NaturalGreenSuccess.copy(alpha = pulseAlpha),
                    radius = 4.5.dp.toPx(),
                    center = Offset(width, currentDownY)
                )
            }
        }
    }
}
