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

/** 「随机彩色」：按课程名取色，同一门课每次看到的颜色都一样。 */
const val RandomCourseColor = -1

fun courseColorFor(colorIndex: Int, key: String): Color =
    if (colorIndex < 0) courseColor(key.hashCode()) else courseColor(colorIndex)

/** 0L means "no explicit colour", which lets the caller fall back to the course colour. */
fun argbOrNull(value: Long): Color? =
    if (value == 0L) null else Color(value.toInt())

fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL
