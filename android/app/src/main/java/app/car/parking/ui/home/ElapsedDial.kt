package app.car.parking.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.car.parking.ui.theme.ElapsedTone
import app.car.parking.ui.theme.LocalCarTokens
import kotlin.math.cos
import kotlin.math.sin

private const val START_DEG = 135f
private const val SWEEP_DEG = 270f
private const val FULL_MS = ElapsedTone.THREE_HOURS_MS

/**
 * UHD 테마의 경과 시간 계기판. 0~3시간을 270° 눈금으로 보여준다(15분 눈금, 1시간 큰 눈금).
 * 바늘·진행 호는 경과 시간 색 규칙(2시간 초록, 3시간 로즈)을 따른다. 3시간 이후에는 끝에 머문다.
 * 경과 시간은 옆 글자가 이미 읽어주므로 접근성 트리에서는 숨긴다.
 */
@Composable
fun ElapsedDial(elapsedMs: Long, modifier: Modifier = Modifier, size: Dp = 68.dp) {
    val t = LocalCarTokens.current
    val progress = (elapsedMs.coerceAtLeast(0L).toFloat() / FULL_MS).coerceIn(0f, 1f)
    val tone = ElapsedTone.color(elapsedMs, t, normal = t.primary)
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val stroke = 2.dp.toPx()
        val radius = this.size.minDimension / 2f - stroke * 2
        val c = center
        val arcTopLeft = Offset(c.x - radius, c.y - radius)
        val arcSize = Size(radius * 2, radius * 2)

        // 바탕 호
        drawArc(t.border, START_DEG, SWEEP_DEG, false, arcTopLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        // 진행 호
        if (progress > 0f) {
            drawArc(tone, START_DEG, SWEEP_DEG * progress, false, arcTopLeft, arcSize, style = Stroke(stroke * 1.4f, cap = StrokeCap.Round))
        }
        // 눈금: 15분마다, 1시간마다 길고 밝게
        for (i in 0..12) {
            val major = i % 4 == 0
            val rad = Math.toRadians((START_DEG + SWEEP_DEG * i / 12f).toDouble())
            val outer = radius - stroke * 2
            val inner = outer - (if (major) 6.dp else 3.dp).toPx()
            drawLine(
                color = if (major) t.textSecondary else t.inactive,
                start = Offset(c.x + inner * cos(rad).toFloat(), c.y + inner * sin(rad).toFloat()),
                end = Offset(c.x + outer * cos(rad).toFloat(), c.y + outer * sin(rad).toFloat()),
                strokeWidth = if (major) 1.5.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        // 바늘
        val needle = Math.toRadians((START_DEG + SWEEP_DEG * progress).toDouble())
        val length = radius - stroke * 2 - 8.dp.toPx()
        drawLine(
            color = tone,
            start = c,
            end = Offset(c.x + length * cos(needle).toFloat(), c.y + length * sin(needle).toFloat()),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(tone, radius = 3.dp.toPx(), center = c)
        drawCircle(t.white, radius = 1.2.dp.toPx(), center = c)
    }
}
