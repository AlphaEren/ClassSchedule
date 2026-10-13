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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 关于 — what this app is and where it came from. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
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
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ClassSchedule 课表", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("版本 1.0 · Kotlin + Jetpack Compose", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "功能与观感参考 WakeUp课程表（i.wakeup.fun）实现：周次标题栏、日期栏、左侧时间轴、彩色课程格子、" +
                            "多时间段课程、课表外观自定义、CSV/备份导入导出。",
                        fontSize = 13.sp,
                    )
                }
            }
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("课表识别", fontWeight = FontWeight.SemiBold)
                    Text(
                        "从学校教务系统导入时，App 会在内置浏览器里依次尝试五级识别管线：" +
                            "接口抓包 → 课表格子 → 指定容器 → 表格网格 → 位置聚类；" +
                            "识别不出时会自动点一次「查询/我的课表」并重试。",
                        fontSize = 13.sp,
                    )
                }
            }
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("数据与隐私", fontWeight = FontWeight.SemiBold)
                    Text(
                        "课表、学校配置与外观设置全部以 JSON 保存在 App 私有目录（files/timetable.json），" +
                            "不上传任何服务器；卸载 App 即删除。",
                        fontSize = 13.sp,
                    )
                }
            }
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("开源组件", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Jetpack Compose / Material 3、AndroidX Navigation 3、kotlinx.serialization。" +
                            "图标为自绘，无第三方图标库。",
                        fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
