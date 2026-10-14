package com.lorat.classschedule.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Course palette. Deliberately soft/pastel so the light skin (tinted cell + coloured text)
 * reads the same way WakeUp's default skin does.
 */
val CourseColors = listOf(
    Color(0xFFEF6C6C),
    Color(0xFFF2A03D),
    Color(0xFF8FA31E),
    Color(0xFF57BB8A),
    Color(0xFF4FB3C7),
    Color(0xFF5B8DEF),
    Color(0xFF9B6BDF),
    Color(0xFFE06CA8),
)

fun courseColor(index: Int): Color {
    val size = CourseColors.size
    return CourseColors[((index % size) + size) % size]
}

/**
 * 自动配色用的 12 色：前 8 个就是 v0.2 的原始调色板（观感不变），
 * 后 4 个是同样柔和的补色，只为「一周内不同课程不撞色」留出余量。
 */
val AutoCourseColors = listOf(
    Color(0xFFEF6C6C), // 红
    Color(0xFFF2A03D), // 橙
    Color(0xFF8FA31E), // 橄榄
    Color(0xFF57BB8A), // 绿
    Color(0xFF4FB3C7), // 青
    Color(0xFF5B8DEF), // 蓝
    Color(0xFF9B6BDF), // 紫
    Color(0xFFE06CA8), // 粉
    Color(0xFF35A79C), // 青绿
    Color(0xFFA98B6B), // 棕
    Color(0xFF7F9BB5), // 灰蓝
    Color(0xFF6FA98F), // 灰绿
)

/** 「随机彩色」：不存具体颜色，交给按周自动配色（默认）。 */
const val RandomCourseColor = -1

/**
 * 手动选定的颜色用 `ExplicitColorBase + 调色板下标` 存，
 * 这样能和历史数据里「导入时写死的 0..7」区分开：后者一律按自动配色显示。
 */
const val ExplicitColorBase = 100

fun explicitColorIndex(paletteIndex: Int): Int = ExplicitColorBase + paletteIndex

fun isExplicitColor(colorIndex: Int): Boolean = colorIndex >= ExplicitColorBase

/**
 * 自动配色表：把这一屏出现的课程名排序后依次取色，因此
 * 同一门课颜色固定（同一屏幕内），一周之内的课程也不会撞色。
 */
fun autoColorMap(courseNames: Collection<String>): Map<String, Color> =
    courseNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        .mapIndexed { index, name -> name to AutoCourseColors[index % AutoCourseColors.size] }
        .toMap()

fun courseColorFor(colorIndex: Int, key: String, auto: Map<String, Color>? = null): Color = when {
    isExplicitColor(colorIndex) -> courseColor(colorIndex - ExplicitColorBase)
    auto != null && auto.containsKey(key.trim()) -> auto.getValue(key.trim())
    else -> AutoCourseColors[Math.floorMod(key.hashCode(), AutoCourseColors.size)]
}

/** 0L means "no explicit colour", which lets the caller fall back to the course colour. */
fun argbOrNull(value: Long): Color? =
    if (value == 0L) null else Color(value.toInt())

fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL
