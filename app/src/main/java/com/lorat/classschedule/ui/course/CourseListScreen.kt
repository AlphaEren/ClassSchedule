@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.lorat.classschedule.ui.course

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.lorat.classschedule.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lorat.classschedule.data.CourseGroup
import com.lorat.classschedule.data.ScheduleRepository
import com.lorat.classschedule.data.courseGroups
import com.lorat.classschedule.data.dayName
import com.lorat.classschedule.data.sectionsText
import com.lorat.classschedule.data.weeksText
import com.lorat.classschedule.ui.autoColorMap
import com.lorat.classschedule.ui.courseColorFor
import kotlinx.coroutines.launch

/** 已添课程: every course in the timetable, with quick access to edit/delete. */
@Composable
fun CourseListScreen(
    repository: ScheduleRepository,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onAddCourse: () -> Unit,
) {
    val timetable by repository.timetable.collectAsStateWithLifecycle()
    val groups = remember(timetable.courses) { timetable.courseGroups() }
    val autoColors = remember(timetable.courses) { autoColorMap(timetable.courses.map { it.name }) }
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<CourseGroup?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("已添课程（${groups.size}）") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = { TextButton(onClick = onAddCourse) { Text("添加") } },
            )
        },
    ) { padding ->
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("还没有课程，点右上角「添加」", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(groups, key = { it.key }) { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .combinedClickable(
                                onClick = { onEdit(group.key) },
                                onLongClick = { pendingDelete = group },
                            )
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(10.dp).clip(CircleShape)
                                .background(courseColorFor(group.colorIndex, group.name, autoColors)),
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(group.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                group.slots.joinToString("；") {
                                    "${dayName(it.dayOfWeek)} ${sectionsText(it.startSection, it.endSection)}"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val extras = listOfNotNull(
                                group.teacher.takeIf { it.isNotBlank() },
                                group.location.takeIf { it.isNotBlank() },
                                weeksText(group.weeks, maxOf(1, timetable.totalWeeks)),
                            )
                            if (extras.isNotEmpty()) {
                                Text(
                                    extras.joinToString(" · "),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(onClick = { pendingDelete = group }) { Text("删除") }
                    }
                }
            }
        }
    }

    pendingDelete?.let { group ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除课程") },
            text = { Text("确定删除「${group.name}」？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deleteCourseGroup(group.key) }
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}
