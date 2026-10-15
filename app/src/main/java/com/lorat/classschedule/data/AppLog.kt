package com.lorat.classschedule.data

import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.WebSettings
import com.lorat.classschedule.data.model.Timetable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 本地日志：只写文件，不联网、不上传。
 *
 * 记录内容参考常见 bug 报告所需：会话环境（版本/机型/系统/WebView）、关键流程结果、
 * 内置浏览器的 JS 报错与 HTTP 错误、以及**未捕获异常堆栈**。课程名/老师/地点不会写入，
 * 但页面网址（只保留主机名与第一段路径）与页面标题可能间接暴露学校，
 * 因此每个日志文件头部都带一句提醒，对外发布前请自行检查。
 *
 * 文件位置（优先外部私有目录，便于用数据线或文件管理器取出）：
 *   /sdcard/Android/data/<包名>/files/logs/classschedule-YYYY-MM-DD.log
 * 外部不可用时退回应用私有目录 files/logs/。按天一个文件，最多保留 [KEEP_FILES] 个。
 */
object AppLog {

    private const val KEEP_FILES = 7
    private const val MAX_LINE = 2000
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "app-log").apply { isDaemon = true }
    }
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Volatile
    private var dir: File? = null

    /** 会话头（App/设备信息），只写一次。 */
    fun init(context: Context) {
        val target = (context.getExternalFilesDir("logs") ?: File(context.filesDir, "logs"))
        dir = target
        runCatching {
            target.mkdirs()
            prune(target)
        }
        write(header(context, target))
        installCrashHandler()
    }

    fun i(tag: String, message: String) = line("I", tag, message)

    fun w(tag: String, message: String) = line("W", tag, message)

    fun e(tag: String, message: String, error: Throwable? = null) {
        val extra = error?.let { "\n" + it.stackTraceToString() }.orEmpty()
        line("E", tag, message + extra)
    }

    /** 课表概况（仅统计量）。 */
    fun logTimetable(timetable: Timetable) {
        val groups = timetable.courseGroups().size
        i(
            "Timetable",
            "课程 ${timetable.courses.size} 条 / 合并 ${groups} 门 / ${timetable.totalWeeks} 周 / " +
                "节次时间 ${timetable.sectionTimes.size} 条 / 学校已配置=${timetable.school != null}",
        )
    }

    fun logDir(): File? = dir

    private fun line(level: String, tag: String, message: String) {
        val text = "$level/$tag: ${message.replace('\n', ' ').take(MAX_LINE)}"
        Log.println(if (level == "E") Log.ERROR else Log.INFO, "ClassSchedule", text)
        write("${stamp.format(Date())} $text")
    }

    private fun write(text: String) {
        val file = dir?.let { File(it, logFileName(day.format(Date()))) } ?: return
        io.execute {
            runCatching { file.appendText(text + "\n") }
        }
    }

    private fun header(context: Context, target: File): String {
        val pkg = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val code = when {
            pkg == null -> 0L
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> pkg.longVersionCode
            else -> @Suppress("DEPRECATION") pkg.versionCode.toLong()
        }
        val webView = runCatching {
            WebSettings.getDefaultUserAgent(context).substringAfter("Chrome/").substringBefore(" ")
        }.getOrNull().orEmpty()
        val dark = (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        return buildString {
            appendLine()
            appendLine("===== 会话开始 ${stamp.format(Date())} =====")
            appendLine("应用: ${pkg?.versionName ?: "?"} ($code)")
            appendLine("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) / ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}")
            appendLine("机型: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("WebView: ${webView.ifBlank { "未知" }}")
            appendLine("区域: ${Locale.getDefault()}  深色模式: ${if (dark) "是" else "否"}")
            appendLine("日志目录: ${target.absolutePath}")
            appendLine("提示: 日志含学校网址与页面标题，对外发布前请自行检查/删改；不含课程名、老师、地点。")
            appendLine("=================================")
        }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            e("Crash", "未捕获异常，线程=${thread.name}", error)
            // 崩溃日志必须先落盘，再交回系统默认处理
            runCatching { io.submit {}.get() }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun prune(target: File) {
        val names = target.listFiles()?.filter { it.isFile }?.map { it.name } ?: return
        filesToDelete(names, KEEP_FILES).forEach { stale -> runCatching { File(target, stale).delete() } }
    }
}

/** 日志文件名（按天）。纯函数，便于单测。 */
fun logFileName(date: String): String = "classschedule-$date.log"

/**
 * 网址脱敏：只保留 `scheme://host/第一段路径`，去掉查询串与后续路径
 * （查询串里可能有学号/令牌，后面几段可能带班级信息）。
 */
fun redactUrl(url: String): String {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return ""
    val schemeEnd = trimmed.indexOf("://")
    if (schemeEnd <= 0) return trimmed.take(60)
    val scheme = trimmed.substring(0, schemeEnd + 3)
    val rest = trimmed.substring(schemeEnd + 3)
    val hostEnd = rest.indexOf('/')
    val host = if (hostEnd < 0) rest else rest.substring(0, hostEnd)
    val path = if (hostEnd < 0) "" else rest.substring(hostEnd)
    val first = path.trimStart('/').substringBefore('/').substringBefore('?')
    return if (first.isEmpty()) "$scheme$host" else "$scheme$host/$first/…"
}

/** 轮转策略：按文件名排序（日期），保留最新 [keep] 个，返回应删除的。纯函数，便于单测。 */
fun filesToDelete(names: List<String>, keep: Int): List<String> {
    if (keep <= 0) return names
    val logs = names.filter { it.startsWith("classschedule-") && it.endsWith(".log") }.sortedDescending()
    return logs.drop(keep)
}
