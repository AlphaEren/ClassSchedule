package com.lorat.classschedule.data.model

import kotlinx.serialization.Serializable

/** How a course cell is painted. */
object CellStyle {
    /** Solid course colour with light text. */
    const val FILLED = "filled"

    /** Tinted cell with course-coloured text (WakeUp's default light skin). */
    const val LIGHT = "light"

    /** Transparent cell with a coloured border. */
    const val OUTLINE = "outline"
}

/** Display theme, mirroring 全局设置 → 显示主题. */
object DarkMode {
    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2
}

/**
 * Everything the user can style: 课表外观 (grid & cell rendering) plus the handful of
 * 全局设置 switches that change how the timetable page itself behaves.
 * Colours are packed ARGB longs; 0 means "derive it from the course colour / theme".
 */
@Serializable
data class Appearance(
    val cellStyle: String = CellStyle.LIGHT,
    /** Height of one 小节 row, in dp (课表外观 → 格子高度). */
    val cellHeight: Int = 80,
    val cornerRadius: Int = 12,
    /** Fill opacity of a cell, in percent (不透明度). */
    val opacity: Int = 75,
    /** Opacity of courses that do not run in the week being displayed (非本周不透明比). */
    val otherWeekOpacity: Int = 50,
    val fontSize: Int = 13,
    val showTime: Boolean = false,
    val showLocation: Boolean = true,
    val showTeacher: Boolean = false,
    val dashedBorder: Boolean = false,
    val borderUseCourseColor: Boolean = true,
    /** 课程文字颜色; 0 = follow the skin. */
    val textColor: Long = 0L,
    /** 格子边框颜色; 0 = follow the course colour. */
    val borderColor: Long = 0L,
    /** 界面文字颜色; 0 = follow the theme. */
    val uiTextColor: Long = 0L,
    val showWeekend: Boolean = true,
    /** 周日为每周第一天. */
    val weekStartSunday: Boolean = false,
    /** 是否显示非本周课程. */
    val showOtherWeeks: Boolean = true,
    /** 0 = follow system, 1 = force light, 2 = force dark. */
    val darkMode: Int = DarkMode.SYSTEM,
    /** 界面语言：0 = 跟随系统, 1 = 简体中文, 2 = English. */
    val language: Int = 0,
    /** 左侧时间轴：显示节次 + 上下课时间 / 只显示节次. */
    val showAxis: Boolean = true,
    val axisShowOnlySection: Boolean = false,
    /** 显示网格辅助线. */
    val gridLines: Boolean = true,
    /** 课表数据 → 一天课程节数 (row count is driven by sectionTimes; this is the target). */
    val extraBottomSpace: Int = 0,
    /** 自动检查更新（静默刷新）. */
    val autoCheckUpdate: Boolean = true,
    /** 显示空视图图片. */
    val showEmptyImage: Boolean = true,
)
