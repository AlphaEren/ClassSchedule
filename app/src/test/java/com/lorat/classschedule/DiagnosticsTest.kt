package com.lorat.classschedule

import com.lorat.classschedule.data.Diagnostics
import com.lorat.classschedule.data.bugReportBody
import com.lorat.classschedule.data.bugReportLeaks
import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.model.School
import com.lorat.classschedule.data.model.Timetable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 问题反馈：报告要够诊断用，但不能把课程/老师/学校信息带出去。 */
class DiagnosticsTest {

    private fun timetable() = Timetable(
        school = School(
            id = "s1",
            name = "示例大学",
            scheduleUrl = "https://jw.example.edu.cn/timetable",
            parserPreset = "auto",
            refreshUrl = "https://jw.example.edu.cn/kb",
        ),
        semesterStartDate = "2026-09-07",
        totalWeeks = 20,
        courses = listOf(
            Course(
                id = "c1",
                name = "高等数学",
                teacher = "张老师",
                location = "A101",
                dayOfWeek = 1,
                startSection = 1,
                endSection = 2,
                weeks = listOf(1, 2, 3),
                groupId = "g1",
            ),
            Course(
                id = "c2",
                name = "高等数学",
                teacher = "张老师",
                location = "A101",
                dayOfWeek = 3,
                startSection = 3,
                endSection = 4,
                weeks = listOf(1, 2, 3),
                groupId = "g1",
            ),
        ),
    )

    @Test
    fun `report carries the numbers needed to debug`() {
        val report = bugReportBody(timetable(), note = "点导入就闪退", logs = emptyList())
        assertTrue(report.contains("点导入就闪退"))
        assertTrue(report.contains("课程条目：2 条，合并后 1 门"))
        assertTrue(report.contains("已添加学校：是"))
        assertTrue(report.contains("已设置开学日期：是"))
        assertTrue(report.contains("周数：20 周"))
    }

    @Test
    fun `report never leaks courses teachers rooms or school`() {
        val report = bugReportBody(timetable(), note = "识别不对", logs = emptyList())
        val leaks = bugReportLeaks(report, timetable())
        assertTrue("报告里不该出现：$leaks", leaks.isEmpty())
        assertTrue(!report.contains("高等数学"))
        assertTrue(!report.contains("张老师"))
        assertTrue(!report.contains("A101"))
        assertTrue(!report.contains("示例大学"))
        assertTrue(!report.contains("jw.example.edu.cn"))
    }

    @Test
    fun `logs are optional and the leak detector actually works`() {
        val tt = timetable()
        val without = bugReportBody(tt, note = "", logs = emptyList())
        assertTrue(!without.contains("【最近诊断】"))

        val with = bugReportBody(tt, note = "", logs = listOf("[ERROR] 页面加载失败 (main.js:1)"))
        assertTrue(with.contains("【最近诊断】"))
        assertTrue(with.contains("页面加载失败"))

        // 反向验证：真把课名塞进报告时，检测函数必须报出来
        val dirty = without + "高等数学"
        assertEquals(listOf("高等数学"), bugReportLeaks(dirty, tt))
    }

    @Test
    fun `diagnostics buffer keeps the newest lines only`() {
        Diagnostics.clear()
        repeat(100) { Diagnostics.add("line-$it") }
        val lines = Diagnostics.snapshot()
        assertTrue(lines.size <= 80)
        assertEquals("line-99", lines.last())
        Diagnostics.clear()
        assertTrue(Diagnostics.snapshot().isEmpty())
    }
}
