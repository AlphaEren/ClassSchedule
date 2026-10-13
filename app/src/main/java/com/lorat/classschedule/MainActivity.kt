package com.lorat.classschedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lorat.classschedule.data.model.DarkMode
import com.lorat.classschedule.theme.ClassScheduleTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    val repository = (application as ClassScheduleApp).repository
    setContent {
      val timetable by repository.timetable.collectAsStateWithLifecycle()
      val dark = when (timetable.appearance.darkMode) {
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
        else -> isSystemInDarkTheme()
      }
      ClassScheduleTheme(darkTheme = dark) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(repository)
        }
      }
    }
  }
}
