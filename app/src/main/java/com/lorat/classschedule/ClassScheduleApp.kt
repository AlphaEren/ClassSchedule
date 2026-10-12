package com.lorat.classschedule

import android.app.Application
import com.lorat.classschedule.data.ScheduleRepository
import com.lorat.classschedule.data.SchoolMemory

class ClassScheduleApp : Application() {
    val repository: ScheduleRepository by lazy { ScheduleRepository(this) }
    val schoolMemory: SchoolMemory by lazy { SchoolMemory(this) }
}
