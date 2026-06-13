package biali.fitmanager

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val ChartGreen = Color(0xFF00C853)
private val ChartBlue = Color(0xFF1E88E5)
private val GridColor = Color(0xFFE8E8E8)

@Composable
fun MiniProgressSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    isTimeBased: Boolean = false
) {
    val lineColor = if (isTimeBased) ChartBlue else ChartGreen
    Box(
        modifier = modifier
            .background(Color(0xFFF8FAF9), RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(44.dp)) {
            if (values.isEmpty()) return@Canvas
            val w = size.width
            val h = size.height
            val maxV = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
            val minV = (values.minOrNull() ?: 0f).coerceAtLeast(0f)
            val range = (maxV - minV).coerceAtLeast(maxV * 0.1f).coerceAtLeast(1f)

            if (values.size == 1) {
                val y = h * 0.35f
                drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(w / 2, y))
                return@Canvas
            }

            val xStep = w / (values.size - 1)
            val fillPath = Path()
            val linePath = Path()

            values.forEachIndexed { i, v ->
                val x = i * xStep
                val y = h - ((v - minV) / range) * h * 0.85f - h * 0.05f
                if (i == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, h)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(w, h)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.25f), lineColor.copy(alpha = 0.02f)),
                    startY = 0f,
                    endY = h
                )
            )
            drawPath(path = linePath, color = lineColor, style = Stroke(width = 2.dp.toPx()))

            values.forEachIndexed { i, v ->
                val x = i * xStep
                val y = h - ((v - minV) / range) * h * 0.85f - h * 0.05f
                drawCircle(color = lineColor, radius = 3.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

@Composable
fun ProgressStrengthChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    isTimeBased: Boolean = false
) {
    val lineColor = if (isTimeBased) ChartBlue else ChartGreen
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F7F6), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            if (values.isEmpty()) return@Canvas
            val w = size.width
            val h = size.height
            val maxV = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
            val minV = ((values.minOrNull() ?: 0f) - maxV * 0.05f).coerceAtLeast(0f)
            val range = (maxV - minV).coerceAtLeast(1f)

            repeat(4) { i ->
                val y = h * i / 3f
                drawLine(GridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }

            if (values.size == 1) {
                val y = h - ((values[0] - minV) / range) * h
                drawCircle(color = lineColor, radius = 8.dp.toPx(), center = Offset(w / 2, y))
                return@Canvas
            }

            val xStep = w / (values.size - 1)
            val fillPath = Path()
            val linePath = Path()

            values.forEachIndexed { i, v ->
                val x = i * xStep
                val y = h - ((v - minV) / range) * h
                if (i == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, h)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(w, h)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.3f), Color.Transparent),
                    startY = 0f,
                    endY = h
                )
            )
            drawPath(path = linePath, color = lineColor, style = Stroke(width = 3.dp.toPx()))

            values.forEachIndexed { i, v ->
                val x = i * xStep
                val y = h - ((v - minV) / range) * h
                drawCircle(color = Color.White, radius = 6.dp.toPx(), center = Offset(x, y))
                drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

@Composable
fun RecordCelebrationBanner(
    messages: List<String>?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (messages.isNullOrEmpty()) return

    LaunchedEffect(messages) {
        delay(3000)
        onDismiss()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(
                brush = Brush.horizontalGradient(listOf(Color(0xFF1B5E20), Color(0xFF00C853))),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = "🏆 Nowy rekord!",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        messages.forEach { msg ->
            Text(text = msg, color = Color.White.copy(alpha = 0.95f), fontSize = 14.sp)
        }
    }
}
