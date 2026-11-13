package com.pagereader.app.ui.reader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pagereader.app.data.BookType
import com.pagereader.app.data.Chapter
import com.pagereader.app.data.ReadingMode
import com.pagereader.app.data.Region
import com.pagereader.app.reader.TocFileIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 目录：章节列表（书页码）+ 分区入口 + JSON 导入导出。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterDialog(
    vm: ReaderViewModel,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val chapters = vm.chapters
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editorTarget by remember { mutableStateOf<Chapter?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showRegions by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) { TocFileIo.readText(context, uri) }
                if (text == null) vm.showMessage("读取 JSON 失败")
                else vm.importTocJson(text)
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    TocFileIo.writeText(context, uri, vm.exportTocJson())
                }
                vm.showMessage(if (ok) "已导出目录 JSON" else "导出失败")
            }
        }
    }

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
                        TextButton(onClick = { vm.importOutline() }) { Text("导入PDF目录") }
                    }
                }
                Row {
                    TextButton(onClick = { showRegions = true }) { Text("分区") }
                    TextButton(onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }) { Text("导入JSON") }
                    TextButton(onClick = { exportLauncher.launch("toc.json") }) { Text("导出JSON") }
                }
                if (vm.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                }
                if (chapters.isEmpty()) {
                    Text(
                        "暂无章节，可新建、从 PDF 导入，或导入 JSON",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(chapters, key = { it.id }) { ch ->
                            val end = vm.chapterEndBookPage(ch)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val pdf = vm.bookToPdfPage(ch.bookPage)
                                        if (pdf != null) {
                                            onDismiss()
                                            onJump(pdf)
                                        } else {
                                            vm.showMessage("该章节书页不在任何编号分区内")
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(ch.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (end > ch.bookPage) "第 ${ch.bookPage} - $end 页"
                                        else "第 ${ch.bookPage} 页",
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
        val cur = vm.book?.currentPage ?: 0
        val defaultBookPage = vm.pdfToBookPage(cur) ?: (cur + 1)
        ChapterEditorDialog(
            initial = editorTarget,
            defaultBookPage = defaultBookPage,
            onSave = { title, bookPage ->
                val t = editorTarget
                if (t == null) vm.addChapter(title, bookPage)
                else vm.updateChapter(t, title, bookPage)
                showEditor = false
            },
            onDismiss = { showEditor = false }
        )
    }

    if (showRegions) {
        RegionDialog(vm, onDismiss = { showRegions = false })
    }
}

@Composable
private fun ChapterEditorDialog(
    initial: Chapter?,
    defaultBookPage: Int,
    onSave: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var bookPageText by remember {
        mutableStateOf((initial?.bookPage ?: defaultBookPage).toString())
    }

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
                OutlinedTextField(
                    value = bookPageText,
                    onValueChange = { bookPageText = it },
                    label = { Text("起始书页") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "只填起始书页；结束页自动取下一章起始页前（或分区末页）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val bp = bookPageText.toIntOrNull() ?: defaultBookPage
                onSave(title.ifBlank { "未命名章节" }, bp.coerceAtLeast(1))
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 分区管理：定义 PDF 页与书页码的对应关系。 */
@Composable
private fun RegionDialog(
    vm: ReaderViewModel,
    onDismiss: () -> Unit
) {
    var editorTarget by remember { mutableStateOf<Region?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val sortedRegions = vm.regions.sortedBy { it.pdfStart }
    val resolved = vm.resolvedRegions

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分区") },
        text = {
            Column {
                TextButton(onClick = {
                    editorTarget = null
                    showEditor = true
                }) { Text("新建分区") }
                if (sortedRegions.isEmpty()) {
                    Text(
                        "还没有分区。整本书当前按「书页 = PDF页 + 1」处理。\n" +
                            "添加分区（如「正文」从 PDF 第 15 页 = 书第 1 页）即可建立偏移。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        itemsIndexed(sortedRegions, key = { _, r -> r.id }) { idx, r ->
                            val rr = resolved.getOrNull(idx)
                            val desc = if (rr != null) {
                                "PDF 第 ${rr.pdfStart + 1} - ${rr.pdfEnd + 1} 页" +
                                    (rr.bookStart?.let { " · 书第 $it 页起" } ?: " · 不编号")
                            } else {
                                "PDF 第 ${r.pdfStart + 1} 页起"
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    editorTarget = r
                                    showEditor = true
                                }) { Icon(Icons.Filled.Edit, contentDescription = "编辑") }
                                IconButton(onClick = { vm.deleteRegion(r) }) {
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
        RegionEditorDialog(
            initial = editorTarget,
            onSave = { name, pdfStart, bookStart ->
                val t = editorTarget
                if (t == null) vm.addRegion(name, pdfStart, bookStart)
                else vm.updateRegion(t, name, pdfStart, bookStart)
                showEditor = false
            },
            onDismiss = { showEditor = false }
        )
    }
}

@Composable
private fun RegionEditorDialog(
    initial: Region?,
    onSave: (String, Int, Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var pdfStartText by remember {
        mutableStateOf(((initial?.pdfStart ?: 0) + 1).toString())
    }
    var bookStartText by remember { mutableStateOf(initial?.bookStart?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建分区" else "编辑分区") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分区名（如 正文）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pdfStartText,
                    onValueChange = { pdfStartText = it },
                    label = { Text("PDF 起始页（1 起）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = bookStartText,
                    onValueChange = { bookStartText = it },
                    label = { Text("该书页码起点（留空=不编号）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "例：正文从 PDF 第 15 页开始、对应书第 1 页 → 填 15 和 1。\n" +
                        "封面/目录等留空表示不编号，不参与章节换算。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val pdfStart = ((pdfStartText.toIntOrNull() ?: 1) - 1).coerceAtLeast(0)
                val bookStart = bookStartText.trim().toIntOrNull()?.takeIf { it > 0 }
                onSave(name.ifBlank { "未命名分区" }, pdfStart, bookStart)
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 书签：显示所属章节名 + 书页码。 */
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
                        val chapterName = vm.chapterTitleOfPdf(bm.pdfPage)
                        val bookPage = vm.pdfToBookPage(bm.pdfPage)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onDismiss(); onJump(bm.pdfPage) }
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
                                    chapterName ?: "未分章",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                val pageText = if (bookPage != null) "第 $bookPage 页"
                                else "PDF 第 ${bm.pdfPage + 1} 页"
                                Text(
                                    if (bm.label.isNotBlank()) "$pageText · ${bm.label}" else pageText,
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
