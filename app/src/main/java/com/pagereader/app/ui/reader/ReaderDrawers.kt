package com.pagereader.app.ui.reader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pagereader.app.data.Bookmark
import com.pagereader.app.data.BookType
import com.pagereader.app.data.Chapter
import com.pagereader.app.data.Region
import com.pagereader.app.reader.PageMap
import com.pagereader.app.reader.TocFileIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 左侧抽屉容器：全高、约 3/4 宽；点右侧遮罩关闭。各面板自带标题栏。 */
@Composable
fun SideDrawer(
    onClose: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { onClose() }
        )
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.75f)
                .align(Alignment.CenterStart),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                content(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DrawerHeader(
    title: String,
    onClose: () -> Unit,
    actions: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.weight(1f))
        if (actions != null) {
            actions()
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "关闭")
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/** 目录面板：标题栏放菜单（新建/分区/导入导出），长按章节弹出编辑/删除。 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TocPanel(
    vm: ReaderViewModel,
    currentPdfPage: Int,
    onJump: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chapters = vm.chapters
    val currentBookPage = vm.pdfToBookPage(currentPdfPage)
    val startIndex = if (currentBookPage == null) {
        0
    } else {
        val idx = chapters.indexOfLast { it.bookPage <= currentBookPage }
        if (idx >= 0) idx else 0
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showMenu by remember { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<Chapter?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showRegions by remember { mutableStateOf(false) }
    var contextChapter by remember { mutableStateOf<Chapter?>(null) }
    var pendingDeleteChapter by remember { mutableStateOf<Chapter?>(null) }

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

    // 预计算每章结束页，避免每个列表项重复排序（快速滚动卡顿的关键）
    val endMap = remember(chapters, vm.regions, vm.book?.totalPages) {
        val sorted = chapters.sortedBy { it.bookPage }
        val resolved = vm.resolvedRegions
        val m = HashMap<Long, Int>(sorted.size)
        sorted.forEachIndexed { i, ch ->
            val next = sorted.getOrNull(i + 1)?.bookPage
            m[ch.id] = PageMap.chapterEnd(resolved, ch.bookPage, next)
        }
        m
    }

    Column(modifier.fillMaxSize()) {
        DrawerHeader(title = "目录", onClose = onClose) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "菜单")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("新建章节") },
                        onClick = {
                            showMenu = false
                            editorTarget = null
                            showEditor = true
                        }
                    )
                    if (vm.book?.type == BookType.PDF) {
                        DropdownMenuItem(
                            text = { Text("导入 PDF 目录") },
                            onClick = {
                                showMenu = false
                                vm.importOutline()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("分区设置") },
                        onClick = {
                            showMenu = false
                            showRegions = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("导入 JSON") },
                        onClick = {
                            showMenu = false
                            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("导出 JSON") },
                        onClick = {
                            showMenu = false
                            exportLauncher.launch("toc.json")
                        }
                    )
                }
            }
        }

        if (vm.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        }

        if (chapters.isEmpty()) {
            Text(
                "暂无章节，可从右上角菜单新建或导入",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(chapters, key = { it.id }) { ch ->
                        val end = endMap[ch.id] ?: ch.bookPage
                        val isCurrent = currentBookPage != null &&
                            currentBookPage >= ch.bookPage && currentBookPage <= end
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .combinedClickable(
                                    onClick = {
                                        val pdf = vm.bookToPdfPage(ch.bookPage)
                                        if (pdf != null) {
                                            onClose()
                                            onJump(pdf)
                                        } else {
                                            vm.showMessage("该章节书页不在任何编号分区内")
                                        }
                                    },
                                    onLongClick = { contextChapter = ch }
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    ch.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    if (end > ch.bookPage) "第 ${ch.bookPage} - $end 页"
                                    else "第 ${ch.bookPage} 页",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                VerticalScrollbar(
                    listState = listState,
                    itemCount = chapters.size,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(12.dp)
                )
            }
        }
    }

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

    contextChapter?.let { ch ->
        AlertDialog(
            onDismissRequest = { contextChapter = null },
            title = { Text(ch.title) },
            text = { Text("起始书页：第 ${ch.bookPage} 页") },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        contextChapter = null
                        editorTarget = ch
                        showEditor = true
                    }) { Text("编辑") }
                    TextButton(onClick = {
                        contextChapter = null
                        pendingDeleteChapter = ch
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = {
                TextButton(onClick = { contextChapter = null }) { Text("取消") }
            }
        )
    }

    pendingDeleteChapter?.let { ch ->
        AlertDialog(
            onDismissRequest = { pendingDeleteChapter = null },
            title = { Text("删除章节") },
            text = { Text("确定删除「${ch.title}」吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteChapter(ch)
                    pendingDeleteChapter = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteChapter = null }) { Text("取消") }
            }
        )
    }
}

/** 书签面板：标题栏 + 列表；点删除按钮会二次确认。 */
@Composable
fun BookmarkPanel(
    vm: ReaderViewModel,
    currentPdfPage: Int,
    onJump: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bookmarks = vm.bookmarks
    var pendingDelete by remember { mutableStateOf<Bookmark?>(null) }

    Column(modifier.fillMaxSize()) {
        DrawerHeader(title = "书签", onClose = onClose)

        TextButton(
            onClick = { vm.toggleBookmark(currentPdfPage) },
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(if (vm.isBookmarked(currentPdfPage)) "取消当前页书签" else "当前页加书签")
        }

        if (bookmarks.isEmpty()) {
            Text(
                "暂无书签",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(bookmarks, key = { it.id }) { bm ->
                    val chapterName = vm.chapterTitleOfPdf(bm.pdfPage)
                    val bookPage = vm.pdfToBookPage(bm.pdfPage)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onClose(); onJump(bm.pdfPage) }
                            .padding(start = 12.dp, end = 0.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(chapterName ?: "未分章", style = MaterialTheme.typography.bodyLarge)
                            val pageText = if (bookPage != null) "第 $bookPage 页"
                            else "PDF 第 ${bm.pdfPage + 1} 页"
                            Text(
                                if (bm.label.isNotBlank()) "$pageText · ${bm.label}" else pageText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { pendingDelete = bm }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { bm ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除书签") },
            text = { Text("确定删除这个书签吗？") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteBookmark(bm)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
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

@Composable
private fun RegionDialog(vm: ReaderViewModel, onDismiss: () -> Unit) {
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

/** 可拖动的竖向滚动条（对相同目标去重，避免快速拖动卡顿）。 */
@Composable
fun VerticalScrollbar(
    listState: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    if (itemCount <= 0) return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val jobRef = remember { arrayOfNulls<Job>(1) }
    val lastTarget = remember { intArrayOf(-1) }

    val layout = listState.layoutInfo
    val visible = layout.visibleItemsInfo.size.coerceAtLeast(1)
    val total = itemCount
    val thumbFraction = (visible.toFloat() / total).coerceIn(0.06f, 1f)
    val first = layout.visibleItemsInfo.firstOrNull()?.index ?: 0
    val scrollFraction = if (total <= visible) 0f
    else (first.toFloat() / (total - visible)).coerceIn(0f, 1f)

    fun jumpToFraction(f: Float) {
        val target = (f * (total - 1)).roundToInt().coerceIn(0, total - 1)
        if (target == lastTarget[0]) return
        lastTarget[0] = target
        jobRef[0]?.cancel()
        jobRef[0] = scope.launch { listState.scrollToItem(target) }
    }

    BoxWithConstraints(modifier) {
        val trackH = constraints.maxHeight.toFloat()
        val thumbH = (trackH * thumbFraction).coerceAtLeast(40f)
        val thumbY = (trackH - thumbH) * scrollFraction

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(total) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            jumpToFraction((offset.y / size.height.toFloat()).coerceIn(0f, 1f))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            jumpToFraction((change.position.y / size.height.toFloat()).coerceIn(0f, 1f))
                        }
                    )
                }
        ) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = with(density) { thumbY.toDp() })
                    .size(width = 8.dp, height = with(density) { thumbH.toDp() })
                    .background(Color.Gray.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            )
        }
    }
}
