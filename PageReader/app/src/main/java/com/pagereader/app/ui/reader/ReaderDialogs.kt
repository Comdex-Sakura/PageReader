package com.pagereader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pagereader.app.data.Note
import com.pagereader.app.data.ReadingMode

/** 设置：阅读模式 + 夜间模式。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    vm: ReaderViewModel,
    onDismiss: () -> Unit
) {
    val isPaged = vm.book?.readingMode == ReadingMode.PAGED

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
            Text("设置", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            Text("阅读模式", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = isPaged,
                    onClick = { vm.setReadingMode(ReadingMode.PAGED) },
                    label = { Text("左右翻页") }
                )
                FilterChip(
                    selected = !isPaged,
                    onClick = { vm.setReadingMode(ReadingMode.SCROLL) },
                    label = { Text("上下滚动") }
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DarkMode, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("夜间模式", modifier = Modifier.weight(1f))
                Switch(
                    checked = vm.nightMode,
                    onCheckedChange = { vm.updateNightMode(it) }
                )
            }
        }
    }
}

/** 亮度：滑块 + 跟随系统。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrightnessSheet(
    vm: ReaderViewModel,
    onDismiss: () -> Unit
) {
    val bright = if (vm.brightness < 0f) 1f else vm.brightness

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
            Text("亮度", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BrightnessLow, contentDescription = null)
                Slider(
                    value = bright,
                    onValueChange = { vm.updateBrightness(it) },
                    valueRange = 0.05f..1f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Icon(Icons.Filled.BrightnessHigh, contentDescription = null)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.updateBrightness(-1f) }) { Text("跟随系统") }
                Spacer(Modifier.weight(1f))
                Text(
                    "${(bright * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 笔记编辑：内容、字体、颜色、字号、旋转。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorDialog(
    initial: Note?,
    onSave: (text: String, color: Long, fontSize: Float, rotation: Float, fontFamily: Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var color by remember { mutableLongStateOf(initial?.color ?: 0xFFE53935) }
    var fontSize by remember { mutableFloatStateOf(initial?.fontSize ?: 16f) }
    var rotation by remember { mutableFloatStateOf(initial?.rotation ?: 0f) }
    var fontFamily by remember { mutableIntStateOf(initial?.fontFamily ?: 0) }

    val palette = listOf(
        0xFF000000,
        0xFFE53935,
        0xFF1E88E5,
        0xFF43A047,
        0xFFFB8C00,
        0xFF8E24AA,
        0xFFFFFFFF
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加笔记" else "编辑笔记") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("笔记内容") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                Text("字体", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "默认", 1 to "宋体", 2 to "等宽", 3 to "手写").forEach { (id, name) ->
                        FilterChip(
                            selected = fontFamily == id,
                            onClick = { fontFamily = id },
                            label = { Text(name) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                Text("颜色", style = MaterialTheme.typography.titleSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    palette.forEach { c ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .background(Color(c), CircleShape)
                                .border(
                                    width = if (color == c) 3.dp else 1.dp,
                                    color = if (color == c) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { color = c }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                Text("字号 ${fontSize.toInt()}", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange = 10f..48f
                )

                Text("旋转 ${rotation.toInt()}°", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = rotation,
                    onValueChange = { rotation = it },
                    valueRange = -180f..180f
                )

                if (onDelete != null) {
                    TextButton(onClick = {
                        onDelete()
                        onDismiss()
                    }) {
                        Text("删除这条笔记", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(text.ifBlank { "笔记" }, color, fontSize, rotation, fontFamily)
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
