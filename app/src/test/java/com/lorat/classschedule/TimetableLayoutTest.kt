package com.lorat.classschedule

import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.model.Timetable
import com.lorat.classschedule.data.placement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 课表格子的排布：同格合并、冲突平分、非本周只在允许时才画。 */
class TimetableLayoutTest {

    private fun course(
        id: String,
        name: String,
        start: Int = 1,
        end: Int = 2,
        weeks: List<Int> = emptyList(),
    ) = Course(
        id = id,
        name = name,
        dayOfWeek = 1,
        startSection = start,
        endSection = end,
        weeks = weeks,
    )

    private fun timetable(vararg courses: Course) = Timetable(courses = courses.toList())

    @Test
    fun `same course in the same slot merges its weeks`() {
        // 两条记录都包含第 1 周，才会合并成一张卡；周次取并集
        val timetable = timetable(
            course("a", "概率论", weeks = (1..6).toList()),
            course("b", "概率论", weeks = (1..10).toList()),
        )
        val placed = placement(timetable, listOf(1), week = 1, includeOtherWeeks = true)
        assertEquals(1, placed.size)
        assertEquals((1..10).toList(), placed.first().course.weeks)
    }

    @Test
    fun `two courses in the same week split the cell`() {
        val timetable = timetable(course("a", "概率论"), course("b", "大学物理"))
        val placed = placement(timetable, listOf(1), week = 1)
        assertEquals(2, placed.size)
        assertTrue(placed.all { it.slices == 2 && it.conflict })
    }

    @Test
    fun `overlapping classes are split instead of stacked`() {
        val timetable = timetable(
            course("a", "概率论", start = 1, end = 4),
            course("b", "实用法律", start = 3, end = 4),
        )
        val placed = placement(timetable, listOf(1), week = 1)
        assertEquals(2, placed.size)
        assertEquals(2, placed.first { it.course.name == "概率论" }.slices)
        assertTrue(placed.all { it.thisWeek })
    }

    @Test
    fun `share image layout leaves out courses of other weeks`() {
        val timetable = timetable(
            course("a", "概率论", weeks = (1..6).toList()),
            course("b", "金工实习", start = 5, end = 6, weeks = (9..10).toList()),
        )
        val onlyThisWeek = placement(timetable, listOf(1), week = 6, includeOtherWeeks = false)
        assertEquals(listOf("概率论"), onlyThisWeek.map { it.course.name })

        val withGhosts = placement(timetable, listOf(1), week = 6, includeOtherWeeks = true)
        assertEquals(listOf("概率论", "金工实习"), withGhosts.map { it.course.name })
        assertTrue(withGhosts.last().thisWeek.not())
    }
}
