package com.lorat.classschedule.data

import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.model.SectionTime
import com.lorat.classschedule.data.model.Timetable

/** 周一..周日 for dayOfWeek 1..7. */
val DayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

val DayShortNames = listOf("一", "二", "三", "四", "五", "六", "日")

fun dayName(day: Int): String = DayNames.getOrElse(day - 1) { "周?" }

/** One course together with all of its meetings ("时间段"). */
data class CourseGroup(
    val key: String,
    val name: String,
    val teacher: String,
    val location: String,
    val colorIndex: Int,
    val credit: String,
    val note: String,
    val slots: List<Course>,
) {
    val weeks: List<Int> get() = slots.flatMap { it.weeks }.distinct().sorted()
}

/** Collapse the flat course list into one entry per course. */
fun Timetable.courseGroups(): List<CourseGroup> =
    courses.groupBy { it.groupKey }.map { (key, slots) ->
        val first = slots.first()
        CourseGroup(
            key = key,
            name = first.name,
            teacher = first.teacher,
            location = first.location,
            colorIndex = first.colorIndex,
            credit = first.credit,
            note = first.note,
            slots = slots.sortedWith(compareBy({ it.dayOfWeek }, { it.startSection })),
        )
    }

fun Timetable.groupOf(key: String): CourseGroup? = courseGroups().firstOrNull { it.key == key }

/** "单周" / "双周" when every week shares the same parity, otherwise null. */
fun parityLabel(weeks: List<Int>, totalWeeks: Int): String? {
    if (weeks.isEmpty() || weeks.size >= totalWeeks) return null
    return when {
        weeks.all { it % 2 == 1 } -> "单周"
        weeks.all { it % 2 == 0 } -> "双周"
        else -> null
    }
}

/** Collapse a week set into consecutive ranges. */
fun weekRanges(weeks: List<Int>): List<Pair<Int, Int>> {
    val sorted = weeks.distinct().sorted()
    if (sorted.isEmpty()) return emptyList()
    val ranges = mutableListOf<Pair<Int, Int>>()
    var start = sorted.first()
    var prev = start
    for (w in sorted.drop(1)) {
        if (w == prev + 1) {
            prev = w
        } else {
            ranges.add(start to prev)
            start = w
            prev = w
        }
    }
    ranges.add(start to prev)
    return ranges
}

/** "1-8,10-16 周", "单周", or 全学期 when the course runs every week. */
fun weeksText(weeks: List<Int>, totalWeeks: Int): String {
    if (weeks.isEmpty() || weeks.size >= totalWeeks) return "全学期"
    val parity = parityLabel(weeks, totalWeeks)
    val body = weekRanges(weeks).joinToString(",") { (from, to) -> if (from == to) "$from" else "$from-$to" }
    return if (parity != null) "$body 周 · $parity" else "$body 周"
}

/** Section range as text: a single section renders as "第3节", a run as "第1-2节". */
fun sectionsText(start: Int, end: Int): String =
    if (end <= start) "第${start}节" else "第${start}-${end}节"

/** Wall-clock range of a meeting, e.g. "08:00-08:50". */
fun timeText(course: Course, sectionTimes: List<SectionTime>): String {
    val from = sectionTimes.firstOrNull { it.section == course.startSection }?.start ?: return ""
    val to = sectionTimes.firstOrNull { it.section == course.endSection }?.end ?: from
    return "$from-$to"
}

/** "08:50" + 10 = "09:00"; returns the input unchanged if it cannot be parsed. */
fun shiftTime(time: String, minutes: Int): String =
    runCatching { java.time.LocalTime.parse(time).plusMinutes(minutes.toLong()).toString() }.getOrDefault(time)

/** The section rows the grid must draw. */
fun Timetable.gridSectionCount(): Int =
    maxOf(1, sectionTimes.size, courses.maxOfOrNull { it.endSection } ?: 0)

/** Colour index for a brand new course: first unused swatch, then round-robin. */
fun Timetable.nextColorIndex(paletteSize: Int): Int {
    if (paletteSize <= 0) return 0
    val used = courses.map { it.colorIndex }.toSet()
    return (0 until paletteSize).firstOrNull { it !in used } ?: (courses.size % paletteSize)
}

/** Distinct course names already used, most recent first — the "历史课程名" quick picks. */
fun Timetable.knownCourseNames(): List<String> =
    courses.map { it.name }.distinct().takeLast(12).reversed()
