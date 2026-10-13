package com.lorat.classschedule.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.lorat.classschedule.data.ScheduleRepository
import com.lorat.classschedule.data.csvOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Reads a text file the user picked. */
fun Context.readTextFromUri(uri: Uri): String? = runCatching {
    contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
}.getOrNull()

/** Writes text to a document the user picked. */
fun Context.writeTextToUri(uri: Uri, text: String): Boolean = runCatching {
    contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    true
}.getOrElse { false }

fun suggestedFileName(extension: String): String = "课表_${LocalDate.now()}.$extension"

/**
 * "导出课表" via the system file picker. [onResult] receives a user-facing message.
 * [asCsv] switches between the full backup (JSON) and the shareable CSV.
 */
@Composable
fun rememberTimetableExporter(
    repository: ScheduleRepository,
    onResult: (String) -> Unit,
): (Boolean) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var asCsv = false
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(if (asCsv) "text/csv" else "application/json"),
    ) { uri ->
        if (uri == null) {
            onResult("已取消导出")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val timetable = repository.timetable.value
            val text = if (asCsv) {
                csvOf(timetable.courses, timetable.totalWeeks)
            } else {
                repository.encodeTimetable(timetable)
            }
            val ok = withContext(Dispatchers.IO) { context.writeTextToUri(uri, text) }
            onResult(if (ok) "已导出课表文件" else "导出失败")
        }
    }
    return remember(launcher) {
        { csv: Boolean ->
            asCsv = csv
            launcher.launch(suggestedFileName(if (csv) "csv" else "json"))
        }
    }
}
