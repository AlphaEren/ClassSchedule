package com.lorat.classschedule

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data object TimetableRoute : NavKey

@Serializable data object ScheduleSettingsRoute : NavKey

@Serializable data object GlobalSettingsRoute : NavKey

@Serializable data object SectionTimesRoute : NavKey

@Serializable data object CourseListRoute : NavKey

/** [groupKey] is the course being edited, or null when adding a new one. */
@Serializable data class CourseEditRoute(val groupKey: String?) : NavKey

@Serializable data object ImportRoute : NavKey

@Serializable data object AboutRoute : NavKey

@Serializable data object AddSchoolRoute : NavKey

@Serializable data class WebImportRoute(val auto: Boolean) : NavKey
