package com.lorat.classschedule.data

import com.lorat.classschedule.data.model.Timetable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Week number (1-based) for [today], based on the semester start date. Falls back to 1. */
fun currentWeekOf(timetable: Timetable, today: LocalDate = LocalDate.now()): Int {
    val start = parsedStart(timetable) ?: return 1
    val days = ChronoUnit.DAYS.between(start, today)
    return (days / 7).toInt().plus(1).coerceIn(1, maxOf(1, timetable.totalWeeks))
}

/** Monday of week 1 for the given semester start, or the current week's Monday if unset. */
fun semesterStartOrNow(timetable: Timetable): LocalDate =
    parsedStart(timetable) ?: LocalDate.now().with(java.time.DayOfWeek.MONDAY)

fun parsedStart(timetable: Timetable): LocalDate? =
    runCatching { LocalDate.parse(timetable.semesterStartDate) }.getOrNull()

/** dayOfWeek values (1=周一..7=周日) in the order the columns are drawn. */
fun dayColumns(weekStartSunday: Boolean, showWeekend: Boolean): List<Int> {
    val all = if (weekStartSunday) listOf(7, 1, 2, 3, 4, 5, 6) else listOf(1, 2, 3, 4, 5, 6, 7)
    return if (showWeekend) all else all.filter { it <= 5 }
}

/** The seven calendar days of [week], in column order. */
fun datesOfWeek(timetable: Timetable, week: Int, weekStartSunday: Boolean): List<LocalDate> {
    val start = semesterStartOrNow(timetable)
    val first = start.plusDays(((week - 1) * 7).toLong())
    val order = if (weekStartSunday) listOf(7, 1, 2, 3, 4, 5, 6) else listOf(1, 2, 3, 4, 5, 6, 7)
    val monday = first.with(java.time.DayOfWeek.MONDAY)
    return order.map { d -> monday.plusDays((d - 1).toLong()) }
}
