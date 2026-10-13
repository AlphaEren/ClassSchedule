package com.lorat.classschedule

import com.lorat.classschedule.data.CsvCourse
import com.lorat.classschedule.data.csvOf
import com.lorat.classschedule.data.csvToCourses
import com.lorat.classschedule.data.csvWeeks
import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.model.defaultSectionTimes
import com.lorat.classschedule.data.parseCsvTimetable
import com.lorat.classschedule.data.parseWeeks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for the WakeUp课程表-compatible CSV format and week syntax. */
class CsvIoTest {

    @Test
    fun `official template sample parses`() {
        val csv = """
            课程名称,星期,开始节数,结束节数,老师,地点,周数
            高等数学,1,1,2,小明,逸夫楼201,1-5、7-11单、12-16双
            线性代数,2,3,4,小红,理工楼110,1-16
            大学英语,2,3,4,小红,文成楼125,2、5、8
        """.trimIndent()

        val rows = parseCsvTimetable(csv, totalWeeks = 20)!!
        assertEquals(3, rows.size)
        assertEquals("高等数学", rows[0].name)
        assertEquals(1, rows[0].dayOfWeek)
        assertEquals(1, rows[0].startSection)
        assertEquals(2, rows[0].endSection)
        assertEquals("小明", rows[0].teacher)
        assertEquals("逸夫楼201", rows[0].location)
        // 1-5 + odd weeks in 7..11 + even weeks in 12..16
        assertEquals(listOf(1, 2, 3, 4, 5, 7, 9, 11, 12, 14, 16), rows[0].weeks)
        assertEquals((1..16).toList(), rows[1].weeks)
        assertEquals(listOf(2, 5, 8), rows[2].weeks)
    }

    @Test
    fun `wu means empty and missing fields are tolerated`() {
        val csv = "名称,星期,开始节数,结束节数,老师,地点,周数\n大学计算机,5,1,2,无,无,1-16"
        val rows = parseCsvTimetable(csv, totalWeeks = 20)!!
        assertEquals("", rows[0].teacher)
        assertEquals("", rows[0].location)
    }

    @Test
    fun `headerless file falls back to the fixed column order`() {
        val rows = parseCsvTimetable("有机化学,4,3,4,李四,逸夫楼108,1-5", totalWeeks = 20)!!
        assertEquals(1, rows.size)
        assertEquals(4, rows[0].dayOfWeek)
        assertEquals("李四", rows[0].teacher)
        assertEquals(listOf(1, 2, 3, 4, 5), rows[0].weeks)
    }

    @Test
    fun `excel date mangling is read back as a week range`() {
        assertEquals((1..5).toList(), parseWeeks("1月5日", 20))
        assertEquals(listOf(1, 2, 3), parseWeeks("1-3", 20))
        assertEquals(emptyList<Int>(), parseWeeks("无", 20))
        assertEquals(emptyList<Int>(), parseWeeks("1-20", 20))
        assertEquals((1..20).filter { it % 2 == 1 }, parseWeeks("单周", 20))
    }

    @Test
    fun `a full semester collapses to the canonical empty week list`() {
        assertEquals(emptyList<Int>(), parseWeeks("1-20", 20))
        // Clipping to a shorter semester still yields an explicit, non-full list here.
        assertEquals(listOf(5, 6, 7, 8, 9, 10), parseWeeks("5-16", 10))
    }

    @Test
    fun `csv export round trips`() {
        val courses = listOf(
            Course("a", "高等数学", "小明", "逸夫楼201", 1, 1, 2, (1..16).toList()),
            Course("b", "体育", "", "", 3, 5, 6, (1..16).filter { it % 2 == 1 }),
            Course("c", "选修", "", "", 5, 7, 8, listOf(2, 5, 8)),
        )
        val csv = csvOf(courses, totalWeeks = 16)
        val rows = parseCsvTimetable(csv, totalWeeks = 16)!!
        assertEquals(3, rows.size)
        assertEquals(emptyList<Int>(), rows[0].weeks)
        assertEquals((1..16).filter { it % 2 == 1 }, rows[1].weeks)
        assertEquals(listOf(2, 5, 8), rows[2].weeks)
        // Blank teacher/location become 无 and come back empty.
        assertEquals("", rows[1].teacher)
        assertEquals("", rows[2].location)
    }

    @Test
    fun `csv weeks uses the official syntax`() {
        assertEquals("1-16", csvWeeks(emptyList(), 16))
        assertEquals("7-11单", csvWeeks(listOf(7, 9, 11), 16))
        assertEquals("1-5单", csvWeeks(listOf(1, 3, 5), 16))
        assertEquals("12-16双", csvWeeks(listOf(12, 14, 16), 16))
        assertEquals("2、5、8", csvWeeks(listOf(2, 5, 8), 16))
    }

    @Test
    fun `official template survives an export round trip`() {
        val csv = """
            课程名称,星期,开始节数,结束节数,老师,地点,周数
            高等数学,1,1,2,小明,逸夫楼201,1-5、7-11单、12-16双
            线性代数,2,3,4,小红,理工楼110,1-16
        """.trimIndent()
        val first = parseCsvTimetable(csv, totalWeeks = 20)!!
        val exported = csvOf(csvToCourses(first, defaultSectionTimes()), totalWeeks = 20)
        val second = parseCsvTimetable(exported, totalWeeks = 20)!!
        assertEquals(first.map { it.weeks }, second.map { it.weeks })
    }

    @Test
    fun `rows become courses grouped by name`() {
        val rows = listOf(
            CsvCourse("高等数学", 1, 1, 2, (1..16).toList(), "小明", "教1-201"),
            CsvCourse("高等数学", 3, 5, 6, (1..16).toList(), "小明", "教1-202"),
        )
        val courses = csvToCourses(rows, defaultSectionTimes())
        assertEquals(2, courses.size)
        assertEquals(courses[0].groupId, courses[1].groupId)
    }

    @Test
    fun `garbage input yields null instead of an empty timetable`() {
        assertNull(parseCsvTimetable("<html><body>没有课表</body></html>", 20))
        assertNull(parseCsvTimetable("", 20))
    }

    @Test
    fun `quoted fields with commas survive`() {
        val csv = "课程名称,星期,开始节数,结束节数,老师,地点,周数\n\"高等数学,下\",1,1,2,张三,教1-201,1-8"
        val rows = parseCsvTimetable(csv, totalWeeks = 20)!!
        assertEquals("高等数学,下", rows[0].name)
        assertTrue(rows[0].weeks.size == 8)
    }
}
