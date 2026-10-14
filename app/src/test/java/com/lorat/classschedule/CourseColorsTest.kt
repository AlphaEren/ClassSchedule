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
        "大学物理实验", "形势与政策（3）", "匹克球（3）", "体育（1）", "线性代数",
    )

    @Test
    fun `different courses get different colours`() {
        val map = autoColorMap(names)
        assertEquals(names.size, map.size)
        assertEquals("同一周里不同课程不能撞色", names.size, map.values.distinct().size)
    }

    @Test
    fun `same course keeps its colour whatever the order`() {
        val a = autoColorMap(names)
        val b = autoColorMap(names.reversed() + listOf("概率论"))
        assertEquals(a["概率论"], b["概率论"])
        assertEquals(a["大学英语（3）"], b["大学英语（3）"])
    }

    @Test
    fun `auto colour is used only when the course has no explicit colour`() {
        val map = autoColorMap(names)
        // 手工选过颜色（colorIndex >= 0）的课不受自动配色影响
        assertEquals(com.lorat.classschedule.ui.courseColor(3), courseColorFor(3, "概率论", map))
        assertEquals(map["概率论"], courseColorFor(RandomCourseColor, "概率论", map))
    }

    @Test
    fun `palette never repeats before it is exhausted`() {
        assertEquals(AutoCourseColors.size, AutoCourseColors.distinct().size)
        // 超过调色板数量才会回绕，此时至少保证相邻不重复
        assertNotEquals(AutoCourseColors[0], AutoCourseColors[1])
    }
}
