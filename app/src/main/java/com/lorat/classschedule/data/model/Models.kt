package com.lorat.classschedule.data.model

import kotlinx.serialization.Serializable

/** One teaching period (小节) with its wall-clock time. */
@Serializable
data class SectionTime(
    val section: Int,
    val start: String,
    val end: String,
)

/**
 * A single class meeting: a course occupies [startSection]..[endSection] on [dayOfWeek].
 * One course may hold several meetings — they share [groupId], which is how "多课时" works.
 */
@Serializable
data class Course(
    val id: String,
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val weeks: List<Int> = emptyList(),
    val colorIndex: Int = 0,
    val groupId: String = "",
    /** Course-level extras, duplicated across the meetings of one course. */
    val credit: String = "",
    val note: String = "",
) {
    fun matchesWeek(week: Int): Boolean = weeks.isEmpty() || week in weeks

    /** Imported courses have no group id, so each of them is a group of its own. */
    val groupKey: String get() = groupId.ifBlank { id }
}

/** The academic system this timetable was imported from. */
@Serializable
data class School(
    val id: String,
    val name: String,
    val scheduleUrl: String = "",
    val parserPreset: String = "generic",
    /** The exact timetable page discovered during import; used for silent refresh. */
    val refreshUrl: String = "",
)

/** Everything we persist locally. */
@Serializable
data class Timetable(
    val school: School? = null,
    /** Free-form term label shown under the week title, e.g. "2024-2025 夏季 第1学期". */
    val semesterName: String = "",
    /** First day of week 1; its weekday decides the column order (see [Appearance.weekStartSunday]). */
    val semesterStartDate: String = "",
    val totalWeeks: Int = 20,
    val sectionTimes: List<SectionTime> = defaultSectionTimes(),
    val courses: List<Course> = emptyList(),
    val lastUpdatedAt: Long = 0L,
    val appearance: Appearance = Appearance(),
) {
    fun coursesForWeek(week: Int): List<Course> = courses.filter { it.matchesWeek(week) }
}

fun defaultSectionTimes(): List<SectionTime> = listOf(
    SectionTime(1, "08:00", "08:45"),
    SectionTime(2, "08:55", "09:40"),
    SectionTime(3, "10:00", "10:45"),
    SectionTime(4, "10:55", "11:40"),
    SectionTime(5, "14:00", "14:45"),
    SectionTime(6, "14:55", "15:40"),
    SectionTime(7, "16:00", "16:45"),
    SectionTime(8, "16:55", "17:40"),
    SectionTime(9, "19:00", "19:45"),
    SectionTime(10, "19:55", "20:40"),
    SectionTime(11, "20:50", "21:35"),
    SectionTime(12, "21:45", "22:30"),
)
