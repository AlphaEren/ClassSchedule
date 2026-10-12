package com.lorat.classschedule.ui

import androidx.compose.ui.graphics.Color

val CourseColors = listOf(
    Color(0xFF5B8DEF),
    Color(0xFFEF6C6C),
    Color(0xFF57BB8A),
    Color(0xFFF2A03D),
    Color(0xFF9B6BDF),
    Color(0xFF4FB3C7),
    Color(0xFFE06CA8),
    Color(0xFF8FA31E),
)

fun courseColor(index: Int): Color {
    val size = CourseColors.size
    return CourseColors[((index % size) + size) % size]
}
