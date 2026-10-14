package com.lorat.classschedule

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data object TimetableRoute : NavKey

@Serializable data object ScheduleSettingsRoute : NavKey

@Serializable data object GlobalSettingsRoute : NavKey

@Serializable data object SectionTimesRoute : NavKey

@Serializable data object CourseListRoute : NavKey

/** [groupKey] is the course being edited, or null when adding a new one.
 *  [day] / [section] 来自课表上长按的空格子，用来预填时间段。 */
@Serializable data class CourseEditRoute(
    val groupKey: String?,
    val day: Int = 0,
    val section: Int = 0,
) : NavKey

@Serializable data object ImportRoute : NavKey

/** 从图片识别课表。 */
@Serializable data object ImageImportRoute : NavKey

@Serializable data object AboutRoute : NavKey

@Serializable data object AddSchoolRoute : NavKey

@Serializable data class WebImportRoute(val auto: Boolean) : NavKey
