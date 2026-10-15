package com.lorat.classschedule

import android.app.Application
import com.lorat.classschedule.data.AppLog
import com.lorat.classschedule.data.ScheduleRepository
import com.lorat.classschedule.data.SchoolMemory

class ClassScheduleApp : Application() {
    val repository: ScheduleRepository by lazy { ScheduleRepository(this) }
    val schoolMemory: SchoolMemory by lazy { SchoolMemory(this) }

    override fun onCreate() {
        super.onCreate()
        // 本地日志：环境 + 关键流程 + 崩溃堆栈（只写文件，不联网）
        AppLog.init(this)
        AppLog.i("App", "应用启动")
        runCatching { AppLog.logTimetable(repository.timetable.value) }
    }
}
