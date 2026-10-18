package com.lorat.classschedule.ui.course

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lorat.classschedule.data.CourseGroup
import com.lorat.classschedule.data.dayName
import com.lorat.classschedule.data.model.Timetable
import com.lorat.classschedule.data.progressText
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

/**
 * 点击课程格子后弹出的详情面板（WakeUp 那种底部卡片）：
 * 左侧自绘小图标 + 一行一项，底部「编辑 / 复制 / 删除」，删除可选「仅本周」或「所有周」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailSheet(
    group: CourseGroup,
    timetable: Timetable,
    week: Int,
    currentWeek: Int,
    accent: Color,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: (onlyThisWeek: Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var onlyThisWeek by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val totalWeeks = maxOf(1, timetable.totalWeeks)
    // 这门课这一周到底上不上：不上时「仅本周」没有意义，置灰
    val runsThisWeek = group.slots.any { it.weeks.isEmpty() || week in it.weeks }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
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

            // 周次 + 进度
            DetailLine(icon = { CalendarIcon(tint = muted) }) {
                Column {
                    Text(
                        group.slots.map { weeksText(it.weeks, totalWeeks) }.distinct().joinToString(" / "),
                        fontSize = 15.sp,
                    )
                    Text(
                        group.progressText(currentWeek, totalWeeks),
                        fontSize = 11.sp,
                        color = muted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            // 每个时间段：第 X - Y 节 + 上下课时间 + 周几
            group.slots.forEach { slot ->
                DetailLine(icon = { ClockIcon(tint = muted) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Lang.tr("第 ${slot.startSection} - ${slot.endSection} 节"), fontSize = 15.sp)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            timeText(slot, timetable.sectionTimes).replace("-", " - "),
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(Lang.tr(dayName(slot.dayOfWeek)), fontSize = 12.sp, color = muted)
                    }
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

            // 备注 + 删除范围（仅本周 / 所有周）
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
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(2.dp),
                ) {
                    ScopeChip(
                        label = if (week == currentWeek) "仅本周" else "仅第 $week 周",
                        selected = onlyThisWeek && runsThisWeek,
                        enabled = runsThisWeek,
                        onClick = { onlyThisWeek = true },
                    )
                    ScopeChip(
                        label = "所有周",
                        selected = !onlyThisWeek,
                        onClick = { onlyThisWeek = false },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
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
                ActionIconButton(onClick = { onDelete(onlyThisWeek && runsThisWeek) }) {
                    TrashIcon(tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
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
private fun ScopeChip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) onSurface else muted.copy(alpha = if (enabled) 1f else 0.35f),
            maxLines = 1,
        )
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
