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

/** ‹ — 上一周. */
@Composable
fun ChevronLeftIcon(tint: Color, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        line(tint, Offset(0.62f, 0.22f), Offset(0.34f, 0.5f))
        line(tint, Offset(0.34f, 0.5f), Offset(0.62f, 0.78f))
    }
}

/** › — 下一周. */
@Composable
fun ChevronRightIcon(tint: Color, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        line(tint, Offset(0.38f, 0.22f), Offset(0.66f, 0.5f))
        line(tint, Offset(0.66f, 0.5f), Offset(0.38f, 0.78f))
    }
}

/* ---------- 课程详情里的行图标（全部自绘，和顶部图标同一套风格） ---------- */

private fun DrawScope.roundRect(
    color: Color,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float = 0.12f,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(left * size.width, top * size.height),
        size = androidx.compose.ui.geometry.Size(
            (right - left) * size.width,
            (bottom - top) * size.height,
        ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
            radius * size.minDimension,
            radius * size.minDimension,
        ),
        style = Stroke(width = size.minDimension * StrokeWidthFraction),
    )
}

/** 日历 — 周次. */
@Composable
fun CalendarIcon(tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        roundRect(tint, 0.14f, 0.2f, 0.86f, 0.86f, 0.18f)
        line(tint, Offset(0.14f, 0.38f), Offset(0.86f, 0.38f))
        line(tint, Offset(0.32f, 0.1f), Offset(0.32f, 0.26f))
        line(tint, Offset(0.68f, 0.1f), Offset(0.68f, 0.26f))
    }
}

/** 时钟 — 节次/时间. */
@Composable
fun ClockIcon(tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val stroke = this.size.minDimension * StrokeWidthFraction
        drawCircle(
            color = tint,
            radius = this.size.minDimension / 2f - stroke / 2f - this.size.minDimension * 0.1f,
            center = Offset(this.size.width / 2f, this.size.height / 2f),
            style = Stroke(width = stroke),
        )
        line(tint, Offset(0.5f, 0.3f), Offset(0.5f, 0.52f))
        line(tint, Offset(0.5f, 0.52f), Offset(0.68f, 0.62f))
    }
}

/** 定位 — 上课地点. */
@Composable
fun LocationIcon(tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val stroke = this.size.minDimension * StrokeWidthFraction
        drawCircle(
            color = tint,
            radius = this.size.minDimension * 0.24f,
            center = Offset(this.size.width * 0.5f, this.size.height * 0.4f),
            style = Stroke(width = stroke),
        )
        line(tint, Offset(0.31f, 0.56f), Offset(0.5f, 0.9f))
        line(tint, Offset(0.69f, 0.56f), Offset(0.5f, 0.9f))
    }
}

/** 人 — 授课老师. */
@Composable
fun PersonIcon(tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val stroke = this.size.minDimension * StrokeWidthFraction
        drawCircle(
            color = tint,
            radius = this.size.minDimension * 0.16f,
            center = Offset(this.size.width * 0.5f, this.size.height * 0.3f),
            style = Stroke(width = stroke),
        )
        drawArc(
            color = tint,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(this.size.width * 0.18f, this.size.height * 0.56f),
            size = androidx.compose.ui.geometry.Size(this.size.width * 0.64f, this.size.height * 0.44f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** 便签 — 备注. */
@Composable
fun NoteIcon(tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        roundRect(tint, 0.16f, 0.14f, 0.84f, 0.86f, 0.16f)
        line(tint, Offset(0.3f, 0.38f), Offset(0.7f, 0.38f))
        line(tint, Offset(0.3f, 0.54f), Offset(0.7f, 0.54f))
        line(tint, Offset(0.3f, 0.7f), Offset(0.55f, 0.7f))
    }
}

/** 复制 — 复制课程. */
@Composable
fun CopyIcon(tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        roundRect(tint, 0.12f, 0.2f, 0.66f, 0.86f, 0.14f)
        roundRect(tint, 0.34f, 0.1f, 0.88f, 0.7f, 0.14f)
    }
}

/** 垃圾桶 — 删除课程. */
@Composable
fun TrashIcon(tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        line(tint, Offset(0.16f, 0.25f), Offset(0.84f, 0.25f))
        line(tint, Offset(0.4f, 0.12f), Offset(0.6f, 0.12f))
        line(tint, Offset(0.28f, 0.25f), Offset(0.34f, 0.88f))
        line(tint, Offset(0.72f, 0.25f), Offset(0.66f, 0.88f))
        line(tint, Offset(0.34f, 0.88f), Offset(0.66f, 0.88f))
    }
}
