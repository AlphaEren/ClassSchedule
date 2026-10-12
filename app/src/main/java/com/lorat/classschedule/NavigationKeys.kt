package com.lorat.classschedule

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data object TimetableRoute : NavKey

@Serializable data object SettingsRoute : NavKey

@Serializable data object AddSchoolRoute : NavKey

@Serializable data class WebImportRoute(val auto: Boolean) : NavKey
