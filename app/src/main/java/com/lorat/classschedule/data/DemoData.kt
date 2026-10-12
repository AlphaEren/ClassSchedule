package com.lorat.classschedule.data

import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.model.School
import com.lorat.classschedule.data.model.Timetable
import java.time.LocalDate
import java.time.DayOfWeek

/** Sample timetable so the UI can be previewed before wiring up a real school. */
fun demoTimetable(): Timetable {
    val monday = LocalDate.now().with(DayOfWeek.MONDAY)
    val all = (1..16).toList()
    return Timetable(
        school = School(id = "demo", name = "示例大学", scheduleUrl = "https://jw.example.edu/schedule"),
        semesterStartDate = monday.toString(),
        totalWeeks = 20,
        courses = listOf(
            Course("d1", "高等数学", "张伟", "教1-201", 1, 1, 2, all),
            Course("d2", "大学英语", "李娜", "外语楼-305", 2, 3, 4, all),
            Course("d3", "数据结构", "王强", "计科楼-401", 3, 1, 2, all),
            Course("d4", "线性代数", "刘洋", "教2-108", 4, 5, 6, all),
            Course("d5", "体育", "陈刚", "田径场", 5, 3, 4, all),
            Course("d6", "操作系统", "赵敏", "计科楼-302", 2, 5, 6, all),
            Course("d7", "程序设计实验", "孙浩", "机房-5", 3, 7, 8, all),
            Course("d8", "大学物理", "周涛", "教3-210", 4, 1, 2, all),
            Course("d9", "马克思主义基本原理", "吴静", "教1-105", 5, 1, 2, all),
            Course("d10", "离散数学", "郑凯", "教2-303", 1, 5, 6, all),
            Course("d11", "软件工程", "何丽", "计科楼-205", 1, 7, 8, all),
            Course("d12", "计算机网络", "马超", "计科楼-303", 4, 7, 8, all),
        ),
        lastUpdatedAt = System.currentTimeMillis(),
    )
}
