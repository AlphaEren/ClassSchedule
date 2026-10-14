package com.lorat.classschedule

import com.lorat.classschedule.ui.AutoCourseColors
import com.lorat.classschedule.ui.RandomCourseColor
import com.lorat.classschedule.ui.autoColorMap
import com.lorat.classschedule.ui.courseColorFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 自动配色：同一门课颜色固定，不同课程颜色不重复。 */
class CourseColorsTest {

    private val names = listOf(
        "概率论", "大学物理（2）", "大学英语（3）", "工程力学", "电路原理", "机械原理",
        "机械原理课程设计", "金工实习", "马克思主义基本原理", "实用法律", "网络文化",
        "大学物理实验",
    )

    @Test
    fun `one week of courses never repeats a colour`() {
        val map = autoColorMap(names)
        assertEquals(names.size, map.size)
        assertEquals("一周之内不同课程不能撞色", names.size, map.values.distinct().size)
    }

    @Test
    fun `same course keeps its colour whatever the order`() {
        val a = autoColorMap(names)
        val b = autoColorMap(names.reversed() + listOf("概率论"))
        assertEquals(a["概率论"], b["概率论"])
        assertEquals(a["大学英语（3）"], b["大学英语（3）"])
    }

    @Test
    fun `auto colour is used unless the course has an explicit colour`() {
        val map = autoColorMap(names)
        // 手动选过的颜色（ExplicitColorBase + 下标）优先
        assertEquals(
            com.lorat.classschedule.ui.courseColor(3),
            courseColorFor(com.lorat.classschedule.ui.explicitColorIndex(3), "概率论", map),
        )
        assertEquals(map["概率论"], courseColorFor(RandomCourseColor, "概率论", map))
        // 历史数据里导入写死的 0..7 一律按自动配色显示
        assertEquals(map["概率论"], courseColorFor(5, "概率论", map))
    }

    @Test
    fun `palette never repeats before it is exhausted`() {
        assertEquals(AutoCourseColors.size, AutoCourseColors.distinct().size)
        assertEquals("自动配色板就 12 色，够一周用", 12, AutoCourseColors.size)
        // 课程数超过调色板数量才会回绕
        val many = (1..14).map { "课程$it" }
        assertEquals(12, autoColorMap(many).values.distinct().size)
    }
}
