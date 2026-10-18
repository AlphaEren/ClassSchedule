package com.lorat.classschedule.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 空课表/空周的小插画：一张迷你课表，右下角留一个「+」空位。
 * 全部用主题色和课程色画，不引入图片资源，深浅色都能看。
 */
@Composable
fun EmptyTimetableIllustration(modifier: Modifier = Modifier, size: Dp = 168.dp) {
    // 不透明底：插画压在课表网格上时，不会被后面的格线穿过去
    val panel = MaterialTheme.colorScheme.surfaceVariant
    val line = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val primary = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(panel, cornerRadius = CornerRadius(w * 0.10f, w * 0.10f))
        val left = w * 0.10f
        val top = h * 0.10f
        val right = w * 0.90f
        val bottom = h * 0.90f
        val cellW = (right - left) / 3f
        val cellH = (bottom - top) / 3f
        for (i in 1..2) {
            drawLine(line, Offset(left + cellW * i, top), Offset(left + cellW * i, bottom), 1.5f)
            drawLine(line, Offset(left, top + cellH * i), Offset(right, top + cellH * i), 1.5f)
        }
        val blocks = listOf(
            CourseColors[5] to (0 to 0),
            CourseColors[3] to (2 to 1),
            CourseColors[1] to (0 to 2),
        )
        for ((color, position) in blocks) {
            drawRoundRect(
                color = color.copy(alpha = 0.32f),
                topLeft = Offset(left + cellW * position.first + 3f, top + cellH * position.second + 3f),
                size = Size(cellW - 6f, cellH - 6f),
                cornerRadius = CornerRadius(8f, 8f),
            )
        }
        // 右下角：虚线的「+」，表示这一天还空着、可以添加课程
        val addLeft = left + cellW * 2 + 3f
        val addTop = top + cellH * 2 + 3f
        val addSize = Size(cellW - 6f, cellH - 6f)
        drawRoundRect(
            color = primary.copy(alpha = 0.10f),
            topLeft = Offset(addLeft, addTop),
            size = addSize,
            cornerRadius = CornerRadius(8f, 8f),
        )
        drawRoundRect(
            color = primary.copy(alpha = 0.45f),
            topLeft = Offset(addLeft, addTop),
            size = addSize,
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
        )
        val cx = addLeft + addSize.width / 2f
        val cy = addTop + addSize.height / 2f
        val arm = minOf(addSize.width, addSize.height) * 0.22f
        drawLine(primary, Offset(cx - arm, cy), Offset(cx + arm, cy), 3f)
        drawLine(primary, Offset(cx, cy - arm), Offset(cx, cy + arm), 3f)
    }
}
