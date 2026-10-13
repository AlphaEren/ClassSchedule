package com.lorat.classschedule.data

import android.content.Context
import com.lorat.classschedule.data.model.Appearance
import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.model.School
import com.lorat.classschedule.data.model.SectionTime
import com.lorat.classschedule.data.model.Timetable
import com.lorat.classschedule.widget.TodayWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Single source of truth for the locally stored timetable.
 * Persisted as JSON in the app's private files directory.
 */
class ScheduleRepository(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(context.filesDir, "timetable.json")
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val _timetable = MutableStateFlow(load())
    val timetable: StateFlow<Timetable> = _timetable.asStateFlow()

    private fun load(): Timetable = runCatching {
        if (file.exists()) json.decodeFromString(Timetable.serializer(), file.readText()) else Timetable()
    }.getOrElse {
        // Never throw away a file we could not read (wrong encoding, half-written, …):
        // keep a copy next to the fresh one so nothing is silently lost.
        runCatching { file.copyTo(File(file.parentFile, "timetable.unreadable.json"), overwrite = true) }
        Timetable()
    }

    suspend fun save(value: Timetable) = withContext(Dispatchers.IO) {
        file.writeText(json.encodeToString(Timetable.serializer(), value))
        _timetable.value = value
        // Keep the home-screen widget in step with the app.
        TodayWidgetProvider.refresh(appContext)
    }

    suspend fun setSchool(school: School) = save(_timetable.value.copy(school = school))

    /** Remember the exact timetable page so background refresh can load it directly. */
    suspend fun setRefreshUrl(url: String) {
        val current = _timetable.value.school ?: return
        if (url.isBlank() || url == current.refreshUrl) return
        save(_timetable.value.copy(school = current.copy(refreshUrl = url)))
    }

    /** Replace the imported courses and stamp the update time. */
    suspend fun replaceCourses(courses: List<Course>) = save(
        _timetable.value.copy(courses = courses, lastUpdatedAt = System.currentTimeMillis()),
    )

    suspend fun updateConfig(semesterStartDate: String, totalWeeks: Int) = save(
        _timetable.value.copy(
            semesterStartDate = semesterStartDate,
            totalWeeks = totalWeeks.coerceIn(1, 30),
        ),
    )

    suspend fun updateSemester(semesterName: String, semesterStartDate: String, totalWeeks: Int) = save(
        _timetable.value.copy(
            semesterName = semesterName,
            semesterStartDate = semesterStartDate,
            totalWeeks = totalWeeks.coerceIn(1, 30),
        ),
    )

    suspend fun updateAppearance(appearance: Appearance) = save(_timetable.value.copy(appearance = appearance))

    /**
     * 修改当前周: shift the semester start date so that [target] is the week containing today.
     */
    suspend fun setCurrentWeek(target: Int, today: java.time.LocalDate = java.time.LocalDate.now()) {
        val current = _timetable.value
        val week = target.coerceIn(1, maxOf(1, current.totalWeeks))
        val start = today.minusDays(((week - 1) * 7 + today.dayOfWeek.value - 1).toLong())
        save(current.copy(semesterStartDate = start.toString()))
    }

    suspend fun updateSectionTimes(sectionTimes: List<SectionTime>) {
        val cleaned = sanitizeSections(sectionTimes)
        if (cleaned.isEmpty()) return
        save(_timetable.value.copy(sectionTimes = cleaned))
    }

    private fun sanitizeSections(sectionTimes: List<SectionTime>): List<SectionTime> = sectionTimes
        .filter { it.section >= 1 }
        .distinctBy { it.section }
        .sortedBy { it.section }
        .mapIndexed { index, st -> st.copy(section = index + 1) }

    /**
     * Apply an imported timetable. Besides the courses, an academic system often knows its own
     * bell schedule, the first Monday of the term and which week "today" is in — take all of it,
     * so the app stops guessing the current week and shows the real 上课时间.
     */
    suspend fun applyImport(
        courses: List<Course>,
        sectionTimes: List<SectionTime> = emptyList(),
        semesterStart: String = "",
        currentWeek: Int = 0,
        weekCount: Int = 0,
    ) {
        val current = _timetable.value
        var next = current.copy(courses = courses, lastUpdatedAt = System.currentTimeMillis())
        val sections = sanitizeSections(sectionTimes)
        if (sections.isNotEmpty()) next = next.copy(sectionTimes = sections)
        val weeks = maxOf(next.totalWeeks, weekCount, currentWeek).coerceIn(1, 30)
        next = next.copy(totalWeeks = weeks)
        if (semesterStart.isNotBlank()) next = next.copy(semesterStartDate = semesterStart)
        save(next)
        // 「今天是第 N 周」以教务为准：用它把学期开始日期锚定好，之后每周自动推进。
        val target = currentWeek.coerceAtMost(weeks)
        if (target > 0 && currentWeekOf(_timetable.value) != target) setCurrentWeek(target)
    }

    /**
     * Create or update one course together with all of its meetings.
     * [key] is the group being edited, or null/blank for a new course.
     */
    suspend fun saveCourseGroup(
        key: String?,
        name: String,
        teacher: String,
        location: String,
        colorIndex: Int,
        slots: List<Course>,
        credit: String = "",
        note: String = "",
    ) {
        val current = _timetable.value
        val groupId = key?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val kept = if (key.isNullOrBlank()) current.courses else current.courses.filterNot { it.groupKey == key }
        val fresh = slots.mapIndexed { index, slot ->
            slot.copy(
                id = "$groupId-$index",
                groupId = groupId,
                name = name.trim(),
                // Teacher/location may differ per 时间段; the group-level values are defaults.
                teacher = slot.teacher.ifBlank { teacher.trim() },
                location = slot.location.ifBlank { location.trim() },
                colorIndex = colorIndex,
                credit = credit.trim(),
                note = note.trim(),
            )
        }
        save(current.copy(courses = kept + fresh))
    }

    suspend fun deleteCourseGroup(key: String) {
        val current = _timetable.value
        save(current.copy(courses = current.courses.filterNot { it.groupKey == key }))
    }

    suspend fun loadDemo() = save(demoTimetable())

    /** Wholesale replacement, used by 导入备份文件. */
    suspend fun replaceAll(value: Timetable) = save(value)

    suspend fun clear() = save(Timetable(appearance = _timetable.value.appearance))

    fun encodeTimetable(value: Timetable = _timetable.value): String =
        json.encodeToString(Timetable.serializer(), value)

    fun decodeTimetable(text: String): Timetable? =
        runCatching { json.decodeFromString(Timetable.serializer(), text) }.getOrNull()
}
