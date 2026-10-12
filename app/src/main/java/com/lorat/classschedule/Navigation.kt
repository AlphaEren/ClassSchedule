package com.lorat.classschedule

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.lorat.classschedule.data.ScheduleRepository
import com.lorat.classschedule.ui.school.AddSchoolScreen
import com.lorat.classschedule.ui.settings.SettingsScreen
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
            onOpenSettings = { backStack.add(SettingsRoute) },
            onAutoUpdate = { backStack.add(WebImportRoute(auto = true)) },
            onManualUpdate = { backStack.add(WebImportRoute(auto = false)) },
          )
        }
        entry<SettingsRoute> {
          SettingsScreen(
            repository = repository,
            onBack = { backStack.removeLastOrNull() },
            onReimport = { backStack.add(WebImportRoute(auto = false)) },
            onAddSchool = { backStack.add(AddSchoolRoute) },
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
