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
 * 「随机彩色」用的扩展调色板：前 8 个与可选调色板一致，后面继续补足到 24 个。
 * 顺序按「相邻取色在色相上尽量拉开」排列，因此一学期十几门课也能做到
 * 「同名同色、异名不撞色」。后面的颜色只在课程数超过 16 门时才会用到。
 */
val AutoCourseColors = listOf(
    Color(0xFFEF6C6C), // 红
    Color(0xFF57BB8A), // 绿
    Color(0xFF5B8DEF), // 蓝
    Color(0xFFF2A03D), // 橙
    Color(0xFF9B6BDF), // 紫
    Color(0xFF35A79C), // 青绿
    Color(0xFFE06CA8), // 粉
    Color(0xFF8FA31E), // 橄榄
    Color(0xFF9A8FCF), // 薰衣草
    Color(0xFFD9A82B), // 琥珀
    Color(0xFF4FB3C7), // 青
    Color(0xFFE06C86), // 玫瑰
    Color(0xFF7FB03F), // 黄绿
    Color(0xFFA98B6B), // 棕
    Color(0xFFC46BD0), // 紫罗兰
    Color(0xFF8FB56F), // 苔绿
    Color(0xFF6E7BE8), // 靛
    Color(0xFFF0803D), // 橘红
    Color(0xFF6FA98F), // 海绿
    Color(0xFFD06BB8), // 洋红
    Color(0xFFBFA32A), // 暗黄
    Color(0xFF7F9BB5), // 灰蓝
    Color(0xFFC96A6A), // 砖红
    Color(0xFF4FA3D1), // 天蓝
)

/** 「随机彩色」：按课程名取色，同一门课每次看到的颜色都一样。 */
const val RandomCourseColor = -1

/**
 * 自动配色表：把课程名排序后依次取色，因此同名同色、异名不重复
 * （课程数超过调色板数量时才会回绕）。
 */
fun autoColorMap(courseNames: Collection<String>): Map<String, Color> =
    courseNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        .mapIndexed { index, name -> name to AutoCourseColors[index % AutoCourseColors.size] }
        .toMap()

fun courseColorFor(colorIndex: Int, key: String, auto: Map<String, Color>? = null): Color = when {
    colorIndex >= 0 -> courseColor(colorIndex)
    auto != null && auto.containsKey(key.trim()) -> auto.getValue(key.trim())
    else -> AutoCourseColors[Math.floorMod(key.hashCode(), AutoCourseColors.size)]
}

/** 0L means "no explicit colour", which lets the caller fall back to the course colour. */
fun argbOrNull(value: Long): Color? =
    if (value == 0L) null else Color(value.toInt())

fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL
