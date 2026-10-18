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

/** 只删掉某一周的这一次课；去掉后没有剩下任何周的时间段会被丢掉。 */
fun List<Course>.withoutWeek(week: Int, totalWeeks: Int): List<Course> = mapNotNull { slot ->
    val weeks = if (slot.weeks.isEmpty()) (1..maxOf(1, totalWeeks)).toList() else slot.weeks
    val remaining = weeks.filter { it != week }
    when {
        remaining.isEmpty() -> null
        remaining.size == maxOf(1, totalWeeks) -> slot.copy(weeks = emptyList())
        else -> slot.copy(weeks = remaining)
    }
}

/**
 * Collapse the flat course list into one entry per course.
 * Meetings on the same day and section range are folded together, so a course that runs
 * 1-6 周 and 9-10 周 shows up as one 时间段 whose week list is the union.
 */
fun Timetable.courseGroups(): List<CourseGroup> =
    courses.groupBy { it.groupKey }.map { (key, slots) ->
        val first = slots.first()
        val merged = slots
            .groupBy { Triple(it.dayOfWeek, it.startSection, it.endSection) }
            .map { (_, same) ->
                val weeks = if (same.any { it.weeks.isEmpty() }) {
                    emptyList()
                } else {
                    same.flatMap { it.weeks }.distinct().sorted()
                }
                val weekNotes = LinkedHashMap<Int, String>()
                same.forEach { weekNotes.putAll(it.weekNotes) }
                same.first().copy(weeks = weeks, weekNotes = weekNotes)
            }
            .sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }))
        CourseGroup(
            key = key,
            name = first.name,
            teacher = first.teacher,
            location = first.location,
            colorIndex = first.colorIndex,
            credit = first.credit,
            note = first.note,
            slots = merged,
        )
    }

fun Timetable.groupOf(key: String): CourseGroup? = courseGroups().firstOrNull { it.key == key }

/** 这一周这一节的备注：优先取按周覆盖的，否则用整门课的备注。 */
fun CourseGroup.noteFor(week: Int, day: Int, start: Int, end: Int): String {
    val slot = slots.firstOrNull { it.dayOfWeek == day && it.startSection == start && it.endSection == end }
        ?: return note
    return slot.weekNotes[week] ?: slot.note
}

/** 只给「某天某节」的某一周写备注（空字符串表示这一周显式没有备注）。 */
fun List<Course>.withWeekNote(
    key: String,
    day: Int,
    start: Int,
    end: Int,
    week: Int,
    note: String,
): List<Course> = map { course ->
    if (course.groupKey == key &&
        course.dayOfWeek == day &&
        course.startSection == start &&
        course.endSection == end &&
        course.matchesWeek(week)
    ) {
        course.copy(weekNotes = course.weekNotes + (week to note))
    } else {
        course
    }
}

/** 给整门课写备注，并清掉之前按周覆盖的备注。 */
fun List<Course>.withGroupNote(key: String, note: String): List<Course> = map { course ->
    if (course.groupKey == key) course.copy(note = note, weekNotes = emptyMap()) else course
}

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

/**
 * Week set as ranges plus the parity it runs on. Odd/even courses are compressed in "parity space"
 * so 7,9,11 becomes 7-11 单 rather than three separate weeks — the same notation 教务 systems use.
 */
data class WeekSpec(val ranges: List<Pair<Int, Int>>, val parity: String?) {
    fun text(separator: String): String =
        ranges.joinToString(separator) { (from, to) -> if (from == to) "$from" else "$from-$to" }
}

fun weekSpec(weeks: List<Int>): WeekSpec {
    val sorted = weeks.distinct().sorted()
    if (sorted.isEmpty()) return WeekSpec(emptyList(), null)
    return when {
        sorted.all { it % 2 == 1 } ->
            WeekSpec(weekRanges(sorted.map { (it + 1) / 2 }).map { (a, b) -> (2 * a - 1) to (2 * b - 1) }, "单")
        sorted.all { it % 2 == 0 } ->
            WeekSpec(weekRanges(sorted.map { it / 2 }).map { (a, b) -> (2 * a) to (2 * b) }, "双")
        else -> WeekSpec(weekRanges(sorted), null)
    }
}

/** "1-8,10-16 周", "1-9 周 · 单周", or 全学期 when the course runs every week. */
fun weeksText(weeks: List<Int>, totalWeeks: Int): String {
    if (weeks.isEmpty() || weeks.size >= totalWeeks) return "全学期"
    val spec = weekSpec(weeks)
    val body = spec.text(",") + " 周"
    return if (spec.parity != null) "$body · ${spec.parity}周" else body
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

/**
 * 「这门课上到哪了」：以 [currentWeek] 为今天，算出这门课已经上了几周、还剩几周。
 * 空周次列表表示全学期。
 */
fun CourseGroup.progressText(currentWeek: Int, totalWeeks: Int): String {
    val list = weeks.ifEmpty { (1..maxOf(totalWeeks, currentWeek)).toList() }
    if (list.isEmpty()) return ""
    val done = list.count { it < currentWeek }
    val left = list.count { it > currentWeek }
    val thisWeek = if (currentWeek in list) "本周有课" else "本周不上"
    return "共 ${list.size} 周 · 已上 $done 周 · 剩 $left 周 · $thisWeek"
}
