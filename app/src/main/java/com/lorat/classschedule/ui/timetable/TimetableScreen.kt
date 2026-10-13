package com.lorat.classschedule.ui.timetable

import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lorat.classschedule.data.ScheduleRepository
import com.lorat.classschedule.data.TimetableExtractor
import com.lorat.classschedule.data.currentWeekOf
import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.model.Timetable
import com.lorat.classschedule.ui.AppBottomBar
import com.lorat.classschedule.ui.WebViewCompat
import com.lorat.classschedule.ui.courseColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private val TimeColumnWidth = 46.dp
private val RowHeight = 56.dp
private val DayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    repository: ScheduleRepository,
    onAddSchool: () -> Unit,
    onOpenSettings: () -> Unit,
    onAutoUpdate: () -> Unit,
    onManualUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimetableViewModel = viewModel { TimetableViewModel(repository) },
) {
    val timetable by viewModel.timetable.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var followCurrent by rememberSaveable { mutableStateOf(true) }
    var selectedWeek by rememberSaveable { mutableStateOf(1) }
    var silentUpdating by remember { mutableStateOf(false) }
    var updateFailed by remember { mutableStateOf(false) }
    var detailCourse by remember { mutableStateOf<Course?>(null) }

    val currentWeek = remember(timetable) { currentWeekOf(timetable) }
    val week = if (followCurrent) currentWeek else selectedWeek.coerceIn(1, maxOf(1, timetable.totalWeeks))

    // Paint the timetable first, then refresh in the background so opening the app stays instant.
    LaunchedEffect(timetable.school) {
        if (viewModel.shouldAutoUpdate()) {
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(timetable.school?.name ?: "我的课表", fontWeight = FontWeight.SemiBold)
                        Text("第 $week 周 / 共 ${timetable.totalWeeks} 周", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onManualUpdate) { Text("⟳", fontSize = 20.sp) }
                    IconButton(onClick = onOpenSettings) { Text("⚙", fontSize = 20.sp) }
                },
            )
        },
        bottomBar = {
            AppBottomBar(
                selectedIndex = 0,
                onSelectTimetable = {},
                onSelectSettings = onOpenSettings,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().height(3.dp)) {
                if (silentUpdating) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (timetable.school == null) {
                OnboardingCard(
                    onAddSchool = onAddSchool,
                    onLoadDemo = { scope.launch { repository.loadDemo() } },
                )
            } else {
                WeekSelector(
                    week = week,
                    totalWeeks = timetable.totalWeeks,
                    followCurrent = followCurrent,
                    onPrev = {
                        followCurrent = false
                        selectedWeek = (week - 1).coerceAtLeast(1)
                    },
                    onNext = {
                        followCurrent = false
                        selectedWeek = (week + 1).coerceAtMost(maxOf(1, timetable.totalWeeks))
                    },
                    onToday = { followCurrent = true },
                )
                if (timetable.coursesForWeek(week).isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("本周暂无课程", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    TimetableGrid(
                        timetable = timetable,
                        week = week,
                        onCourseClick = { detailCourse = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    detailCourse?.let { course ->
        AlertDialog(
            onDismissRequest = { detailCourse = null },
            confirmButton = { TextButton(onClick = { detailCourse = null }) { Text("关闭") } },
            title = { Text(course.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DetailRow("星期", DayLabels.getOrElse(course.dayOfWeek - 1) { "?" })
                    DetailRow("节次", "第 ${course.startSection}-${course.endSection} 节")
                    if (course.teacher.isNotBlank()) DetailRow("教师", course.teacher)
                    if (course.location.isNotBlank()) DetailRow("地点", course.location)
                    DetailRow("周次", weekRangeText(course.weeks, timetable.totalWeeks))
                }
            },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row {
        Text(label, modifier = Modifier.width(56.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(value, fontSize = 14.sp)
    }
}

private fun weekRangeText(weeks: List<Int>, totalWeeks: Int): String {
    if (weeks.isEmpty() || weeks.size >= totalWeeks) return "全学期"
    val sorted = weeks.sorted()
    val ranges = mutableListOf<String>()
    var start = sorted.first()
    var prev = start
    for (w in sorted.drop(1)) {
        if (w == prev + 1) {
            prev = w
        } else {
            ranges.add(if (start == prev) "$start" else "$start-$prev")
            start = w; prev = w
        }
    }
    ranges.add(if (start == prev) "$start" else "$start-$prev")
    return ranges.joinToString(", ") + " 周"
}

@Composable
private fun WeekSelector(
    week: Int,
    totalWeeks: Int,
    followCurrent: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrev) { Text("< 上一周") }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("第 $week 周", fontWeight = FontWeight.Bold)
            if (followCurrent) Text("本周", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onNext) { Text("下一周 >") }
    }
    OutlinedButton(onClick = onToday, modifier = Modifier.padding(horizontal = 8.dp)) { Text("回到本周") }
}

@Composable
private fun OnboardingCard(onAddSchool: () -> Unit, onLoadDemo: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("还没有课表", style = MaterialTheme.typography.titleLarge)
                Text(
                    "添加你学校的教务系统地址，登录后即可自动识别并导入课表。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onAddSchool, modifier = Modifier.fillMaxWidth()) { Text("添加学校") }
                OutlinedButton(onClick = onLoadDemo, modifier = Modifier.fillMaxWidth()) { Text("载入示例课表看看效果") }
            }
        }
    }
}

@Composable
private fun TimetableGrid(
    timetable: Timetable,
    week: Int,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier,
) {
    val courses = timetable.coursesForWeek(week)
    val maxSection = maxOf(
        1,
        timetable.sectionTimes.size,
        courses.maxOfOrNull { it.endSection } ?: 0,
    )
    val today = LocalDate.now().dayOfWeek.value

    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
        Row(Modifier.fillMaxWidth().height(28.dp)) {
            Spacer(Modifier.width(TimeColumnWidth))
            for (d in 1..7) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        DayLabels[d - 1],
                        fontSize = 11.sp,
                        fontWeight = if (d == today) FontWeight.Bold else FontWeight.Medium,
                        color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(TimeColumnWidth).height(RowHeight * maxSection)) {
                for (s in 1..maxSection) {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(RowHeight),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("$s", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        timetable.sectionTimes.firstOrNull { it.section == s }?.let { st ->
                            Text(st.start, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(st.end, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            BoxWithConstraints(Modifier.weight(1f).height(RowHeight * maxSection)) {
                val cellWidth = maxWidth / 7
                Column(Modifier.fillMaxSize()) {
                    for (s in 1..maxSection) {
                        Row(Modifier.fillMaxWidth().height(RowHeight)) {
                            for (d in 1..7) {
                                Box(Modifier.weight(1f).fillMaxHeight().padding(1.dp)) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(3.dp),
                                        modifier = Modifier.fillMaxSize(),
                                    ) {}
                                }
                            }
                        }
                    }
                }
                for (course in courses) {
                    val day = course.dayOfWeek
                    val start = course.startSection
                    val end = course.endSection.coerceAtMost(maxSection)
                    if (day !in 1..7 || start !in 1..maxSection || end < start) continue
                    val span = end - start + 1
                    Box(
                        Modifier
                            .offset(x = cellWidth * (day - 1), y = RowHeight * (start - 1))
                            .width(cellWidth)
                            .height(RowHeight * span)
                            .padding(1.dp),
                    ) {
                        Surface(
                            color = courseColor(course.colorIndex),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxSize().clickable { onCourseClick(course) },
                        ) {
                            Column(Modifier.padding(4.dp)) {
                                Text(
                                    course.name,
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (course.location.isNotBlank()) {
                                    Text(
                                        course.location,
                                        fontSize = 8.sp,
                                        lineHeight = 10.sp,
                                        color = Color.White.copy(alpha = 0.9f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
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
