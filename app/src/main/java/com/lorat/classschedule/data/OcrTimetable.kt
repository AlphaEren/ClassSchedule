package com.lorat.classschedule.data

import com.lorat.classschedule.data.model.Course
import kotlin.math.abs

/**
 * 从「图片识别」得到的文字块还原课表。
 *
 * OCR 只能给出一堆带坐标的文字，所以这里按版面还原：
 *  1. 上方的 `周一..周日` / `星期一..星期日` 定出星期列；
 *  2. 左侧的 `①..⑳` / `第N节` / `N` / `N-M` 定出节次刻度，刻度之间切成「行带」；
 *  3. 其余文字按坐标落进 (星期, 行带)，再在列内按「课程名 / 老师 / 周次 / 地点」的结构切成一门门课。
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
private val TimeLike = Regex("^\\d{1,2}[:：]\\d{2}(\\s*[-~—]\\s*\\d{1,2}[:：]\\d{2})?$")
private val WeekMarker = Regex(
    "[（(【\\[]?\\s*\\d{1,2}\\s*(?:[-~－—至]\\s*\\d{1,2}\\s*)?周(?:\\s*[（(【\\[]?\\s*[单双]\\s*[)）】\\]]?)?\\s*[)）】\\]]?" +
        "|[（(【\\[]\\s*[单双]\\s*[)）】\\]]",
)

internal fun dayOfText(raw: String): Int? {
    val t = raw.trim()
    if (t.isEmpty() || t.length > 5) return null
    val cn = Regex("(?:周|星期|礼拜)\\s*([一二三四五六日天])").find(t)?.groupValues?.get(1)
    if (cn != null) return ChineseDays[cn]
    if (t.length == 1) ChineseDays[t]?.let { return it }
    val lower = t.lowercase()
    return EnglishDays.firstOrNull { lower.startsWith(it.first) }?.second
}

/** `上午` / `下午` / `晚上` 这类小节分组的字，OCR 常和节次数字粘在一起（`上午1`）。 */
private fun stripSectionGroupWords(text: String): String =
    text.replace(Regex("上午|下午|晚上|中午|早上|大节|小节|第|节"), "")

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
    val n = stripSectionGroupWords(t).filter { it.isDigit() }.toIntOrNull()
    return if (n != null && n in 1..20 && t.length <= 6) n..n else null
}

/**
 * 整行就是节次标签吗？`①` / `第3节` / `3` / `3-4` / `上午1` 是；
 * `@教1-201` / `老师W【4-19周】` 这种不是（否则会被当成节次吃掉）。
 */
internal fun isSectionLabel(text: String): Boolean {
    val stripped = stripSectionGroupWords(text).replace(Regex("\\s"), "")
    if (stripped.isEmpty()) return false
    if (stripped.all { Circled.indexOf(it) >= 0 }) return true
    val rest = stripped.replace(Regex("[\\d\\-~－—至]"), "")
    return rest.isEmpty() && sectionRangeOfText(text) != null
}

/** 时间轴上的「08:00」、纯数字、纯符号、整行的节次标签都不是课程内容。 */
internal fun isNoiseText(text: String): Boolean =
    TimeLike.matches(text) || text.none { it.isLetter() } || isSectionLabel(text)

/** 地点：`@教1-201` / `A101` / `A108` / `A109` / `东A101` / `大教室8` / `楼101`。 */
internal fun looksLikeRoom(s: String): Boolean {
    val t = s.trim()
    if (t.startsWith("@")) return true
    if (Regex("^[\\d\\-—~]{3,}$").matches(t)) return true
    if (listOf("楼", "室", "馆", "场", "区", "机房", "教室", "操场", "大教室").any { t.contains(it) }) return true
    // 两三个字的前缀 + 2~4 位数字 + 可能的后缀字母：A101 / A108 / 东A101 / E203
    return Regex("^[\\p{IsHan}A-Za-z]{0,3}\\d{2,4}[A-Za-z]?$").matches(t)
}

internal fun looksLikeTeacher(s: String): Boolean =
    s.length in 2..4 && s.none { it.isDigit() } && !looksLikeRoom(s)

/** 把「老师W【4-19周】」这类混排拆成 (剩下的文字, 周次)。 */
internal fun splitWeeks(raw: String, totalWeeks: Int): Pair<String, List<Int>> {
    val match = WeekMarker.find(raw) ?: return raw to emptyList()
    val weeks = parseWeeks(match.value, totalWeeks)
    val rest = raw.removeRange(match.range).trim(' ', '，', ',', '、', '【', '】', '[', ']', '(', ')', '（', '）', '·')
    return rest to weeks
}

/** 没找到星期表头时，按横向等分把课程分到 1..7 列。 */
private fun fallbackDay(cx: Int, width: Int, columns: Int): Int {
    val cell = width.toFloat() / columns
    return ((cx - cell * 0.08f) / cell).toInt().coerceIn(0, columns - 1) + 1
}

private class Draft {
    var name = ""
    var teacher = ""
    var room = ""
    var weeks: List<Int> = emptyList()
    var hasRoom = false
    var nameCy: Int? = null
    val cys = mutableListOf<Int>()

    /** 周次或地点都收到过，就算一条完整的课程记录。 */
    val complete: Boolean get() = weeks.isNotEmpty() || hasRoom

    fun addName(text: String, cy: Int) {
        if (name.isBlank()) nameCy = cy
        name += text
    }

    fun close(): Boolean = name.isNotBlank() && name.length >= 2
}

fun coursesFromOcr(
    lines: List<OcrLine>,
    totalWeeks: Int = 20,
    width: Int = 1080,
    columns: Int = 7,
): List<Course> {
    val cleaned = lines
        .map {
            it.copy(
                text = it.text.replace('\u00a0', ' ')
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .trim('|', '｜', '丨', '*', '·')
                    .trim(),
            )
        }
        .filter { it.text.isNotEmpty() }
    if (cleaned.size < 4) return emptyList()

    // 1) 星期表头：标题里也可能出现「一」「日」这种单字，所以取「同一行里星期最全」的那一组
    val dayLike = cleaned.mapNotNull { line -> dayOfText(line.text)?.let { it to line } }
    val headerGroup = dayLike
        .groupBy { it.second.cy / 24 }
        .filter { (_, v) -> v.map { it.first }.distinct().size >= 3 }
        .maxByOrNull { (_, v) -> v.map { it.first }.distinct().size }
        ?.value
        .orEmpty()
    val dayHeaders = headerGroup.ifEmpty { dayLike }
    val headerTop = dayHeaders.minOfOrNull { it.second.top } ?: 0
    val dayColumns: Map<Int, Int> = if (dayHeaders.map { it.first }.distinct().size >= 3) {
        dayHeaders.groupBy { it.first }.mapValues { (_, v) -> v.minByOrNull { it.second.top }!!.second.cx }
    } else {
        emptyMap()
    }
    val dayCenters = dayColumns.entries.sortedBy { it.value }.map { it.key to it.value }
    val gap = if (dayCenters.size >= 2) {
        (dayCenters.last().second - dayCenters.first().second) / (dayCenters.size - 1)
    } else {
        width / columns
    }
    val firstBandLeft = if (dayCenters.isNotEmpty()) dayCenters.first().second - gap / 2 else Int.MIN_VALUE

    // 2) 节次刻度 → 行带（刻度之间的中线切分，跨两小节的课自然落进两格）
    val scale = cleaned
        .filter { it.top > headerTop + 24 }
        .filter { isSectionLabel(it.text) }
        .filter { dayCenters.isEmpty() || it.cx < firstBandLeft }
        .sortedBy { it.cy }
        .fold(mutableListOf<Pair<Int, IntRange>>()) { acc, line ->
            if (acc.none { abs(it.first - line.cy) < 20 }) acc.add(line.cy to sectionRangeOfText(line.text)!!)
            acc
        }
    val rowGap = if (scale.size >= 2) {
        (scale.last().first - scale.first().first) / (scale.size - 1)
    } else {
        0
    }
    // 每个刻度带的下边界（与下一刻度中线）；最后一个带往下延伸
    val bandBottom = scale.indices.map { i ->
        when {
            scale.size == 1 -> Int.MAX_VALUE
            i == scale.lastIndex -> scale[i].first + rowGap
            else -> (scale[i].first + scale[i + 1].first) / 2
        }
    }
    fun bandOf(cy: Int): Int? {
        if (scale.isEmpty()) return null
        for (i in scale.indices) {
            val top = if (i == 0) Int.MIN_VALUE else bandBottom[i - 1]
            if (cy >= top && cy < bandBottom[i]) return i
        }
        return null
    }

    // 3) 按星期列收文字（不含节次刻度、表头本身，以及表头以上的标题）
    val perDay = LinkedHashMap<Int, MutableList<OcrLine>>()
    for (line in cleaned) {
        if (isSectionLabel(line.text)) continue
        if (dayOfText(line.text) != null && line.top <= headerTop + 40) continue
        if (headerTop > 0 && line.bottom < headerTop) continue
        if (dayCenters.isNotEmpty()) {
            if (line.cx < firstBandLeft) continue
        } else if (line.cx < width * 0.15f) {
            continue // 没有表头时，把最左侧一列（节次/时间轴）丢掉
        }
        val day = dayCenters.minByOrNull { abs(it.second - line.cx) }?.first
            ?: fallbackDay(line.cx, width, columns)
        perDay.getOrPut(day) { mutableListOf() }.add(line)
    }

    // 4) 每一天按「课程名 → 老师 → 地点」结构切成一门门课（一格两门课也能切开）
    val draftsByDay = LinkedHashMap<Int, List<Draft>>()
    for ((day, dayLines) in perDay) draftsByDay[day] = splitCourses(dayLines, totalWeeks)

    // 4b) 节次刻度一个都没识别出来时的兜底：把所有课程名的纵坐标聚类成「行」，按顺序编号
    val fallbackRow: Map<Int, Int> = if (scale.isEmpty()) {
        val centers = draftsByDay.values.flatten().mapNotNull { it.nameCy }.sorted()
        val diffs = centers.zipWithNext { a, b -> b - a }.filter { it > 0 }.sorted()
        val gap = diffs.getOrNull(diffs.size / 2) ?: 40
        val threshold = maxOf(18, (gap * 1.6).toInt())
        val rowStarts = mutableListOf<Int>()
        for (c in centers) {
            if (rowStarts.isEmpty() || c - rowStarts.last() > threshold) rowStarts.add(c)
        }
        centers.associateWith { c -> (rowStarts.indexOfLast { it <= c } + 1).coerceAtLeast(1) }
    } else {
        emptyMap()
    }

    val out = mutableListOf<Course>()
    for ((day, drafts) in draftsByDay) {
        for (draft in drafts) {
            val name = draft.name.trim().take(40)
            if (name.length < 2) continue
            val (start, end) = if (scale.isEmpty()) {
                val row = fallbackRow[draft.nameCy ?: draft.cys.firstOrNull() ?: 0] ?: 1
                row to row
            } else {
                val firstBand = (bandOf(draft.nameCy ?: draft.cys.firstOrNull() ?: 0) ?: 0).coerceIn(0, scale.lastIndex)
                var lastBand = firstBand
                for (cy in draft.cys) {
                    val band = bandOf(cy) ?: continue
                    if (band > lastBand && cy > bandBottom[lastBand] + rowGap * 0.25) lastBand = band
                }
                lastBand = lastBand.coerceIn(firstBand, scale.lastIndex)
                scale[firstBand].second.first to scale[lastBand].second.last
            }
            out.add(courseOf(out.size, name, draft, day, start.coerceIn(1, 20), end.coerceIn(start, 20)))
        }
    }
    return out
}

/** 把一天的文字按「课程名 / 老师 / 地点 / 周次」的重复结构切成若干门课。 */
private fun splitCourses(dayLines: List<OcrLine>, totalWeeks: Int): List<Draft> {
    val drafts = mutableListOf<Draft>()
    var current = Draft()
    for (line in dayLines.sortedBy { it.cy }) {
        val text = line.text.trim()
        if (text.isEmpty() || isNoiseText(text)) continue
        val (rest, weeks) = splitWeeks(text, totalWeeks)
        val hasContent = rest.isNotBlank()
        val room = hasContent && looksLikeRoom(rest)
        val teacher = hasContent && !room && looksLikeTeacher(rest) && current.name.isNotBlank()
        // 上一门课已经收全（有周次或地点），这一行又是「新的课程名」→ 上一门结束
        // （同一格塞两门网课的情况就靠这个切开）
        if (hasContent && current.complete && !room && !teacher) {
            if (current.close()) drafts.add(current)
            current = Draft()
        }
        if (hasContent) {
            when {
                room -> {
                    current.room = rest
                    current.hasRoom = true
                }
                teacher -> current.teacher = current.teacher.ifBlank { rest }
                else -> current.addName(rest, line.cy)
            }
        }
        if (weeks.isNotEmpty()) current.weeks = weeks
        current.cys.add(line.cy)
    }
    if (current.close()) drafts.add(current)
    return drafts
}

private fun courseOf(
    index: Int,
    name: String,
    draft: Draft,
    day: Int,
    start: Int,
    end: Int,
): Course = Course(
    id = "ocr-$index",
    name = name,
    teacher = draft.teacher,
    location = draft.room,
    dayOfWeek = day,
    startSection = start,
    endSection = end,
    weeks = draft.weeks,
    colorIndex = -1,
    groupId = "ocr-" + name.take(24),
)
