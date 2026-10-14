package com.lorat.classschedule.data

import com.lorat.classschedule.data.model.Course

/**
 * 从「图片识别」得到的文字块还原课表。
 *
 * OCR 只能给出一堆带坐标的文字，所以这里按版面还原：
 *  1. 上方的 `周一..周日` 定出星期列（没有就用等分兜底）；
 *  2. 左侧的 `①..⑳` / `第N节` / `N` / `N-M` 定出节次行；
 *  3. 其余文字按坐标落进 (星期, 节次) 格子，再在格子里分出课程名 / 教师 / 地点 / 周次。
 *
 * 纯函数，方便用单测覆盖（见 `OcrTimetableTest`）。
 */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val cx: Int get() = (left + right) / 2
    val cy: Int get() = (top + bottom) / 2
}

private val ChineseDays = mapOf(
    "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7,
)
private val EnglishDays = listOf("mon" to 1, "tue" to 2, "wed" to 3, "thu" to 4, "fri" to 5, "sat" to 6, "sun" to 7)
private val Circled = "①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳"

internal fun dayOfText(raw: String): Int? {
    val t = raw.trim()
    if (t.isEmpty() || t.length > 4) return null
    val cn = Regex("(?:周|星期|礼拜)\\s*([一二三四五六日天])").find(t)?.groupValues?.get(1)
    if (cn != null) return ChineseDays[cn]
    if (t.length == 1) ChineseDays[t]?.let { return it }
    val lower = t.lowercase()
    return EnglishDays.firstOrNull { lower.startsWith(it.first) }?.second
}

/** `①` / `第3节` / `3` → 1..20；`3-4` / `第3-4节` → 3..4；都不是就 null。 */
internal fun sectionRangeOfText(raw: String): IntRange? {
    val t = raw.trim()
    if (t.isEmpty() || t.length > 8) return null
    Circled.indexOf(t.firstOrNull() ?: ' ').takeIf { it >= 0 }?.let { return (it + 1)..(it + 1) }
    Regex("(\\d{1,2})\\s*[-~－—至]\\s*(\\d{1,2})").find(t)?.let { m ->
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].toInt()
        if (a in 1..20 && b in 1..20 && a <= b) return a..b
    }
    val n = t.filter { it.isDigit() }.toIntOrNull()
    return if (n != null && n in 1..20 && t.length <= 3) n..n else null
}

internal fun looksLikeRoom(s: String): Boolean =
    s.startsWith("@") ||
        Regex("^[\\d\\-—~]{3,}$").matches(s) ||
        listOf("楼", "室", "馆", "场", "区", "机房", "教室", "操场", "大教室").any { s.contains(it) }

internal fun looksLikeTeacher(s: String): Boolean =
    s.length in 2..4 && s.none { it.isDigit() } && !looksLikeRoom(s)

private val TimeLike = Regex("^\\d{1,2}[:：]\\d{2}(\\s*[-~—]\\s*\\d{1,2}[:：]\\d{2})?$")

/**
 * 整行就是节次标签吗？`①` / `第3节` / `3` / `3-4` 是；
 * `@教1-201` 这种「房间号里带 1-2」的不是（否则会被当成节次吃掉）。
 */
internal fun isSectionLabel(text: String): Boolean {
    val stripped = text.replace(Regex("[第节大\\s]"), "")
    if (stripped.isEmpty()) return false
    if (stripped.all { Circled.indexOf(it) >= 0 }) return true
    val rest = stripped.replace(Regex("[\\d\\-~－—至]"), "")
    return rest.isEmpty() && sectionRangeOfText(text) != null
}

/** 时间轴上的「08:00」、纯数字、纯符号、整行的节次标签都不是课程名。 */
internal fun isNoiseText(text: String): Boolean =
    TimeLike.matches(text) || text.none { it.isLetter() } || isSectionLabel(text)

/** 没找到星期表头时，按横向等分把课程分到 1..7 列。 */
private fun fallbackDay(cx: Int, width: Int, columns: Int): Int {
    val cell = width.toFloat() / columns
    return ((cx - cell * 0.08f) / cell).toInt().coerceIn(0, columns - 1) + 1
}

fun coursesFromOcr(
    lines: List<OcrLine>,
    totalWeeks: Int = 20,
    width: Int = 1080,
    columns: Int = 7,
): List<Course> {
    val cleaned = lines
        .map { it.copy(text = it.text.replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()) }
        .filter { it.text.isNotEmpty() }
    if (cleaned.size < 4) return emptyList()

    // 1) 星期表头
    val dayHeaders = cleaned.mapNotNull { line -> dayOfText(line.text)?.let { it to line } }
    val headerTop = dayHeaders.minOfOrNull { it.second.top } ?: 0
    val dayColumns: Map<Int, Int> = if (dayHeaders.size >= 3) {
        dayHeaders.groupBy { it.first }.mapValues { (_, v) -> v.minByOrNull { it.second.top }!!.second.cx }
    } else {
        emptyMap()
    }

    // 2) 节次行：必须在表头下方、且在星期列左侧（否则日期栏里的「12/13/14」会被当成节次）
    val firstDayCx = dayColumns.values.minOrNull() ?: Int.MAX_VALUE
    val rowLabels = cleaned
        .filter { it.top > headerTop + 24 }
        .filter { isSectionLabel(it.text) }
        .filter { dayColumns.isEmpty() || it.cx < firstDayCx - 20 }
        .sortedBy { it.cy }
    val rows: List<Pair<Int, IntRange>> = rowLabels
        .fold(mutableListOf<Pair<Int, IntRange>>()) { acc, line ->
            if (acc.none { kotlin.math.abs(it.first - line.cy) < 24 }) acc.add(line.cy to sectionRangeOfText(line.text)!!)
            acc
        }
    val headerCx = dayColumns.values.minOrNull() ?: Int.MAX_VALUE

    // 3) 其余文字落格子
    val cells = LinkedHashMap<Pair<Int, IntRange>, MutableList<OcrLine>>()
    for (line in cleaned) {
        if (isSectionLabel(line.text) &&
            rows.any { line.cy >= it.first - 12 && line.cy <= it.first + 12 }
        ) {
            continue
        }
        if (dayOfText(line.text) != null && line.top <= headerTop + 40) continue
        // 只有在找到了星期表头时，才把「表头左边的文字」当成节次列丢掉
        if (dayColumns.isNotEmpty() && rows.isNotEmpty() && line.cx < headerCx - 8) continue

        // 星期列：就近归入，但完全落在第一列左侧的（节次刻度、时间）丢掉
        val dayCenters = dayColumns.entries.sortedBy { it.value }.map { it.key to it.value }
        val gap = if (dayCenters.size >= 2) {
            (dayCenters.last().second - dayCenters.first().second) / (dayCenters.size - 1)
        } else {
            width / columns
        }
        val firstBandLeft = if (dayCenters.isNotEmpty()) dayCenters.first().second - gap / 2 else Int.MIN_VALUE
        if (dayCenters.isNotEmpty() && line.cx < firstBandLeft) continue
        val day = dayCenters.minByOrNull { kotlin.math.abs(it.second - line.cx) }?.first
            ?: fallbackDay(line.cx, width, columns)

        val row = rows.minByOrNull { kotlin.math.abs(it.first - line.cy) }
            ?.takeIf { kotlin.math.abs(it.first - line.cy) < 90 }
            ?.second ?: continue
        cells.getOrPut(day to row) { mutableListOf() }.add(line)
    }

    val out = mutableListOf<Course>()
    for ((key, block) in cells) {
        val (day, range) = key
        val ordered = block.sortedBy { it.cy }
        val weeks = ordered.mapNotNull { line ->
            parseWeeks(line.text, totalWeeks).takeIf { it.isNotEmpty() && Regex("\\d\\s*周|单周|双周").containsMatchIn(line.text) }
        }.firstOrNull() ?: emptyList()
        val body = ordered.filterNot { line ->
            Regex("\\d\\s*周|单周|双周").containsMatchIn(line.text) || isNoiseText(line.text)
        }
        // 课程名在格子里会换行（「大学」/「英语」/「（3）」），所以把开头那些「还不像地点」的行拼起来
        val nameLines = body.takeWhile { !looksLikeRoom(it.text) && !looksLikeTeacher(it.text) }
            .ifEmpty { body.take(1) }
        val name = nameLines.joinToString("") { it.text }.trim().take(40)
        if (name.length < 2) continue
        val rest = body.drop(nameLines.size).map { it.text }
        val teacher = rest.firstOrNull { looksLikeTeacher(it) }.orEmpty()
        val location = rest.firstOrNull { looksLikeRoom(it) }.orEmpty()
        out.add(
            Course(
                id = "ocr-${out.size}",
                name = name.take(40),
                teacher = teacher,
                location = location,
                dayOfWeek = day,
                startSection = range.first,
                endSection = range.last,
                weeks = weeks,
                colorIndex = -1,
                groupId = "ocr-" + name.take(24),
            ),
        )
    }
    return out
}
