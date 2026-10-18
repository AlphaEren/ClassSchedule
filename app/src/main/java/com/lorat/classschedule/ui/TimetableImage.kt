package com.lorat.classschedule.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import com.lorat.classschedule.data.DayShortNames
import com.lorat.classschedule.data.currentWeekOf
import com.lorat.classschedule.data.courseGroups
import com.lorat.classschedule.data.datesOfWeek
import com.lorat.classschedule.data.dayColumns
import com.lorat.classschedule.data.gridSectionCount
import com.lorat.classschedule.data.placement
import com.lorat.classschedule.i18n.Lang
import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.model.Timetable
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 「课表转图片」：把某一周的课表画成一张可以发微信/QQ 的长图。
 *
 * 不用屏幕截图，是因为分享出去的图通常是浅色底、比格子里更大的字，而且要能整周完整呈现
 * （屏幕上的课表可能只露出一半）。这里用 Canvas 直接画，配色跟 App 里保持一致。
 */
object TimetableImage {
    private const val WIDTH = 1080f
    private const val PAD = 44f
    private const val HEADER = 150f
    private const val DATE_BAR = 108f
    private const val AXIS = 116f
    private const val ROW = 140f
    private const val FOOTER = 92f
    private const val GAP = 4f
    private const val INK = 0xFF1F2126.toInt()
    private const val MUTED = 0xFF6B7280.toInt()
    private const val HAIRLINE = 0xFFE3E5E7.toInt()
    private const val PAPER = 0xFFFFFFFF.toInt()
    private const val ACCENT = 0xFF3D6BE5.toInt()
    /** 不能出现在行首的标点。 */
    private const val CLOSING = "）】」』》〉，。、；：！？%’”…"
    /** 不能出现在行尾的标点。 */
    private const val OPENING = "（【「『《〈“‘"

    /** 画出一整周；[today] 用来高亮今天（只有当前周会高亮）。 */
    fun render(timetable: Timetable, week: Int, today: LocalDate = LocalDate.now()): Bitmap {
        Lang.mode = timetable.appearance.language
        val appearance = timetable.appearance
        val columns = dayColumns(appearance.weekStartSunday, appearance.showWeekend)
        val dates = datesOfWeek(timetable, week, appearance.weekStartSunday)
            .take(if (appearance.showWeekend) 7 else 5)
        val sections = timetable.gridSectionCount()
        val height = PAD + HEADER + DATE_BAR + ROW * sections + FOOTER + PAD
        val bitmap = Bitmap.createBitmap(WIDTH.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(PAPER)

        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val regular = Typeface.DEFAULT
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val gridLeft = PAD + AXIS
        val colWidth = (WIDTH - PAD * 2 - AXIS) / columns.size
        val gridTop = PAD + HEADER + DATE_BAR
        val gridBottom = gridTop + ROW * sections

        // 顶部：第 N 周 + 日期范围 + 课表名
        paint.color = INK
        paint.textSize = 52f
        paint.typeface = bold
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(Lang.tr("第 $week 周"), PAD, PAD + 54f, paint)

        paint.color = MUTED
        paint.textSize = 26f
        paint.typeface = regular
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(dateRange(dates), WIDTH - PAD, PAD + 46f, paint)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(Lang.tr(timetable.semesterName.ifBlank { "${timetable.courseGroups().size} 门课程" }), PAD, PAD + 102f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(Lang.tr("共 ${maxOf(1, timetable.totalWeeks)} 周"), WIDTH - PAD, PAD + 102f, paint)
        paint.textAlign = Paint.Align.LEFT

        // 日期栏
        val isCurrentWeek = week == currentWeekOf(timetable, today)
        dates.forEachIndexed { index, date ->
            val cx = gridLeft + colWidth * index + colWidth / 2
            val isToday = isCurrentWeek && date == today
            paint.color = if (isToday) ACCENT else MUTED
            paint.textSize = 28f
            paint.typeface = if (isToday) bold else regular
            paint.textAlign = Paint.Align.CENTER
            val day = columns.getOrElse(index) { index + 1 }
            canvas.drawText(Lang.tr(DayShortNames.getOrElse(day - 1) { "?" }), cx, gridTop - 62f, paint)
            if (isToday) {
                paint.color = 0x223D6BE5
                canvas.drawCircle(cx, gridTop - 26f, 24f, paint)
                paint.color = ACCENT
            } else {
                paint.color = INK
            }
            paint.textSize = 30f
            canvas.drawText("${date.dayOfMonth}", cx, gridTop - 16f, paint)
        }

        // 网格线
        paint.strokeWidth = 2f
        paint.color = HAIRLINE
        for (s in 0..sections) {
            val y = gridTop + ROW * s
            canvas.drawLine(gridLeft, y, WIDTH - PAD, y, paint)
        }
        for (c in 0..columns.size) {
            val x = gridLeft + colWidth * c
            canvas.drawLine(x, gridTop, x, gridBottom, paint)
        }
        canvas.drawLine(gridLeft, gridTop - DATE_BAR, gridLeft, gridBottom, paint)

        // 左侧节次 + 时间
        paint.typeface = regular
        paint.textAlign = Paint.Align.CENTER
        for (section in 1..sections) {
            val top = gridTop + ROW * (section - 1)
            paint.color = MUTED
            paint.textSize = 30f
            canvas.drawText("$section", PAD + AXIS / 2, top + 48f, paint)
            if (!appearance.axisShowOnlySection) {
                val time = timetable.sectionTimes.firstOrNull { it.section == section } ?: continue
                paint.textSize = 20f
                canvas.drawText(time.start, PAD + AXIS / 2, top + 84f, paint)
                canvas.drawText(time.end, PAD + AXIS / 2, top + 108f, paint)
            }
        }

        // 课程格子：只画这一周要上的课
        val placed = placement(timetable, columns, week, includeOtherWeeks = false)
        val autoColors = autoColorMap(placed.map { it.course.name })
        for (p in placed) {
            val base = courseColorFor(p.course.colorIndex, p.course.name, autoColors).toArgb()
            val sliceWidth = colWidth / p.slices
            val left = gridLeft + colWidth * p.col + sliceWidth * p.slice + GAP
            val top = gridTop + ROW * (p.course.startSection - 1) + GAP
            val right = gridLeft + colWidth * p.col + sliceWidth * (p.slice + 1) - GAP
            val bottom = gridTop + ROW * p.course.endSection - GAP
            val rect = RectF(left, top, right, bottom)

            // 卡片底色要「实」：直接半透明填色会把底下网格线透出来（屏幕上是合到 surface 上的，
            // 所以看不到格线）。这里先和纸色混合成不透明色再画。
            paint.style = Paint.Style.FILL
            paint.color = ColorUtils.blendARGB(PAPER, base, 0x24 / 255f)
            paint.alpha = 0xFF
            canvas.drawRoundRect(rect, 16f, 16f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = base
            paint.alpha = 0x5A
            canvas.drawRoundRect(rect, 16f, 16f, paint)
            paint.alpha = 0xFF
            paint.style = Paint.Style.FILL

            val inset = 12f
            val innerWidth = rect.width() - inset * 2
            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = regular
                textSize = 22f
                textAlign = Paint.Align.LEFT
                color = MUTED
            }
            // 地点经常比一行长，格子够高就让它在图片里换行，别变成「@致远…」
            val locationLines = if (rect.height() >= 150f) 2 else 1
            val metaLines = buildList {
                if (appearance.showTime) {
                    timeTextOf(p.course, timetable)?.let { add(wrapText(it, metaPaint, innerWidth, 1)) }
                }
                if (appearance.showTeacher && p.course.teacher.isNotBlank()) {
                    add(wrapText(p.course.teacher, metaPaint, innerWidth, 1))
                }
                if (appearance.showLocation && p.course.location.isNotBlank()) {
                    add(wrapText("@" + p.course.location, metaPaint, innerWidth, locationLines))
                }
            }
            val metaLineCount = metaLines.sumOf { it.size }
            val metaHeight = metaLineCount * 26f
            val nameHeight = (rect.height() - inset * 2 - metaHeight).coerceAtLeast(30f)

            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = bold
                textAlign = Paint.Align.LEFT
                color = base
            }
            val nameLines = fitName(p.course.name, namePaint, innerWidth, nameHeight)
            var baseline = rect.top + inset + namePaint.textSize
            for (line in nameLines) {
                canvas.drawText(line, rect.left + inset, baseline, namePaint)
                baseline += namePaint.textSize * 1.22f
            }

            var metaBaseline = rect.bottom - inset - 4f - 26f * (metaLineCount - 1)
            for (group in metaLines) {
                for (line in group) {
                    canvas.drawText(line, rect.left + inset, metaBaseline, metaPaint)
                    metaBaseline += 26f
                }
            }
        }

        // 页脚
        paint.typeface = regular
        paint.textSize = 24f
        paint.color = MUTED
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(Lang.tr("由 ClassSchedule 生成"), WIDTH / 2, height - PAD * 0.7f, paint)

        return bitmap
    }

    fun write(context: Context, timetable: Timetable, week: Int): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "timetable-week-$week.png")
        val bitmap = render(timetable, week)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        return file
    }

    /**
     * 保存到系统相册（Pictures/ClassSchedule）。返回保存后的文件名；失败返回 null。
     * Android 10+ 走 MediaStore，不需要权限；9 及以下写公共 Pictures 目录，需要存储权限。
     */
    fun saveToGallery(context: Context, timetable: Timetable, week: Int): String? {
        val name = "ClassSchedule_week${week}_" +
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".png"
        val bitmap = render(timetable, week)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, bitmap, name)
            } else {
                saveToPictures(context, bitmap, name)
            }
        } catch (e: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    private fun saveViaMediaStore(context: Context, bitmap: Bitmap, name: String): String? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/ClassSchedule",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: return null
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return name
    }

    private fun saveToPictures(context: Context, bitmap: Bitmap, name: String): String? {
        @Suppress("DEPRECATION")
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "ClassSchedule",
        ).apply { mkdirs() }
        val file = File(dir, name)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
        return name
    }

    /** 生成并拉起系统分享面板；失败时返回 false（调用方给一句提示）。 */
    fun share(context: Context, timetable: Timetable, week: Int): Boolean = runCatching {
        val file = write(context, timetable, week)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, Lang.tr("第 $week 周课表"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "分享课表图片").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
        true
    }.getOrDefault(false)

    private fun timeTextOf(course: Course, timetable: Timetable): String? {
        val start = timetable.sectionTimes.firstOrNull { it.section == course.startSection } ?: return null
        val end = timetable.sectionTimes.firstOrNull { it.section == course.endSection } ?: start
        return "${start.start}-${end.end}"
    }

    private fun dateRange(dates: List<LocalDate>): String {
        if (dates.isEmpty()) return ""
        val first = dates.first()
        val last = dates.last()
        fun two(value: Int) = value.toString().padStart(2, '0')
        return "${first.year}/${two(first.monthValue)}/${two(first.dayOfMonth)} – " +
            "${two(last.monthValue)}/${two(last.dayOfMonth)}"
    }

    /** 字号从大到小试，直到整门课名能完整放进 [maxHeight]；放不下就用 18sp + 省略号。 */
    private fun fitName(name: String, paint: Paint, maxWidth: Float, maxHeight: Float): List<String> {
        var size = 30f
        while (size >= 18f) {
            paint.textSize = size
            val maxLines = (maxHeight / (size * 1.22f)).toInt().coerceIn(1, 6)
            val lines = wrapText(name, paint, maxWidth, maxLines)
            val complete = lines.joinToString("").removeSuffix("…").length >= name.length
            val narrow = lines.all { paint.measureText(it) <= maxWidth * 1.03f }
            if (complete && narrow) return lines
            size -= 2f
        }
        paint.textSize = 18f
        return wrapText(name, paint, maxWidth, (maxHeight / (18f * 1.22f)).toInt().coerceAtLeast(1))
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        if (text.isEmpty() || maxLines <= 0) return emptyList()
        val raw = mutableListOf<String>()
        var current = StringBuilder()
        for (ch in text) {
            current.append(ch)
            if (paint.measureText(current.toString()) > maxWidth && current.length > 1) {
                current.deleteCharAt(current.length - 1)
                raw.add(current.toString())
                current = StringBuilder().append(ch)
            }
        }
        if (current.isNotEmpty()) raw.add(current.toString())

        // 避头尾：不让「）」、」」这类标点跑到行首，也不让「（」留在行尾
        for (i in 1 until raw.size) {
            var moved = 0
            while (raw[i].isNotEmpty() && raw[i][0] in CLOSING && moved < 2) {
                raw[i - 1] = raw[i - 1] + raw[i][0]
                raw[i] = raw[i].substring(1)
                moved++
            }
        }
        for (i in 0 until raw.size - 1) {
            if (raw[i].isNotEmpty() && raw[i].last() in OPENING) {
                raw[i + 1] = raw[i].last() + raw[i + 1]
                raw[i] = raw[i].dropLast(1)
            }
        }
        raw.removeAll { it.isEmpty() }
        if (raw.size <= maxLines) return raw

        val kept = raw.take(maxLines).toMutableList()
        val rest = raw.drop(maxLines).joinToString("")
        kept[kept.size - 1] = ellipsize(kept.last() + rest, paint, maxWidth)
        return kept
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var out = text
        while (out.length > 1 && paint.measureText("$out…") > maxWidth) out = out.dropLast(1)
        return "$out…"
    }
}
