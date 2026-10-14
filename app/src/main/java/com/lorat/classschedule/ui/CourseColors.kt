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
 * 「自动配色」用的 12 色：比可选调色板更饱和，
 * 因为格子底色很淡（约 20%），只有饱和色才能让「文字颜色」一眼分得开。
 */
val AutoCourseColors = listOf(
    Color(0xFFE53935), // 红
    Color(0xFF1E88E5), // 蓝
    Color(0xFF43A047), // 绿
    Color(0xFFFB8C00), // 橙
    Color(0xFF8E24AA), // 紫
    Color(0xFF00897B), // 青绿
    Color(0xFFD81B60), // 玫红
    Color(0xFF6D4C41), // 棕
    Color(0xFFF9A825), // 琥珀
    Color(0xFF00ACC1), // 青
    Color(0xFF3949AB), // 靛
    Color(0xFF546E7A), // 灰蓝
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
