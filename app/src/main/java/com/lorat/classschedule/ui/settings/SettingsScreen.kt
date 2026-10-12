package com.lorat.classschedule.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lorat.classschedule.data.ScheduleRepository
import com.lorat.classschedule.ui.AppBottomBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: ScheduleRepository,
    onBack: () -> Unit,
    onReimport: () -> Unit,
    onAddSchool: () -> Unit,
) {
    val timetable by repository.timetable.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var startDate by remember(timetable.semesterStartDate) { mutableStateOf(timetable.semesterStartDate) }
    var totalWeeks by remember(timetable.totalWeeks) { mutableStateOf(timetable.totalWeeks.toString()) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) },
        bottomBar = {
            AppBottomBar(selectedIndex = 1, onSelectTimetable = onBack, onSelectSettings = {})
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("当前学校", fontWeight = FontWeight.SemiBold)
                    Text(timetable.school?.name ?: "未设置", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(timetable.school?.scheduleUrl.orEmpty(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onAddSchool) { Text("添加 / 更换学校") }
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("学期设置", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("第 1 周周一日期 (yyyy-MM-dd)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = totalWeeks,
                        onValueChange = { totalWeeks = it.filter { c -> c.isDigit() } },
                        label = { Text("学期总周数") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = {
                        scope.launch { repository.updateConfig(startDate.trim(), totalWeeks.toIntOrNull() ?: 20) }
                    }) { Text("保存设置") }
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("课表操作", fontWeight = FontWeight.SemiBold)
                    Text("上次更新：" + formatTime(timetable.lastUpdatedAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onReimport, modifier = Modifier.fillMaxWidth()) { Text("立即更新 / 重新导入") }
                    OutlinedButton(onClick = { scope.launch { repository.loadDemo() } }, modifier = Modifier.fillMaxWidth()) { Text("载入示例课表") }
                    OutlinedButton(onClick = { scope.launch { repository.clear() } }, modifier = Modifier.fillMaxWidth()) { Text("清空本地课表") }
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("节次时间", fontWeight = FontWeight.SemiBold)
                    timetable.sectionTimes.forEach { st ->
                        Row(Modifier.fillMaxWidth()) {
                            Text("第 ${st.section} 节", modifier = Modifier.padding(end = 12.dp), fontSize = 13.sp)
                            Text("${st.start} - ${st.end}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0L) return "从未"
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(millis))
}
