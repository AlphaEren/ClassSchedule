package com.lorat.classschedule.ui.school

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import com.lorat.classschedule.i18n.Lang
import com.lorat.classschedule.i18n.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lorat.classschedule.ClassScheduleApp
import com.lorat.classschedule.data.ScheduleRepository
import com.lorat.classschedule.data.SchoolDraft
import com.lorat.classschedule.data.TimetableExtractor
import com.lorat.classschedule.data.model.School
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private val PresetHints = mapOf(
    TimetableExtractor.PRESET_AUTO to "推荐的默认值。会自动判断你用的是哪种教务系统并选择对应识别方式，不确定就选它。",
    TimetableExtractor.PRESET_GENERIC to "通用表格识别，适用于绝大多数把课表画成表格的系统。",
    TimetableExtractor.PRESET_ZF to "页面带「正方」字样，或网址含 zfsoft、界面是蓝色导航条时选它。",
    TimetableExtractor.PRESET_JWGLXT to "新版正方教务，网址通常含 jwglxt，课表页叫「学生课表查询」。",
    TimetableExtractor.PRESET_QZ to "页面带「强智科技」字样或网址含 qz，课表是彩色方块网格时选它。",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSchoolScreen(
    repository: ScheduleRepository,
    onBack: () -> Unit,
    onSchoolSaved: () -> Unit,
) {
    val context = LocalContext.current
    val memory = remember { (context.applicationContext as ClassScheduleApp).schoolMemory }
    val currentSchool = remember { repository.timetable.value.school }
    val draft = remember { memory.lastDraft() }
    val recents = remember { memory.recents() }

    var name by rememberSaveable { mutableStateOf(currentSchool?.name ?: draft?.name.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(currentSchool?.scheduleUrl ?: draft?.url.orEmpty()) }
    var preset by rememberSaveable {
        mutableStateOf(currentSchool?.parserPreset ?: draft?.preset ?: TimetableExtractor.PRESET_AUTO)
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(name, url, preset) {
        delay(400)
        memory.saveDraft(SchoolDraft(name.trim(), url.trim(), preset))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (currentSchool != null) "更换学校" else "添加学校") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←", fontSize = 20.sp) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "填入学校教务系统的课表页地址。下一步会打开该页面，你登录并进入课表后，App 会自动识别课程。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("学校名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("课表页网址") },
                placeholder = { Text("https://jw.example.edu/schedule") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (recents.isNotEmpty()) {
                Text("最近使用", fontWeight = FontWeight.SemiBold)
                recents.forEach { recent ->
                    OutlinedButton(
                        onClick = {
                            name = recent.name
                            url = recent.url
                            preset = recent.preset
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            (recent.name.ifBlank { "未命名" }) + "  ·  " + recent.url,
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text("解析规则", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                Lang.tr("不同学校的教务系统页面结构不一样。不确定选哪个时保持「自动检测」即可：")
                    + Lang.tr("App 会先尝试按表格识别，再尝试按课程方块的位置识别。"),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TimetableExtractor.presets.forEach { (key, label) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = preset == key, onClick = { preset = key }),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = preset == key, onClick = { preset = key })
                        Text(label, fontWeight = if (preset == key) FontWeight.SemiBold else FontWeight.Normal)
                    }
                    PresetHints[key]?.let { hint ->
                        Text(
                            hint,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    val school = School(
                        id = currentSchool?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        scheduleUrl = url.trim(),
                        parserPreset = preset,
                        refreshUrl = if (currentSchool?.scheduleUrl == url.trim()) currentSchool.refreshUrl else "",
                    )
                    memory.addRecent(SchoolDraft(school.name, school.scheduleUrl, school.parserPreset))
                    scope.launch {
                        repository.setSchool(school)
                        onSchoolSaved()
                    }
                },
                enabled = name.isNotBlank() && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存并去登录") }
        }
    }
}
