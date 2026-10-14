package com.lorat.classschedule.data

import com.lorat.classschedule.data.model.Timetable

/**
 * 轻量「最近诊断」缓冲：内置浏览器加载页面时的 JS 报错 / HTTP 错误 / DOM 探针会记到这里，
 * 「关于 → 问题反馈」可以把它们随报告一起带上，方便定位 bug。
 *
 * 只保留内存、最多 [MAX] 条（App 重启即清空）；不做任何持久化，也不上报到任何服务器。
 */
object Diagnostics {

    private const val MAX = 80
    private val lines = ArrayDeque<String>()

    fun add(line: String) {
        if (line.isBlank()) return
        lines.addLast(line.take(300))
        while (lines.size > MAX) lines.removeFirst()
    }

    fun snapshot(): List<String> = lines.toList()

    fun clear() = lines.clear()
}

/**
 * 生成反馈正文（纯函数，便于单测）。
 *
 * 刻意**只带统计量**：课程条数 / 门数 / 周数 / 节次数、是否配了学校、外观取值，
 * 不带课程名、老师、地点、学校名或网址 —— 这些属于个人与学校信息，不应该外发。
 */
fun bugReportBody(
    timetable: Timetable,
    note: String,
    logs: List<String> = emptyList(),
): String {
    val groups = timetable.courseGroups()
    val appearance = timetable.appearance
    return buildString {
        appendLine("【问题描述】")
        appendLine(note.trim().ifBlank { "（在这里描述遇到的问题 / 复现步骤）" })
        appendLine()
        appendLine("【课表概况】")
        appendLine("- 已添加学校：${if (timetable.school == null) "否" else "是"}（解析规则：${timetable.school?.parserPreset ?: "无"}）")
        appendLine("- 已设置开学日期：${if (timetable.semesterStartDate.isBlank()) "否" else "是"}")
        appendLine("- 课程条目：${timetable.courses.size} 条，合并后 ${groups.size} 门")
        appendLine("- 周数：${timetable.totalWeeks} 周；节次时间：${timetable.sectionTimes.size} 条")
        appendLine("- 外观：格子样式 ${appearance.cellStyle}，不透明比 ${appearance.opacity}，非本周比 ${appearance.otherWeekOpacity}")
        if (logs.isNotEmpty()) {
            appendLine()
            appendLine("【最近诊断】（仅统计信息，可能含页面标题）")
            logs.forEach { appendLine("- $it") }
        }
    }
}

/** 反馈正文里绝不该出现的内容：课程名 / 老师 / 地点 / 学校名 / 网址。 */
fun bugReportLeaks(report: String, timetable: Timetable): List<String> {
    val words = buildList {
        timetable.courses.forEach { course ->
            add(course.name)
            if (course.teacher.isNotBlank()) add(course.teacher)
            if (course.location.isNotBlank()) add(course.location)
        }
        timetable.school?.let {
            if (it.name.isNotBlank()) add(it.name)
            if (it.scheduleUrl.isNotBlank()) add(it.scheduleUrl)
            if (it.refreshUrl.isNotBlank()) add(it.refreshUrl)
        }
    }
    return words.filter { it.isNotBlank() && it.length >= 2 && report.contains(it) }.distinct()
}
