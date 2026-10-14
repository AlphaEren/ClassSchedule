@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.lorat.classschedule.ui.imports

import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lorat.classschedule.data.ScheduleRepository
import com.lorat.classschedule.data.TimetableExtractor
import com.lorat.classschedule.data.csvToCourses
import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.parseCsvTimetable
import com.lorat.classschedule.ui.WebViewCompat
import com.lorat.classschedule.ui.readTextFromUri
import com.lorat.classschedule.ui.settings.SettingsCard
import com.lorat.classschedule.ui.settings.SettingsNavRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 导入课表: 教务系统 / 图片识别 / CSV / 备份文件 / HTML 文件 / 粘贴文本. */
@Composable
fun ImportScreen(
    repository: ScheduleRepository,
    onBack: () -> Unit,
    onOpenSchoolImport: () -> Unit,
    onOpenImageImport: () -> Unit,
) {
    val timetable by repository.timetable.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var status by remember { mutableStateOf("") }
    var showPaste by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    var html by remember { mutableStateOf<String?>(null) }

    fun report(message: String) {
        status = message
        scope.launch { snackbar.showSnackbar(message) }
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = context.readTextFromUri(uri)
        if (text.isNullOrBlank()) {
            report("读取文件失败")
            return@rememberLauncherForActivityResult
        }
        val rows = parseCsvTimetable(text, timetable.totalWeeks)
        if (rows == null) {
            report("没解析出课程，请确认是 CSV/Excel 导出的表格")
        } else {
            scope.launch {
                repository.replaceCourses(csvToCourses(rows, timetable.sectionTimes))
                report("已导入 ${rows.map { it.name }.distinct().size} 门课程（${rows.size} 个时间段）")
            }
        }
    }

    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = context.readTextFromUri(uri)
        val backup = text?.let { repository.decodeTimetable(it) }
        if (backup == null) {
            report("备份文件无法识别")
        } else {
            scope.launch {
                repository.replaceAll(backup)
                report("已从备份恢复 ${backup.courses.size} 个时间段")
            }
        }
    }

    val htmlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = context.readTextFromUri(uri)
        if (text.isNullOrBlank()) {
            report("读取 HTML 文件失败")
        } else {
            html = text
            report("正在用识别管线解析 HTML…")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("导入课表") },
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
            SettingsCard("从学校教务导入") {
                SettingsNavRow(
                    title = "从学校教务导入",
                    subtitle = "内置浏览器登录后自动识别课表，支持 5 级识别管线",
                    onClick = onOpenSchoolImport,
                )
            }

            SettingsCard("从图片识别") {
                SettingsNavRow(
                    title = "从图片识别课表",
                    subtitle = "选一张课表截图，本机离线识别课程名 / 地点 / 节次",
                    onClick = onOpenImageImport,
                )
            }

            SettingsCard("从文件导入") {
                SettingsNavRow(
                    title = "从 Excel / CSV 文件导入",
                    subtitle = "列序：课程名称,星期,开始节数,结束节数,老师,地点,周数",
                    onClick = { csvLauncher.launch(arrayOf("*/*")) },
                )
                SettingsNavRow(
                    title = "从备份文件导入",
                    subtitle = "App 导出的 JSON 备份，可完整还原课表与外观",
                    onClick = { jsonLauncher.launch(arrayOf("*/*")) },
                )
                SettingsNavRow(
                    title = "从 HTML 文件导入",
                    subtitle = "教务页面「查看源代码」另存为 HTML 后导入",
                    onClick = { htmlLauncher.launch(arrayOf("*/*")) },
                )
            }

            SettingsCard("粘贴文本导入") {
                OutlinedButton(onClick = { showPaste = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("粘贴 CSV 或备份内容")
                }
                Text(
                    "支持 WakeUp 课程表模板的 CSV 文本，也支持本 App 导出的 JSON 备份内容。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsCard("其它") {
                OutlinedButton(
                    onClick = { scope.launch { repository.loadDemo(); report("已载入示例课表") } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("载入示例课表") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            repository.clear()
                            report("已清空本地课表")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("清空本地课表") }
            }

            if (status.isNotBlank()) {
                Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPaste) {
        AlertDialog(
            onDismissRequest = { showPaste = false },
            title = { Text("粘贴导入") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pasteText,
                        onValueChange = { pasteText = it },
                        label = { Text("CSV 或 JSON 内容") },
                        minLines = 6,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "首字符是 { 时按备份 JSON 解析，否则按 CSV 解析。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val text = pasteText.trim()
                    showPaste = false
                    if (text.isEmpty()) return@TextButton
                    if (text.startsWith("{")) {
                        val backup = repository.decodeTimetable(text)
                        if (backup == null) {
                            report("备份内容无法识别")
                        } else {
                            scope.launch {
                                repository.replaceAll(backup)
                                report("已从备份恢复 ${backup.courses.size} 个时间段")
                            }
                        }
                    } else {
                        val rows = parseCsvTimetable(text, timetable.totalWeeks)
                        if (rows == null) {
                            report("没解析出课程")
                        } else {
                            scope.launch {
                                repository.replaceCourses(csvToCourses(rows, timetable.sectionTimes))
                                report("已导入 ${rows.map { it.name }.distinct().size} 门课程")
                            }
                        }
                    }
                }) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { showPaste = false }) { Text("取消") } },
        )
    }

    html?.let { content ->
        HtmlImportRunner(
            html = content,
            totalWeeks = timetable.totalWeeks,
            preset = timetable.school?.parserPreset ?: "auto",
            onResult = { courses, result ->
                html = null
                if (courses == null || courses.isEmpty()) {
                    report("HTML 里没识别出课表（可试试在教务页面直接导入）")
                } else {
                    scope.launch {
                        repository.applyImport(
                            courses = courses,
                            sectionTimes = result?.sectionTimes.orEmpty(),
                            semesterStart = result?.semesterStart.orEmpty(),
                            currentWeek = result?.currentWeek ?: 0,
                            weekCount = result?.weekCount ?: 0,
                        )
                        report("已从 HTML 导入 ${courses.size} 个时间段")
                    }
                }
            },
        )
    }
}

/** Runs the very same scraper pipeline the browser import uses, but on a saved HTML file. */
@Composable
private fun HtmlImportRunner(
    html: String,
    totalWeeks: Int,
    preset: String,
    onResult: (List<Course>?, TimetableExtractor.ScrapeResult?) -> Unit,
) {
    var answered by remember { mutableStateOf(false) }

    fun answer(courses: List<Course>?, result: TimetableExtractor.ScrapeResult?) {
        if (answered) return
        answered = true
        onResult(courses, result)
    }

    LaunchedEffect(html) {
        delay(20_000)
        if (!answered) answer(null, null)
    }

    AndroidView(
        modifier = Modifier.size(1.dp),
        factory = { context ->
            WebView(context).apply {
                WebViewCompat.configure(
                    web = this,
                    headless = true,
                    onPageFinished = {
                        postDelayed({
                            evaluateJavascript(TimetableExtractor.scraperJs(preset)) { raw ->
                                val result = TimetableExtractor.parseScrapeResult(raw)
                                val courses =
                                    if (result.ok) TimetableExtractor.coursesFrom(result, totalWeeks) else emptyList()
                                answer(courses.ifEmpty { null }, result)
                            }
                        }, 800)
                    },
                    onError = { answer(null, null) },
                )
                loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
    )
}
