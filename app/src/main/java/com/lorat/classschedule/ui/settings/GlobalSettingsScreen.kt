@file:OptIn(ExperimentalMaterial3Api::class)

package com.lorat.classschedule.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.lorat.classschedule.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lorat.classschedule.data.ScheduleRepository
import com.lorat.classschedule.data.model.DarkMode
import kotlinx.coroutines.launch

/** 全局设置 — the switches that are not part of one timetable's look. */
@Composable
fun GlobalSettingsScreen(
    repository: ScheduleRepository,
    onBack: () -> Unit,
    onOpenScheduleSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    val timetable by repository.timetable.collectAsStateWithLifecycle()
    val appearance = timetable.appearance
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("全局设置") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsCard("显示") {
                SettingsChoiceRow(
                    title = "显示主题",
                    options = listOf(
                        DarkMode.SYSTEM to "跟随系统",
                        DarkMode.LIGHT to "浅色",
                        DarkMode.DARK to "深色",
                    ),
                    selected = appearance.darkMode,
                    onSelect = { scope.launch { repository.updateAppearance(appearance.copy(darkMode = it)) } },
                )
                SettingsSwitchRow(
                    title = "显示周六周日",
                    checked = appearance.showWeekend,
                    onChange = { scope.launch { repository.updateAppearance(appearance.copy(showWeekend = it)) } },
                )
                SettingsSliderRow("课表下方增加留白", appearance.extraBottomSpace, 0..200, " dp") {
                    scope.launch { repository.updateAppearance(appearance.copy(extraBottomSpace = it)) }
                }
                SettingsSwitchRow(
                    title = "显示空视图图片",
                    subtitle = "课表没有课程时显示占位提示",
                    checked = appearance.showEmptyImage,
                    onChange = { scope.launch { repository.updateAppearance(appearance.copy(showEmptyImage = it)) } },
                )
            }

            SettingsCard("课表") {
                SettingsNavRow(
                    title = "课表设置",
                    subtitle = "课表数据与课表外观",
                    onClick = onOpenScheduleSettings,
                )
                SettingsSwitchRow(
                    title = "自动检查更新",
                    subtitle = "打开 App 时静默刷新一次课表（需要教务登录态）",
                    checked = appearance.autoCheckUpdate,
                    onChange = { scope.launch { repository.updateAppearance(appearance.copy(autoCheckUpdate = it)) } },
                )
            }

            SettingsCard("其它") {
                SettingsChoiceRow(
                    title = "语言",
                    options = listOf(
                        0 to "跟随系统",
                        1 to "简体中文",
                        2 to "English",
                    ),
                    selected = appearance.language,
                    onSelect = { scope.launch { repository.updateAppearance(appearance.copy(language = it)) } },
                )
                SettingsNavRow(title = "关于", onClick = onAbout)
            }

            Text(
                "所有数据只保存在本机，卸载 App 即删除。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
