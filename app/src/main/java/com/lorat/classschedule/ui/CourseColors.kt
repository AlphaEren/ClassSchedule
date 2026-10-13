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

/** 0L means "no explicit colour", which lets the caller fall back to the course colour. */
fun argbOrNull(value: Long): Color? =
    if (value == 0L) null else Color(value.toInt())

fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL
