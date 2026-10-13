@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.lorat.classschedule.ui.course

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lorat.classschedule.data.ScheduleRepository
import com.lorat.classschedule.data.dayName
import com.lorat.classschedule.data.groupOf
import com.lorat.classschedule.data.knownCourseNames
import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.nextColorIndex
import com.lorat.classschedule.data.sectionsText
import com.lorat.classschedule.data.weeksText
import com.lorat.classschedule.ui.CourseColors
import kotlinx.coroutines.launch

/** One 时间段 row in the editor. */
private data class SlotDraft(
    val day: Int,
    val start: Int,
    val end: Int,
    val weeks: Set<Int>,
    val teacher: String,
    val location: String,
)

@Composable
fun CourseEditScreen(
    repository: ScheduleRepository,
    groupKey: String?,
    onBack: () -> Unit,
) {
    val timetable by repository.timetable.collectAsStateWithLifecycle()
    val totalWeeks = maxOf(1, timetable.totalWeeks)
    val sectionCount = maxOf(1, timetable.sectionTimes.size)
    val existing = remember(groupKey) { groupKey?.let { repository.timetable.value.groupOf(it) } }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var credit by remember { mutableStateOf(existing?.credit.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var colorIndex by remember {
        mutableStateOf(existing?.colorIndex ?: timetable.nextColorIndex(CourseColors.size))
    }
    val slots = remember {
        (
            existing?.slots?.map {
                SlotDraft(
                    day = it.dayOfWeek,
                    start = it.startSection,
                    end = it.endSection,
                    weeks = if (it.weeks.isEmpty()) (1..totalWeeks).toSet() else it.weeks.toSet(),
                    teacher = it.teacher,
                    location = it.location,
                )
            } ?: listOf(
                SlotDraft(1, 1, 2, (1..totalWeeks).toSet(), "", ""),
            )
            ).toMutableStateList()
    }

    var editingWeeks by remember { mutableStateOf<Int?>(null) }
    var editingDay by remember { mutableStateOf<Int?>(null) }
    var editingSection by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (groupKey == null) "添加课程" else "编辑课程") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = {
                    TextButton(onClick = {
                        val cleaned = name.trim()
                        when {
                            cleaned.isEmpty() -> scope.launch { snackbarHostState.showSnackbar("请填写课程名称") }
                            slots.isEmpty() -> scope.launch { snackbarHostState.showSnackbar("至少需要一个时间段") }
                            else -> scope.launch {
                                repository.saveCourseGroup(
                                    key = groupKey,
                                    name = cleaned,
                                    teacher = slots.first().teacher,
                                    location = slots.first().location,
                                    colorIndex = colorIndex,
                                    credit = credit,
                                    note = note,
                                    slots = slots.map { draft ->
                                        Course(
                                            id = "",
                                            name = cleaned,
                                            teacher = draft.teacher,
                                            location = draft.location,
                                            dayOfWeek = draft.day,
                                            startSection = draft.start,
                                            endSection = draft.end,
                                            weeks = if (draft.weeks.size >= totalWeeks) emptyList() else draft.weeks.sorted(),
                                            colorIndex = colorIndex,
                                        )
                                    },
                                )
                                onBack()
                            }
                        }
                    }) { Text("保存", fontWeight = FontWeight.Medium) }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("课程基础信息", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("课程名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val history = timetable.knownCourseNames()
                    if (history.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(history) { known ->
                                Text(
                                    known,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                        .combinedClickable { name = known }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                    Text("颜色", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CourseColors.forEachIndexed { index, color ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (index == colorIndex) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .combinedClickable { colorIndex = index },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = credit,
                            onValueChange = { credit = it },
                            label = { Text("学分（可不填）") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("备注（可不填）") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            slots.forEachIndexed { index, slot ->
                Card {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("时间段 ${index + 1}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (slots.size > 1) {
                                TextButton(onClick = { slots.removeAt(index) }) { Text("删除") }
                            }
                        }
                        SettingRow("周数") {
                            TextButton(onClick = { editingWeeks = index }) {
                                Text(weeksText(slot.weeks.sorted(), totalWeeks))
                            }
                        }
                        SettingRow("时间") {
                            Row {
                                TextButton(onClick = { editingDay = index }) { Text(dayName(slot.day)) }
                                TextButton(onClick = { editingSection = index to true }) { Text("第 ${slot.start} 节") }
                                TextButton(onClick = { editingSection = index to false }) { Text("第 ${slot.end} 节") }
                            }
                        }
                        Text(
                            "共 ${sectionsText(slot.start, slot.end)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = slot.teacher,
                            onValueChange = { slots[index] = slot.copy(teacher = it) },
                            label = { Text("授课老师") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = slot.location,
                            onValueChange = { slots[index] = slot.copy(location = it) },
                            label = { Text("上课地点") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    val last = slots.lastOrNull() ?: SlotDraft(1, 1, 2, (1..totalWeeks).toSet(), "", "")
                    slots.add(last)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("新增时间段") }
            Spacer(Modifier.height(24.dp))
        }
    }

    editingWeeks?.let { index ->
        WeekSelectSheet(
            totalWeeks = totalWeeks,
            selected = slots[index].weeks,
            onConfirm = { weeks ->
                slots[index] = slots[index].copy(weeks = weeks)
                editingWeeks = null
            },
            onDismiss = { editingWeeks = null },
        )
    }

    editingDay?.let { index ->
        ChoiceSheet(
            title = "选择星期",
            options = (1..7).map { it to dayName(it) },
            selected = slots[index].day,
            onPick = { day ->
                slots[index] = slots[index].copy(day = day)
                editingDay = null
            },
            onDismiss = { editingDay = null },
        )
    }

    editingSection?.let { (index, isStart) ->
        ChoiceSheet(
            title = if (isStart) "选择开始节次" else "选择结束节次",
            options = (1..sectionCount).map { it to "第 $it 节" },
            selected = if (isStart) slots[index].start else slots[index].end,
            onPick = { section ->
                val slot = slots[index]
                slots[index] = if (isStart) {
                    slot.copy(start = section, end = maxOf(section, slot.end))
                } else {
                    slot.copy(end = section, start = minOf(section, slot.start))
                }
                editingSection = null
            },
            onDismiss = { editingSection = null },
        )
    }
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, modifier = Modifier.width(56.dp))
        content()
    }
}

@Composable
private fun ChoiceSheet(
    title: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(options) { (value, label) ->
                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (value == selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                            )
                            .combinedClickable { onPick(value) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            fontSize = 13.sp,
                            color = if (value == selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekSelectSheet(
    totalWeeks: Int,
    selected: Set<Int>,
    onConfirm: (Set<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf(selected) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("选择周数", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShortcutChip("全选") { current = (1..totalWeeks).toSet() }
                ShortcutChip("单周") { current = (1..totalWeeks).filter { it % 2 == 1 }.toSet() }
                ShortcutChip("双周") { current = (1..totalWeeks).filter { it % 2 == 0 }.toSet() }
                ShortcutChip("清空") { current = emptySet() }
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items((1..totalWeeks).toList()) { week ->
                    val on = week in current
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (on) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                            )
                            .combinedClickable {
                                current = if (on) current - week else current + week
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$week",
                            fontSize = 13.sp,
                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (current.size >= totalWeeks) "全学期" else "${current.size} 周 · " + weeksText(current.sorted(), totalWeeks),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                )
                Button(onClick = { onConfirm(current) }) { Text("确定") }
            }
        }
    }
}

@Composable
private fun ShortcutChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
