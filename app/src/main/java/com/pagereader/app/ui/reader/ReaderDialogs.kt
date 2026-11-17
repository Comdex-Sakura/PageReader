package com.pagereader.app.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pagereader.app.data.BookType
import com.pagereader.app.data.Chapter
import com.pagereader.app.data.ReadingMode

/** 目录：列出章节，支持跳转、新建、改名、删除、一键导入 PDF 目录。 */
@Composable
fun ChapterDialog(
    vm: ReaderViewModel,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val chapters = vm.chapters
    val total = vm.pageCount
    var editorTarget by remember { mutableStateOf<Chapter?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("目录") },
        text = {
            Column {
                Row {
                    TextButton(onClick = {
                        editorTarget = null
                        showEditor = true
                    }) { Text("新建章节") }
                    if (vm.book?.type == BookType.PDF) {
                        TextButton(onClick = { vm.importOutline() }) { Text("导入 PDF 目录") }
                    }
                }
                if (vm.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                }
                if (chapters.isEmpty()) {
                    Text(
                        "暂无章节，可新建或从 PDF 导入",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(chapters, key = { it.id }) { ch ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onDismiss(); onJump(ch.startPage) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(ch.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "第 ${ch.startPage + 1} - ${ch.endPage + 1} 页",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    editorTarget = ch
                                    showEditor = true
                                }) { Icon(Icons.Filled.Edit, contentDescription = "编辑") }
                                IconButton(onClick = { vm.deleteChapter(ch) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )

    if (showEditor) {
        ChapterEditorDialog(
            initial = editorTarget,
            totalPages = total,
            onSave = { title, start, end ->
                val t = editorTarget
                if (t == null) vm.addChapter(title, start, end)
                else vm.updateChapter(t, title, start, end)
                showEditor = false
            },
            onDismiss = { showEditor = false }
        )
    }
}

@Composable
private fun ChapterEditorDialog(
    initial: Chapter?,
    totalPages: Int,
    onSave: (String, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var start by remember { mutableStateOf(((initial?.startPage ?: 0) + 1).toString()) }
    var end by remember { mutableStateOf(((initial?.endPage ?: 0) + 1).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建章节" else "编辑章节") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("章节名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it },
                        label = { Text("起始页") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = end,
                        onValueChange = { end = it },
                        label = { Text("结束页") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "页码范围 1 - $totalPages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val last = (totalPages - 1).coerceAtLeast(0)
                val s = start.toIntOrNull()?.minus(1)?.coerceIn(0, last) ?: 0
                val e = end.toIntOrNull()?.minus(1)?.coerceIn(0, last) ?: 0
                val (a, b) = if (s <= e) s to e else e to s
                onSave(title.ifBlank { "未命名章节" }, a, b)
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkSheet(
    vm: ReaderViewModel,
    currentPage: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val bookmarks = vm.bookmarks
    var showAdd by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
            Text("书签", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("把当前页加为书签")
            }
            if (bookmarks.isEmpty()) {
                Text(
                    "暂无书签",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(bookmarks, key = { it.id }) { bm ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onDismiss(); onJump(bm.page) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Bookmark,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    bm.label.ifBlank { "第 ${bm.page + 1} 页" },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    "第 ${bm.page + 1} 页",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { vm.deleteBookmark(bm) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var label by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("添加书签") },
            text = {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addBookmark(currentPage, label)
                    showAdd = false
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    vm: ReaderViewModel,
    onDismiss: () -> Unit
) {
    val isPaged = vm.book?.readingMode == ReadingMode.PAGED
    val bright = if (vm.brightness < 0f) 1f else vm.brightness

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
            Text("阅读设置", style = MaterialTheme.typography.titleLarge)
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

            Spacer(Modifier.height(8.dp))
            Text("亮度", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = bright,
                onValueChange = { vm.updateBrightness(it) },
                valueRange = 0.05f..1f
            )
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
