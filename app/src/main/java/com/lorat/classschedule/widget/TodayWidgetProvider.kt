package com.lorat.classschedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.lorat.classschedule.MainActivity
import com.lorat.classschedule.R
import com.lorat.classschedule.data.currentWeekOf
import com.lorat.classschedule.data.dayName
import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.model.Timetable
import com.lorat.classschedule.data.parityLabel
import com.lorat.classschedule.data.timeText
import com.lorat.classschedule.ui.autoColorMap
import com.lorat.classschedule.ui.courseColorFor
import kotlinx.serialization.json.Json
import androidx.compose.ui.graphics.toArgb
import java.io.File
import java.time.LocalDate

/**
 * 今日课程 widget. Reads the same JSON the app writes, shows up to six of today's classes and
 * an empty state otherwise. Rows are pre-declared in the layout so no RemoteViewsService is needed.
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val timetable = WidgetData.load(context)
        for (id in ids) manager.updateAppWidget(id, buildViews(context, timetable))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refresh(context)
    }

    companion object {
        const val ACTION_REFRESH = "com.lorat.classschedule.WIDGET_REFRESH"
        private const val MAX_ROWS = 6

        private val RowIds = intArrayOf(
            R.id.widget_row_1,
            R.id.widget_row_2,
            R.id.widget_row_3,
            R.id.widget_row_4,
            R.id.widget_row_5,
            R.id.widget_row_6,
        )

        private val NameIds = intArrayOf(
            R.id.widget_name_1,
            R.id.widget_name_2,
            R.id.widget_name_3,
            R.id.widget_name_4,
            R.id.widget_name_5,
            R.id.widget_name_6,
        )

        private val MetaIds = intArrayOf(
            R.id.widget_meta_1,
            R.id.widget_meta_2,
            R.id.widget_meta_3,
            R.id.widget_meta_4,
            R.id.widget_meta_5,
            R.id.widget_meta_6,
        )

        private val TimeIds = intArrayOf(
            R.id.widget_time_1,
            R.id.widget_time_2,
            R.id.widget_time_3,
            R.id.widget_time_4,
            R.id.widget_time_5,
            R.id.widget_time_6,
        )

        private val BarIds = intArrayOf(
            R.id.widget_bar_1,
            R.id.widget_bar_2,
            R.id.widget_bar_3,
            R.id.widget_bar_4,
            R.id.widget_bar_5,
            R.id.widget_bar_6,
        )

        /** Push fresh data into every placed widget. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val timetable = WidgetData.load(context)
            for (id in ids) manager.updateAppWidget(id, buildViews(context, timetable))
        }

        private fun buildViews(context: Context, timetable: Timetable): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            val today = LocalDate.now()
            val week = currentWeekOf(timetable)
            val title = if (timetable.semesterName.isBlank()) "今日课程" else timetable.semesterName
            views.setTextViewText(
                R.id.widget_title,
                "$title · ${today.monthValue}/${today.dayOfMonth} ${dayName(today.dayOfWeek.value)} · 第 $week 周",
            )

            val mine = WidgetData.todayCourses(timetable, today)
            if (mine.isEmpty()) {
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                views.setTextViewText(R.id.widget_empty, "今天没有课，好好休息 🎉")
            } else {
                views.setViewVisibility(R.id.widget_empty, View.GONE)
            }

            val autoColors = autoColorMap(timetable.courses.map { it.name })
            for (index in 0 until MAX_ROWS) {
                val course = mine.getOrNull(index)
                if (course == null) {
                    views.setViewVisibility(RowIds[index], View.GONE)
                    continue
                }
                views.setViewVisibility(RowIds[index], View.VISIBLE)
                views.setTextViewText(NameIds[index], course.name)
                views.setTextViewText(MetaIds[index], WidgetData.metaOf(course, timetable))
                views.setTextViewText(TimeIds[index], timeText(course, timetable.sectionTimes))
                views.setInt(BarIds[index], "setBackgroundColor", courseColorFor(course.colorIndex, course.name, autoColors).toArgb())
            }

            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            return views
        }
    }
}

internal object WidgetData {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): Timetable = runCatching {
        val file = File(context.filesDir, "timetable.json")
        if (!file.exists()) Timetable() else json.decodeFromString(Timetable.serializer(), file.readText())
    }.getOrElse { Timetable() }

    /** Today's classes in section order, taking 单双周 into account. */
    fun todayCourses(timetable: Timetable, today: LocalDate): List<Course> {
        val week = currentWeekOf(timetable, today)
        val day = today.dayOfWeek.value
        return timetable.courses
            .filter { it.dayOfWeek == day && it.matchesWeek(week) }
            .sortedBy { it.startSection }
    }

    fun metaOf(course: Course, timetable: Timetable): String {
        val parts = mutableListOf<String>()
        if (course.location.isNotBlank()) parts.add("@${course.location}")
        if (course.teacher.isNotBlank()) parts.add(course.teacher)
        parityLabel(course.weeks, timetable.totalWeeks)?.let { parts.add(it) }
        return parts.joinToString(" ")
    }
}
