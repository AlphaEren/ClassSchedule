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
}
