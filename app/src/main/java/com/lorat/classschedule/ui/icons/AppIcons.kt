package com.lorat.classschedule.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn header icons (WakeUp style: ⊕ ↓ ↑ ⊙). Drawn with Canvas so the app needs no
 * icon dependency at all — matches the "小巧" spirit of the original.
 */
private const val StrokeWidthFraction = 0.075f

private fun DrawScope.line(color: Color, from: Offset, to: Offset) {
    drawLine(
        color = color,
        start = Offset(from.x * size.width, from.y * size.height),
        end = Offset(to.x * size.width, to.y * size.height),
        strokeWidth = size.minDimension * StrokeWidthFraction,
        cap = StrokeCap.Round,
    )
}

/** ⊕ — 新建课程. */
@Composable
fun AddCircleIcon(tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val stroke = this.size.minDimension * StrokeWidthFraction
        drawCircle(
            color = tint,
            radius = this.size.minDimension / 2f - stroke / 2f,
            style = Stroke(width = stroke),
        )
        line(tint, Offset(0.28f, 0.5f), Offset(0.72f, 0.5f))
        line(tint, Offset(0.5f, 0.28f), Offset(0.5f, 0.72f))
    }
}

/** ↓ — 导入课表. */
@Composable
fun ImportIcon(tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        line(tint, Offset(0.5f, 0.12f), Offset(0.5f, 0.62f))
        line(tint, Offset(0.26f, 0.40f), Offset(0.5f, 0.64f))
        line(tint, Offset(0.74f, 0.40f), Offset(0.5f, 0.64f))
        line(tint, Offset(0.22f, 0.86f), Offset(0.78f, 0.86f))
    }
}

/** ↑ — 导出 / 分享课表. */
@Composable
fun ExportIcon(tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        line(tint, Offset(0.5f, 0.88f), Offset(0.5f, 0.38f))
        line(tint, Offset(0.26f, 0.60f), Offset(0.5f, 0.36f))
        line(tint, Offset(0.74f, 0.60f), Offset(0.5f, 0.36f))
        line(tint, Offset(0.22f, 0.14f), Offset(0.78f, 0.14f))
    }
}

/** ⊙ — 更多. */
@Composable
fun MoreCircleIcon(tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val stroke = this.size.minDimension * StrokeWidthFraction
        drawCircle(
            color = tint,
            radius = this.size.minDimension / 2f - stroke / 2f,
            style = Stroke(width = stroke),
        )
        val r = stroke * 0.55f
        for (x in listOf(0.32f, 0.5f, 0.68f)) {
            drawCircle(color = tint, radius = r, center = Offset(x * this.size.width, 0.5f * this.size.height))
        }
    }
}

/** ▾ — 展开周次选择. */
@Composable
fun ChevronDownIcon(tint: Color, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        line(tint, Offset(0.22f, 0.38f), Offset(0.5f, 0.66f))
        line(tint, Offset(0.78f, 0.38f), Offset(0.5f, 0.66f))
    }
}
