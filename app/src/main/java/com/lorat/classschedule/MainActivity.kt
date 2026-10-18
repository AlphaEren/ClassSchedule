package com.lorat.classschedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lorat.classschedule.data.model.DarkMode
import com.lorat.classschedule.i18n.Lang
import com.lorat.classschedule.theme.ClassScheduleTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    val repository = (application as ClassScheduleApp).repository
    // 冷启动时先把语言定下来，首帧就不会闪一下中文
    Lang.mode = repository.timetable.value.appearance.language
    setContent {
      val timetable by repository.timetable.collectAsStateWithLifecycle()
      val dark = when (timetable.appearance.darkMode) {
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
        else -> isSystemInDarkTheme()
      }
      SideEffect { Lang.mode = timetable.appearance.language }
      ClassScheduleTheme(darkTheme = dark) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          // 平板 / 横屏：内容按最大 720dp 居中，7 列不会被拉得很宽、设置页也不会一行横跨整屏
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.fillMaxHeight().widthIn(max = 720.dp).fillMaxWidth()) {
              MainNavigation(repository)
            }
          }
        }
      }
    }
  }
}
