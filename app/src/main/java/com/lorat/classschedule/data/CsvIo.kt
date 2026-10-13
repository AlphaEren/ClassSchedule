package com.lorat.classschedule.data

import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.model.SectionTime

/**
 * CSV import/export, byte-compatible with the official WakeUp课程表 template so a file
 * produced by either app can be fed to the other:
 *
 * ```
 * 课程名称,星期,开始节数,结束节数,老师,地点,周数
 * 高等数学,1,1,2,小明,逸夫楼201,1-5、7-11单、12-16双
 * 线性代数,2,3,4,小红,理工楼110,1-16
 * 大学英语,2,3,4,小红,文成楼125,2、5、8
 * ```
 *
 * Weeks accept `1-16`, `1-5、7-11单`, `2、5、8`, and the date-looking `1月5日` Excel likes to
 * produce. `无` in the teacher/location column means "empty".
 */
private const val CSV_HEADER = "课程名称,星期,开始节数,结束节数,老师,地点,周数"

data class CsvCourse(
    val name: String,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val weeks: List<Int>,
    val teacher: String,
    val location: String,
)

/** Split one CSV/TSV line, honouring double quotes. */
fun splitCsvLine(line: String, delimiter: Char): List<String> {
    val out = mutableListOf<String>()
    val sb = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '"' -> {
                if (quoted && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    quoted = !quoted
                }
            }
            c == delimiter && !quoted -> {
                out.add(sb.toString().trim())
                sb.clear()
            }
            else -> sb.append(c)
        }
        i++
    }
    out.add(sb.toString().trim())
    return out
}

/** "周一" / "星期一" / "1" / "一" all mean day 1. Only a single day is allowed. */
fun parseDay(raw: String): Int? {
    val text = raw.trim().removePrefix("周").removePrefix("星期")
    val chinese = mapOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7)
    chinese[text]?.let { return it }
    val digits = text.filter { it.isDigit() }
    val value = digits.toIntOrNull() ?: return null
    return value.coerceIn(1, 7)
}

/**
 * Week list from `1-16`, `1-5、7-11单、12-16双`, `2、5、8`, `单周`, `全`.
 * An empty result means "every week"; `无` is read the same way.
 */
fun parseWeeks(raw: String, totalWeeks: Int = 30): List<Int> {
    val text = raw.trim()
    if (text.isEmpty() || text.contains("全") || text == "无") return emptyList()
    // WPS/Excel turns "1-5" into a date; read it back.
    val normalized = text
        .replace(Regex("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]"), "$1-$2")
        .replace(Regex("(\\d{1,2})/(\\d{1,2})"), "$1-$2")

    val wholeOdd = normalized.contains("单")
    val wholeEven = normalized.contains("双")
    val segments = normalized.split('、', ',', '，', ';', '；', ' ', '\u3000')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val weeks = mutableSetOf<Int>()
    for (segment in segments) {
        val odd = segment.contains("单") || (wholeOdd && !segment.contains("双"))
        val even = segment.contains("双") || (wholeEven && !segment.contains("单"))
        val body = segment.filter { it.isDigit() || it == '-' || it == '~' || it == '–' }
            .replace('~', '-')
            .replace('–', '-')
            .trim('-')
        if (body.isEmpty()) continue
        val bounds = body.split('-').filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
        val range: IntRange = when {
            bounds.isEmpty() -> continue
            bounds.size == 1 -> bounds[0]..bounds[0]
            else -> bounds[0]..bounds[1]
        }
        for (w in range) {
            if (w < 1 || w > totalWeeks) continue
            when {
                odd && !even -> if (w % 2 == 1) weeks.add(w)
                even && !odd -> if (w % 2 == 0) weeks.add(w)
                else -> weeks.add(w)
            }
        }
    }
    // "单周" / "双周" on its own means every odd/even week of the semester.
    if (weeks.isEmpty() && (wholeOdd || wholeEven)) {
        for (w in 1..totalWeeks) {
            when {
                wholeOdd && !wholeEven -> if (w % 2 == 1) weeks.add(w)
                wholeEven && !wholeOdd -> if (w % 2 == 0) weeks.add(w)
                else -> weeks.add(w)
            }
        }
    }
    // Canonical form: a course that runs the whole semester stores no week list at all.
    val sorted = weeks.sorted()
    return if (totalWeeks in 1..sorted.size && sorted.size >= totalWeeks) emptyList() else sorted
}

private val HeaderAliases = mapOf(
    "name" to listOf("课程名称", "课程名", "名称", "科目"),
    "day" to listOf("星期", "周几", "星期几", "上课星期"),
    "start" to listOf("开始节数", "开始节", "起始节", "起节", "节次开始", "开始节次"),
    "end" to listOf("结束节数", "结束节", "终止节", "止节", "节次结束", "结束节次"),
    "teacher" to listOf("老师", "教师", "授课教师", "任课教师"),
    "location" to listOf("地点", "教室", "上课地点", "上课教室"),
    "weeks" to listOf("周数", "周次", "上课周次", "上课周数"),
)

private fun columnIndex(header: List<String>, key: String): Int {
    val aliases = HeaderAliases.getValue(key)
    return header.indexOfFirst { cell -> aliases.any { alias -> cell.replace(" ", "").contains(alias) } }
}

/** WakeUp's fixed column order: 名称,星期,开始节,结束节,老师,地点,周数. */
private val DefaultOrder =
    mapOf("name" to 0, "day" to 1, "start" to 2, "end" to 3, "teacher" to 4, "location" to 5, "weeks" to 6)

/**
 * Read a CSV/TSV timetable. Returns null when nothing usable was found.
 * A missing header row is tolerated and read as WakeUp's column order.
 */
fun parseCsvTimetable(text: String, totalWeeks: Int = 30): List<CsvCourse>? {
    val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        .map { it.trim().removePrefix("\uFEFF") }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
    if (lines.isEmpty()) return null

    val delimiter = if (lines.first().count { it == '\t' } > lines.first().count { it == ',' }) '\t' else ','
    val firstCells = splitCsvLine(lines.first(), delimiter)
    val hasHeader = firstCells.any { cell ->
        HeaderAliases.values.any { aliases -> aliases.any { cell.replace(" ", "").contains(it) } }
    }

    val order: Map<String, Int> =
        if (hasHeader) {
            HeaderAliases.keys.associateWith { columnIndex(firstCells, it) }
        } else {
            DefaultOrder
        }

    val body = if (hasHeader) lines.drop(1) else lines
    val result = mutableListOf<CsvCourse>()
    for (line in body) {
        val cells = splitCsvLine(line, delimiter)
        fun cell(key: String): String {
            val value = order[key]?.takeIf { it >= 0 }?.let { cells.getOrNull(it) }.orEmpty().trim()
            return if (value == "无") "" else value
        }
        val name = cell("name")
        if (name.isBlank()) continue
        val day = parseDay(cell("day")) ?: continue
        val start = cell("start").filter { it.isDigit() }.toIntOrNull() ?: continue
        val end = cell("end").filter { it.isDigit() }.toIntOrNull() ?: start
        result.add(
            CsvCourse(
                name = name,
                dayOfWeek = day,
                startSection = start,
                endSection = maxOf(start, end),
                weeks = parseWeeks(cell("weeks"), totalWeeks),
                teacher = cell("teacher"),
                location = cell("location"),
            ),
        )
    }
    return result.ifEmpty { null }
}

/** CSV text for the given courses; re-importable by either app. */
fun csvOf(courses: List<Course>, totalWeeks: Int): String {
    val sb = StringBuilder(CSV_HEADER).append('\n')
    for (course in courses.sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }))) {
        sb.append(
            listOf(
                course.name,
                course.dayOfWeek.toString(),
                course.startSection.toString(),
                course.endSection.toString(),
                course.teacher.ifBlank { "无" },
                course.location.ifBlank { "无" },
                csvWeeks(course.weeks, totalWeeks),
            ).joinToString(",") { escapeCsv(it) },
        ).append('\n')
    }
    return sb.toString()
}

/** `1-16`, `1-5、7-11单`, `2、5、8` — the week syntax the official template uses. */
fun csvWeeks(weeks: List<Int>, totalWeeks: Int): String {
    if (weeks.isEmpty() || weeks.size >= totalWeeks) return "1-$totalWeeks"
    val allOdd = weeks.all { it % 2 == 1 }
    val allEven = weeks.all { it % 2 == 0 }
    return when {
        // Compress in parity space so "7,9,11" is written as "7-11单", like the official template.
        allOdd -> compactWeeks(weeks, { (it + 1) / 2 }, { 2 * it - 1 }) + "单"
        allEven -> compactWeeks(weeks, { it / 2 }, { 2 * it }) + "双"
        else -> weekRanges(weeks).joinToString("、") { (from, to) -> if (from == to) "$from" else "$from-$to" }
    }
}

private fun compactWeeks(
    weeks: List<Int>,
    toIndex: (Int) -> Int,
    toWeek: (Int) -> Int,
): String = weekRanges(weeks.map(toIndex)).joinToString("、") { (from, to) ->
    val start = toWeek(from)
    val end = toWeek(to)
    if (start == end) "$start" else "$start-$end"
}

private fun escapeCsv(value: String): String =
    if (value.contains(',') || value.contains('"') || value.contains('\n')) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }

/** Turn parsed CSV rows into courses; rows sharing a name become one course with several slots. */
fun csvToCourses(rows: List<CsvCourse>, sectionTimes: List<SectionTime>): List<Course> {
    val maxSection = sectionTimes.size.coerceAtLeast(12)
    val names = rows.map { it.name }.distinct()
    return rows.mapIndexed { index, row ->
        Course(
            id = "csv-$index",
            name = row.name,
            teacher = row.teacher,
            location = row.location,
            dayOfWeek = row.dayOfWeek,
            startSection = row.startSection.coerceIn(1, maxSection),
            endSection = row.endSection.coerceIn(1, maxSection),
            weeks = row.weeks,
            colorIndex = names.indexOf(row.name),
            groupId = "csv-group-${names.indexOf(row.name)}",
        )
    }
}
