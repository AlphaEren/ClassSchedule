package com.lorat.classschedule.data

import com.lorat.classschedule.data.model.Timetable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Week number (1-based) for [today], based on the semester start date. Falls back to 1. */
fun currentWeekOf(timetable: Timetable, today: LocalDate = LocalDate.now()): Int {
    val start = runCatching { LocalDate.parse(timetable.semesterStartDate) }.getOrNull() ?: return 1
    val days = ChronoUnit.DAYS.between(start, today)
    return (days / 7).toInt().plus(1).coerceIn(1, timetable.totalWeeks)
}

/** Monday of week 1 for the given semester start, or the current week's Monday if unset. */
fun semesterStartOrNow(timetable: Timetable): LocalDate =
    runCatching { LocalDate.parse(timetable.semesterStartDate) }.getOrNull()
        ?: LocalDate.now().with(java.time.DayOfWeek.MONDAY)
