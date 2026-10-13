package com.lorat.classschedule

import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.model.School
import com.lorat.classschedule.data.model.Timetable
import com.lorat.classschedule.widget.WidgetData
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** The widget must show today's classes only, honouring 单双周 and section order. */
class WidgetDataTest {

    private val monday = LocalDate.of(2026, 10, 12)

    private fun timetable() = Timetable(
        school = School(id = "demo", name = "示例大学"),
        semesterStartDate = monday.toString(),
        totalWeeks = 20,
        courses = listOf(
            Course("1", "高等数学", "张伟", "教1-201", 1, 3, 4, emptyList()),
            Course("2", "大学英语", "李娜", "外语楼-305", 1, 1, 2, emptyList()),
            Course("3", "单周实验", "", "机房", 1, 5, 6, (1..20).filter { it % 2 == 1 }),
            Course("4", "双周实验", "", "机房", 1, 7, 8, (1..20).filter { it % 2 == 0 }),
            Course("5", "周二课", "", "", 2, 1, 2, emptyList()),
        ),
    )

    @Test
    fun `odd week of the semester keeps odd-week courses only`() {
        val today = monday // week 1
        val names = WidgetData.todayCourses(timetable(), today).map { it.name }
        assertEquals(listOf("大学英语", "高等数学", "单周实验"), names)
    }

    @Test
    fun `even week swaps the parity course`() {
        val today = monday.plusWeeks(1) // week 2
        val names = WidgetData.todayCourses(timetable(), today).map { it.name }
        assertEquals(listOf("大学英语", "高等数学", "双周实验"), names)
    }

    @Test
    fun `weekly courses disappear in later weeks`() {
        val today = monday.plusWeeks(1)
        val names = WidgetData.todayCourses(timetable(), today).map { it.name }
        assertEquals(listOf("大学英语", "高等数学", "双周实验"), names)
    }

    @Test
    fun `meta joins location teacher and parity`() {
        val tt = timetable()
        assertEquals("@教1-201 张伟", WidgetData.metaOf(tt.courses[0], tt))
        assertEquals("@机房 单周", WidgetData.metaOf(tt.courses[2], tt))
    }
}
