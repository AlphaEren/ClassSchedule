@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.lorat.classschedule.ui.timetable

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import com.lorat.classschedule.i18n.Lang
import com.lorat.classschedule.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lorat.classschedule.data.DayShortNames
import com.lorat.classschedule.data.AppLog
import com.lorat.classschedule.data.ScheduleRepository
import com.lorat.classschedule.data.TimetableExtractor
import com.lorat.classschedule.data.currentWeekOf
import com.lorat.classschedule.data.courseGroups
import com.lorat.classschedule.data.dayColumns
import com.lorat.classschedule.data.dayName
import com.lorat.classschedule.data.datesOfWeek
import com.lorat.classschedule.data.gridSectionCount
import com.lorat.classschedule.data.placement
import com.lorat.classschedule.data.model.Appearance
import com.lorat.classschedule.data.model.CellStyle
import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.model.SectionTime
import com.lorat.classschedule.data.model.Timetable
import com.lorat.classschedule.data.parityLabel
import com.lorat.classschedule.data.progressText
import com.lorat.classschedule.data.sectionsText
import com.lorat.classschedule.data.timeText
import com.lorat.classschedule.data.weeksText
import com.lorat.classschedule.data.CourseGroup
import com.lorat.classschedule.ui.WebViewCompat
import com.lorat.classschedule.ui.course.CourseDetailSheet
import com.lorat.classschedule.ui.course.DeleteScope
import com.lorat.classschedule.ui.EmptyTimetableIllustration
import com.lorat.classschedule.ui.TimetableImage
import com.lorat.classschedule.ui.argbOrNull
import com.lorat.classschedule.ui.autoColorMap
import com.lorat.classschedule.ui.courseColorFor
import com.lorat.classschedule.ui.icons.AddCircleIcon
import com.lorat.classschedule.ui.icons.ChevronDownIcon
import com.lorat.classschedule.ui.icons.ChevronLeftIcon
import com.lorat.classschedule.ui.icons.ChevronRightIcon
import com.lorat.classschedule.ui.icons.ExportIcon
import com.lorat.classschedule.ui.icons.ImportIcon
import com.lorat.classschedule.ui.icons.MoreCircleIcon
import com.lorat.classschedule.ui.rememberTimetableExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val AxisWidth = 46.dp
private val DateFormatter = DateTimeFormatter.ofPattern("yyyy/M/d")

@Composable
fun TimetableScreen(
    repository: ScheduleRepository,
    onAddSchool: () -> Unit,
    onAddCourse: (String?) -> Unit,
    /** 长按课表空格子：带着星期与节次直接新建课程。 */
    onAddCourseAt: (Int, Int) -> Unit,
    onOpenScheduleSettings: () -> Unit,
    onOpenGlobalSettings: () -> Unit,
    onOpenCourseList: () -> Unit,
    onOpenSectionTimes: () -> Unit,
    onOpenImport: () -> Unit,
    onManualUpdate: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 只有一屏 7 列 + 4 个顶部图标，系统字体调到 1.5x 以上会把网格挤坏：
    // 主界面限到 1.25x（设置页仍然跟随系统，可滚动）。
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale.coerceIn(0.9f, 1.25f)),
    ) {
        TimetableScreenContent(
            repository = repository,
            onAddSchool = onAddSchool,
            onAddCourse = onAddCourse,
            onAddCourseAt = onAddCourseAt,
            onOpenScheduleSettings = onOpenScheduleSettings,
            onOpenGlobalSettings = onOpenGlobalSettings,
            onOpenCourseList = onOpenCourseList,
            onOpenSectionTimes = onOpenSectionTimes,
            onOpenImport = onOpenImport,
            onManualUpdate = onManualUpdate,
            onAbout = onAbout,
            modifier = modifier,
        )
    }
}

@Composable
private fun TimetableScreenContent(
    repository: ScheduleRepository,
    onAddSchool: () -> Unit,
    onAddCourse: (String?) -> Unit,
    onAddCourseAt: (Int, Int) -> Unit,
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
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var followCurrent by rememberSaveable { mutableStateOf(true) }
    var selectedWeek by rememberSaveable { mutableStateOf(1) }
    var silentUpdating by remember { mutableStateOf(false) }
    var updateFailed by remember { mutableStateOf(false) }
    var detailTarget by remember { mutableStateOf<Pair<CourseGroup, Course>?>(null) }
    var actionCourse by remember { mutableStateOf<CourseGroup?>(null) }
    var showWeekPicker by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CourseGroup?>(null) }

    // 「保存到相册」：Android 10 起走 MediaStore 不需要权限，9 及以下临时申请存储权限
    var pendingSave by remember { mutableStateOf<(() -> Unit)?>(null) }
    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingSave
        pendingSave = null
        if (granted) {
            action?.invoke()
        } else {
            scope.launch { snackbarHostState.showSnackbar(Lang.tr("需要存储权限才能保存到相册")) }
        }
    }
    // 保存动作在拿到权限后才真正执行，所以先入队、再申请
    fun saveImageToGallery(targetWeek: Int) {
        val snapshot = timetable
        scope.launch {
            val name = withContext(Dispatchers.IO) {
                runCatching { TimetableImage.saveToGallery(context, snapshot, targetWeek) }.getOrNull()
            }
            snackbarHostState.showSnackbar(
                if (name != null) Lang.tr("已保存到相册：$name") else Lang.tr("保存到相册失败"),
            )
        }
    }

    // 日期不能只在首次组合时取一次：进程驻留过夜后，日期栏、当前周、今天高亮都要跟着走。
    var today by remember { mutableStateOf(LocalDate.now()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) today = LocalDate.now()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // App 一直开着也不过夜：零点后自己醒一次，不必重启也不用回前台
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay().plusSeconds(2)
            delay(Duration.between(now, nextMidnight).toMillis().coerceIn(1_000L, 86_400_000L))
            today = LocalDate.now()
        }
    }
    val currentWeek = remember(timetable, today) { currentWeekOf(timetable, today) }
    val totalWeeks = maxOf(1, timetable.totalWeeks)
    val groupCount = remember(timetable.courses) { timetable.courseGroups().size }
    val week = if (followCurrent) currentWeek else selectedWeek.coerceIn(1, totalWeeks)

    /** 切到某一周：离开当前周后就不再跟随「本周」，回到当前周则重新跟随。 */
    fun goToWeek(target: Int) {
        val clamped = target.coerceIn(1, totalWeeks)
        followCurrent = clamped == currentWeek
        selectedWeek = clamped
    }
    val isCurrentWeek = week == currentWeek

    // 横屏时标题区能占掉大半个屏幕：课表上滑就把顶部收起来，往下滑（或滑回顶部）再放回来。
    // 只按「手指方向 + 累计位移」判断，不看 ScrollState 的数值：顶栏收起会改变可视高度，
    // 滚动值会被布局变化牵连（到底部时尤其明显），按数值判断会来回弹跳。
    val gridScroll = rememberScrollState()
    var topBarVisible by remember { mutableStateOf(true) }
    val dragAccum = remember { mutableFloatStateOf(0f) }
    val hideStep = with(LocalDensity.current) { 16.dp.toPx() }
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    val current = dragAccum.floatValue
                    dragAccum.floatValue = if (current * available.y > 0) current + available.y else available.y
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (gridScroll.value == 0) topBarVisible = true
                return Velocity.Zero
            }
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { dragAccum.floatValue }.collect { accumulated ->
            when {
                accumulated <= -hideStep && topBarVisible -> topBarVisible = false
                accumulated >= hideStep && !topBarVisible -> topBarVisible = true
            }
        }
    }

    val exportTimetable = rememberTimetableExporter(repository) { message ->
        scope.launch { snackbarHostState.showSnackbar(Lang.tr(message)) }
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
            message = Lang.tr("自动更新失败，课表可能不是最新"),
            actionLabel = Lang.tr("手动更新"),
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
                onResult = { courses, foundUrl, result ->
                    silentUpdating = false
                    if (courses != null && courses.isNotEmpty()) {
                        AppLog.i("Update", "静默更新成功：课程 ${courses.size}")
                        scope.launch {
                            if (foundUrl.isNotBlank()) repository.setRefreshUrl(foundUrl)
                            repository.applyImport(
                                courses = courses,
                                sectionTimes = result?.sectionTimes.orEmpty(),
                                semesterStart = result?.semesterStart.orEmpty(),
                                currentWeek = result?.currentWeek ?: 0,
                                weekCount = result?.weekCount ?: 0,
                            )
                        }
                    } else {
                        updateFailed = true
                        AppLog.w(
                            "Update",
                            "静默更新失败 mode=${result?.mode.orEmpty()} system=${result?.system.orEmpty()} " +
                                "原因=${result?.let { TimetableExtractor.failureMessage(it) } ?: "识别失败"}",
                        )
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollConnection),
        ) {
            Box(Modifier.fillMaxWidth().height(2.dp)) {
                if (silentUpdating) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            AnimatedVisibility(
                visible = topBarVisible,
                enter = expandVertically(expandFrom = Alignment.Top) +
                    slideInVertically(initialOffsetY = { -it }),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) +
                    slideOutVertically(targetOffsetY = { -it }),
            ) {
                Column {
                    Header(
                        date = today.format(DateFormatter),
                        week = week,
                        totalWeeks = totalWeeks,
                        isCurrentWeek = isCurrentWeek,
                        subtitle = subtitleOf(timetable),
                        progress = if (timetable.semesterStartDate.isBlank()) {
                            "未设置开学日期 · 点此说明现在是第几周"
                        } else {
                            "本周 ${timetable.coursesForWeek(week).size} 节课 · 全学期 $groupCount 门 · 共 $totalWeeks 周"
                        },
                        textOverride = argbOrNull(appearance.uiTextColor),
                        onWeekClick = { showWeekPicker = true },
                        onWeekStep = { delta -> goToWeek(week + delta) },
                        onBackToCurrentWeek = { followCurrent = true },
                        onProgressClick = { showWeekPicker = true },
                        onAddCourse = { onAddCourse(null) },
                        onImport = onOpenImport,
                        onExport = { showExport = true },
                        onMore = { showMore = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }
            }
            DateBar(
                dates = datesOfWeek(timetable, week, appearance.weekStartSunday)
                    .take(if (appearance.showWeekend) 7 else 5),
                columns = dayColumns(appearance.weekStartSunday, appearance.showWeekend),
                today = today,
                dimAll = !isCurrentWeek,
                textOverride = argbOrNull(appearance.uiTextColor),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            // 只要还没有课程，就按「未导入」的样子显示（不再显示空网格 + 提示文字）
            if (timetable.courses.isEmpty()) {
                Onboarding(
                    onAddSchool = onAddSchool,
                    onAddCourse = { onAddCourse(null) },
                    onLoadDemo = { scope.launch { repository.loadDemo() } },
                )
            } else {
                TimetableGrid(
                    timetable = timetable,
                    week = week,
                    today = today,
                    scrollState = gridScroll,
                    onWeekChange = { target ->
                        if (target != week) {
                            followCurrent = target == currentWeek
                            selectedWeek = target
                        }
                    },
                    onCourseClick = { group, slot -> detailTarget = group to slot },
                    onCourseLongClick = { actionCourse = it },
                    onEmptyLongPress = { day, section -> onAddCourseAt(day, section) },
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
                    snackbarHostState.showSnackbar(Lang.tr("已将第 $it 周设为当前周"))
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
            onToggleOtherWeeks = { show ->
                scope.launch { repository.updateAppearance(appearance.copy(showOtherWeeks = show)) }
            },
        )
    }

    if (showExport) {
        ExportSheet(
            week = week,
            onDismiss = { showExport = false },
            onBackup = { showExport = false; exportTimetable(false) },
            onCsv = { showExport = false; exportTimetable(true) },
            onShare = {
                showExport = false
                val snapshot = timetable
                val targetWeek = week
                scope.launch {
                    val ok = withContext(Dispatchers.Default) {
                        runCatching { TimetableImage.share(context, snapshot, targetWeek) }.getOrDefault(false)
                    }
                    snackbarHostState.showSnackbar(
                        if (ok) Lang.tr("已生成第 $targetWeek 周课表图片") else Lang.tr("生成课表图片失败"),
                    )
                }
            },
            onSave = {
                showExport = false
                val targetWeek = week
                val action = { saveImageToGallery(targetWeek) }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingSave = action
                    storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    action()
                }
            },
        )
    }

    actionCourse?.let { group ->
        CourseActionSheet(
            group = group,
            onDismiss = { actionCourse = null },
            onEdit = { actionCourse = null; onAddCourse(group.key) },
            onDelete = { actionCourse = null; pendingDelete = group },
            onDetail = { actionCourse = null; group.slots.firstOrNull()?.let { detailTarget = group to it } },
        )
    }

    detailTarget?.let { (group, slot) ->
        CourseDetailSheet(
            group = group,
            slot = slot,
            timetable = timetable,
            week = week,
            isCurrentWeek = week == currentWeek,
            accent = courseColorFor(
                group.colorIndex,
                group.name,
                autoColorMap(timetable.coursesForWeek(week).map { it.name }),
            ),
            onDismiss = { detailTarget = null },
            onEdit = { detailTarget = null; onAddCourse(group.key) },
            onSaveNote = { text, onlyThisWeek ->
                scope.launch {
                    repository.setNote(
                        key = group.key,
                        day = slot.dayOfWeek,
                        start = slot.startSection,
                        end = slot.endSection,
                        week = week,
                        note = text,
                        onlyThisWeek = onlyThisWeek,
                    )
                    snackbarHostState.showSnackbar(Lang.tr("备注已保存"))
                }
            },
            onDuplicate = {
                detailTarget = null
                scope.launch {
                    val newKey = repository.duplicateCourseGroup(group.key, " " + Lang.tr("副本"))
                    snackbarHostState.showSnackbar(Lang.tr("已复制「${group.name}」"))
                    if (newKey != null) onAddCourse(newKey)
                }
            },
            onDelete = { range ->
                detailTarget = null
                scope.launch {
                    val message = when (range) {
                        DeleteScope.WEEK -> {
                            repository.deleteSlotWeek(group.key, slot.dayOfWeek, slot.startSection, slot.endSection, week)
                            Lang.tr("已删除第 $week 周「${group.name}」的这节课")
                        }
                        DeleteScope.SLOT -> {
                            repository.deleteCourseSlot(group.key, slot.dayOfWeek, slot.startSection, slot.endSection)
                            Lang.tr("已删除「${group.name}」在${dayName(slot.dayOfWeek)}的这节课")
                        }
                        DeleteScope.COURSE -> {
                            repository.deleteCourseGroup(group.key)
                            Lang.tr("已删除「${group.name}」")
                        }
                    }
                    snackbarHostState.showSnackbar(message)
                }
            },
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
                        snackbarHostState.showSnackbar(Lang.tr("已删除「${group.name}」"))
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
    totalWeeks: Int,
    isCurrentWeek: Boolean,
    subtitle: String,
    progress: String,
    textOverride: Color?,
    onWeekClick: () -> Unit,
    onWeekStep: (Int) -> Unit,
    onBackToCurrentWeek: () -> Unit,
    onProgressClick: () -> Unit,
    onAddCourse: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onMore: () -> Unit,
) {
    val tint = textOverride ?: MaterialTheme.colorScheme.onSurface
    val muted = textOverride?.copy(alpha = 0.7f) ?: MaterialTheme.colorScheme.onSurfaceVariant
    val accent = textOverride ?: MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(date, fontSize = 13.sp, color = muted, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeekStepButton(enabled = week > 1, right = false, tint = tint) { onWeekStep(-1) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).combinedClickable(onClick = onWeekClick),
                ) {
                    Text("第 $week 周", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    ChevronDownIcon(tint = tint, size = 16.dp)
                }
                WeekStepButton(enabled = week < totalWeeks, right = true, tint = tint) { onWeekStep(1) }
            }
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 11.sp, color = muted)
        }
        HeaderButton(onAddCourse) { AddCircleIcon(tint = tint) }
        HeaderButton(onImport) { ImportIcon(tint = tint) }
        HeaderButton(onExport) { ExportIcon(tint = tint) }
        HeaderButton(onMore) { MoreCircleIcon(tint = tint) }
    }
    // 固定高度的一行：状态 + 「回到本周」。高度写死，切换周次时顶部不会跳。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .padding(start = 16.dp, end = 12.dp)
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (progress.isNotBlank()) {
            Text(
                progress,
                fontSize = 11.sp,
                color = if (progress.startsWith("未设置")) accent else muted.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).combinedClickable(onClick = onProgressClick),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (!isCurrentWeek) {
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.12f))
                    .combinedClickable(onClick = onBackToCurrentWeek)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                // lineHeight 收到与字号一致，文字才居中不偏下
                Text(
                    "回到本周",
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    color = accent,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun HeaderButton(onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

/** ‹ › 快速翻周；到头了就把箭头淡掉。 */
@Composable
private fun WeekStepButton(enabled: Boolean, right: Boolean, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .combinedClickable(onClick = { if (enabled) onClick() }),
        contentAlignment = Alignment.Center,
    ) {
        val color = if (enabled) tint else tint.copy(alpha = 0.22f)
        if (right) ChevronRightIcon(tint = color) else ChevronLeftIcon(tint = color)
    }
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
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp, max = 64.dp)) {
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
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (isToday) primary.copy(alpha = 0.15f) else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${date.dayOfMonth}",
                        fontSize = 13.sp,
                        maxLines = 1,
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

@Composable
private fun TimetableGrid(
    timetable: Timetable,
    week: Int,
    today: LocalDate,
    scrollState: ScrollState,
    onWeekChange: (Int) -> Unit,
    onCourseClick: (CourseGroup, Course) -> Unit,
    onCourseLongClick: (CourseGroup) -> Unit,
    onEmptyLongPress: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appearance = timetable.appearance
    val columns = dayColumns(appearance.weekStartSunday, appearance.showWeekend)
    val sections = timetable.gridSectionCount()
    val rowHeight = appearance.cellHeight.dp
    val totalWeeks = maxOf(1, timetable.totalWeeks)
    val groups = remember(timetable.courses) { timetable.courseGroups().associateBy { it.key } }
    val todayCol = columns.indexOf(today.dayOfWeek.value)
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (appearance.gridLines) 0.12f else 0f)
    val primary = MaterialTheme.colorScheme.primary

    // 左右滑动翻周：一页一周。这里关掉 Pager 自带的拖拽，改成自己识别横向滑动，
    // 因为 Pager 的「拖动距离 + 惯性」会一次翻两页（表现为跳过中间那一周）。
    val pagerState = rememberPagerState(initialPage = (week - 1).coerceIn(0, totalWeeks - 1)) { totalWeeks }
    LaunchedEffect(week, totalWeeks) {
        val target = (week - 1).coerceIn(0, totalWeeks - 1)
        if (pagerState.currentPage != target || pagerState.currentPageOffsetFraction != 0f) {
            pagerState.animateScrollToPage(target)
        }
    }
    val swipeThreshold = with(LocalDensity.current) { 48.dp.toPx() }

    Column(modifier.verticalScroll(scrollState)) {
        Row(Modifier.fillMaxWidth()) {
            AxisColumn(timetable = timetable, sections = sections, rowHeight = rowHeight)
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .weight(1f)
                    .height(rowHeight * sections)
                    .pointerInput(week, totalWeeks, swipeThreshold) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onHorizontalDrag = { _, delta -> total += delta },
                            onDragEnd = {
                                val step = when {
                                    total <= -swipeThreshold -> 1
                                    total >= swipeThreshold -> -1
                                    else -> 0
                                }
                                if (step != 0) onWeekChange((week + step).coerceIn(1, totalWeeks))
                            },
                        )
                    },
                beyondViewportPageCount = 1,
            ) { page ->
                WeekPage(
                    timetable = timetable,
                    week = page + 1,
                    columns = columns,
                    sections = sections,
                    rowHeight = rowHeight,
                    groups = groups,
                    todayCol = todayCol,
                    lineColor = lineColor,
                    primary = primary,
                    onCourseClick = onCourseClick,
                    onCourseLongClick = onCourseLongClick,
                    onEmptyLongPress = onEmptyLongPress,
                )
            }
        }
        Spacer(Modifier.height((appearance.extraBottomSpace + 24).dp))
    }
}

/** One week of the grid (a page of the swipeable timetable). */
@Composable
private fun WeekPage(
    timetable: Timetable,
    week: Int,
    columns: List<Int>,
    sections: Int,
    rowHeight: Dp,
    groups: Map<String, CourseGroup>,
    todayCol: Int,
    lineColor: Color,
    primary: Color,
    onCourseClick: (CourseGroup, Course) -> Unit,
    onCourseLongClick: (CourseGroup) -> Unit,
    onEmptyLongPress: (Int, Int) -> Unit,
) {
    val appearance = timetable.appearance
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val colWidth = maxWidth / columns.size
        val colWidthPx = with(LocalDensity.current) { colWidth.toPx() }
        val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
        Box(
            Modifier
                .fillMaxSize()
                // 长按空格子 = 在这一格新建课程；课程卡片自己会先吃掉事件，所以不会互相打架
                .pointerInput(week, columns, colWidthPx, rowHeightPx) {
                    detectTapGestures(
                        onLongPress = { offset ->
                            val col = (offset.x / colWidthPx).toInt().coerceIn(0, columns.size - 1)
                            val row = ((offset.y / rowHeightPx).toInt() + 1).coerceIn(1, sections)
                            onEmptyLongPress(columns[col], row)
                        },
                    )
                }
                .drawBehind {
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
        // 自动配色只看「这一周」出现的课程：一周之内不出现重复色就够（调色板 12 色）
        val autoColors = remember(placed) { autoColorMap(placed.map { it.course.name }) }
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
                    onClick = { onCourseClick(group, p.course) },
                    onLongClick = { onCourseLongClick(group) },
                    autoColors = autoColors,
                )
            }
        }
        if (placed.isEmpty() && appearance.showEmptyImage) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                EmptyTimetableIllustration()
                Spacer(Modifier.height(12.dp))
                Text("第 $week 周没有课", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "左右滑动切换周次，长按空白格子可以加课",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
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
    autoColors: Map<String, Color>,
) {
    val base = courseColorFor(group.colorIndex, group.name, autoColors)
    val overrideText = argbOrNull(appearance.textColor)
    val overrideBorder = argbOrNull(appearance.borderColor)
    // 非本周：卡片明显更淡，跟左边的本周课程拉开对比（文字留一点淡化但保证还能读）
    val dim = if (thisWeek) 1f else ((appearance.otherWeekOpacity / 100f) * 0.55f).coerceIn(0.03f, 1f)
    val textFade = if (thisWeek) 1f else 0.85f
    val fill = when (appearance.cellStyle) {
        CellStyle.FILLED -> appearance.opacity / 100f
        CellStyle.OUTLINE -> 0.05f
        else -> appearance.opacity / 100f * 0.26f
    }
    // 复合到不透明的表面色上：卡片盖住的网格辅助线不再透出来，格子里的字更清楚
    val background = base.copy(alpha = fill * dim).compositeOver(MaterialTheme.colorScheme.surface)
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
    val titleText = titleColor.copy(alpha = titleColor.alpha * textFade)
    val metaText = metaColor.copy(alpha = metaColor.alpha * textFade)
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
        // 字号跟着格子大小走：窄列/短格子自动缩字，宁可小一点，也别把课程名截成「马克思主…」
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 3.dp)) {
            val innerWidth = maxWidth.value
            val innerHeight = maxHeight.value
            val showTimeText = appearance.showTime && timeText(course, sectionTimes).isNotBlank()
            val showTeacherText = appearance.showTeacher && group.teacher.isNotBlank()
            val showLocationText = appearance.showLocation && group.location.isNotBlank()
            val metaCount = listOf(showTimeText, showTeacherText, showLocationText).count { it }
            val marks = buildList {
                if (!thisWeek) add("非本周")
                parityLabel(group.weeks, totalWeeks)?.let { add(it) }
            }
            val showMarks = marks.isNotEmpty() && span >= 2 && innerHeight > 62f
            val metaFont = (appearance.fontSize - 3).coerceAtLeast(8).toFloat()
            val locationLines = if (innerHeight >= 100f) 2 else 1
            val metaReserve = if (metaCount == 0) {
                0f
            } else {
                metaFont * 1.45f * (metaCount + if (showLocationText && locationLines == 2) 1 else 0)
            }
            val nameBudget = (innerHeight - metaReserve - if (showMarks) 16f else 0f).coerceAtLeast(12f)
            val nameFont = fitNameFontSize(group.name, innerWidth, nameBudget, appearance.fontSize.toFloat())
            val nameLineHeight = nameFont + 1.5f
            val nameMaxLines = (nameBudget / nameLineHeight).toInt().coerceIn(1, 6)

            Column(Modifier.fillMaxSize()) {
                Text(
                    text = group.name,
                    fontSize = nameFont.sp,
                    lineHeight = nameLineHeight.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = titleText,
                    maxLines = nameMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
                val metaSize = metaFont.sp
                if (showTimeText) {
                    Text(timeText(course, sectionTimes), fontSize = metaSize, color = metaText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (showTeacherText) {
                    Text(group.teacher, fontSize = metaSize, color = metaText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (showLocationText) {
                    Text(
                        "@${group.location}",
                        fontSize = metaSize,
                        color = metaText,
                        maxLines = locationLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 标签：带边框的小牌子。非本周用更实的边框保持醒目，单双周淡一点。
                if (showMarks) {
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (mark in marks) {
                            val strong = mark == "非本周"
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(titleText.copy(alpha = if (strong) 0.10f else 0f))
                                    .border(
                                        width = 1.dp,
                                        color = titleText.copy(alpha = if (strong) 0.65f else 0.28f),
                                        shape = RoundedCornerShape(4.dp),
                                    )
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    mark,
                                    fontSize = 9.sp,
                                    color = titleText.copy(alpha = if (strong) 0.95f else 0.7f),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
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

/**
 * 按格子宽度和可用高度挑一个能放下整门课名的字号（中文按「1 字 ≈ 1 字号」估）。
 * 实在放不下才缩到 8sp，剩下的交给省略号。
 */
private fun fitNameFontSize(name: String, widthDp: Float, heightDp: Float, preferred: Float): Float {
    if (widthDp <= 0f || name.isEmpty()) return preferred
    var size = preferred
    while (size > 8f) {
        val lineHeight = size + 1.5f
        val maxLines = (heightDp / lineHeight).toInt().coerceAtLeast(1)
        val perLine = (widthDp / (size * 1.02f)).toInt().coerceAtLeast(1)
        val lines = (name.length + perLine - 1) / perLine
        if (lines <= maxLines) return size
        size -= 0.5f
    }
    return 8f
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
    onToggleOtherWeeks: (Boolean) -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("隐藏非本周课程", fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = !timetable.appearance.showOtherWeeks,
                    onCheckedChange = { onToggleOtherWeeks(!it) },
                )
            }
            Spacer(Modifier.height(6.dp))
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
                                .heightIn(min = 58.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .combinedClickable(onClick = action)
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(label, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ExportSheet(
    week: Int,
    onDismiss: () -> Unit,
    onBackup: () -> Unit,
    onCsv: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("导出课表", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            SheetRow("分享课表图片", "生成第 $week 周的长图，可直接发微信 / QQ", onShare)
            SheetRow("保存到相册", "存到相册的 Pictures/ClassSchedule，随时可找到", onSave)
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
private fun Onboarding(onAddSchool: () -> Unit, onAddCourse: () -> Unit, onLoadDemo: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EmptyTimetableIllustration()
                Text("还没有课表", style = MaterialTheme.typography.titleLarge)
                Text(
                    "从学校教务导入最省事；也可以手动添加，或先载入示例看看效果。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(2.dp))
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
    onResult: (List<Course>?, String, TimetableExtractor.ScrapeResult?) -> Unit,
) {
    var answered by remember { mutableStateOf(false) }

    fun answer(courses: List<Course>?, foundUrl: String, result: TimetableExtractor.ScrapeResult?) {
        if (answered) return
        answered = true
        onResult(courses, foundUrl, result)
    }

    LaunchedEffect(url) {
        delay(15_000)
        if (!answered) answer(null, "", null)
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
                            courses.isNotEmpty() || answered -> answer(courses.ifEmpty { null }, result.foundUrl, result)
                            attempt < 2 -> {
                                attempt += 1
                                postDelayed({ runScrape() }, 1800)
                            }
                            else -> answer(null, result.foundUrl, result)
                        }
                    }
                }
                WebViewCompat.configure(
                    web = this,
                    headless = true,
                    onPageFinished = { postDelayed({ runScrape() }, 1200) },
                    onError = { answer(null, "", null) },
                )
                loadUrl(url)
            }
        },
    )
}
