@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.lorat.classschedule.ui.imports

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.lorat.classschedule.data.OcrLine
import com.lorat.classschedule.data.ScheduleRepository
import com.lorat.classschedule.data.coursesFromOcr
import com.lorat.classschedule.data.dayName
import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.sectionsText
import kotlinx.coroutines.launch

/** 课表截图字普遍很小，先放大到长边约 2000px 再喂给 OCR，识别率高一截。 */
private fun upscaleForOcr(bitmap: Bitmap): Bitmap {
    val maxSide = maxOf(bitmap.width, bitmap.height)
    // 注意用 ceil：小图必须真的放大（(2000/1114).toInt() = 1 等于没放大）
    val factor = kotlin.math.ceil(2000.0 / maxSide).toInt().coerceIn(1, 3)
    if (factor <= 1) return bitmap
    return runCatching {
        Bitmap.createScaledBitmap(bitmap, bitmap.width * factor, bitmap.height * factor, true)
    }.getOrDefault(bitmap)
}

/** 从图片识别课表：本机离线 OCR（ML Kit 中文），不出网。 */
@Composable
fun ImageImportScreen(    repository: ScheduleRepository,
    onBack: () -> Unit,
) {
    val timetable by repository.timetable.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("选一张课表截图，App 在本机离线识别，图片不会上传。") }
    var parsed by remember { mutableStateOf<List<Course>>(emptyList()) }
    var rawLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var showRaw by remember { mutableStateOf(false) }
    var pickedName by remember { mutableStateOf("") }

    fun recognize(uri: Uri) {
        busy = true
        status = "正在识别…"
        pickedName = uri.lastPathSegment?.substringAfterLast('/') ?: "已选择图片"
        val bitmap = runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        if (bitmap == null) {
            busy = false
            status = "打不开这张图片"
            return
        }
        val software = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false).also { bitmap.recycle() }
        }
        // 小图先放大：课表截图字很小，放大后识别率明显更高
        val forOcr = upscaleForOcr(software)
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        recognizer.process(InputImage.fromBitmap(forOcr, 0))
            .addOnSuccessListener { text ->
                // 用 element（词级）而不是 line：教务课表里一排格子经常被 OCR 读成一整行，
                // 词级坐标才有各自的框，能正确落回每一格。
                val lines = text.textBlocks.flatMap { block -> block.lines }.flatMap { line ->
                    val elements = line.elements
                    if (elements.isEmpty()) {
                        listOfNotNull(line.boundingBox?.let { OcrLine(line.text, it.left, it.top, it.right, it.bottom) })
                    } else {
                        elements.mapNotNull { element ->
                            element.boundingBox?.let { OcrLine(element.text, it.left, it.top, it.right, it.bottom) }
                        }
                    }
                }
                val courses = coursesFromOcr(lines, timetable.totalWeeks, forOcr.width)
                parsed = courses
                rawLines = lines.map { it.text.trim() }.filter { it.isNotEmpty() }
                busy = false
                status = if (courses.isEmpty()) {
                    "没识别出课表：截图里要能看到「周一..周日」表头和节次刻度"
                } else {
                    "识别到 ${courses.size} 门课，确认无误再导入"
                }
                if (forOcr != software) forOcr.recycle()
                recognizer.close()
            }
            .addOnFailureListener { error ->
                busy = false
                status = "识别失败：${error.message ?: "未知错误"}"
                if (forOcr != software) forOcr.recycle()
                recognizer.close()
            }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) recognize(uri)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("从图片识别") },
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
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("选择课表截图", fontWeight = FontWeight.SemiBold)
                    Text(
                        "支持教务系统网页截图、别人分享的课表图片。识别在本机完成，离线可用。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "实验功能：识别结果可能有偏差，导入前请核对下面的列表。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(
                        onClick = { picker.launch(arrayOf("image/*")) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (busy) "识别中…" else "选择图片") }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (pickedName.isNotBlank()) {
                        Text(pickedName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(status, fontSize = 12.sp)
                }
            }

            if (rawLines.isNotEmpty()) {
                Card {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().combinedClickable { showRaw = !showRaw },
                        ) {
                            Text("识别到的原始文字（${rawLines.size} 行）", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(if (showRaw) "收起" else "展开", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        if (showRaw) {
                            Text(rawLines.joinToString("\n"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (parsed.isNotEmpty()) {
                Card {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("识别结果（${parsed.size}）", fontWeight = FontWeight.SemiBold)
                        parsed.forEach { course ->
                            Text(
                                "${dayName(course.dayOfWeek)} ${sectionsText(course.startSection, course.endSection)}  " +
                                    course.name +
                                    if (course.teacher.isNotBlank()) "  ${course.teacher}" else "" +
                                        if (course.location.isNotBlank()) "  @${course.location}" else "",
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                repository.applyImport(courses = timetable.courses + parsed)
                                snackbar.showSnackbar("已追加 ${parsed.size} 个课程格子")
                                onBack()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("追加") }
                    Button(
                        onClick = {
                            scope.launch {
                                repository.applyImport(courses = parsed)
                                snackbar.showSnackbar("已导入 ${parsed.size} 个课程格子")
                                onBack()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("覆盖导入") }
                }
                Text(
                    "「追加」保留现有课程，只补充识别到的；「覆盖导入」用识别结果替换全部课程。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
