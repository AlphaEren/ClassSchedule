package com.lorat.classschedule

import com.lorat.classschedule.data.OcrLine
import com.lorat.classschedule.data.coursesFromOcr
import com.lorat.classschedule.data.dayName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 图片识别：把「带坐标的文字块」还原成课程。 */
class OcrTimetableTest {

    private fun line(text: String, cx: Int, cy: Int) = OcrLine(text, cx - 30, cy - 10, cx + 30, cy + 10)

    private fun timetableLines(): List<OcrLine> = listOf(
        // 星期表头
        line("周一", 260, 100), line("周二", 380, 100), line("周三", 500, 100),
        line("周四", 620, 100), line("周五", 740, 100), line("周六", 860, 100), line("周日", 980, 100),
        // 节次（左侧）
        line("①", 60, 200), line("②", 60, 340), line("③", 60, 480), line("④", 60, 620),
        // 周一第1节：高等数学
        line("高等数学", 260, 200), line("@教1-201", 260, 235), line("1-16周", 260, 270),
        // 周三第2节：大学英语（奇数周）
        line("大学英语", 500, 340), line("@外语楼305", 500, 375), line("1-15周(单)", 500, 410),
        // 周五第3-4节：体育（用 3-4 这种大节标签）
        line("3-4", 60, 550), line("体育", 740, 545), line("@田径场", 740, 580),
    )

    @Test
    fun `restores day, sections, name, room and weeks`() {
        val courses = coursesFromOcr(timetableLines(), totalWeeks = 20, width = 1080)
        assertTrue("至少识别出 3 门：$courses", courses.size >= 3)

        val math = courses.first { it.name == "高等数学" }
        assertEquals(1, math.dayOfWeek)
        assertEquals(1, math.startSection)
        assertEquals(1, math.endSection)
        assertEquals("@教1-201", math.location)
        assertEquals((1..16).toList(), math.weeks)

        val english = courses.first { it.name == "大学英语" }
        assertEquals(3, english.dayOfWeek)
        assertEquals(2, english.startSection)
        assertEquals((1..15).filter { it % 2 == 1 }, english.weeks)

        val pe = courses.first { it.name == "体育" }
        assertEquals(5, pe.dayOfWeek)
        // 大节标签 3-4 表示跨两小节
        assertEquals("${dayName(5)} 3-4 节", "${dayName(pe.dayOfWeek)} ${pe.startSection}-${pe.endSection} 节")
        assertEquals("@田径场", pe.location)
    }

    @Test
    fun `garbage image yields nothing instead of a broken timetable`() {
        assertTrue(coursesFromOcr(listOf(line("啊", 10, 10), line("哦", 20, 20)), 20, 1080).isEmpty())
        assertTrue(coursesFromOcr(emptyList(), 20, 1080).isEmpty())
    }

    @Test
    fun `sections without a header still get assigned by column order`() {
        val noHeader = listOf(
            line("①", 60, 200), line("②", 60, 340),
            line("线性代数", 260, 200), line("@教2-108", 260, 235),
        )
        val courses = coursesFromOcr(noHeader, 20, 1080)
        assertTrue(courses.isNotEmpty())
        assertEquals(1, courses.first().startSection)
    }

    /** 有星期表头但完全没识别出节次刻度时，不能崩，节次退回默认值。 */
    @Test
    fun `table without section numbers does not crash`() {
        val lines = listOf(
            line("星期一", 200, 40), line("星期二", 330, 40), line("星期三", 460, 40), line("星期四", 590, 40),
            line("程序设计基础", 200, 140), line("老师W【4-19周】", 200, 175), line("A101", 200, 210),
        )
        val courses = coursesFromOcr(lines, totalWeeks = 20, width = 1114)
        assertTrue(courses.isNotEmpty())
        assertEquals(1, courses.first().startSection)
        assertEquals(1, courses.first().endSection)
        assertEquals("A101", courses.first().location)
    }

    /** 学校教务导出的课表样式：`星期一`表头、`老师【4-19周】`混排、`A101`地点、一格两门课。 */
    @Test
    fun `parses a school style table with teacher-weeks lines and two courses in one cell`() {
        val dayX = listOf(200, 330, 460, 590, 720, 850, 980)
        val lines = mutableListOf<OcrLine>()
        listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日").forEachIndexed { i, d ->
            lines += line(d, dayX[i], 40)
        }
        // 节次刻度：1..10，第 1 行在 y=110，每行 60px
        (1..10).forEach { s -> lines += line(s.toString(), 60, 80 + s * 60) }

        // 周一 1-2 节：程序设计基础 / 老师W【4-19周】 / A101
        lines += line("程序设计基础", dayX[0], 140)
        lines += line("老师W【4-19周】", dayX[0], 175)
        lines += line("A101", dayX[0], 210)
        // 周五 5-6 节：一格两门课，中间一条虚线
        lines += line("大学美育", dayX[4], 440)
        lines += line("老师Y【4-11周】", dayX[4], 470)
        lines += line("A106", dayX[4], 500)
        lines += line("中华优秀传统文化", dayX[4], 540)
        lines += line("老师X【12-19周】", dayX[4], 570)
        lines += line("A105", dayX[4], 600)

        val courses = coursesFromOcr(lines, totalWeeks = 20, width = 1114)
        val basic = courses.firstOrNull { it.name == "程序设计基础" }
        assertTrue("没识别出「程序设计基础」：$courses", basic != null)
        assertEquals(1, basic!!.dayOfWeek)
        assertEquals(1, basic.startSection)
        assertEquals(2, basic.endSection)
        assertEquals("老师W", basic.teacher)
        assertEquals("A101", basic.location)
        assertEquals((4..19).toList(), basic.weeks)

        val friday = courses.filter { it.dayOfWeek == 5 }
        assertTrue("周五应该有两门课：$friday", friday.size >= 2)
        val meiyu = friday.first { it.name.contains("美育") }
        assertEquals("A106", meiyu.location)
        assertEquals((4..11).toList(), meiyu.weeks)
        val tradition = friday.first { it.name.contains("传统文化") }
        assertEquals("老师X", tradition.teacher)
        assertEquals((12..19).toList(), tradition.weeks)
    }
}
