package com.lorat.classschedule.ui.timetable

import androidx.lifecycle.ViewModel
import com.lorat.classschedule.data.ScheduleRepository
import com.lorat.classschedule.data.model.Timetable
import kotlinx.coroutines.flow.StateFlow

class TimetableViewModel(private val repository: ScheduleRepository) : ViewModel() {

    val timetable: StateFlow<Timetable> = repository.timetable

    private var autoUpdateHandled = false

    /**
     * True if we should attempt a silent background refresh: data is missing or older than
     * [staleMillis]. Only fires once per screen instance so it does not loop.
     */
    fun shouldAutoUpdate(staleMillis: Long = 6 * 60 * 60 * 1000L): Boolean {
        if (autoUpdateHandled) return false
        val current = repository.timetable.value
        if (current.school == null || current.school.scheduleUrl.isBlank()) return false
        autoUpdateHandled = true
        val age = System.currentTimeMillis() - current.lastUpdatedAt
        return current.courses.isEmpty() || age > staleMillis
    }
}
