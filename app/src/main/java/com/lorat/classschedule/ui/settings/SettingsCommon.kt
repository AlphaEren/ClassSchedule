@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.lorat.classschedule.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lorat.classschedule.ui.argbOrNull
import com.lorat.classschedule.ui.toArgbLong
import kotlin.math.roundToInt

/** A titled block of settings rows. */
@Composable
fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            content()
        }
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    subtitle: String = "",
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable { onChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            if (subtitle.isNotBlank()) {
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingsNavRow(title: String, value: String = "", subtitle: String = "", onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            if (subtitle.isNotBlank()) {
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (value.isNotBlank()) {
            Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("  ›", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsSliderRow(
    title: String,
    value: Int,
    range: IntRange,
    suffix: String = "",
    onChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("$value$suffix", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}

@Composable
fun SettingsChoiceRow(
    title: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                val on = value == selected
                Text(
                    label,
                    fontSize = 13.sp,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (on) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            },
                        )
                        .combinedClickable { onSelect(value) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
fun SettingsColorRow(title: String, value: Long, onPick: (Long) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable { showDialog = true }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (value == 0L) {
            Text("跟随默认", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
        }
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(argbOrNull(value) ?: MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape),
        )
    }
    if (showDialog) {
        ColorPickerDialog(
            value = value,
            onPick = { onPick(it); showDialog = false },
            onDismiss = { showDialog = false },
        )
    }
}

/** Preset swatches — a full HSV picker would be overkill for this app. */
private val PickerColors = listOf(
    Color(0xFF000000), Color(0xFF5F6368), Color(0xFF9AA0A6), Color(0xFFFFFFFF),
    Color(0xFFEF6C6C), Color(0xFFF2A03D), Color(0xFF8FA31E), Color(0xFF57BB8A),
    Color(0xFF4FB3C7), Color(0xFF5B8DEF), Color(0xFF9B6BDF), Color(0xFFE06CA8),
    Color(0xFFB71C1C), Color(0xFFE65100), Color(0xFF33691E), Color(0xFF00695C),
    Color(0xFF0D47A1), Color(0xFF4A148C), Color(0xFF880E4F), Color(0xFF3E2723),
)

@Composable
private fun ColorPickerDialog(value: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择颜色") },
        text = {
            Column {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(PickerColors) { color ->
                        val packed = color.toArgbLong()
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (packed == value) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    } else {
                                        Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape)
                                    },
                                )
                                .combinedClickable { onPick(packed) },
                        )
                    }
                }
                TextButton(onClick = { onPick(0L) }) { Text("恢复默认（跟随主题）") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
