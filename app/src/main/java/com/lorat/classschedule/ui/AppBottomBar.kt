package com.lorat.classschedule.ui

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun AppBottomBar(
    selectedIndex: Int,
    onSelectTimetable: () -> Unit,
    onSelectSettings: () -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedIndex == 0,
            onClick = onSelectTimetable,
            icon = { Text("📅") },
            label = { Text("课表") },
        )
        NavigationBarItem(
            selected = selectedIndex == 1,
            onClick = onSelectSettings,
            icon = { Text("⚙") },
            label = { Text("设置") },
        )
    }
}
