package com.lorat.classschedule.ui.course

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lorat.classschedule.data.CourseGroup
import com.lorat.classschedule.data.dayName
import com.lorat.classschedule.data.hasWeekNote
import com.lorat.classschedule.data.model.Course
import com.lorat.classschedule.data.model.Timetable
import com.lorat.classschedule.data.slotNote
import com.lorat.classschedule.data.timeText
import com.lorat.classschedule.data.weeksText
import com.lorat.classschedule.data.weekNote
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CourseDetailSheet(
    group: CourseGroup,
    slot: Course,
    timetable: Timetable,
    week: Int,
    isCurrentWeek: Boolean,
    accent: Color,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onSaveNote: (note: String, onlyThisWeek: Boolean) -> Unit,
    onDelete: (DeleteScope) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var choosingDelete by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val totalWeeks = maxOf(1, timetable.totalWeeks)

    // 备注：点一下就地编辑；收键盘 / 关闭面板时自动保存。
    // 「仅本周」和「所有周」是两份独立的备注，切范围时各自保存 / 加载各自的内容。
    var editingNote by remember { mutableStateOf(false) }
    var noteValue by remember { mutableStateOf(TextFieldValue("")) }
    var scopeBefore by remember { mutableStateOf("") }
    var noteWeekOnly by remember(group.key, week, slot.dayOfWeek, slot.startSection, slot.endSection) {
        mutableStateOf(group.hasWeekNote(week, slot.dayOfWeek, slot.startSection, slot.endSection))
    }
    var noteAll by remember(group.key, slot.dayOfWeek, slot.startSection, slot.endSection) {
        mutableStateOf(group.slotNote(slot.dayOfWeek, slot.startSection, slot.endSection))
    }
    var noteWeek by remember(group.key, week, slot.dayOfWeek, slot.startSection, slot.endSection) {
        mutableStateOf(group.weekNote(week, slot.dayOfWeek, slot.startSection, slot.endSection) ?: "")
    }
    var noteFocusedOnce by remember { mutableStateOf(false) }
    var imeWasVisible by remember { mutableStateOf(false) }
    val noteFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    fun valueOfScope(onlyWeek: Boolean): String = if (onlyWeek) noteWeek else noteAll

    fun saveScope(onlyWeek: Boolean, text: String) {
        if (onlyWeek) noteWeek = text else noteAll = text
        onSaveNote(text, onlyWeek)
    }

    fun saveCurrentScope() {
        val trimmed = noteValue.text.trim()
        if (trimmed != scopeBefore) {
            saveScope(noteWeekOnly, trimmed)
            scopeBefore = trimmed
        }
    }

    fun commitNote() {
        if (!editingNote) return
        saveCurrentScope()
        editingNote = false
        noteFocusedOnce = false
    }

    fun startEditing(onlyWeek: Boolean) {
        noteWeekOnly = onlyWeek
        val current = valueOfScope(onlyWeek)
        noteValue = TextFieldValue(current, selection = TextRange(current.length))
        scopeBefore = current
        noteFocusedOnce = false
        imeWasVisible = false
        editingNote = true
    }

    /**
     * 点「仅本周 / 所有周」：正在编辑时先把当前内容存回旧范围，再切到新范围并载入它的内容；
     * 只读时直接进入编辑。这样两份备注可以分别写、互不覆盖。
     */
    fun chooseScope(onlyWeek: Boolean) {
        if (!editingNote) {
            startEditing(onlyWeek)
            return
        }
        if (onlyWeek == noteWeekOnly) return
        saveCurrentScope()
        noteWeekOnly = onlyWeek
        val next = valueOfScope(onlyWeek)
        noteValue = TextFieldValue(next, selection = TextRange(next.length))
        scopeBefore = next
    }

    LaunchedEffect(editingNote) { if (editingNote) noteFocus.requestFocus() }

    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            imeWasVisible = true
        } else if (imeWasVisible) {
            imeWasVisible = false
            commitNote()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            commitNote()
            onDismiss()
        },
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
                    if (editingNote) {
                        BasicTextField(
                            value = noteValue,
                            onValueChange = { value ->
                                // 备注只允许单行：换行直接丢掉
                                val clean = value.text.replace("\n", "").replace("\r", "").take(60)
                                noteValue = if (clean == value.text) value else value.copy(text = clean)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(noteFocus)
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        noteFocusedOnce = true
                                    } else if (noteFocusedOnce) {
                                        noteFocusedOnce = false
                                        commitNote()
                                    }
                                }
                                .padding(vertical = 2.dp),
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    commitNote()
                                    focusManager.clearFocus()
                                },
                            ),
                            decorationBox = { inner ->
                                Box {
                                    if (noteValue.text.isEmpty()) {
                                        Text("备注（可不填）", fontSize = 15.sp, color = muted)
                                    }
                                    inner()
                                }
                            },
                        )
                    } else {
                        val shown = valueOfScope(noteWeekOnly)
                        Text(
                            shown.ifBlank { "备注（可不填）" },
                            fontSize = 15.sp,
                            color = if (shown.isBlank()) muted else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { startEditing(noteWeekOnly) },
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    ScopeChip(
                        label = if (isCurrentWeek) "仅本周" else "仅第 $week 周",
                        selected = noteWeekOnly,
                        onClick = { chooseScope(true) },
                    )
                    Spacer(Modifier.width(2.dp))
                    ScopeChip(
                        label = "所有周",
                        selected = !noteWeekOnly,
                        onClick = { chooseScope(false) },
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { commitNote(); onEdit() },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text("编辑", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                    ActionIconButton(onClick = { commitNote(); onDuplicate() }) {
                        CopyIcon(tint = MaterialTheme.colorScheme.onSurface)
                    }
                    ActionIconButton(onClick = { commitNote(); choosingDelete = true }) {
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
private fun ScopeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            // 用 pointerInput 而不是 clickable：点胶囊不会把焦点从备注输入框抢走
            .pointerInput(onClick) { detectTapGestures { onClick() } }
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) onSurface else muted,
            maxLines = 1,
        )
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
