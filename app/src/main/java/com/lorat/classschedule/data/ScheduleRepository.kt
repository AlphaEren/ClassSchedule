package com.lorat.classschedule.data

import android.content.Context
import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.model.School
import com.lorat.classschedule.data.model.Timetable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Single source of truth for the locally stored timetable.
 * Persisted as JSON in the app's private files directory.
 */
class ScheduleRepository(context: Context) {

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
    }.getOrElse { Timetable() }

    suspend fun save(value: Timetable) = withContext(Dispatchers.IO) {
        file.writeText(json.encodeToString(Timetable.serializer(), value))
        _timetable.value = value
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

    suspend fun loadDemo() = save(demoTimetable())

    suspend fun clear() = save(Timetable())
}
