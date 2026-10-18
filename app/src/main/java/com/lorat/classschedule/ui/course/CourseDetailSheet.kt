package com.lorat.classschedule.ui.course

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lorat.classschedule.data.CourseGroup
import com.lorat.classschedule.data.dayName
import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.model.Timetable
import com.lorat.classschedule.data.timeText
import com.lorat.classschedule.data.weeksText
import com.lorat.classschedule.i18n.Lang
import com.lorat.classschedule.i18n.Text
import com.lorat.classschedule.ui.icons.CalendarIcon
import com.lorat.classschedule.ui.icons.ClockIcon
import com.lorat.classschedule.ui.icons.CopyIcon
import com.lorat.classschedule.ui.icons.LocationIcon
import com.lorat.classschedule.ui.icons.NoteIcon
import com.lorat.classschedule.ui.icons.PersonIcon
import com.lorat.classschedule.ui.icons.TrashIcon

/** 删除范围：这一周 / 这个时间段的所有周 / 整门课。 */
enum class DeleteScope { WEEK, SLOT, COURSE }

/**
 * 点击课程格子后弹出的详情面板（WakeUp 那种底部卡片）。
 * 只显示「点中的这一节课」，不是整门课的所有时间段；点垃圾桶后原地切成删除范围选择。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailSheet(
    group: CourseGroup,
    slot: Course,
    timetable: Timetable,
    week: Int,
    accent: Color,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: (DeleteScope) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var choosingDelete by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val totalWeeks = maxOf(1, timetable.totalWeeks)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        if (choosingDelete) {
            DeleteRangeContent(
                week = week,
                day = slot.dayOfWeek,
                onCancel = { choosingDelete = false },
                onPick = onDelete,
            )
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            ) {
                // 标题：课程色的小竖条 + 课程名
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(22.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accent),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        group.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 26.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(Modifier.height(2.dp))

                // 只显示点中的这一次课
                DetailLine(icon = { CalendarIcon(tint = muted) }) {
                    Text(weeksText(slot.weeks, totalWeeks), fontSize = 15.sp)
                }
                DetailLine(icon = { ClockIcon(tint = muted) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Lang.tr("第 ${slot.startSection} - ${slot.endSection} 节"), fontSize = 15.sp)
                        Spacer(Modifier.width(14.dp))
                        Text(timeText(slot, timetable.sectionTimes).replace("-", " - "), fontSize = 15.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(Lang.tr(dayName(slot.dayOfWeek)), fontSize = 12.sp, color = muted)
                    }
                }
                if (group.location.isNotBlank()) {
                    DetailLine(icon = { LocationIcon(tint = muted) }) {
                        Text(group.location, fontSize = 15.sp)
                    }
                }
                if (group.teacher.isNotBlank() || group.credit.isNotBlank()) {
                    DetailLine(icon = { PersonIcon(tint = muted) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (group.teacher.isNotBlank()) Text(group.teacher, fontSize = 15.sp)
                            if (group.credit.isNotBlank()) {
                                Spacer(Modifier.width(10.dp))
                                Text(Lang.tr("学分 ${group.credit}"), fontSize = 12.sp, color = muted)
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { NoteIcon(tint = muted) }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        group.note.ifBlank { "备注（可不填）" },
                        fontSize = 15.sp,
                        color = if (group.note.isBlank()) muted else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text("编辑", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                    ActionIconButton(onClick = onDuplicate) { CopyIcon(tint = MaterialTheme.colorScheme.onSurface) }
                    ActionIconButton(onClick = { choosingDelete = true }) {
                        TrashIcon(tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

/** 删除范围选择：仅这一周 / 这个时间段的所有周 / 整门课。 */
@Composable
private fun DeleteRangeContent(
    week: Int,
    day: Int,
    onCancel: () -> Unit,
    onPick: (DeleteScope) -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Text(
            "选择删除范围，请三思 🥺",
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
            color = muted,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
        DeleteOption(Lang.tr("仅第 $week 周${dayName(day)}的这节课")) { onPick(DeleteScope.WEEK) }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
        DeleteOption(Lang.tr("全部${dayName(day)}同老师同地点的这节课")) { onPick(DeleteScope.SLOT) }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
        DeleteOption("这门课程的全部时间段") { onPick(DeleteScope.COURSE) }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .clickable(onClick = onCancel)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("取消", fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun DeleteOption(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DetailLine(icon: @Composable () -> Unit, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun ActionIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
