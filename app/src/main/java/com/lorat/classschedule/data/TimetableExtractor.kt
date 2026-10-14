package com.lorat.classschedule.data

import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.model.SectionTime
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID
import kotlin.math.abs

/**
 * Turns an arbitrary academic-system timetable page into [Course]s.
 *
 * The injected script walks the top document plus every same-origin frame (depth 3) and tries:
 *  1. "box"      - course boxes (a name/teacher/week/room block inside a div or td) matched to
 *                  the nearest day column and period row; this handles timetables that are not
 *                  a real <table> grid.
 *  2. "selector" - a known timetable container for the detected system.
 *  3. "grid"     - the most timetable-like <table> (scored by day headers + week density).
 *  4. "geometry" - cluster course-like leaf elements into day columns and period rows.
 *
 * It also reports which document the timetable was found in (foundUrl), which is remembered so
 * later background refreshes can load the timetable page directly instead of the login shell.
 */
object TimetableExtractor {

    const val PRESET_AUTO = "auto"
    const val PRESET_GENERIC = "generic"
    const val PRESET_ZF = "zf"
    const val PRESET_JWGLXT = "jwglxt"
    const val PRESET_QZ = "qz"
    /** 教务处 SaaS（EXESAC）：页面只放一个空 table，课表全来自 TimeTableHandler.ashx 的 JSON。 */
    const val PRESET_SAAS = "saas"

    val presets: List<Pair<String, String>> = listOf(
        PRESET_AUTO to "自动检测（推荐）",
        PRESET_GENERIC to "通用",
        PRESET_ZF to "正方 / URP",
        PRESET_JWGLXT to "正方教务（jwglxt 新版）",
        PRESET_QZ to "强智教务",
        PRESET_SAAS to "教务处 SaaS（接口原始数据）",
    )

    fun presetLabel(key: String): String =
        presets.firstOrNull { it.first == key }?.second ?: presets.first().second

    /** True when this scrape came from a system that handed us its own structured data. */
    fun isStructured(result: ScrapeResult): Boolean = result.mode == "net" && result.courses.isNotEmpty()

    fun scraperJs(preset: String): String = RAW_SCRAPER_JS.replace("__PRESET__", preset)

    data class Cell(
        val text: String,
        val colSpan: Int,
        val rowSpan: Int,
        /** Week spec taken from a title/id attribute when the visible text has none. */
        val weeks: String = "",
        /** Day/section baked into an id like kb_3_2; 0 when unknown. */
        val day: Int = 0,
        val section: Int = 0,
    )

    data class ScrapeResult(
        val ok: Boolean,
        val error: String? = null,
        val message: String = "",
        val mode: String = "",
        val system: String = "unknown",
        val preset: String = "",
        val diagnostics: String = "",
        val title: String = "",
        val url: String = "",
        val foundUrl: String = "",
        val rows: List<List<Cell>> = emptyList(),
        /** Courses already structured by the page's own data, bypassing text parsing. */
        val courses: List<Course> = emptyList(),
        /** The source's own bell schedule, when it published one (exact 上课/下课时间). */
        val sectionTimes: List<SectionTime> = emptyList(),
        /** The week the source considers current; 0 when it did not say. */
        val currentWeek: Int = 0,
        /** First Monday of the semester according to the source (yyyy-MM-dd). */
        val semesterStart: String = "",
        /** Last teaching week the source mentioned. */
        val weekCount: Int = 0,
        /** Shape report of the tables found, for troubleshooting an unrecognized page. */
        val sample: String = "",
    )

    fun failureMessage(result: ScrapeResult): String = when (result.error) {
        "no-page" -> "没能读取到页面内容，请确认课表页已完全加载后再试。"
        "no-grid" -> if (result.diagnostics.contains("cross-origin") || result.diagnostics.contains("blocked")) {
            "课表在一个跨域的框架里，无法直接读取。请在该系统里找到课表本身的页面地址（通常地址带有 xskbcx / kb 等字样）后重新添加。"
        } else {
            "页面里没有找到课表。请确认已经点开「课表」页面（不是登录页或首页导航页）后再试。"
        }
        "exception" -> "识别时出错：${result.message}"
        else -> "识别失败（${result.error ?: "未知错误"}）。"
    }

    // ---- parsing the JS payload -------------------------------------------------------------

    fun decodeJsValue(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed == "null") return ""
        return runCatching {
            val v = JSONTokener(trimmed).nextValue()
            if (v is String) v else trimmed
        }.getOrDefault(trimmed)
    }

    fun parseScrapeResult(rawJsValue: String?): ScrapeResult {
        val decoded = decodeJsValue(rawJsValue)
        if (decoded.isBlank()) return ScrapeResult(ok = false, error = "no-page")
        return runCatching {
            val obj = JSONObject(decoded)
            val ok = obj.optBoolean("ok", false)
            val rowsJson = obj.optJSONArray("rows")
            val rows = ArrayList<List<Cell>>()
            if (rowsJson != null) {
                for (i in 0 until rowsJson.length()) {
                    val rowJson = rowsJson.optJSONArray(i) ?: continue
                    val row = ArrayList<Cell>()
                    for (j in 0 until rowJson.length()) {
                        val c = rowJson.optJSONObject(j) ?: continue
                        row.add(
                            Cell(
                                text = c.optString("t"),
                                colSpan = c.optInt("cs", 1),
                                rowSpan = c.optInt("rs", 1),
                                weeks = c.optString("w", ""),
                                day = c.optInt("d", 0),
                                section = c.optInt("so", 0),
                            )
                        )
                    }
                    rows.add(row)
                }
            }
            val dataCourses = ArrayList<Course>()
            obj.optJSONArray("courses")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val c = arr.optJSONObject(i) ?: continue
                    val name = c.optString("n", "").trim()
                    if (name.length < 2) continue
                    val weeksArr = c.optJSONArray("w")
                    val weeks = ArrayList<Int>()
                    if (weeksArr != null) for (j in 0 until weeksArr.length()) weeks.add(weeksArr.optInt(j))
                    val start = c.optInt("s", 1)
                    dataCourses.add(
                        Course(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            teacher = c.optString("t", "").trim(),
                            location = c.optString("r", "").trim(),
                            dayOfWeek = c.optInt("d", 0),
                            startSection = start,
                            endSection = c.optInt("e", start),
                            weeks = weeks,
                        )
                    )
                }
            }
            val sectionTimes = ArrayList<SectionTime>()
            obj.optJSONArray("st")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val start = item.optString("s", "").trim()
                    val end = item.optString("e", "").trim()
                    if (start.isBlank()) continue
                    sectionTimes.add(SectionTime(sectionTimes.size + 1, start, end.ifBlank { start }))
                }
            }
            ScrapeResult(
                ok = ok && (rows.isNotEmpty() || dataCourses.isNotEmpty()),
                error = obj.optString("error").takeIf { it.isNotBlank() },
                message = obj.optString("message", ""),
                mode = obj.optString("mode", ""),
                system = obj.optString("system", "unknown"),
                preset = obj.optString("preset", ""),
                diagnostics = obj.optString("diagnostics", ""),
                title = obj.optString("title", ""),
                url = obj.optString("url", ""),
                foundUrl = obj.optString("foundUrl", ""),
                rows = rows,
                courses = dataCourses,
                sectionTimes = sectionTimes,
                currentWeek = obj.optInt("week", 0),
                semesterStart = obj.optString("start", "").trim(),
                weekCount = obj.optInt("weeks", 0),
                sample = obj.optString("sample", ""),
            )
        }.getOrElse { ScrapeResult(ok = false, error = "exception", message = it.message ?: "parse-error") }
    }

    // ---- grid -> courses --------------------------------------------------------------------

    private val chineseDays = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7,
    )
    private val englishDays = listOf(
        "mon" to 1, "tue" to 2, "wed" to 3, "thu" to 4, "fri" to 5, "sat" to 6, "sun" to 7,
    )

    /** Prefer structured courses the page exposed; otherwise rebuild them from the grid. */
    fun coursesFrom(result: ScrapeResult, totalWeeks: Int): List<Course> {
        val limit = maxOf(totalWeeks, 1)
        if (result.courses.isNotEmpty()) {
            return result.courses
                .filter { it.name.length >= 2 && it.dayOfWeek in 1..7 && it.startSection in 1..24 }
                .map { c ->
                    c.copy(
                        weeks = c.weeks.filter { it in 1..limit },
                        colorIndex = -1,
                        // 同一门课的多个时间段归到一个分组，课表里才会当做一门课（同色、可一起编辑）
                        groupId = c.groupId.ifBlank { "n-" + abs(c.name.hashCode()) },
                    )
                }
        }
        return extractCourses(result, totalWeeks)
    }

    fun extractCourses(result: ScrapeResult, totalWeeks: Int): List<Course> {
        if (!result.ok || result.rows.isEmpty()) return emptyList()
        val rows = result.rows
        val header = detectHeader(rows) ?: return emptyList()
        val headerIndex = header.first
        val dayColumns = header.second
        val minDayColumn = dayColumns.keys.minOrNull() ?: 1
        val sectionRows = resolveSectionRows(rows, headerIndex)

        val byKey = LinkedHashMap<String, Course>()
        for (ri in rows.indices) {
            if (ri <= headerIndex) continue
            val section = sectionRows[ri] ?: continue
            var col = 0
            for (cell in rows[ri]) {
                val startCol = col
                col += cell.colSpan
                val day = dayColumns[startCol] ?: cell.day.takeIf { it in 1..7 } ?: continue
                if (startCol < minDayColumn && cell.day <= 0) continue
                if (!looksLikeCourse(cell.text)) continue
                val start = if (cell.section in 1..20) cell.section else section.first
                val end = maxOf(section.second, start + cell.rowSpan - 1)
                val course = parseCourseCell(cell.text, cell.weeks, day, start, end, totalWeeks) ?: continue
                // 同名、同星期、同节次的课可能只是「周次不同」（例如 1-6 周与 9-10 周），
                // 去重键必须带上周次，否则后面的记录会被前面吃掉。
                byKey.putIfAbsent(
                    "${course.name}|${course.dayOfWeek}|${course.startSection}-${course.endSection}|${course.weeks.joinToString(",")}",
                    course,
                )
            }
        }
        return byKey.values.toList()
    }

    private fun detectHeader(rows: List<List<Cell>>): Pair<Int, Map<Int, Int>>? {
        var bestIndex = -1
        var best: Map<Int, Int> = emptyMap()
        rows.take(5).forEachIndexed { index, row ->
            val map = LinkedHashMap<Int, Int>()
            var col = 0
            for (cell in row) {
                val day = dayOfWeekOf(cell.text)
                if (day != null && !map.containsKey(col)) map[col] = day
                col += cell.colSpan
            }
            if (map.size > best.size) {
                best = map
                bestIndex = index
            }
        }
        if (best.size >= 3) return bestIndex to best

        val idx = rows.indexOfFirst { row -> row.sumOf { it.colSpan } >= 8 }
        if (idx >= 0) {
            val map = LinkedHashMap<Int, Int>()
            var col = 0
            var cellIndex = 0
            for (cell in rows[idx]) {
                if (cellIndex in 1..7) map[col] = cellIndex
                cellIndex++
                col += cell.colSpan
            }
            if (map.size >= 5) return idx to map
        }
        return null
    }

    private fun dayOfWeekOf(raw: String): Int? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        Regex("[周星期礼拜]\\s*([一二三四五六日天])").find(text)?.let { m ->
            chineseDays[m.groupValues[1]]?.let { return it }
        }
        val lower = text.lowercase()
        englishDays.firstOrNull { lower.contains(it.first) }?.let { return it.second }
        return null
    }

    private fun resolveSectionRows(rows: List<List<Cell>>, headerIndex: Int): Map<Int, Pair<Int, Int>> {
        val dataIndices = (headerIndex + 1 until rows.size).toList()
        if (dataIndices.isEmpty()) return emptyMap()

        val labelled = LinkedHashMap<Int, Pair<Int, Int>>()
        for (ri in dataIndices) {
            val label = rows[ri].firstOrNull()?.text ?: ""
            parseSectionLabel(label)?.let { labelled[ri] = it }
        }
        if (labelled.size >= maxOf(2, dataIndices.size / 2)) return labelled

        val perRow = if (dataIndices.size in 4..7) 2 else 1
        val result = LinkedHashMap<Int, Pair<Int, Int>>()
        var counter = 1
        for (ri in dataIndices) {
            result[ri] = counter to (counter + perRow - 1)
            counter += perRow
        }
        return result
    }

    private fun parseSectionLabel(raw: String): Pair<Int, Int>? {
        val t = raw.trim()
        if (t.isEmpty() || t.length > 12) return null
        Regex("(\\d{1,2})\\s*[-~至]\\s*(\\d{1,2})").find(t)?.let { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            if (a in 1..20 && b in 1..20 && a <= b) return a to b
        }
        Regex("(\\d{1,2})").find(t)?.let { m ->
            val a = m.groupValues[1].toInt()
            if (a in 1..20 && (t.contains("节") || t.length <= 2)) return a to a
        }
        return null
    }

    private fun looksLikeCourse(text: String): Boolean {
        val t = text.trim()
        if (t.length < 2) return false
        return !t.matches(Regex("^[\\d\\s.,，、\\-~周第节()（）]+$"))
    }

    private fun parseCourseCell(
        text: String,
        weekHint: String,
        day: Int,
        start: Int,
        end: Int,
        totalWeeks: Int,
    ): Course? {
        val cleaned = text.replace("\u00a0", " ").trim()
        if (cleaned.isEmpty()) return null
        val weeks = parseWeeks(weekHint.ifBlank { cleaned }, totalWeeks)
        val stripped = cleaned
            .replace(Regex("\\(\\s*\\d+[^)]*周\\s*\\)|（\\s*\\d+[^）]*周\\s*）"), " ")
            .replace(Regex("(第)?\\s*\\d+(\\s*[-~至]\\s*\\d+)?(\\s*[,，、]\\s*\\d+(\\s*[-~至]\\s*\\d+)?)*\\s*周"), " ")
            .replace(Regex("单周|双周"), " ")
            .replace(Regex("第?\\s*\\d+(\\s*[-~至]\\s*\\d+)?\\s*节"), " ")

        var tokens = stripped.split(Regex("[\\n\\r]+|\\s{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.size <= 1) {
            val single = stripped.trim()
            if (single.contains("@")) {
                tokens = listOf(single.substringBefore("@").trim(), single.substringAfter("@").trim())
            } else if (single.length > 10 && single.contains(' ')) {
                val spaced = single.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
                if (spaced.size >= 2) tokens = spaced
            }
        }
        val name = (tokens.firstOrNull() ?: stripped.trim()).take(40).trim()
        if (name.length < 2) return null
        val rest = tokens.drop(1)
        val teacher = rest.firstOrNull { isTeacherLike(it) }.orEmpty()
        val location = rest.firstOrNull { isLocationLike(it) }.orEmpty()
        return Course(
            id = UUID.randomUUID().toString(),
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = day,
            startSection = start,
            endSection = end,
            weeks = weeks,
            colorIndex = -1,
        )
    }

    private fun parseWeeks(text: String, totalWeeks: Int): List<Int> {
        val spec = Regex("(\\d+(?:\\s*[-~至]\\s*\\d+)?(?:\\s*[,，、]\\s*\\d+(?:\\s*[-~至]\\s*\\d+)?)*)\\s*周")
            .find(text)?.groupValues?.get(1) ?: return emptyList()
        val oddOnly = text.contains("单周") || text.contains("(单)") || text.contains("（单）")
        val evenOnly = text.contains("双周") || text.contains("(双)") || text.contains("（双）")
        val weeks = sortedSetOf<Int>()
        for (part in spec.split(Regex("[,，、]"))) {
            val p = part.trim()
            val range = Regex("^(\\d+)\\s*[-~至]\\s*(\\d+)$").find(p)
            if (range != null) {
                val a = range.groupValues[1].toInt()
                val b = range.groupValues[2].toInt()
                if (a <= b) for (w in a..b) weeks.add(w)
            } else {
                p.toIntOrNull()?.let { weeks.add(it) }
            }
        }
        var list = weeks.toList()
        if (oddOnly) list = list.filter { it % 2 == 1 }
        if (evenOnly) list = list.filter { it % 2 == 0 }
        return list.filter { it in 1..maxOf(totalWeeks, 1) }
    }

    private fun isTeacherLike(s: String): Boolean =
        s.length in 2..6 && !s.any { it.isDigit() } && !isLocationLike(s)

    private fun isLocationLike(s: String): Boolean =
        s.any { it.isDigit() } || s.contains("楼") || s.contains("室") || s.contains("馆") ||
            s.contains("区") || s.contains("场") || s.contains("机房") || s.contains("教")

    // ---- injected script --------------------------------------------------------------------

    private const val RAW_SCRAPER_JS: String = """
(function(){
var PRESET='__PRESET__';
function txt(e){var t=(e.innerText||e.textContent||'')+'';return t.replace(/\u00a0/g,' ').replace(/[ \t]+/g,' ').trim();}
function rc(e){try{return e.getBoundingClientRect();}catch(err){return null;}}
var WEEK_RE=/(\d+\s*[-~至]\s*\d+|\d+)\s*周/;
var DAY_RE=/(?:周|星期|礼拜)\s*([一二三四五六日天])/;
var DAYN={'一':1,'二':2,'三':3,'四':4,'五':5,'六':6,'日':7,'天':7};
var log=[];
function docs(){
  var out=[];
  function url(d){try{return d.location.href||'';}catch(e){return '';}}
  function walk(doc,depth){
    out.push({doc:doc,url:url(doc)});
    if(depth>2) return;
    var f;
    try{ f=doc.querySelectorAll('iframe,frame'); }catch(e){ return; }
    for(var i=0;i<f.length;i++){
      var inner=null;
      try{ inner=f[i].contentDocument; }catch(e){ log.push('frame'+i+':blocked'); continue; }
      if(inner) walk(inner,depth+1);
      else log.push('frame'+i+':cross-origin');
    }
  }
  walk(document,0);
  return out;
}
function dayOf(t){var m=DAY_RE.exec((t||'')+'');return m?(DAYN[m[1]]||0):0;}
function expandWeeks(spec,odd,even){
  var set={},parts=(''+spec).split(/[,，、]/);
  for(var i=0;i<parts.length;i++){
    var p=parts[i].replace(/\s/g,'');
    var r=/^(\d{1,2})[-~至](\d{1,2})$/.exec(p);
    if(r){var a=parseInt(r[1],10),b=parseInt(r[2],10); if(a<=b){for(var w=a;w<=b;w++) set[w]=1;}}
    else if(/^\d{4}$/.test(p)){var a2=parseInt(p.substring(0,2),10),b2=parseInt(p.substring(2,4),10); if(a2>=1&&b2>=a2&&b2<=30){for(var w2=a2;w2<=b2;w2++) set[w2]=1;}}
    else {var v=parseInt(p,10); if(v>=1&&v<=30) set[v]=1;}
  }
  var out=[];
  for(var k in set){var n=parseInt(k,10); if(odd&&n%2===0) continue; if(even&&n%2===1) continue; out.push(n);}
  out.sort(function(a,b){return a-b;});
  return out.length?out:null;
}
function weekNums(s){
  if(s&&typeof s!=='string'&&s.length){
    var aout=[];
    for(var ai=0;ai<s.length;ai++){ var av=parseInt(s[ai],10); if(av>=1&&av<=30) aout.push(av); }
    aout.sort(function(a,b){return a-b;});
    return aout.length?aout:null;
  }
  var t=(s||'')+'';
  var odd=/单周|\(单\)|（单）/.test(t), even=/双周|\(双\)|（双）/.test(t);
  var m=/(\d{1,2}(?:\s*[-~至]\s*\d{1,2})?(?:\s*[,，、]\s*\d{1,2}(?:\s*[-~至]\s*\d{1,2})?)*)\s*周/.exec(t);
  if(m) return expandWeeks(m[1],odd,even);
  if(/^\s*\d{1,2}(\s*[-~至,，、]\s*\d{1,2})*\s*$/.test(t)) return expandWeeks(t,odd,even);
  return null;
}
function jcRange(s){
  if(s&&typeof s!=='string'&&s.length){
    var a1=parseInt(s[0],10),b1=parseInt(s[s.length-1],10);
    if(a1>=1&&a1<=20&&b1>=a1&&b1<=20) return {s:a1,e:b1};
    return null;
  }
  var raw=(s||'')+'';
  var m=/^(\d{1,2})\s*[-~至]\s*(\d{1,2})$/.exec(raw.replace(/[^0-9\-~至]/g,''));
  if(m){var a=parseInt(m[1],10),b=parseInt(m[2],10); if(a>=1&&a<=20&&b>=a&&b<=20) return {s:a,e:b};}
  var d=raw.replace(/[^0-9]/g,'');
  if(d.length===4){var a2=parseInt(d.substring(0,2),10),b2=parseInt(d.substring(2,4),10); if(a2>=1&&a2<=20&&b2>=a2&&b2<=20) return {s:a2,e:b2};}
  var n=parseInt(d,10); if(n>=1&&n<=20) return {s:n,e:n};
  return null;
}
function cellObj(c){
  var o={t:txt(c),cs:c.colSpan||1,rs:c.rowSpan||1,w:'',d:0,so:0};
  try{
    if(!WEEK_RE.test(o.t)){
      var ti=c.getAttribute('title')||''; var id=c.getAttribute('id')||'';
      if(WEEK_RE.test(ti)) o.w=ti; else { var wm=WEEK_RE.exec(id); if(wm) o.w=wm[0]; }
    }
  }catch(e){}
  try{
    var id2=c.getAttribute('id')||'';
    var mm=/kb[_-]?(\d{1,2})[_-](\d{1,2})/i.exec(id2);
    if(mm){
      var a=parseInt(mm[1],10),b=parseInt(mm[2],10);
      if(a>=1&&a<=7&&b>=1&&b<=20){ o.d=a; o.so=b; }
      else if(b>=1&&b<=7&&a>=1&&a<=20){ o.d=b; o.so=a; }
    }
  }catch(e){}
  return o;
}
function matrix(tb,u){
  var rows=tb&&tb.rows; if(!rows||rows.length<4||rows.length>60) return null;
  var out=[],mc=0,wh=0,dh=0,cw=0,cd=0;
  for(var r=0;r<rows.length;r++){
    var cs=rows[r].cells,row=[];
    if(cs.length>mc) mc=cs.length;
    var rt=''; try{ rt=(rows[r].innerText||'')+''; }catch(e){}
    if(WEEK_RE.test(rt)) wh++;
    for(var c=0;c<cs.length;c++){
      var o=cellObj(cs[c]);
      row.push(o);
      if(o.w||WEEK_RE.test(o.t)) cw++;
      if(r<4&&dayOf(o.t)) dh++;
      if(c===0&&dayOf(o.t)) cd++;
    }
    out.push(row);
  }
  if(mc<5) return null;
  return {rows:out,cols:mc,weekRows:wh,cellsWithWeeks:cw,dayHdr:dh,colDay:cd,url:u};
}
function scoreGrid(m){
  if(!m) return -1;
  var s=m.dayHdr*200+m.weekRows*60;
  var filled=0;
  for(var r=0;r<m.rows.length;r++) for(var c=0;c<m.rows[r].length;c++){ if(m.rows[r][c].t) filled++; }
  var ratio=filled>0?m.cellsWithWeeks/filled:0;
  if(m.cols>=7&&m.cols<=9) s+=250;
  if(m.rows.length>=6&&m.rows.length<=28) s+=150; else s-=200;
  if(ratio>=0.25) s+=200; else if(ratio<0.1) s-=250;
  return s;
}
function transposeRows(rows){
  var out=[],maxc=0;
  for(var r=0;r<rows.length;r++) if(rows[r].length>maxc) maxc=rows[r].length;
  for(var c=0;c<maxc;c++){
    var row=[];
    for(var r2=0;r2<rows.length;r2++) row.push(rows[r2][c]||{t:'',cs:1,rs:1,w:'',d:0,so:0});
    out.push(row);
  }
  return out;
}
function jsLit(s){
  var t=s.replace(/([\{,\[]\s*)([A-Za-z_][A-Za-z0-9_]*)\s*:/g,function(m,a,b){ return a+'"'+b+'":'; });
  t=t.replace(/'((?:[^'\\]|\\.)*)'/g,function(m,b){ return '"'+b.replace(/\\'/g,"'").replace(/"/g,'\\"')+'"'; });
  t=t.replace(/,(\s*[\}\]])/g,function(m,a){ return a; });
  return t;
}
function arrFromText(t){
  var out=[];
  for(var i=0;i<t.length;i++){
    if(t.charAt(i)!=='[') continue;
    var depth=0,inStr=false,q='',esc=false,end=-1;
    for(var j=i;j<t.length;j++){
      var ch=t.charAt(j);
      if(inStr){ if(esc){esc=false;} else if(ch==="\\"){esc=true;} else if(ch===q){inStr=false;} continue; }
      if(ch==='"'||ch==="'"){inStr=true;q=ch;continue;}
      if(ch==='[') depth++;
      else if(ch===']'){ depth--; if(depth===0){ end=j; break; } }
    }
    if(end<0) break;
    var s=t.substring(i,end+1);
    if(s.length>40){
      var v=null,ok=false;
      try{ v=JSON.parse(s); ok=true; }catch(e){
        try{ v=JSON.parse(jsLit(s)); ok=true; }catch(e2){ ok=false; }
      }
      if(ok&&v&&v.length>=3&&typeof v[0]==='object'&&!Array.isArray(v[0])) out.push(v);
    }
    i=end;
  }
  return out;
}
var K_NAME=['kcmc','coursename','course_name','kcname','kcmcshow','name','title','kmc','kcmc1'];
var K_TEA=['jsxm','jsmc','teacher','jsname','skjs','teachernames','teachername','rkjs','xm'];
var K_ROOM=['cdmc','location','room','classroom','jsdd','jxdd','skdd','place','venue','roomname','skddmc'];
var K_DAY=['xqj','weekday','day','xingqi','dayofweek','weekdayname','xqjmc'];
var K_JC=['jcs','jcor','jcstr','jcinfo','section','sections','jcid','ksjc','pkjc','jc'];
var K_ZC=['zcd','zcmc','zcstr','zc','weeks','weekstr'];
function pickRaw(o,keys){
  var k,lk,i,v;
  if(!o||typeof o!=='object') return null;
  for(k in o){ lk=(k||'').toLowerCase(); for(i=0;i<keys.length;i++){ if(lk===keys[i]){ v=o[k]; if(v!==null&&v!==undefined&&(''+v).length>0) return v; } } }
  for(k in o){ lk=(k||'').toLowerCase(); for(i=0;i<keys.length;i++){ if(lk.indexOf(keys[i])>=0){ v=o[k]; if(v!==null&&v!==undefined&&(''+v).length>0) return v; } } }
  return null;
}
function pick(o,keys){
  var v=pickRaw(o,keys);
  return (v===null||v===undefined)?'':(''+v);
}
function normDay(v){
  var d=dayOf(v); if(d) return d;
  var n=parseInt((v||'')+'',10); if(n>=1&&n<=7) return n;
  return 0;
}
function scoreArr(arr){
  var score=0,lim=Math.min(arr.length,80);
  for(var i=0;i<lim;i++){
    var o=arr[i]; if(!o||typeof o!=='object') continue;
    if(pick(o,K_NAME)) score+=3;
    if(pick(o,K_DAY)) score+=3;
    if(pickRaw(o,K_JC)) score+=3;
    if(pickRaw(o,K_ZC)) score+=1;
    if(pick(o,K_ROOM)) score+=1;
    if(pick(o,K_TEA)) score+=1;
  }
  return score;
}
function considerText(t,src,S){
  if(!t||t.length<40||t.indexOf('[')<0) return;
  var arrs=arrFromText(t);
  for(var a=0;a<arrs.length;a++){
    var sc=scoreArr(arrs[a]);
    if(sc>S.best){ S.best=sc; S.arr=arrs[a]; S.src=src; }
  }
}
function arrToCourses(arr){
  var out=[];
  for(var k2=0;k2<arr.length;k2++){
    var ob=arr[k2]; if(!ob||typeof ob!=='object') continue;
    var nm=pick(ob,K_NAME); if(!nm||nm.length<2) continue;
    var day=normDay(pickRaw(ob,K_DAY)); if(!day) continue;
    var jc=jcRange(pickRaw(ob,K_JC)); if(!jc) continue;
    var wk=weekNums(pickRaw(ob,K_ZC));
    out.push({n:nm.substring(0,40),t:pick(ob,K_TEA),r:pick(ob,K_ROOM),d:day,s:jc.s,e:jc.e,w:wk||[]});
  }
  return out;
}
/* ---------- 教务处 SaaS（EXESAC）原始接口：一次拿到全学期每次课 ----------
   {"ClassTimePatterns":[{"StartTimeSlot":96,"EndTimeSlot":105},...],
    "CurrentWeekIndex":6,
    "Data":[{"LUName":"机械原理","FullName":"张老师","Building":"B楼","Classroom":"F教3",
             "WeekStart":1,"WeekEnd":9,"WeekInterval":0,"TimeSlotStart":168,"TimeSlotEnd":188,
             "StartDay":"2026-09-07","OnMonday":false,"OnTuesday":true,...}]}
   节次用「5 分钟为单位」的节号表示，ClassTimePatterns 把节号换算成上下课时间。 */
function kbJson(t){
  if(!t) return null;
  t=(''+t).replace(/^\uFEFF/,'').replace(/^\s+/,'');
  if(t.length<40) return null;
  var c=t.charAt(0); if(c!=='{'&&c!=='[') return null;
  try{ return JSON.parse(t); }catch(e){ return null; }
}
function slotClock(slot){
  var m=Math.round(Number(slot)*5); if(!isFinite(m)||m<0) return '';
  var h=Math.floor(m/60), mi=m%60;
  return (h<10?'0':'')+h+':'+(mi<10?'0':'')+mi;
}
function kbRoom(building,room){
  var b=(''+(building||'')).trim(), r=(''+(room||'')).trim();
  if(!r) return b;
  var at=r.indexOf(b);
  if(b&&at>=0) r=r.substring(at);
  // "A116" / "E楼104104" / "A118" 这类「名字重复一遍」的写法只留一份，
  // 并且去掉前面的英文房号前缀（CS610 -> 610）。
  var m2=r.match(/^(.*?)([^\s]{2,8})\2$/);
  if(m2&&/\d/.test(m2[2])) r=(/^[A-Za-z]*$/.test(m2[1])?m2[2]:(m2[1]+m2[2]));
  if(b&&r.indexOf(b)===0) return r;
  return b+r;
}
function kbExesac(obj){
  if(!obj||typeof obj!=='object') return null;
  var pats=obj.ClassTimePatterns, data=obj.Data;
  if(!pats||!data||!pats.length||!data.length) return null;
  var patterns=[],i;
  for(i=0;i<pats.length;i++) patterns.push({s:slotClock(pats[i].StartTimeSlot),e:slotClock(pats[i].EndTimeSlot)});
  var flags=['OnMonday','OnTuesday','OnWednesday','OnThursday','OnFriday','OnSaturday','OnSunday'];
  var out=[],maxWeek=0,startDay='',seen={};
  for(i=0;i<data.length;i++){
    var d=data[i]; if(!d) continue;
    var name=(''+(d.LUName||'')).trim(); if(name.length<2) continue;
    var s0=Number(d.TimeSlotStart), s1=Number(d.TimeSlotEnd), secs=[], p;
    for(p=0;p<pats.length;p++){
      var ps=Number(pats[p].StartTimeSlot), pe=Number(pats[p].EndTimeSlot);
      if(ps>=s0-1&&pe<=s1+1) secs.push(p+1);
    }
    if(!secs.length) for(p=0;p<pats.length;p++){
      if(Number(pats[p].StartTimeSlot)<=s0&&s0<=Number(pats[p].EndTimeSlot)){ secs=[p+1]; break; }
    }
    if(!secs.length) continue;
    var ws=Math.max(1,Number(d.WeekStart)||1), we=Math.max(ws,Number(d.WeekEnd)||ws);
    // WeekInterval>0 表示隔周上课，奇偶跟着起始周走
    var step=(Number(d.WeekInterval)>0)?2:1, weeks=[];
    for(var w=ws;w<=we;w+=step) weeks.push(w);
    if(we>maxWeek) maxWeek=we;
    if(!startDay&&d.StartDay) startDay=(''+d.StartDay).substring(0,10);
    var room=kbRoom(d.Building,d.Classroom), tea=(''+(d.FullName||'')).trim();
    for(var di=0;di<7;di++){
      if(!d[flags[di]]) continue;
      var item={n:name.substring(0,40),t:tea,r:room,d:di+1,s:secs[0],e:secs[secs.length-1],w:weeks};
      // 同一门课在同一个格子里的多条记录（合班/多教师）合并成一条，教师用逗号连起来
      var key=item.n+'|'+item.d+'|'+item.s+'|'+item.e+'|'+weeks.join(',');
      if(seen[key]){
        var prev=seen[key];
        if(tea&&prev.t.indexOf(tea)<0) prev.t=prev.t?(prev.t+','+tea):tea;
        continue;
      }
      seen[key]=item; out.push(item);
    }
  }
  if(out.length<2) return null;
  return {courses:out,patterns:patterns,week:(Number(obj.CurrentWeekIndex)>0?Number(obj.CurrentWeekIndex):0),start:startDay,totalWeeks:maxWeek};
}
function exesacTry(ds,recs){
  var i;
  if(recs) for(i=0;i<recs.length;i++){
    var r=kbExesac(kbJson(recs[i].r)); if(r) return r;
  }
  for(i=0;i<ds.length;i++){
    var sc=null; try{ sc=ds[i].doc.getElementsByTagName('script'); }catch(e){ sc=null; }
    if(!sc) continue;
    for(var s=0;s<sc.length;s++){
      var txt=sc[s].text||'';
      if(txt.indexOf('ClassTimePatterns')<0) continue;
      var r2=kbExesac(kbJson(txt)); if(r2) return r2;
    }
  }
  return null;
}
function dataTry(ds,recs){
  var S={best:0,arr:null,src:''};
  for(var d=0;d<ds.length;d++){
    var doc=ds[d].doc,src='dom';
    try{ if(ds[d].url&&ds[d].url.indexOf('net:')===0) src='net '+ds[d].url.substring(4); }catch(e){}
    var scripts=null; try{ scripts=doc.getElementsByTagName('script'); }catch(e){ scripts=null; }
    if(scripts) for(var s=0;s<scripts.length;s++) considerText(scripts[s].text||'',src,S);
    var hid=null; try{ hid=doc.querySelectorAll('input[type=hidden],textarea'); }catch(e){ hid=null; }
    if(hid) for(var h=0;h<hid.length&&h<40;h++){ var hv=hid[h].value||hid[h].textContent||''; if(hv.length>60) considerText(hv,src+' field',S); }
  }
  if(recs) for(var i=0;i<recs.length;i++) considerText(recs[i].r||'','net '+(recs[i].u||''),S);
  if(!S.arr||S.best<24) return null;
  var out=arrToCourses(S.arr);
  if(out.length<2) return null;
  return {courses:out,src:S.src,score:S.best};
}
function netEntries(ds){
  var out=[],seen={},wk=0;
  function take(w){
    if(!w) return;
    wk++;
    var rec=null; try{ rec=w.__kbNet; }catch(e){ return; }
    if(!rec||!rec.length) return;
    for(var i=0;i<rec.length;i++){
      var r=rec[i]; if(!r) continue;
      var key=wk+'|'+(r.u||'')+'|'+(r.r?r.r.length:0)+'|'+i;
      if(seen[key]) continue; seen[key]=1;
      out.push({u:r.u||'',m:r.m||'GET',b:r.b||'',r:r.r||''});
    }
  }
  try{ take(window); }catch(e){}
  for(var d=0;d<ds.length;d++){
    var w=null; try{ w=ds[d].doc.defaultView; }catch(e){ w=null; }
    if(w&&w!==window) take(w);
  }
  return out;
}
function netDocs(recs){
  var out=[];
  for(var i=0;i<recs.length;i++){
    var t=recs[i].r||'';
    if(t.length<60||t.length>900000) continue;
    if(t.indexOf('<table')<0&&t.indexOf('<TABLE')<0) continue;
    try{
      var doc=new DOMParser().parseFromString(t,'text/html');
      if(doc&&doc.querySelectorAll('table').length) out.push({doc:doc,url:''});
    }catch(e){}
  }
  return out;
}
function clickQuery(ds){
  if(window.__kbClicked) return '';
  var RE=/(查询|搜索|检索|刷新|我的课表|学生课表|班级课表|课表)/;
  var hit=null,lab2='';
  for(var d=0;d<ds.length&&!hit;d++){
    var els=null;
    try{ els=ds[d].doc.querySelectorAll('input[type=button],input[type=submit],button,a'); }catch(e){ continue; }
    for(var i=0;i<els.length&&i<150;i++){
      var e=els[i],tg=(e.tagName||'').toLowerCase();
      var lab=((e.value||e.innerText||e.textContent||'')+'').replace(/\s+/g,'').trim();
      if(!lab||lab.length>12) continue;
      if(!RE.test(lab)) continue;
      if(/退出|注销|登出|返回|主页|首页/.test(lab)) continue;
      if(tg==='a'&&!/课表/.test(lab)) continue;
      var rects=null; try{ rects=e.getClientRects?e.getClientRects():null; }catch(e4){ rects=null; }
      if(!rects||!rects.length) continue;
      hit=e; lab2=lab; break;
    }
  }
  if(!hit) return '';
  window.__kbClicked=1;
  try{ hit.click(); }catch(e5){ return ''; }
  return lab2;
}function sampleInfo(ds){
  var parts=[];
  for(var d=0;d<ds.length;d++){
    var doc=ds[d].doc,ts=null;
    try{ ts=doc.querySelectorAll('table'); }catch(e){}
    parts.push('doc'+d+':tables='+(ts?ts.length:0));
    if(ts) for(var i=0;i<ts.length&&i<6;i++){
      var m=matrix(ts[i],'');
      parts.push('t'+i+'='+(m?(m.rows.length+'x'+m.cols+',wk'+m.weekRows+',day'+m.dayHdr+',colDay'+m.colDay):'skip'));
    }
    var dv=0,td=0; try{ dv=doc.querySelectorAll('div').length; }catch(e){}
    try{ td=doc.querySelectorAll('td').length; }catch(e){}
    parts.push('divs='+dv+',tds='+td);
  }
  return parts.join('; ');
}
var SEL={
  zf:['#kbtable','.timetable','#table1'],
  jwglxt:['#kbtable','#kbgrid','.timetable','#table1'],
  qz:['#timetable','.qztimetable','#tablediv'],
  generic:['#kbtable','#timetable','.qztimetable','.timetable','#kbgrid']
};
function detectSystem(ds){
  for(var d=0;d<ds.length;d++){
    var doc=ds[d].doc;
    try{ if(doc.querySelector('#kbtable')) return 'zf'; }catch(e){}
    try{ if(doc.querySelector('#timetable,.qztimetable')) return 'qz'; }catch(e){}
  }
  var blob='';
  try{ blob=((location.href||'')+(document.title||'')).toLowerCase(); }catch(e){}
  if(blob.indexOf('jwglxt')>=0) return 'jwglxt';
  if(blob.indexOf('zf')>=0) return 'zf';
  if(blob.indexOf('qz')>=0) return 'qz';
  return 'unknown';
}
function selectorTry(ds,sels){
  for(var d=0;d<ds.length;d++){
    var doc=ds[d].doc;
    for(var s=0;s<sels.length;s++){
      var el=null; try{ el=doc.querySelector(sels[s]); }catch(e){ continue; }
      if(!el) continue;
      var tb=null;
      if(el.tagName==='TABLE') tb=el; else { try{ tb=el.querySelector&&el.querySelector('table'); }catch(e){} }
      if(!tb) continue;
      var m=matrix(tb,ds[d].url); if(!m) continue;
      var sc=scoreGrid(m); if(sc<150) continue;
      return {rows:m.rows,info:'sel '+sels[s]+' '+m.rows.length+'x'+m.cols,score:sc,url:ds[d].url};
    }
  }
  return null;
}
function gridTry(ds){
  var best=null,bs=-1;
  for(var d=0;d<ds.length;d++){
    var ts; try{ ts=ds[d].doc.querySelectorAll('table'); }catch(e){ continue; }
    for(var i=0;i<ts.length;i++){
      var m=matrix(ts[i],ds[d].url); if(!m) continue;
      var s=scoreGrid(m),extra='';
      if(m.colDay>=3&&m.dayHdr<3){
        var canT=true;
        for(var r=0;r<m.rows.length;r++) for(var c=0;c<m.rows[r].length;c++){ var oo=m.rows[r][c]; if(oo.cs!==1||oo.rs!==1) canT=false; }
        if(canT){ m.rows=transposeRows(m.rows); extra='(transposed)'; }
      }
      if(s>bs){ bs=s; best={rows:m.rows,info:'grid '+m.rows.length+'x'+m.cols+',wkRows='+m.weekRows+',dayHdr='+m.dayHdr+extra,score:s,url:m.url}; }
    }
  }
  if(!best||bs<200) return null;
  return best;
}
function cluster(v,tol){
  var s=v.slice().sort(function(a,b){return a-b;}),g=[],cur=[];
  for(var i=0;i<s.length;i++){
    if(!cur.length||s[i]-cur[cur.length-1]<=tol) cur.push(s[i]);
    else { g.push(cur); cur=[s[i]]; }
  }
  if(cur.length) g.push(cur);
  return g.map(function(x){var t=0;for(var j=0;j<x.length;j++)t+=x[j];return t/x.length;});
}
function geomTry(ds){
  for(var d=0;d<ds.length;d++){
    var doc=ds[d].doc,all;
    try{ all=doc.querySelectorAll('*'); }catch(e){ continue; }
    var cells=[],heads=[],labels=[];
    for(var i=0;i<all.length;i++){
      var el=all[i],skip=false;
      for(var ci=0;ci<el.children.length;ci++){ if(txt(el.children[ci]).length>0){ skip=true; break; } }
      if(skip) continue;
      var r=rc(el); if(!r||r.width<=0||r.height<=0) continue;
      var t=txt(el); if(!t) continue;
      var flat=t.replace(/\s/g,'');
      if(flat.length<=4){ var m=DAY_RE.exec(flat); if(m){ heads.push({day:DAYN[m[1]],x:r.left+r.width/2}); continue; } }
      if(flat.length<=6 && /^(第?\d{1,2}节?|\d{1,2}[-~]\d{1,2})$/.test(flat)){ labels.push({t:t,x:r.left+r.width/2,y:r.top+r.height/2}); continue; }
      if(WEEK_RE.test(t)&&t.length<=90){ cells.push({text:t,x:r.left+r.width/2,y:r.top+r.height/2}); }
    }
    if(cells.length<3) continue;
    var xg=cluster(cells.map(function(c){return c.x;}),26);
    var yg=cluster(cells.map(function(c){return c.y;}),26);
    if(xg.length<2||yg.length<2) continue;
    function near(v,a){var bi=0,bd=1e9;for(var k=0;k<a.length;k++){var dd=Math.abs(a[k]-v);if(dd<bd){bd=dd;bi=k;}}return bi;}
    var spanX=xg.length>1?(xg[xg.length-1]-xg[0])/(xg.length-1):0;
    var xtol=spanX>0?spanX*0.55:1e9;
    var colDay={};
    for(var h=0;h<heads.length;h++){
      var hx=near(heads[h].x,xg);
      if(Math.abs(xg[hx]-heads[h].x)<=xtol) colDay[hx]=heads[h].day;
    }
    if(Object.keys(colDay).length<3 && xg.length===7){ colDay={}; for(var k2=0;k2<7;k2++) colDay[k2]=k2+1; }
    if(Object.keys(colDay).length<3) continue;
    var gm={};
    for(var c2=0;c2<cells.length;c2++){
      var cc=cells[c2],xi=near(cc.x,xg),yi=near(cc.y,yg),key=yi+'|'+xi;
      if(!gm[key]||gm[key].text.length<cc.text.length) gm[key]=cc;
    }
    var rows=[],hdr=[{t:'',cs:1,rs:1}],DL='一二三四五六日';
    for(var dd=1;dd<=7;dd++) hdr.push({t:'周'+DL.charAt(dd-1),cs:1,rs:1});
    rows.push(hdr);
    var spanY=yg.length>1?(yg[yg.length-1]-yg[0])/(yg.length-1):0;
    var ytol=spanY>0?spanY*0.55:1e9;
    for(var ri=0;ri<yg.length;ri++){
      var lab='',bd2=1e9;
      for(var L=0;L<labels.length;L++){
        if(labels[L].x>xg[0]-4) continue;
        var dy=Math.abs(labels[L].y-yg[ri]); if(dy<bd2){bd2=dy;lab=labels[L].t;}
      }
      if(bd2>ytol) lab='';
      var row2=[{t:lab,cs:1,rs:1}];
      for(var day=1;day<=7;day++){
        var col=-1;
        for(var ck in colDay){ if(colDay[ck]===day){ col=parseInt(ck,10); break; } }
        var f=null;
        if(col>=0){ var gv=gm[ri+'|'+col]; if(gv) f={t:gv.text,cs:1,rs:1}; }
        row2.push(f||{t:'',cs:1,rs:1});
      }
      rows.push(row2);
    }
    return {rows:rows,info:'geometry '+yg.length+'x'+xg.length+', cells='+cells.length,url:ds[d].url};
  }
  return null;
}
function charPeriod(c){
  if(c>=0x2460&&c<=0x2473) return c-0x2460+1;
  if(c>=0x2474&&c<=0x2487) return c-0x2474+1;
  if(c>=0x2488&&c<=0x249B) return c-0x2488+1;
  if(c>=0x2160&&c<=0x216B) return c-0x2160+1;
  if(c>=0x2170&&c<=0x217B) return c-0x2170+1;
  return 0;
}
function idxNums(s){
  var t=((s||'')+'').replace(/[\u00a0\s]/g,'').replace(/[\uff10-\uff19]/g,function(x){return String.fromCharCode(x.charCodeAt(0)-0xfee0);});
  if(!t||t.length>24) return null;
  var out=[],i=0;
  while(i<t.length){
    var c=t.charCodeAt(i),cp=charPeriod(c),ch=t.charAt(i);
    if(cp){ out.push(cp); i++; continue; }
    if(ch==='第'||ch==='节'||ch==='小'||ch===','||ch==='，'||ch==='、'||ch==='/'||ch==='-'||ch==='~'||ch==='至'||ch==='('||ch===')'||ch==='（'||ch==='）'){ i++; continue; }
    if(ch>='0'&&ch<='9'){
      var m=/^(\d{1,2})/.exec(t.substring(i)),v=parseInt(m[1],10);
      if(v<1||v>20) return null;
      out.push(v); i+=m[1].length; continue;
    }
    return null;
  }
  if(!out.length) return null;
  for(var k=1;k<out.length;k++) if(out[k]<=out[k-1]) return null;
  return out;
}
function isWeekOnly(t){
  if(!WEEK_RE.test(t)) return false;
  var s=((t||'')+'').replace(/\d+\s*[-~至]\s*\d+/g,'').replace(/\d+/g,'').replace(/[\s\u00a0,，、()（）\[\]【】\/]/g,'').replace(/[单双]/g,'');
  return s.replace(/周/g,'').length<=2;
}
function boxLines(el){
  var raw='';
  try{ raw=el.innerText||''; }catch(e){ raw=''; }
  if(!raw) raw=txt(el);
  var parts=raw.split(/\r?\n/),out=[];
  for(var i=0;i<parts.length;i++){ var s=parts[i].replace(/\u00a0/g,' ').trim(); if(s) out.push(s); }
  if(out.length===1&&out[0].length>16){
    var sp=out[0].split(/\s{2,}/),o2=[];
    for(var j=0;j<sp.length;j++){ var s2=sp[j].trim(); if(s2) o2.push(s2); }
    if(o2.length>out.length) out=o2;
  }
  if(out.length===1&&out[0].length>8){
    var one=out[0],wm=/(\d[\d\s\-~至,，、]*周[单双]?)/.exec(one);
    if(wm){
      var pre=one.substring(0,wm.index),post=one.substring(wm.index+wm[1].length),seg=[];
      if(pre) seg.push(pre);
      seg.push(wm[1]);
      if(post) seg.push(post);
      out=seg;
    }
  }
  return out;
}
function locWord(s){ return /楼|室|场|馆|栋|阶|梯|号|中心|基地|园区|校区|操场|食堂|机房|语音/.test(s); }
function nameLikeLine(s){
  if(!s) return false;
  var v=((s||'')+'').trim();
  if(v.length<2||v.length>30) return false;
  if(!/[\u4e00-\u9fa5A-Za-z]/.test(v)) return false;
  if(v.replace(/[^0-9]/g,'').length>=4&&!/[\u4e00-\u9fa5]/.test(v)) return false;
  if(locWord(v)) return false;
  return true;
}
function locLikeLine(s){ return locWord(s)||/[0-9]/.test(s); }
function parseBoxLines(lines){
  var wkTotal=0;
  for(var i=0;i<lines.length;i++) if(isWeekOnly(lines[i])) wkTotal++;
  var allowSplit=wkTotal>=2;
  var blocks=[],cur=null;
  for(var i=0;i<lines.length;i++){
    var s=lines[i];
    if(isWeekOnly(s)){ if(cur) cur.wk.push(s); continue; }
    if(allowSplit&&cur&&cur.wk.length&&nameLikeLine(s)){ blocks.push(cur); cur=null; }
    if(!cur) cur={lines:[],wk:[]};
    cur.lines.push(s);
  }
  if(cur) blocks.push(cur);
  var out=[];
  for(var b=0;b<blocks.length;b++){
    var ls=blocks[b].lines.slice();
    if(!ls.length) continue;
    var name='',ti=-1;
    for(var i=0;i<ls.length;i++){ if(nameLikeLine(ls[i])){ name=ls[i]; ti=i; break; } }
    if(!name) for(var i=0;i<ls.length;i++){ if(!locLikeLine(ls[i])){ name=ls[i]; ti=i; break; } }
    if(!name){ name=ls[0]; ti=0; }
    var teacher='';
    for(var i=0;i<ls.length;i++){ if(i===ti) continue; var v=ls[i]; if(/^[\u4e00-\u9fa5·]{2,4}$/.test(v)){ teacher=v; break; } }
    var locs=[];
    for(var i=0;i<ls.length;i++){
      if(i===ti) continue;
      var v2=ls[i];
      if(v2===teacher) continue;
      if(locLikeLine(v2)) locs.push(v2);
    }
    out.push({n:name.substring(0,40),t:teacher,r:locs.join(' '),w:blocks[b].wk.join(',')});
  }
  return out;
}
function boxTry(ds){
  for(var d=0;d<ds.length;d++){
    var doc=ds[d].doc,all;
    try{ all=doc.querySelectorAll('*'); }catch(e){ continue; }
    var heads=[],labs=[],inner=[];
    for(var i=0;i<all.length;i++){
      var el=all[i];
      var tg=el.tagName;
      if(tg==='SCRIPT'||tg==='STYLE'||tg==='NOSCRIPT'||tg==='TITLE'||tg==='META'||tg==='LINK'||tg==='HEAD') continue;
      var r=rc(el); if(!r||r.width<=0||r.height<=0) continue;
      var t=txt(el); if(!t) continue;
      var flat=t.replace(/\s/g,'');
      var leaf=true;
      for(var ci=0;ci<el.children.length;ci++){ if(txt(el.children[ci]).length>0){ leaf=false; break; } }
      if(leaf){
        if(flat.length<=4&&DAY_RE.test(flat)){
          var hm=DAY_RE.exec(flat);
          if(hm) heads.push({day:DAYN[hm[1]],x:r.left+r.width/2,y:r.top+r.height/2});
        } else if(flat.length<=10&&/[0-9\u2460-\u249b\u2160-\u217b]/.test(flat)){
          var ns=idxNums(flat);
          if(ns) labs.push({nums:ns,top:r.top,h:r.height,x:r.left+r.width/2});
        }
      }
      if(WEEK_RE.test(t)&&r.height>=16&&r.width>=36) inner.push({el:el,r:r});
    }
    if(inner.length<2) continue;
    var tight=[];
    for(var a=0;a<inner.length;a++){
      var A=inner[a],big=false;
      for(var b=0;b<inner.length;b++){
        if(a===b) continue;
        var B=inner[b];
        if(A.r.left<=B.r.left+1&&A.r.top<=B.r.top+1&&A.r.right>=B.r.right-1&&A.r.bottom>=B.r.bottom-1&&A.r.width*A.r.height>B.r.width*B.r.height*1.15){ big=true; break; }
      }
      if(!big) tight.push(A);
    }
    if(tight.length<2) continue;
    var boxes=[],nodes=[];
    for(var a=0;a<tight.length;a++){
      var node=tight[a].el,orig=tight[a].r,lines=boxLines(node),lim=0;
      while(lines.length<2&&lim<3){
        var par=node.parentElement; if(!par) break;
        var pr=rc(par); if(!pr||pr.width<=0||pr.height<=0) break;
        if(pr.width*pr.height>orig.width*orig.height*15) break;
        node=par; lines=boxLines(node); lim++;
      }
      if(lines.length<2) continue;
      var br=rc(node); if(!br||br.width<=0||br.height<=0) continue;
      var dup=false;
      for(var q=0;q<nodes.length;q++) if(nodes[q]===node){ dup=true; break; }
      if(dup) continue;
      nodes.push(node);
      boxes.push({x:br.left+br.width/2,top:br.top,bottom:br.bottom,w:br.width,lines:lines});
    }
    if(boxes.length<2) continue;
    var minTop=1e9,maxBot=-1e9,avgW=0,minL=1e9;
    for(var a=0;a<boxes.length;a++){
      var bx=boxes[a];
      if(bx.top<minTop) minTop=bx.top;
      if(bx.bottom>maxBot) maxBot=bx.bottom;
      if(bx.x-bx.w/2<minL) minL=bx.x-bx.w/2;
      avgW+=bx.w;
    }
    avgW=avgW/boxes.length;
    var hd={},hl=[];
    for(var i=0;i<heads.length;i++){
      var h=heads[i];
      if(h.y>minTop+50) continue;
      if(hd[h.day]===undefined||h.y<hd[h.day].y) hd[h.day]=h;
    }
    for(var k in hd) hl.push(hd[k]);
    hl.sort(function(p,q){return p.x-q.x;});
    var dayOf=null;
    if(hl.length>=3){
      var xs=hl.map(function(p){return p.x;});
      var span=xs.length>1?(xs[xs.length-1]-xs[0])/(xs.length-1):0;
      var xlim=span>0?span*0.62:1e9;
      dayOf=function(cx){ var bi=0,bd=1e9; for(var k2=0;k2<xs.length;k2++){ var dd=Math.abs(xs[k2]-cx); if(dd<bd){bd=dd;bi=k2;} } return bd<=xlim?hl[bi].day:0; };
    } else {
      var xg=cluster(boxes.map(function(p){return p.x;}),Math.max(26,avgW*0.5));
      if(xg.length>=3&&xg.length<=8){
        dayOf=function(cx){ var bi=0,bd=1e9; for(var k3=0;k3<xg.length;k3++){ var dd=Math.abs(xg[k3]-cx); if(dd<bd){bd=dd;bi=k3;} } return bi+1; };
      }
    }
    if(!dayOf) continue;
    var pts=[];
    for(var i=0;i<labs.length;i++){
      var L=labs[i];
      if(L.x>minL+10) continue;
      for(var k4=0;k4<L.nums.length;k4++){
        var yy=L.top+L.h*(k4+0.5)/L.nums.length;
        if(yy<minTop-80||yy>maxBot+80) continue;
        pts.push({p:L.nums[k4],y:yy,h:L.h/L.nums.length});
      }
    }
    pts.sort(function(p,q){return p.y-q.y;});
    var secOf=null;
    if(pts.length>=2){
      secOf=function(top,bot){
        var mid=(top+bot)/2,inr=[],k5,k6;
        function pitch(i){
          if(i>0&&i<pts.length-1) return (pts[i+1].y-pts[i-1].y)/2;
          if(i>0) return pts[i].y-pts[i-1].y;
          if(i<pts.length-1) return pts[i+1].y-pts[i].y;
          return Math.max(24,(bot-top)/2);
        }
        for(k5=0;k5<pts.length;k5++){
          var h5=pitch(k5);
          if(!h5||h5<6) h5=24;
          var ov=Math.min(bot,pts[k5].y+h5/2)-Math.max(top,pts[k5].y-h5/2);
          if(ov>0.35*h5) inr.push(pts[k5].p);
        }
        if(inr.length<=1){
          var ci=0,cd=1e9;
          for(k5=0;k5<pts.length;k5++){ var d5=Math.abs(pts[k5].y-mid); if(d5<cd){cd=d5;ci=k5;} }
          var ph=(pts[ci]&&pts[ci].h)||0; if(!ph||ph<6) ph=pitch(ci); if(!ph||ph<6) ph=24;
          var n=Math.round((bot-top)/ph);
          if(n>=2&&n<=6){
            var ti=ci,bi=ci;
            for(k5=0;k5<pts.length;k5++){ if(pts[k5].y>=top-6&&pts[k5].y<=top+ph*0.9) ti=k5; }
            for(k5=0;k5<pts.length;k5++){ if(pts[k5].y>=bot-ph*0.9&&pts[k5].y<=bot+6) bi=k5; }
            if(bi<=ti){ ti=ci; bi=Math.min(pts.length-1,ci+n-1); }
            inr=[];
            for(k5=ti;k5<=bi;k5++) inr.push(pts[k5].p);
          }
        }
        if(!inr.length){
          var ni=0,nd2=1e9;
          for(k6=0;k6<pts.length;k6++){ var d6=Math.abs(pts[k6].y-mid); if(d6<nd2){nd2=d6;ni=k6;} }
          inr=[pts[ni].p];
        }
        var lo=inr[0],hi=inr[0];
        for(k6=0;k6<inr.length;k6++){ if(inr[k6]<lo) lo=inr[k6]; if(inr[k6]>hi) hi=inr[k6]; }
        return {s:lo,e:hi};
      };
    } else {
      var ys=[];
      for(var a=0;a<boxes.length;a++){ ys.push(boxes[a].top); ys.push(boxes[a].bottom); }
      var yg=cluster(ys,Math.max(18,(maxBot-minTop)/34));
      if(yg.length>=3&&yg.length<=30){
        secOf=function(top,bot){ var s=0,e=0; for(var k8=0;k8<yg.length;k8++){ if(yg[k8]>=top-10&&yg[k8]<=bot+10){ if(!s) s=k8+1; e=k8+1; } } if(!s){ s=1; e=1; } return {s:s,e:e}; };
      }
    }
    if(!secOf) continue;
    var out=[];
    for(var a=0;a<boxes.length;a++){
      var bx2=boxes[a],day=dayOf(bx2.x); if(!day) continue;
      var rr=secOf(bx2.top,bx2.bottom),parsed=parseBoxLines(bx2.lines);
      for(var p2=0;p2<parsed.length;p2++){
        var pc=parsed[p2];
        if(!pc.n||pc.n.length<2) continue;
        out.push({n:pc.n,t:pc.t,r:pc.r,d:day,s:rr.s,e:rr.e,w:weekNums(pc.w)||[]});
      }
    }
    log.push('box cand='+inner.length+' tight='+tight.length+' boxes='+boxes.length+' days='+hl.length+' rows='+pts.length+' courses='+out.length);
    if(out.length<4&&!(out.length>=2&&hl.length>=3)) continue;
    var seen={},uniq=[];
    for(var a=0;a<out.length;a++){
      var key=out[a].n+'|'+out[a].d+'|'+out[a].s+'|'+out[a].e+'|'+((out[a].w||[]).join(','));
      if(seen[key]) continue;
      seen[key]=1; uniq.push(out[a]);
    }
    return {courses:uniq,info:'box '+boxes.length+' boxes, '+hl.length+' days, '+pts.length+' row-labels',url:ds[d].url};
  }
  return null;
}
try{
  var ds=docs(); log.push('frames='+ds.length);
  var recs=[]; try{ recs=netEntries(ds); }catch(e){ recs=[]; }
  if(recs.length){
    log.push('net='+recs.length);
    for(var ri=0;ri<recs.length&&ri<3;ri++){ var uu=(recs[ri].u||'').replace(/^https?:\/\//,''); if(uu.length>64) uu=uu.substring(0,64); log.push('netUrl:'+uu); }
  }
  var nd=[]; try{ nd=netDocs(recs); }catch(e){ nd=[]; }
  if(nd.length) log.push('netHtml='+nd.length);
  var dsAll=ds.concat(nd);
  var sys=detectSystem(dsAll);
  var eff=(PRESET==='auto')?(sys==='unknown'?'generic':sys):PRESET;
  var ex=null; try{ ex=exesacTry(dsAll,recs); }catch(e){ log.push('exesac-err'); }
  if(ex&&ex.courses.length>=2){
    log.push('exesac='+ex.courses.length+' weeks='+ex.totalWeeks+' cur='+ex.week+' start='+ex.start+' secs='+ex.patterns.length);
    return JSON.stringify({ok:true,mode:'net',system:'saas',preset:eff,diagnostics:log.join('; '),title:document.title||'',url:location.href,foundUrl:location.href,courses:ex.courses,st:ex.patterns,week:ex.week,start:ex.start,weeks:ex.totalWeeks});
  }
  var data=null; try{ data=dataTry(dsAll,recs); }catch(e){ log.push('data-err'); }
  if(data&&data.courses.length>=2){
    log.push('data='+data.courses.length+((data.src)?('@'+data.src.substring(0,44)):''));
    return JSON.stringify({ok:true,mode:(data.src.indexOf('net')===0?'net':'data'),system:sys,preset:eff,diagnostics:log.join('; '),title:document.title||'',url:location.href,foundUrl:location.href,courses:data.courses});
  }
  var sels=SEL[eff]||SEL.generic;
  var res=selectorTry(dsAll,sels),mode='selector';
  var bx=null; try{ bx=boxTry(dsAll); }catch(e){ log.push('box-err'); }
  if(bx){ mode='box'; res={courses:bx.courses,info:bx.info,url:bx.url}; }
  if(!res){ res=gridTry(dsAll); mode='grid'; }
  if(!res){ res=geomTry(dsAll); mode='geometry'; }
  if(!res){
    var ck=''; try{ ck=clickQuery(ds); }catch(e){ ck=''; }
    if(ck) log.push('click='+ck);
    return JSON.stringify({ok:false,error:'no-grid',system:sys,preset:eff,diagnostics:log.join('; '),sample:sampleInfo(ds),title:document.title||'',url:location.href,foundUrl:''});
  }
  var out={ok:true,mode:mode,system:sys,preset:eff,diagnostics:log.join('; ')+' | '+res.info,title:document.title||'',url:location.href,foundUrl:res.url||location.href};
  if(res.courses&&res.courses.length) out.courses=res.courses; else out.rows=res.rows;
  return JSON.stringify(out);
}catch(e){
  return JSON.stringify({ok:false,error:'exception',message:String(e),diagnostics:log.join('; '),title:document.title||'',url:location.href,foundUrl:''});
}
})()
"""
}
