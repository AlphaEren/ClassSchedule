@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.lorat.classschedule.ui.timetable

import android.webkit.WebView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lorat.classschedule.data.DayShortNames
import com.lorat.classschedule.data.ScheduleRepository
import com.lorat.classschedule.data.TimetableExtractor
import com.lorat.classschedule.data.currentWeekOf
import com.lorat.classschedule.data.courseGroups
import com.lorat.classschedule.data.dayColumns
import com.lorat.classschedule.data.dayName
import com.lorat.classschedule.data.datesOfWeek
import com.lorat.classschedule.data.gridSectionCount
import com.lorat.classschedule.data.model.Appearance
import com.lorat.classschedule.data.model.CellStyle
import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.model.SectionTime
import com.lorat.classschedule.data.model.Timetable
import com.lorat.classschedule.data.parityLabel
import com.lorat.classschedule.data.sectionsText
import com.lorat.classschedule.data.timeText
import com.lorat.classschedule.data.weeksText
import com.lorat.classschedule.data.CourseGroup
import com.lorat.classschedule.ui.WebViewCompat
import com.lorat.classschedule.ui.argbOrNull
import com.lorat.classschedule.ui.courseColor
import com.lorat.classschedule.ui.icons.AddCircleIcon
import com.lorat.classschedule.ui.icons.ChevronDownIcon
import com.lorat.classschedule.ui.icons.ExportIcon
import com.lorat.classschedule.ui.icons.ImportIcon
import com.lorat.classschedule.ui.icons.MoreCircleIcon
import com.lorat.classschedule.ui.rememberTimetableExporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val AxisWidth = 46.dp
private val DateFormatter = DateTimeFormatter.ofPattern("yyyy/M/d")

@Composable
fun TimetableScreen(
    repository: ScheduleRepository,
    onAddSchool: () -> Unit,
    onAddCourse: (String?) -> Unit,
    onOpenScheduleSettings: () -> Unit,
    onOpenGlobalSettings: () -> Unit,
    onOpenCourseList: () -> Unit,
    onOpenSectionTimes: () -> Unit,
    onOpenImport: () -> Unit,
    onManualUpdate: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimetableViewModel = viewModel { TimetableViewModel(repository) },
) {
    val timetable by viewModel.timetable.collectAsStateWithLifecycle()
    val appearance = timetable.appearance
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var followCurrent by rememberSaveable { mutableStateOf(true) }
    var selectedWeek by rememberSaveable { mutableStateOf(1) }
    var silentUpdating by remember { mutableStateOf(false) }
    var updateFailed by remember { mutableStateOf(false) }
    var detailCourse by remember { mutableStateOf<CourseGroup?>(null) }
    var actionCourse by remember { mutableStateOf<CourseGroup?>(null) }
    var showWeekPicker by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CourseGroup?>(null) }

    val today = remember { LocalDate.now() }
    val currentWeek = remember(timetable) { currentWeekOf(timetable) }
    val totalWeeks = maxOf(1, timetable.totalWeeks)
    val week = if (followCurrent) currentWeek else selectedWeek.coerceIn(1, totalWeeks)
    val isCurrentWeek = week == currentWeek

    val exportTimetable = rememberTimetableExporter(repository) { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // Paint the timetable first, then refresh in the background so opening the app stays instant.
    LaunchedEffect(timetable.school, appearance.autoCheckUpdate) {
        if (appearance.autoCheckUpdate && viewModel.shouldAutoUpdate()) {
            delay(700)
            silentUpdating = true
        }
    }

    LaunchedEffect(updateFailed) {
        if (!updateFailed) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "自动更新失败，课表可能不是最新",
            actionLabel = "手动更新",
            duration = SnackbarDuration.Long,
        )
        updateFailed = false
        if (result == SnackbarResult.ActionPerformed) onManualUpdate()
    }

    if (silentUpdating) {
        val school = timetable.school
        if (school != null && school.scheduleUrl.isNotBlank()) {
            SilentUpdater(
                url = school.refreshUrl.ifBlank { school.scheduleUrl },
                totalWeeks = timetable.totalWeeks,
                preset = school.parserPreset,
                onResult = { courses, foundUrl ->
                    silentUpdating = false
                    if (courses != null && courses.isNotEmpty()) {
                        scope.launch {
                            if (foundUrl.isNotBlank()) repository.setRefreshUrl(foundUrl)
                            repository.replaceCourses(courses)
                        }
                    } else {
                        updateFailed = true
                    }
                },
            )
        } else {
            silentUpdating = false
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().height(2.dp)) {
                if (silentUpdating) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Header(
                date = today.format(DateFormatter),
                week = week,
                isCurrentWeek = isCurrentWeek,
                hint = if (isCurrentWeek) dayName(today.dayOfWeek.value) else "非本周 · 点此回到本周",
                subtitle = subtitleOf(timetable),
                textOverride = argbOrNull(appearance.uiTextColor),
                onWeekClick = { showWeekPicker = true },
                onHintClick = { followCurrent = true },
                onAddCourse = { onAddCourse(null) },
                onImport = onOpenImport,
                onExport = { showExport = true },
                onMore = { showMore = true },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            DateBar(
                dates = datesOfWeek(timetable, week, appearance.weekStartSunday)
                    .take(if (appearance.showWeekend) 7 else 5),
                columns = dayColumns(appearance.weekStartSunday, appearance.showWeekend),
                today = today,
                dimAll = !isCurrentWeek,
                textOverride = argbOrNull(appearance.uiTextColor),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            if (timetable.school == null && timetable.courses.isEmpty()) {
                Onboarding(
                    onAddSchool = onAddSchool,
                    onAddCourse = { onAddCourse(null) },
                    onLoadDemo = { scope.launch { repository.loadDemo() } },
                )
            } else {
                TimetableGrid(
                    timetable = timetable,
                    week = week,
                    onCourseClick = { detailCourse = it },
                    onCourseLongClick = { actionCourse = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showWeekPicker) {
        WeekPickerSheet(
            pageWeek = week,
            currentWeek = currentWeek,
            totalWeeks = totalWeeks,
            onPick = {
                followCurrent = it == currentWeek
                selectedWeek = it
                showWeekPicker = false
            },
            onSetCurrentWeek = {
                scope.launch {
                    repository.setCurrentWeek(it)
                    followCurrent = true
                    snackbarHostState.showSnackbar("已将第 $it 周设为当前周")
                }
                showWeekPicker = false
            },
            onDismiss = { showWeekPicker = false },
        )
    }

    if (showMore) {
        MoreSheet(
            timetable = timetable,
            onDismiss = { showMore = false },
            onSectionTimes = { showMore = false; onOpenSectionTimes() },
            onScheduleSettings = { showMore = false; onOpenScheduleSettings() },
            onGlobalSettings = { showMore = false; onOpenGlobalSettings() },
            onCourseList = { showMore = false; onOpenCourseList() },
            onAddCourse = { showMore = false; onAddCourse(null) },
            onImport = { showMore = false; onOpenImport() },
            onExport = { showMore = false; showExport = true },
            onAbout = { showMore = false; onAbout() },
            onSetCurrentWeek = { target -> scope.launch { repository.setCurrentWeek(target) } },
        )
    }

    if (showExport) {
        ExportSheet(
            onDismiss = { showExport = false },
            onBackup = { showExport = false; exportTimetable(false) },
            onCsv = { showExport = false; exportTimetable(true) },
        )
    }

    actionCourse?.let { group ->
        CourseActionSheet(
            group = group,
            onDismiss = { actionCourse = null },
            onEdit = { actionCourse = null; onAddCourse(group.key) },
            onDelete = { actionCourse = null; pendingDelete = group },
            onDetail = { actionCourse = null; detailCourse = group },
        )
    }

    detailCourse?.let { group ->
        CourseDetailDialog(
            group = group,
            timetable = timetable,
            onDismiss = { detailCourse = null },
            onEdit = { detailCourse = null; onAddCourse(group.key) },
            onDelete = { detailCourse = null; pendingDelete = group },
        )
    }

    pendingDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除课程") },
            text = { Text("确定删除「${group.name}」的全部 ${group.slots.size} 个时间段吗？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.deleteCourseGroup(group.key)
                        snackbarHostState.showSnackbar("已删除「${group.name}」")
                    }
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

private fun subtitleOf(timetable: Timetable): String {
    val name = timetable.semesterName.trim()
    if (name.isNotEmpty()) return name
    val school = timetable.school?.name.orEmpty()
    return if (school.isNotEmpty() && school != "示例大学") school else "第 1 学期"
}

/* ------------------------------------------------------------------ 顶部 */

@Composable
private fun Header(
    date: String,
    week: Int,
    isCurrentWeek: Boolean,
    hint: String,
    subtitle: String,
    textOverride: Color?,
    onWeekClick: () -> Unit,
    onHintClick: () -> Unit,
    onAddCourse: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onMore: () -> Unit,
) {
    val tint = textOverride ?: MaterialTheme.colorScheme.onSurface
    val muted = textOverride?.copy(alpha = 0.7f) ?: MaterialTheme.colorScheme.onSurfaceVariant
    val accent = textOverride ?: MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(date, fontSize = 13.sp, color = muted, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).combinedClickable(onClick = onWeekClick),
                ) {
                    Text("第 $week 周", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    ChevronDownIcon(tint = tint, size = 16.dp)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = hint,
                    fontSize = 12.sp,
                    color = if (isCurrentWeek) muted else accent,
                    modifier = Modifier.combinedClickable(
                        onClick = { if (!isCurrentWeek) onHintClick() },
                    ),
                )
            }
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 11.sp, color = muted)
        }
        HeaderButton(onAddCourse) { AddCircleIcon(tint = tint) }
        HeaderButton(onImport) { ImportIcon(tint = tint) }
        HeaderButton(onExport) { ExportIcon(tint = tint) }
        HeaderButton(onMore) { MoreCircleIcon(tint = tint) }
    }
}

@Composable
private fun HeaderButton(onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
private fun DateBar(
    dates: List<LocalDate>,
    columns: List<Int>,
    today: LocalDate,
    dimAll: Boolean,
    textOverride: Color?,
) {
    val primary = textOverride ?: MaterialTheme.colorScheme.primary
    val muted = textOverride?.copy(alpha = 0.7f) ?: MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = textOverride ?: MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().height(52.dp)) {
        Column(
            modifier = Modifier.width(AxisWidth).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("${today.monthValue}月", fontSize = 10.sp, color = muted)
            Text("${today.dayOfMonth}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primary)
        }
        dates.forEachIndexed { index, date ->
            val day = columns.getOrElse(index) { index + 1 }
            val isToday = date == today
            val dim = dimAll && !isToday
            Column(
                modifier = Modifier.weight(1f).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    DayShortNames.getOrElse(day - 1) { "?" },
                    fontSize = 11.sp,
                    color = if (dim) muted.copy(alpha = 0.45f) else muted,
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isToday) primary.copy(alpha = 0.15f) else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${date.dayOfMonth}",
                        fontSize = 13.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isToday -> primary
                            dim -> onSurface.copy(alpha = 0.35f)
                            else -> onSurface
                        },
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ 课表网格 */

private data class Placed(
    val course: Course,
    val col: Int,
    val slice: Int,
    val slices: Int,
    val thisWeek: Boolean,
    val conflict: Boolean,
)

private fun placement(timetable: Timetable, columns: List<Int>, week: Int): List<Placed> {
    val appearance = timetable.appearance
    val out = mutableListOf<Placed>()
    for ((col, day) in columns.withIndex()) {
        val dayCourses = timetable.courses.filter { it.dayOfWeek == day }
        for (course in dayCourses) {
            val thisWeek = course.matchesWeek(week)
            if (!thisWeek && !appearance.showOtherWeeks) continue
            val sameRange = dayCourses.filter {
                it.startSection == course.startSection && it.endSection == course.endSection
            }
            val conflict = dayCourses.any { other ->
                other !== course && other.startSection <= course.endSection && course.startSection <= other.endSection
            }
            out.add(
                Placed(
                    course = course,
                    col = col,
                    slice = sameRange.indexOf(course).coerceAtLeast(0),
                    slices = maxOf(1, sameRange.size),
                    thisWeek = thisWeek,
                    conflict = conflict,
                ),
            )
        }
    }
    return out
}

@Composable
private fun TimetableGrid(
    timetable: Timetable,
    week: Int,
    onCourseClick: (CourseGroup) -> Unit,
    onCourseLongClick: (CourseGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appearance = timetable.appearance
    val columns = dayColumns(appearance.weekStartSunday, appearance.showWeekend)
    val sections = timetable.gridSectionCount()
    val rowHeight = appearance.cellHeight.dp
    val groups = remember(timetable.courses) { timetable.courseGroups().associateBy { it.key } }
    val todayCol = columns.indexOf(LocalDate.now().dayOfWeek.value)
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (appearance.gridLines) 0.12f else 0f)
    val primary = MaterialTheme.colorScheme.primary

    Column(modifier.verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth()) {
            AxisColumn(timetable = timetable, sections = sections, rowHeight = rowHeight)
            BoxWithConstraints(
                Modifier.weight(1f).height(rowHeight * sections),
            ) {
                val colWidth = maxWidth / columns.size
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        val rowPx = rowHeight.toPx()
                        val colPx = colWidth.toPx()
                        val lineWidthPx = 1.dp.toPx()
                        if (todayCol >= 0) {
                            drawRect(
                                color = primary.copy(alpha = 0.05f),
                                topLeft = Offset(colPx * todayCol, 0f),
                                size = Size(colPx, size.height),
                            )
                        }
                        if (appearance.gridLines) {
                            for (s in 0..sections) {
                                val y = (rowPx * s).coerceAtMost(size.height - lineWidthPx)
                                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), lineWidthPx)
                            }
                            for (c in 1 until columns.size) {
                                val x = colPx * c
                                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), lineWidthPx)
                            }
                        }
                    },
                )
                val placed = remember(timetable.courses, columns, week, appearance) {
                    placement(timetable, columns, week)
                }
                for (p in placed) {
                    val group = groups[p.course.groupKey] ?: continue
                    val sliceWidth = colWidth / p.slices
                    Box(
                        Modifier
                            .offset(
                                x = colWidth * p.col + sliceWidth * p.slice,
                                y = rowHeight * (p.course.startSection - 1),
                            )
                            .width(sliceWidth)
                            .height(rowHeight * (p.course.endSection - p.course.startSection + 1))
                            .padding(1.5.dp),
                    ) {
                        CourseCell(
                            course = p.course,
                            group = group,
                            thisWeek = p.thisWeek,
                            conflict = p.conflict,
                            appearance = appearance,
                            span = p.course.endSection - p.course.startSection + 1,
                            totalWeeks = maxOf(1, timetable.totalWeeks),
                            sectionTimes = timetable.sectionTimes,
                            onClick = { onCourseClick(group) },
                            onLongClick = { onCourseLongClick(group) },
                        )
                    }
                }
                if (timetable.courses.isEmpty() && appearance.showEmptyImage) {
                    Box(Modifier.fillMaxSize().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                        Text(
                            "还没有课程\n点右上角 ⊕ 手动添加，或 ⤓ 从教务/文件导入",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height((appearance.extraBottomSpace + 24).dp))
    }
}

@Composable
private fun AxisColumn(timetable: Timetable, sections: Int, rowHeight: Dp) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (!timetable.appearance.showAxis) return
    Column(Modifier.width(AxisWidth)) {
        for (s in 1..sections) {
            val section = timetable.sectionTimes.firstOrNull { it.section == s }
            Column(
                modifier = Modifier.fillMaxWidth().height(rowHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("$s", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = muted)
                if (!timetable.appearance.axisShowOnlySection && section != null) {
                    Text(section.start, fontSize = 9.sp, color = muted.copy(alpha = 0.8f))
                    Text(section.end, fontSize = 9.sp, color = muted.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
private fun CourseCell(
    course: Course,
    group: CourseGroup,
    thisWeek: Boolean,
    conflict: Boolean,
    appearance: Appearance,
    span: Int,
    totalWeeks: Int,
    sectionTimes: List<SectionTime>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val base = courseColor(group.colorIndex)
    val overrideText = argbOrNull(appearance.textColor)
    val overrideBorder = argbOrNull(appearance.borderColor)
    val dim = if (thisWeek) 1f else (appearance.otherWeekOpacity / 100f).coerceIn(0.05f, 1f)
    val fill = when (appearance.cellStyle) {
        CellStyle.FILLED -> appearance.opacity / 100f
        CellStyle.OUTLINE -> 0.05f
        else -> appearance.opacity / 100f * 0.26f
    }
    val background = base.copy(alpha = fill * dim)
    val titleColor = when {
        overrideText != null -> overrideText
        appearance.cellStyle == CellStyle.FILLED -> Color.White
        else -> base
    }
    val metaColor = when {
        overrideText != null -> overrideText.copy(alpha = 0.8f)
        appearance.cellStyle == CellStyle.FILLED -> Color.White.copy(alpha = 0.88f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = RoundedCornerShape(appearance.cornerRadius.dp)
    val borderColor = overrideBorder ?: base
    val wantsBorder = appearance.cellStyle == CellStyle.OUTLINE || appearance.dashedBorder || overrideBorder != null

    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(background)
            .then(
                if (wantsBorder) {
                    Modifier.dashedRoundedBorder(
                        color = borderColor.copy(alpha = (if (appearance.borderUseCourseColor || overrideBorder != null) 0.9f else 0.35f) * dim),
                        width = 1.dp,
                        radius = appearance.cornerRadius.dp,
                        dashed = appearance.dashedBorder,
                    )
                } else {
                    Modifier
                },
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 4.dp)) {
            Text(
                text = group.name,
                fontSize = appearance.fontSize.sp,
                lineHeight = (appearance.fontSize + 2).sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                maxLines = if (span >= 3) 5 else if (span == 2) 3 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            val metaSize = (appearance.fontSize - 3).coerceAtLeast(7).sp
            if (appearance.showTime) {
                val time = timeText(course, sectionTimes)
                if (time.isNotBlank()) Text(time, fontSize = metaSize, color = metaColor, maxLines = 1)
            }
            if (appearance.showTeacher && group.teacher.isNotBlank()) {
                Text(group.teacher, fontSize = metaSize, color = metaColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (appearance.showLocation && group.location.isNotBlank()) {
                Text("@${group.location}", fontSize = metaSize, color = metaColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val badges = buildList {
                if (!thisWeek) add("非本周")
                parityLabel(group.weeks, totalWeeks)?.let { add(it) }
            }
            if (badges.isNotEmpty()) {
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (badge in badges) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(titleColor.copy(alpha = 0.16f))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) { Text(badge, fontSize = 9.sp, color = titleColor) }
                    }
                }
            }
        }
        if (conflict) {
            androidx.compose.foundation.Canvas(Modifier.align(Alignment.BottomEnd).size(12.dp)) {
                val path = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(size.width, size.height)
                    lineTo(size.width, 0f)
                    close()
                }
                drawPath(path, color = titleColor.copy(alpha = 0.85f))
            }
        }
    }
}

private fun Modifier.dashedRoundedBorder(
    color: Color,
    width: Dp,
    radius: Dp,
    dashed: Boolean,
): Modifier = drawBehind {
    val strokeWidth = width.toPx()
    val stroke = Stroke(
        width = strokeWidth,
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f) else null,
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
        size = Size(size.width - strokeWidth, size.height - strokeWidth),
        cornerRadius = CornerRadius(radius.toPx() - strokeWidth / 2f),
        style = stroke,
    )
}

/* ------------------------------------------------------------------ 面板与弹窗 */

@Composable
private fun WeekPickerSheet(
    pageWeek: Int,
    currentWeek: Int,
    totalWeeks: Int,
    onPick: (Int) -> Unit,
    onSetCurrentWeek: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var pending by remember { mutableStateOf(pageWeek) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("选择周次", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "当前周为第 $currentWeek 周 · 共 $totalWeeks 周",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.fillMaxWidth().height(260.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items((1..totalWeeks).toList()) { w ->
                    val selected = w == pending
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    selected -> MaterialTheme.colorScheme.primary
                                    w == currentWeek -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                            )
                            .combinedClickable(onClick = { pending = w }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$w",
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (w == currentWeek) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onSetCurrentWeek(pending) }, modifier = Modifier.weight(1f)) {
                    Text("把第 $pending 周设为当前周")
                }
                Button(onClick = { onPick(pending) }, modifier = Modifier.weight(1f)) { Text("查看该周") }
            }
        }
    }
}

@Composable
private fun MoreSheet(
    timetable: Timetable,
    onDismiss: () -> Unit,
    onSectionTimes: () -> Unit,
    onScheduleSettings: () -> Unit,
    onGlobalSettings: () -> Unit,
    onCourseList: () -> Unit,
    onAddCourse: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onAbout: () -> Unit,
    onSetCurrentWeek: (Int) -> Unit,
) {
    val currentWeek = currentWeekOf(timetable)
    var slider by remember { mutableStateOf(currentWeek.toFloat()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("周数", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Slider(
                    value = slider,
                    onValueChange = { slider = it },
                    valueRange = 1f..maxOf(1, timetable.totalWeeks).toFloat(),
                    steps = maxOf(0, timetable.totalWeeks - 2),
                    modifier = Modifier.weight(1f),
                )
                Text("第 ${slider.toInt()} 周", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onSetCurrentWeek(slider.toInt().coerceIn(1, maxOf(1, timetable.totalWeeks))) },
                    modifier = Modifier.weight(1f),
                ) { Text("修改当前周") }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("导入课表") }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Spacer(Modifier.height(16.dp))
            val items = listOf(
                "上课时间" to onSectionTimes,
                "课表设置" to onScheduleSettings,
                "已添课程" to onCourseList,
                "添加课程" to onAddCourse,
                "导出课表" to onExport,
                "全局设置" to onGlobalSettings,
                "关于" to onAbout,
            )
            for (row in items.chunked(4)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((label, action) in row) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .combinedClickable(onClick = action)
                                .padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) { Text(label, fontSize = 12.sp) }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ExportSheet(onDismiss: () -> Unit, onBackup: () -> Unit, onCsv: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("导出课表", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            SheetRow("备份文件", "完整课表数据（JSON），可用「导入课表」还原", onBackup)
            SheetRow("Excel / CSV 文件", "与 WakeUp 课程表模板列序一致的表格", onCsv)
            Text(
                "导出日历文件（.ics）与分享口令尚未实现。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SheetRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CourseActionSheet(
    group: CourseGroup,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDetail: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(group.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            SheetRow("课程详情", "查看全部时间段与周次", onDetail)
            SheetRow("编辑课程", "修改名称、颜色与时间段", onEdit)
            SheetRow("删除课程", "删除这门课的全部时间段", onDelete)
        }
    }
}

@Composable
private fun CourseDetailDialog(
    group: CourseGroup,
    timetable: Timetable,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(group.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                group.slots.forEach { slot ->
                    DetailRow("时间", "${dayName(slot.dayOfWeek)} ${sectionsText(slot.startSection, slot.endSection)}")
                    if (timetable.appearance.showTime) {
                        DetailRow("节次时间", timeText(slot, timetable.sectionTimes))
                    }
                }
                DetailRow("周次", group.slots.map { weeksText(it.weeks, timetable.totalWeeks) }.distinct().joinToString(" / "))
                if (group.teacher.isNotBlank()) DetailRow("教师", group.teacher)
                if (group.location.isNotBlank()) DetailRow("地点", group.location)
                if (group.credit.isNotBlank()) DetailRow("学分", group.credit)
                if (group.note.isNotBlank()) DetailRow("备注", group.note)
            }
        },
        confirmButton = { TextButton(onClick = onEdit) { Text("编辑") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("删除") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row {
        Text(
            label,
            modifier = Modifier.width(60.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Text(value, fontSize = 13.sp)
    }
}

@Composable
private fun Onboarding(onAddSchool: () -> Unit, onAddCourse: () -> Unit, onLoadDemo: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("还没有课表", style = MaterialTheme.typography.titleLarge)
                Text(
                    "可以从学校教务系统导入，也可以手动添加课程。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Button(onClick = onAddSchool, modifier = Modifier.fillMaxWidth()) { Text("从学校教务导入") }
                OutlinedButton(onClick = onAddCourse, modifier = Modifier.fillMaxWidth()) { Text("手动添加课程") }
                OutlinedButton(onClick = onLoadDemo, modifier = Modifier.fillMaxWidth()) { Text("载入示例课表看看效果") }
            }
        }
    }
}

/**
 * Headless WebView that quietly re-runs the scraper using the stored session cookies.
 * Reports null if the page could not be read (e.g. the login expired).
 */
@Composable
private fun SilentUpdater(
    url: String,
    totalWeeks: Int,
    preset: String,
    onResult: (List<Course>?, String) -> Unit,
) {
    var answered by remember { mutableStateOf(false) }

    fun answer(courses: List<Course>?, foundUrl: String) {
        if (answered) return
        answered = true
        onResult(courses, foundUrl)
    }

    LaunchedEffect(url) {
        delay(15_000)
        if (!answered) answer(null, "")
    }

    AndroidView(
        modifier = Modifier.size(1.dp),
        factory = { context ->
            WebView(context).apply {
                var attempt = 0
                fun runScrape() {
                    evaluateJavascript(TimetableExtractor.scraperJs(preset)) { raw ->
                        val result = TimetableExtractor.parseScrapeResult(raw)
                        val courses = if (result.ok) {
                            TimetableExtractor.coursesFrom(result, totalWeeks)
                        } else {
                            emptyList()
                        }
                        when {
                            courses.isNotEmpty() || answered -> answer(courses.ifEmpty { null }, result.foundUrl)
                            attempt < 2 -> {
                                attempt += 1
                                postDelayed({ runScrape() }, 1800)
                            }
                            else -> answer(null, result.foundUrl)
                        }
                    }
                }
                WebViewCompat.configure(
                    web = this,
                    headless = true,
                    onPageFinished = { postDelayed({ runScrape() }, 1200) },
                    onError = { answer(null, "") },
                )
                loadUrl(url)
            }
        },
    )
}
