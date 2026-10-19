package com.lorat.classschedule

import com.lorat.classschedule.data.CourseGroup
import com.lorat.classschedule.data.courseGroups
import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.model.Timetable
import com.lorat.classschedule.data.noteChipText
import com.lorat.classschedule.data.noteFor
import com.lorat.classschedule.data.parityLabel
import com.lorat.classschedule.data.progressText
import com.lorat.classschedule.data.sectionsText
import com.lorat.classschedule.data.timeText
import com.lorat.classschedule.data.weekRanges
import com.lorat.classschedule.data.weeksText
import com.lorat.classschedule.data.withSlotNote
import com.lorat.classschedule.data.withWeekNote
import com.lorat.classschedule.data.withoutWeek
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

    @Test
    fun `deleting one week keeps the other weeks`() {
        val full = Course("1", "A", dayOfWeek = 1, startSection = 1, endSection = 2)
        val afterFull = listOf(full).withoutWeek(6, 10)
        assertEquals(1, afterFull.size)
        assertEquals((1..10).filter { it != 6 }, afterFull.first().weeks)

        val onlyWeek6 = full.copy(id = "2", weeks = listOf(6))
        assertEquals(emptyList<Course>(), listOf(onlyWeek6).withoutWeek(6, 10))

        val multi = full.copy(id = "3", weeks = listOf(5, 6, 7))
        assertEquals(listOf(5, 7), listOf(multi).withoutWeek(6, 10).first().weeks)

        // 全学期、且总周数只剩这一周 → 整段删掉
        assertEquals(emptyList<Course>(), listOf(full).withoutWeek(1, 1))
    }

    @Test
    fun `per-week note overrides the course note`() {
        val slot = Course(
            "1",
            "A",
            dayOfWeek = 1,
            startSection = 1,
            endSection = 2,
            weeks = listOf(5, 6, 7, 8),
            note = "带书",
        )
        val withWeek = listOf(slot).withWeekNote("1", 1, 1, 2, 6, "带电脑")
        val group = Timetable(courses = withWeek).courseGroups().first()
        assertEquals("带电脑", group.noteFor(6, 1, 1, 2))
        assertEquals("带书", group.noteFor(7, 1, 1, 2))

        // 清空某一周 = 去掉这一周的单独备注，回到这个时间段的备注
        val cleared = listOf(slot).withWeekNote("1", 1, 1, 2, 6, " ")
        assertEquals("带书", Timetable(courses = cleared).courseGroups().first().noteFor(6, 1, 1, 2))

        // 写「所有周」不会清掉已有的按周备注：那一周仍然优先显示本周备注
        val all = withWeek.withSlotNote("1", 1, 1, 2, "全班")
        val g2 = Timetable(courses = all).courseGroups().first()
        assertEquals("带电脑", g2.noteFor(6, 1, 1, 2))
        assertEquals("全班", g2.noteFor(7, 1, 1, 2))
    }

    @Test
    fun `all-weeks note only touches the selected slot`() {
        val monday = Course(
            "1", "A", dayOfWeek = 1, startSection = 1, endSection = 2,
            weeks = listOf(5, 6, 7, 8), groupId = "g", note = "周一旧",
        )
        val tuesday = monday.copy(id = "2", dayOfWeek = 2, startSection = 3, endSection = 4, note = "周二旧")
        val courses = listOf(monday, tuesday).withSlotNote("g", 1, 1, 2, "周一新")
        val groups = Timetable(courses = courses).courseGroups().first()
        assertEquals("周一新", groups.noteFor(6, 1, 1, 2))
        assertEquals("周二旧", groups.noteFor(6, 2, 3, 4))
    }

    @Test
    fun `per-week note only touches the selected slot`() {
        val monday = Course(
            "1", "A", dayOfWeek = 1, startSection = 1, endSection = 2,
            weeks = listOf(5, 6, 7, 8), groupId = "g", note = "整门课",
        )
        val tuesday = monday.copy(id = "2", dayOfWeek = 2, startSection = 3, endSection = 4, note = "整门课")
        val courses = listOf(monday, tuesday).withWeekNote("g", 1, 1, 2, 6, "只看周一")
        val groups = Timetable(courses = courses).courseGroups().first()
        assertEquals("只看周一", groups.noteFor(6, 1, 1, 2))
        assertEquals("整门课", groups.noteFor(6, 2, 3, 4))
    }

    @Test
    fun `note chip text keeps four characters`() {
        assertEquals("test", noteChipText("test"))
        assertEquals("带电脑", noteChipText("带电脑"))
        assertEquals("带电脑上…", noteChipText("带电脑上课"))
        assertEquals("", noteChipText("   "))
    }
}
