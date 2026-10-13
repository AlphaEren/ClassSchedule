@file:OptIn(ExperimentalMaterial3Api::class)

package com.lorat.classschedule.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lorat.classschedule.data.ScheduleRepository
import com.lorat.classschedule.data.model.SectionTime
import com.lorat.classschedule.data.shiftTime
import kotlinx.coroutines.launch

/** 上课时间: per-section start/end times. */
@Composable
fun SectionTimesScreen(repository: ScheduleRepository, onBack: () -> Unit) {
    val timetable by repository.timetable.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val rows = remember {
        timetable.sectionTimes.map { it.start to it.end }.toMutableStateList()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("上课时间") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = {
                    TextButton(onClick = {
                        val cleaned = rows.mapIndexed { index, (start, end) ->
                            SectionTime(index + 1, start.trim().ifBlank { "08:00" }, end.trim().ifBlank { "08:45" })
                        }
                        scope.launch {
                            repository.updateSectionTimes(cleaned)
                            snackbar.showSnackbar("已保存上课时间")
                        }
                    }) { Text("保存") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "24 小时制。默认时间表不可删除，可以逐节调整上下课时间。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            rows.forEachIndexed { index, (start, end) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("第 ${index + 1} 节", fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = start,
                        onValueChange = { rows[index] = it to rows[index].second },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f),
                    )
                    OutlinedTextField(
                        value = end,
                        onValueChange = { rows[index] = rows[index].first to it },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f),
                    )
                    if (rows.size > 1) {
                        TextButton(onClick = { rows.removeAt(index) }) { Text("删除") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        val last = rows.lastOrNull()
                        val start = last?.let { shiftTime(it.second, 10) } ?: "08:00"
                        rows.add(start to shiftTime(start, 45))
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("新增一节") }
                Button(
                    onClick = {
                        val cleaned = rows.mapIndexed { index, (start, end) ->
                            SectionTime(index + 1, start.trim().ifBlank { "08:00" }, end.trim().ifBlank { "08:45" })
                        }
                        scope.launch {
                            repository.updateSectionTimes(cleaned)
                            snackbar.showSnackbar("已保存上课时间")
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("保存") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
