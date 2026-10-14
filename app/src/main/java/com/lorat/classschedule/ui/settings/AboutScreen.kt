@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.lorat.classschedule.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.webkit.WebSettings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lorat.classschedule.data.Diagnostics
import com.lorat.classschedule.data.ScheduleRepository
import com.lorat.classschedule.data.bugReportBody
import kotlinx.coroutines.launch

private const val REPO_URL = "https://github.com/AlphaEren/ClassSchedule"

/** 环境信息（版本 / 机型 / WebView），不含任何课表或学校内容。 */
private fun deviceInfo(context: Context): String {
    val pkg = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    val versionCode = when {
        pkg == null -> 0L
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> pkg.longVersionCode
        else -> @Suppress("DEPRECATION") pkg.versionCode.toLong()
    }
    val webView = runCatching {
        WebSettings.getDefaultUserAgent(context).substringAfter("Chrome/").substringBefore(" ")
    }.getOrNull().orEmpty()
    return buildString {
        appendLine("【应用】")
        appendLine("- 版本：${pkg?.versionName ?: "?"} ($versionCode)")
        appendLine("【设备】")
        appendLine("- Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) / ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}")
        appendLine("- 机型：${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("- WebView：${webView.ifBlank { "未知" }}")
    }
}

/** GitHub 新建 issue 的地址（正文做长度与转义处理）。 */
private fun issueUrl(report: String): String {
    val title = java.net.URLEncoder.encode("问题反馈", "UTF-8")
    val body = java.net.URLEncoder.encode(report.take(4000), "UTF-8")
    return "$REPO_URL/issues/new?title=$title&body=$body"
}

/** 关于 — what this app is and where it came from. */
@Composable
fun AboutScreen(repository: ScheduleRepository, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val timetable by repository.timetable.collectAsStateWithLifecycle()
    var note by remember { mutableStateOf("") }
    var withLogs by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    val report = remember(timetable, note, withLogs) {
        deviceInfo(context) + "\n" + bugReportBody(
            timetable = timetable,
            note = note,
            logs = if (withLogs) Diagnostics.snapshot() else emptyList(),
        )
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("关于") },
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
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ClassSchedule 课表", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("版本 0.2 · Kotlin + Jetpack Compose", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "功能与观感参考 WakeUp课程表（i.wakeup.fun）实现：周次标题栏、日期栏、左侧时间轴、彩色课程格子、" +
                            "多时间段课程、课表外观自定义、CSV/备份导入导出。",
                        fontSize = 13.sp,
                    )
                }
            }
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("开源", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("项目地址", fontSize = 13.sp, modifier = Modifier.width(72.dp))
                        Text(
                            "github.com/AlphaEren/ClassSchedule",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.combinedClickable {
                                runCatching { uriHandler.openUri(REPO_URL) }
                            },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("开源协议", fontSize = 13.sp, modifier = Modifier.width(72.dp))
                        Text("MIT License", fontSize = 13.sp)
                    }
                    Text(
                        "源码以 MIT 协议开源：可自由使用、修改、分发，保留版权与许可声明即可。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("课表识别", fontWeight = FontWeight.SemiBold)
                    Text(
                        "从学校教务系统导入时，App 会在内置浏览器里依次尝试五级识别管线：" +
                            "接口抓包 → 课表格子 → 指定容器 → 表格网格 → 位置聚类；" +
                            "识别不出时会自动点一次「查询/我的课表」并重试。",
                        fontSize = 13.sp,
                    )
                }
            }
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("数据与隐私", fontWeight = FontWeight.SemiBold)
                    Text(
                        "课表、学校配置与外观设置全部以 JSON 保存在 App 私有目录（files/timetable.json），" +
                            "不上传任何服务器；卸载 App 即删除。",
                        fontSize = 13.sp,
                    )
                }
            }
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("开源组件", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Jetpack Compose / Material 3、AndroidX Navigation 3、kotlinx.serialization。" +
                            "图标为自绘，无第三方图标库。",
                        fontSize = 13.sp,
                    )
                }
            }
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("问题反馈", fontWeight = FontWeight.SemiBold)
                    Text(
                        "遇到异常时生成一份反馈：自动带上版本、机型、WebView 版本与课表统计，" +
                            "方便定位问题。报告里不会包含课程名、老师、地点或学校信息。",
                        fontSize = 13.sp,
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("问题描述 / 复现步骤（可留空）") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = withLogs, onCheckedChange = { withLogs = it })
                        Text(
                            "附上最近页面诊断（含页面标题与报错，可能间接暴露学校）",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.combinedClickable { withLogs = !withLogs },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(report))
                                scope.launch { snackbar.showSnackbar("反馈内容已复制") }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("复制") }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND)
                                                .setType("text/plain")
                                                .putExtra(Intent.EXTRA_SUBJECT, "课表助手反馈")
                                                .putExtra(Intent.EXTRA_TEXT, report),
                                            "分享反馈",
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("分享") }
                        Button(
                            onClick = { uriHandler.openUri(issueUrl(report)) },
                            modifier = Modifier.weight(1f),
                        ) { Text("提 Issue") }
                    }
                    Text(
                        if (showReport) "收起反馈内容" else "查看将要发送的内容",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.combinedClickable { showReport = !showReport },
                    )
                    if (showReport) {
                        Text(
                            report,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
