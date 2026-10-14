package com.lorat.classschedule

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.lorat.classschedule.data.ScheduleRepository
import com.lorat.classschedule.ui.course.CourseEditScreen
import com.lorat.classschedule.ui.course.CourseListScreen
import com.lorat.classschedule.ui.imports.ImageImportScreen
import com.lorat.classschedule.ui.imports.ImportScreen
import com.lorat.classschedule.ui.school.AddSchoolScreen
import com.lorat.classschedule.ui.settings.AboutScreen
import com.lorat.classschedule.ui.settings.GlobalSettingsScreen
import com.lorat.classschedule.ui.settings.ScheduleSettingsScreen
import com.lorat.classschedule.ui.settings.SectionTimesScreen
import com.lorat.classschedule.ui.timetable.TimetableScreen
import com.lorat.classschedule.ui.webimport.WebImportScreen

@Composable
fun MainNavigation(repository: ScheduleRepository) {
  val backStack = rememberNavBackStack(TimetableRoute)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<TimetableRoute> {
          TimetableScreen(
            repository = repository,
            onAddSchool = { backStack.add(AddSchoolRoute) },
            onAddCourse = { groupKey -> backStack.add(CourseEditRoute(groupKey)) },
            onAddCourseAt = { day, section -> backStack.add(CourseEditRoute(null, day, section)) },
            onOpenScheduleSettings = { backStack.add(ScheduleSettingsRoute) },
            onOpenGlobalSettings = { backStack.add(GlobalSettingsRoute) },
            onOpenCourseList = { backStack.add(CourseListRoute) },
            onOpenSectionTimes = { backStack.add(SectionTimesRoute) },
            onOpenImport = { backStack.add(ImportRoute) },
            onManualUpdate = { backStack.add(WebImportRoute(auto = false)) },
            onAbout = { backStack.add(AboutRoute) },
          )
        }
        entry<ScheduleSettingsRoute> {
          ScheduleSettingsScreen(
            repository = repository,
            onBack = { backStack.removeLastOrNull() },
            onOpenSectionTimes = { backStack.add(SectionTimesRoute) },
            onOpenCourseList = { backStack.add(CourseListRoute) },
          )
        }
        entry<GlobalSettingsRoute> {
          GlobalSettingsScreen(
            repository = repository,
            onBack = { backStack.removeLastOrNull() },
            onOpenScheduleSettings = { backStack.add(ScheduleSettingsRoute) },
            onAbout = { backStack.add(AboutRoute) },
          )
        }
        entry<SectionTimesRoute> {
          SectionTimesScreen(repository = repository, onBack = { backStack.removeLastOrNull() })
        }
        entry<AboutRoute> {
          AboutScreen(onBack = { backStack.removeLastOrNull() })
        }
        entry<CourseListRoute> {
          CourseListScreen(
            repository = repository,
            onBack = { backStack.removeLastOrNull() },
            onEdit = { key -> backStack.add(CourseEditRoute(key)) },
            onAddCourse = { backStack.add(CourseEditRoute(null)) },
          )
        }
        entry<CourseEditRoute> { route ->
          CourseEditScreen(
            repository = repository,
            groupKey = route.groupKey,
            initialDay = route.day,
            initialSection = route.section,
            onBack = { backStack.removeLastOrNull() },
          )
        }
        entry<ImportRoute> {
          ImportScreen(
            repository = repository,
            onBack = { backStack.removeLastOrNull() },
            onOpenSchoolImport = { backStack.add(AddSchoolRoute) },
            onOpenImageImport = { backStack.add(ImageImportRoute) },
          )
        }
        entry<ImageImportRoute> {
          ImageImportScreen(
            repository = repository,
            onBack = { backStack.removeLastOrNull() },
          )
        }
        entry<AddSchoolRoute> {
          AddSchoolScreen(
            repository = repository,
            onBack = { backStack.removeLastOrNull() },
            onSchoolSaved = { backStack.add(WebImportRoute(auto = false)) },
          )
        }
        entry<WebImportRoute> { route ->
          WebImportScreen(
            repository = repository,
            auto = route.auto,
            onBack = { backStack.removeLastOrNull() },
            onFinished = { backStack.removeLastOrNull() },
          )
        }
      },
  )
}
