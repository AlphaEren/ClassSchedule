@file:OptIn(ExperimentalFoundationApi::class)

package com.lorat.classschedule.ui.webimport

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import com.lorat.classschedule.i18n.Lang
import com.lorat.classschedule.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lorat.classschedule.data.AppLog
import com.lorat.classschedule.data.ScheduleRepository
import com.lorat.classschedule.data.TimetableExtractor
import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.ui.WebViewCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
private const val MAX_ATTEMPTS = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebImportScreen(
    repository: ScheduleRepository,
    auto: Boolean,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    val timetable by repository.timetable.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val school = timetable.school
    val preset = school?.parserPreset ?: TimetableExtractor.PRESET_AUTO
    val scope = rememberCoroutineScope()

    val webViewState = remember { mutableStateOf<WebView?>(null) }
    val pageLoaded = remember { mutableStateOf(0) }
    var progress by remember { mutableStateOf(0) }
    var status by remember {
        mutableStateOf(if (auto) "正在自动更新…" else "请登录并打开课表页面，再点击「识别课表」")
    }
    var parsed by remember { mutableStateOf<List<Course>>(emptyList()) }
    var showPreview by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var revealPage by remember { mutableStateOf(!auto) }
    var scanning by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<TimetableExtractor.ScrapeResult?>(null) }
    // 页面加载层面的日志（JS console / HTTP 错误 / 最终 URL）：登录页白屏时靠它定位
    val loadLogs = remember { mutableStateListOf<String>() }
    var pageUrl by remember { mutableStateOf("") }
    var showLoadLog by remember { mutableStateOf(false) }
    var pageHint by remember { mutableStateOf("") }

    fun scrape(web: WebView, autoMode: Boolean, attempt: Int) {
        scanning = true
        web.evaluateJavascript(TimetableExtractor.scraperJs(preset)) { raw ->
            val result = TimetableExtractor.parseScrapeResult(raw)
            lastResult = result
            val courses = if (result.ok) {
                TimetableExtractor.coursesFrom(result, timetable.totalWeeks)
            } else {
                emptyList()
            }
            when {
                courses.isNotEmpty() -> {
                    val found = result.foundUrl
                    AppLog.i(
                        "Import",
                        "识别成功 mode=${result.mode} system=${result.system} 课程=${courses.size} 节次=${result.sectionTimes.size}",
                    )
                    if (autoMode) {
                        scope.launch {
                            if (found.isNotBlank()) repository.setRefreshUrl(found)
                            repository.applyImport(
                                courses = courses,
                                sectionTimes = result.sectionTimes,
                                semesterStart = result.semesterStart,
                                currentWeek = result.currentWeek,
                                weekCount = result.weekCount,
                            )
                            onFinished()
                        }
                    } else {
                        parsed = courses
                        status = buildString {
                            append("识别到 ${courses.size} 门课程（${systemLabel(result.system)}）")
                            if (result.sectionTimes.isNotEmpty()) append("，${result.sectionTimes.size} 节课时间")
                            if (result.currentWeek > 0) append("，教务说现在是第 ${result.currentWeek} 周")
                        }
                        scanning = false
                        showPreview = true
                        if (found.isNotBlank()) scope.launch { repository.setRefreshUrl(found) }
                    }
                }
                attempt < MAX_ATTEMPTS -> {
                    status = "正在识别…（第 ${attempt + 1} 次尝试，页面可能还没加载完）"
                    scope.launch {
                        delay(1600)
                        webViewState.value?.let { scrape(it, autoMode, attempt + 1) }
                    }
                }
                else -> {
                    scanning = false
                    val reason = TimetableExtractor.failureMessage(result)
                    AppLog.w("Import", "识别失败 mode=${result.mode} system=${result.system} 原因=$reason")
                    if (autoMode) {
                        revealPage = true
                        status = "自动更新失败：$reason"
                    } else {
                        status = reason
                    }
                }
            }
        }
    }

    LaunchedEffect(pageLoaded.value) {
        if (pageLoaded.value == 0) return@LaunchedEffect
        if (auto && !revealPage) {
            delay(1500)
            webViewState.value?.let { scrape(it, true, 1) }
        }
    }

    /** 落库：课程之外，把教务自报的上课时间/开学日期/当前周一起带进去。 */
    fun applyParsed() {
        val courses = parsed
        if (courses.isEmpty()) return
        val r = lastResult
        scope.launch {
            repository.applyImport(
                courses = courses,
                sectionTimes = r?.sectionTimes.orEmpty(),
                semesterStart = r?.semesterStart.orEmpty(),
                currentWeek = r?.currentWeek ?: 0,
                weekCount = r?.weekCount ?: 0,
            )
            onFinished()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(school?.name ?: "导入课表", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (revealPage) "解析规则：${TimetableExtractor.presetLabel(preset)}" else "自动更新中",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←", fontSize = 20.sp) }
                },
                actions = {
                    if (pageUrl.isNotBlank()) {
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }) { Text("浏览器") }
                    }
                    if (lastResult != null) {
                        TextButton(onClick = { showDiagnostics = true }) { Text("详情") }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        status,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                    )
                    if (pageHint.isNotBlank()) {
                        Text(
                            pageHint,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 3,
                        )
                    }
                    if (pageUrl.isNotBlank()) {
                        Text(
                            pageUrl,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (loadLogs.isNotEmpty()) {
                        Text(
                            if (showLoadLog) "收起加载诊断" else "加载诊断（${loadLogs.size}）",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.combinedClickable { showLoadLog = !showLoadLog },
                        )
                        if (showLoadLog) {
                            Text(
                                loadLogs.takeLast(12).joinToString("\n"),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 14,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { webViewState.value?.let { scrape(it, false, 1) } },
                            enabled = webViewState.value != null && school != null && !scanning,
                            modifier = Modifier.weight(1f),
                        ) { Text(if (scanning) "识别中…" else "识别课表") }
                        Button(
                            onClick = { applyParsed() },
                            enabled = parsed.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) { Text("导入 (${parsed.size})") }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (school == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("尚未添加学校")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onBack) { Text("返回") }
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize().alpha(if (revealPage) 1f else 0f),
                    factory = { context ->
                        WebView(context).apply {
                            WebViewCompat.configure(
                                web = this,
                                onPageFinished = { url ->
                                    pageUrl = url
                                    pageLoaded.value += 1
                                },
                                onProgress = { progress = it },
                                onError = { status = "页面加载失败：$it（可先用系统浏览器确认网址是否正确）" },
                                onPageInfo = { info ->
                                    if (loadLogs.size > 40) loadLogs.removeAt(0)
                                    loadLogs.add(info)
                                    if (info.contains("\"input\":\"") && !info.contains("\"input\":\"none\"")) {
                                        pageHint = Lang.tr("已检测到登录表单：在页面里登录后再点「识别课表」。") +
                                            Lang.tr("若页面空白/点不动，点右上角「浏览器」改用系统浏览器打开。")
                                    }
                                },
                                onConsole = { line ->
                                    if (loadLogs.size > 40) loadLogs.removeAt(0)
                                    loadLogs.add(line)
                                },
                                onHttpError = { url, code ->
                                    if (loadLogs.size > 40) loadLogs.removeAt(0)
                                    loadLogs.add("HTTP $code  $url")
                                },
                            )
                            webViewState.value = this
                            loadUrl(school.refreshUrl.ifBlank { school.scheduleUrl })
                        }
                    },
                )
                if (!revealPage) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("正在尝试自动更新课表…")
                        }
                    }
                }
                if (progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    )
                }
            }
        }
    }

    if (showPreview) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            title = { Text("识别结果") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    parsed
                        .sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }))
                        .forEach { c ->
                            Text(
                                Lang.tr(DayNames.getOrElse(c.dayOfWeek - 1) { "?" }) +
                                    " " + Lang.tr("第${c.startSection}-${c.endSection}节") + "  ${c.name}" +
                                    if (c.location.isNotBlank()) "  @${c.location}" else "",
                                fontSize = 13.sp,
                            )
                        }
                }
            },
            confirmButton = { Button(onClick = { showPreview = false }) { Text("知道了") } },
            dismissButton = {
                TextButton(onClick = {
                    showPreview = false
                    applyParsed()
                }) { Text("直接导入") }
            },
        )
    }

    if (showDiagnostics) {
        val r = lastResult
        AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            title = { Text("识别详情") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DiagRow("是否成功", if (r?.ok == true) "是" else "否")
                    DiagRow("识别方式", modeLabel(r?.mode ?: ""))
                    DiagRow("疑似系统", systemLabel(r?.system ?: "unknown"))
                    DiagRow("实际规则", TimetableExtractor.presetLabel(r?.preset ?: ""))
                    DiagRow("页面标题", r?.title.orEmpty())
                    DiagRow("当前地址", r?.url.orEmpty())
                    DiagRow("课表页地址", r?.foundUrl.orEmpty())
                    DiagRow("表格/网格", r?.diagnostics.orEmpty())
                    DiagRow("数据行数", (r?.rows?.size ?: 0).toString())
                    if (!r?.courses.isNullOrEmpty()) DiagRow("内嵌课程", r!!.courses.size.toString())
                    DiagRow("页面结构", r?.sample.orEmpty())
                    if (r != null && !r.ok) DiagRow("失败原因", TimetableExtractor.failureMessage(r))
                }
            },
            confirmButton = { Button(onClick = { showDiagnostics = false }) { Text("关闭") } },
        )
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(value, fontSize = 12.sp)
    }
}

private fun systemLabel(system: String): String = when (system) {
    "zf" -> "正方 / URP"
    "jwglxt" -> "正方教务（新版）"
    "qz" -> "强智教务"
    "kbpro" -> "课表系统（kbpro）"
    else -> "未识别"
}

private fun modeLabel(mode: String): String = when (mode) {
    "data" -> "页面内嵌数据"
    "selector" -> "指定课表容器"
    "grid" -> "表格网格"
    "geometry" -> "位置聚类"
    "box" -> "课表格子"
    "net" -> "接口原始数据"
    "" -> "-"
    else -> mode
}
