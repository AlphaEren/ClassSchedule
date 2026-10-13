package com.lorat.classschedule

import com.lorat.classschedule.data.CourseGroup
import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.parityLabel
import com.lorat.classschedule.data.progressText
import com.lorat.classschedule.data.sectionsText
import com.lorat.classschedule.data.timeText
import com.lorat.classschedule.data.weekRanges
import com.lorat.classschedule.data.weeksText
import com.lorat.classschedule.data.model.defaultSectionTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Week/period formatting and the "这门课上到哪了" progress text. */
class CourseOpsTest {

    @Test
    fun `week ranges collapse consecutive weeks`() {
        assertEquals(listOf(1 to 5, 7 to 7, 9 to 9, 11 to 11), weekRanges(listOf(1, 2, 3, 4, 5, 7, 9, 11)))
        assertEquals(listOf(3 to 3), weekRanges(listOf(3)))
        assertEquals(emptyList<Pair<Int, Int>>(), weekRanges(emptyList()))
    }

    @Test
    fun `weeks text mentions parity`() {
        assertEquals("全学期", weeksText(emptyList(), 18))
        assertEquals("1-9 周 · 单周", weeksText(listOf(1, 3, 5, 7, 9), 18))
        assertEquals("2-12 周 · 双周", weeksText(listOf(2, 4, 6, 8, 10, 12), 18))
        assertEquals("1-6,9-10 周", weeksText(listOf(1, 2, 3, 4, 5, 6, 9, 10), 18))
    }

    @Test
    fun `parity label only when it is a real odd-or-even course`() {
        assertEquals("单周", parityLabel(listOf(1, 3, 5), 18))
        assertEquals("双周", parityLabel(listOf(2, 4), 18))
        assertNull(parityLabel(listOf(1, 2, 3), 18))
        assertNull(parityLabel(emptyList(), 18))
        assertNull(parityLabel((1..18).toList(), 18))
    }

    @Test
    fun `progress follows the current week`() {
        val group = CourseGroup(
            key = "k",
            name = "概率论",
            teacher = "",
            location = "",
            colorIndex = 0,
            credit = "",
            note = "",
            slots = listOf(Course("1", "概率论", dayOfWeek = 1, startSection = 1, endSection = 2, weeks = (1..9).toList())),
        )
        assertEquals("共 9 周 · 已上 5 周 · 剩 3 周 · 本周有课", group.progressText(currentWeek = 6, totalWeeks = 18))
        // 第 10 周已经结课
        assertEquals("共 9 周 · 已上 9 周 · 剩 0 周 · 本周不上", group.progressText(currentWeek = 10, totalWeeks = 18))

        val full = group.copy(slots = listOf(group.slots.first().copy(weeks = emptyList())))
        assertEquals("共 18 周 · 已上 5 周 · 剩 12 周 · 本周有课", full.progressText(currentWeek = 6, totalWeeks = 18))
    }

    @Test
    fun `section and time text`() {
        assertEquals("第3节", sectionsText(3, 3))
        assertEquals("第1-2节", sectionsText(1, 2))
        val times = defaultSectionTimes()
        assertEquals("08:00-09:40", timeText(Course("1", "x", dayOfWeek = 1, startSection = 1, endSection = 2), times))
    }
}
