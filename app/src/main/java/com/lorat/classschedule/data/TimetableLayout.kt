package com.lorat.classschedule.data

import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.model.Timetable

/** 课表里的一张卡片：第 [col] 列、第 [slice]/[slices] 份（冲突时左右平分）。 */
internal data class Placed(
    val course: Course,
    val col: Int,
    val slice: Int,
    val slices: Int,
    val thisWeek: Boolean,
    val conflict: Boolean,
)

/**
 * Lay out one week of one column.
 *
 * A slot usually holds several records that never actually clash, so:
 *  1. records of the same course with different 周次 (1-6 周 + 9-10 周) merge into one card;
 *  2. the classes that really run in [week] are drawn first and get the full width;
 *  3. classes of other weeks are only drawn when they do not overlap something already drawn —
 *     otherwise their text would sit on top of this week's class (14-16 周的 1-4 节 vs 1-6 周的 1-2 节);
 *  4. two different classes in the same week are the only case that splits a cell (conflict marker).
 *
 * [includeOtherWeeks] is false when the result is used for something that only shows this week
 * (e.g. the shared image), so the faded 非本周 cards do not leak in.
 */
internal fun placement(
    timetable: Timetable,
    columns: List<Int>,
    week: Int,
    includeOtherWeeks: Boolean = timetable.appearance.showOtherWeeks,
): List<Placed> {
    val out = mutableListOf<Placed>()
    for ((col, day) in columns.withIndex()) {
        val dayCourses = timetable.courses.filter { it.dayOfWeek == day }
        val (active, others) = dayCourses.partition { it.matchesWeek(week) }
        val taken = mutableListOf<IntRange>()
        // 本周要上的课里，互相重叠的（含部分重叠，例如 1-4 节与 3-4 节）左右平分，避免叠字
        val groups = mutableListOf<MutableList<Placed>>()
        for (placed in layOutSlot(active, col, thisWeek = true)) {
            val range = placed.course.startSection..placed.course.endSection
            val hit = groups.firstOrNull { group ->
                group.any { it.course.startSection <= range.last && range.first <= it.course.endSection }
            }
            if (hit != null) hit.add(placed) else groups.add(mutableListOf(placed))
        }
        for (group in groups) {
            group.forEachIndexed { index, placed ->
                out.add(placed.copy(slice = index, slices = group.size, conflict = group.size > 1))
            }
        }
        if (!includeOtherWeeks) continue
        for (placed in out.filter { it.col == col && it.thisWeek }) {
            taken.add(placed.course.startSection..placed.course.endSection)
        }
        // 非本周的课：跳过与已画格子重叠的，剩下的每个都能占满整格
        val survivors = mutableListOf<Course>()
        for (placed in layOutSlot(others, col, thisWeek = false)) {
            val range = placed.course.startSection..placed.course.endSection
            if (taken.any { it.first <= range.last && range.first <= it.last }) continue
            if (survivors.any { it.startSection <= range.last && range.first <= it.endSection }) continue
            survivors.add(placed.course)
            out.add(placed.copy(slice = 0, slices = 1, conflict = false))
        }
    }
    return out
}

/** Same section range: one card per course name; different courses split the cell. */
private fun layOutSlot(courses: List<Course>, col: Int, thisWeek: Boolean): List<Placed> {
    val bySlot = LinkedHashMap<Pair<Int, Int>, MutableList<Course>>()
    for (course in courses) {
        bySlot.getOrPut(course.startSection to course.endSection) { mutableListOf() }.add(course)
    }
    val out = mutableListOf<Placed>()
    for ((_, sameSlot) in bySlot) {
        val merged = sameSlot.groupBy { it.name }.values.map { records ->
            val weeks = if (records.any { it.weeks.isEmpty() }) {
                emptyList()
            } else {
                records.flatMap { it.weeks }.distinct().sorted()
            }
            val weekNotes = LinkedHashMap<Int, String>()
            records.forEach { weekNotes.putAll(it.weekNotes) }
            records.first().copy(weeks = weeks, weekNotes = weekNotes)
        }
        merged.forEachIndexed { index, course ->
            out.add(
                Placed(
                    course = course,
                    col = col,
                    slice = index,
                    slices = merged.size,
                    thisWeek = thisWeek,
                    conflict = thisWeek && merged.size > 1,
                ),
            )
        }
    }
    return out
}
