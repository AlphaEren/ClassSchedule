@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.lorat.classschedule.ui.settings

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.lorat.classschedule.i18n.Lang
import com.lorat.classschedule.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.lorat.classschedule.data.ScheduleRepository
import com.lorat.classschedule.data.currentWeekOf
import com.lorat.classschedule.data.courseGroups
import com.lorat.classschedule.data.model.CellStyle
import com.lorat.classschedule.data.model.SectionTime
import com.lorat.classschedule.data.shiftTime
import kotlinx.coroutines.launch

/** 课表设置: 课表数据 + 课表外观 (the two pages WakeUp puts behind the ⋮ panel). */
@Composable
fun ScheduleSettingsScreen(
    repository: ScheduleRepository,
    onBack: () -> Unit,
    onOpenSectionTimes: () -> Unit,
    onOpenCourseList: () -> Unit,
) {
    val timetable by repository.timetable.collectAsStateWithLifecycle()
    val appearance = timetable.appearance
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var semesterName by remember(timetable.semesterName) { mutableStateOf(timetable.semesterName) }
    var startDate by remember(timetable.semesterStartDate) { mutableStateOf(timetable.semesterStartDate) }
    var totalWeeks by remember(timetable.totalWeeks) { mutableStateOf(timetable.totalWeeks.toString()) }
    var sectionCount by remember(timetable.sectionTimes.size) { mutableStateOf(timetable.sectionTimes.size.toString()) }
    var showWeekSheet by remember { mutableStateOf(false) }

    val groupCount = remember(timetable.courses) { timetable.courseGroups().size }
    val currentWeek = currentWeekOf(timetable)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("课表设置") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
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
            SettingsCard("课表数据") {
                OutlinedTextField(
                    value = semesterName,
                    onValueChange = { semesterName = it },
                    label = { Text("课表名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsNavRow(
                    title = "上课时间",
                    value = "${timetable.sectionTimes.size} 节",
                    onClick = onOpenSectionTimes,
                )
                OutlinedTextField(
                    value = startDate,
                    onValueChange = { startDate = it },
                    label = { Text("学期开始日期（第 1 周第一天，yyyy-MM-dd）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsNavRow(
                    title = "当前周",
                    value = "第 $currentWeek 周",
                    subtitle = "点此修改当前周（会顺延学期开始日期）",
                    onClick = { showWeekSheet = true },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = sectionCount,
                        onValueChange = { sectionCount = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("一天课程节数") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = totalWeeks,
                        onValueChange = { totalWeeks = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("学期周数") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                SettingsSwitchRow(
                    title = "周日为每周第一天",
                    checked = appearance.weekStartSunday,
                    onChange = { scope.launch { repository.updateAppearance(appearance.copy(weekStartSunday = it)) } },
                )
                SettingsNavRow(
                    title = "管理已添加课程",
                    value = "$groupCount 门",
                    onClick = onOpenCourseList,
                )
                Button(
                    onClick = {
                        scope.launch {
                            repository.updateSemester(
                                semesterName = semesterName.trim(),
                                semesterStartDate = startDate.trim(),
                                totalWeeks = totalWeeks.toIntOrNull() ?: timetable.totalWeeks,
                            )
                            val target = (sectionCount.toIntOrNull() ?: timetable.sectionTimes.size).coerceIn(1, 24)
                            if (target != timetable.sectionTimes.size) {
                                repository.updateSectionTimes(resizeSections(timetable.sectionTimes, target))
                            }
                            snackbar.showSnackbar(Lang.tr("已保存课表数据"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("保存课表数据") }
            }

            SettingsCard("课表外观") {
                val styles = listOf(CellStyle.LIGHT, CellStyle.FILLED, CellStyle.OUTLINE)
                SettingsChoiceRow(
                    title = "格子样式",
                    options = listOf(
                        0 to "浅色",
                        1 to "填充",
                        2 to "描边",
                    ),
                    selected = styles.indexOf(appearance.cellStyle).coerceAtLeast(0),
                    onSelect = { index ->
                        scope.launch { repository.updateAppearance(appearance.copy(cellStyle = styles[index])) }
                    },
                )
                SettingsColorRow("课程文字颜色", appearance.textColor) { value ->
                    scope.launch { repository.updateAppearance(appearance.copy(textColor = value)) }
                }
                SettingsColorRow("格子边框颜色", appearance.borderColor) { value ->
                    scope.launch { repository.updateAppearance(appearance.copy(borderColor = value)) }
                }
                SettingsColorRow("界面文字颜色", appearance.uiTextColor) { value ->
                    scope.launch { repository.updateAppearance(appearance.copy(uiTextColor = value)) }
                }
                SettingsSliderRow("格子高度", appearance.cellHeight, 40..160, " dp") { value ->
                    scope.launch { repository.updateAppearance(appearance.copy(cellHeight = value)) }
                }
                SettingsSliderRow("圆角半径", appearance.cornerRadius, 0..30) { value ->
                    scope.launch { repository.updateAppearance(appearance.copy(cornerRadius = value)) }
                }
                SettingsSliderRow("不透明度", appearance.opacity, 5..100, "%") { value ->
                    scope.launch { repository.updateAppearance(appearance.copy(opacity = value)) }
                }
                SettingsSliderRow("非本周课程不透明比", appearance.otherWeekOpacity, 5..100, "%") { value ->
                    scope.launch { repository.updateAppearance(appearance.copy(otherWeekOpacity = value)) }
                }
                SettingsSliderRow("字体大小", appearance.fontSize, 8..20) { value ->
                    scope.launch { repository.updateAppearance(appearance.copy(fontSize = value)) }
                }
                SettingsSwitchRow("格子内显示上课时间", appearance.showTime) {
                    scope.launch { repository.updateAppearance(appearance.copy(showTime = it)) }
                }
                SettingsSwitchRow("格子内显示上课地点", appearance.showLocation) {
                    scope.launch { repository.updateAppearance(appearance.copy(showLocation = it)) }
                }
                SettingsSwitchRow("格子内显示授课老师", appearance.showTeacher) {
                    scope.launch { repository.updateAppearance(appearance.copy(showTeacher = it)) }
                }
                SettingsSwitchRow("格子使用虚线边框", appearance.dashedBorder) {
                    scope.launch { repository.updateAppearance(appearance.copy(dashedBorder = it)) }
                }
                SettingsSwitchRow("边框使用格子颜色", appearance.borderUseCourseColor) {
                    scope.launch { repository.updateAppearance(appearance.copy(borderUseCourseColor = it)) }
                }
                SettingsSwitchRow("显示网格辅助线", appearance.gridLines) {
                    scope.launch { repository.updateAppearance(appearance.copy(gridLines = it)) }
                }
                SettingsSwitchRow("显示非本周课程", appearance.showOtherWeeks, "关闭后本周没有的课直接隐藏") {
                    scope.launch { repository.updateAppearance(appearance.copy(showOtherWeeks = it)) }
                }
                SettingsSwitchRow("显示周六周日", appearance.showWeekend) {
                    scope.launch { repository.updateAppearance(appearance.copy(showWeekend = it)) }
                }
                SettingsSwitchRow("显示左侧时间轴", appearance.showAxis) {
                    scope.launch { repository.updateAppearance(appearance.copy(showAxis = it)) }
                }
                SettingsSwitchRow("时间轴只显示节次", appearance.axisShowOnlySection) {
                    scope.launch { repository.updateAppearance(appearance.copy(axisShowOnlySection = it)) }
                }
                Text(
                    "外观改完立即生效并保存，回到课表即可看到效果。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showWeekSheet) {
        CurrentWeekSheet(
            currentWeek = currentWeek,
            totalWeeks = maxOf(1, timetable.totalWeeks),
            onPick = { week ->
                scope.launch {
                    repository.setCurrentWeek(week)
                    snackbar.showSnackbar(Lang.tr("已将第 $week 周设为当前周"))
                }
                showWeekSheet = false
            },
            onDismiss = { showWeekSheet = false },
        )
    }
}

/** Grow/shrink the section list when 一天课程节数 changes. */
private fun resizeSections(current: List<SectionTime>, target: Int): List<SectionTime> {
    val out = current.toMutableList()
    while (out.size < target) {
        val last = out.lastOrNull()
        val start = last?.let { shiftTime(it.end, 10) } ?: "08:00"
        out.add(SectionTime(out.size + 1, start, shiftTime(start, 45)))
    }
    while (out.size > target && out.size > 1) out.removeAt(out.size - 1)
    return out.mapIndexed { index, st -> st.copy(section = index + 1) }
}

@Composable
private fun CurrentWeekSheet(
    currentWeek: Int,
    totalWeeks: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("修改当前周", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "当前为第 $currentWeek 周。选择后会把学期开始日期顺延，使所选周包含今天。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items((1..totalWeeks).toList()) { week ->
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (week == currentWeek) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                            )
                            .combinedClickable { onPick(week) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$week",
                            color = if (week == currentWeek) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
