package com.pagereader.app.ui.reader

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pagereader.app.data.Note
import com.pagereader.app.data.ReadingMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class ReaderDrawer { NONE, TOC, BOOKMARKS }

/** 笔记编辑器状态：note 为 null 表示新建。 */
private data class NoteEditorState(val note: Note?)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    bookId: Long,
    onBack: () -> Unit,
    vm: ReaderViewModel = viewModel()
) {
    LaunchedEffect(bookId) { vm.load(bookId) }

    val activity = LocalContext.current as? Activity
    LaunchedEffect(vm.brightness) {
        val w = activity?.window ?: return@LaunchedEffect
        w.attributes = w.attributes.apply { screenBrightness = vm.brightness }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    BackHandler(onBack = onBack)

    val book = vm.book
    if (book == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    var uiVisible by remember { mutableStateOf(true) }
    var drawer by remember { mutableStateOf(ReaderDrawer.NONE) }
    var showSettings by remember { mutableStateOf(false) }
    var showBrightness by remember { mutableStateOf(false) }
    var noteEditor by remember { mutableStateOf<NoteEditorState?>(null) }

    val pageCount = book.totalPages.coerceAtLeast(1)
    val initial = book.currentPage.coerceIn(0, pageCount - 1)
    val pagerState = rememberPagerState(initialPage = initial, pageCount = { pageCount })
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val currentPage =
        if (book.readingMode == ReadingMode.PAGED) pagerState.currentPage
        else listState.firstVisibleItemIndex

    // 让长生命周期的下滑手势始终读到最新页码
    val currentPageState = rememberUpdatedState(currentPage)

    fun jumpTo(page: Int, hideUI: Boolean = true) {
        val target = page.coerceIn(0, pageCount - 1)
        if (hideUI) uiVisible = false
        scope.launch {
            if (book.readingMode == ReadingMode.PAGED) pagerState.scrollToPage(target)
            else listState.scrollToItem(target)
        }
    }

    fun prevPage() {
        scope.launch {
            if (book.readingMode == ReadingMode.PAGED) {
                pagerState.animateScrollToPage((pagerState.targetPage - 1).coerceAtLeast(0))
            } else {
                listState.animateScrollBy(-listState.layoutInfo.viewportSize.height.toFloat())
            }
        }
    }

    fun nextPage() {
        scope.launch {
            if (book.readingMode == ReadingMode.PAGED) {
                pagerState.animateScrollToPage(
                    (pagerState.targetPage + 1).coerceAtMost(pageCount - 1)
                )
            } else {
                listState.animateScrollBy(listState.layoutInfo.viewportSize.height.toFloat())
            }
        }
    }

    // 切换阅读模式时把位置带到新容器
    LaunchedEffect(book.readingMode) {
        if (book.readingMode == ReadingMode.PAGED) pagerState.scrollToPage(book.currentPage)
        else listState.scrollToItem(book.currentPage)
    }

    // 记录阅读进度
    LaunchedEffect(book.readingMode) {
        if (book.readingMode == ReadingMode.PAGED) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect { vm.setPage(it) }
        } else {
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { vm.setPage(it) }
        }
    }

    val currentBookPage = vm.pdfToBookPage(currentPage)
    val isMarked = vm.bookmarks.any { it.pdfPage == currentPage }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val contentMaxW = constraints.maxWidth
        val contentMaxH = constraints.maxHeight
        val prefetchMaxH =
            if (book.readingMode == ReadingMode.PAGED) contentMaxH else Int.MAX_VALUE / 2
        LaunchedEffect(currentPage, book.readingMode, contentMaxW, prefetchMaxH) {
            vm.prefetchAround(currentPage, contentMaxW, prefetchMaxH)
        }

        // 内容区：左/右翻页，中间唤出菜单；翻页模式下下滑切换书签
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(book.readingMode) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        when {
                            offset.x < w / 3f -> prevPage()
                            offset.x > w * 2f / 3f -> nextPage()
                            else -> uiVisible = !uiVisible
                        }
                    }
                }
                .pointerInput(book.readingMode) {
                    if (book.readingMode == ReadingMode.PAGED) {
                        var totalDy = 0f
                        detectVerticalDragGestures(
                            onDragStart = { totalDy = 0f },
                            onDragCancel = { totalDy = 0f },
                            onDragEnd = {
                                if (totalDy > with(density) { 90.dp.toPx() }) {
                                    vm.toggleBookmark(currentPageState.value)
                                }
                            },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                totalDy += amount
                            }
                        )
                    }
                }
        ) {
            if (book.readingMode == ReadingMode.PAGED) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondBoundsPageCount = 2
                ) { page ->
                    ReaderPage(
                        vm, page, Modifier.fillMaxSize(), fitHeight = true,
                        onEditNote = { noteEditor = NoteEditorState(it) }
                    )
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(pageCount) { page ->
                        ReaderPage(
                            vm, page, Modifier.fillMaxWidth(), fitHeight = false,
                            onEditNote = { noteEditor = NoteEditorState(it) }
                        )
                    }
                }
            }

            if (vm.nightMode) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            }
        }

        // 当前页已书签的贴纸
        if (isMarked) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = if (uiVisible) 64.dp else 12.dp, end = 12.dp),
                color = Color(0xFFE53935),
                shape = RoundedCornerShape(6.dp),
                shadowElevation = 4.dp
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Bookmark,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("书签", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        AnimatedVisibility(
            visible = uiVisible,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopBar(
                title = book.title,
                bookPage = currentBookPage,
                pdfPage = currentPage,
                totalPdf = book.totalPages,
                onBack = onBack,
                onSettings = { showSettings = true }
            )
        }

        AnimatedVisibility(
            visible = uiVisible,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderBottomBar(
                vm = vm,
                currentPage = currentPage,
                totalPages = book.totalPages,
                onSeek = { jumpTo(it, hideUI = false) },
                onToc = { drawer = ReaderDrawer.TOC },
                onBookmarks = { drawer = ReaderDrawer.BOOKMARKS },
                onNote = { noteEditor = NoteEditorState(null) },
                onBrightness = { showBrightness = true }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp)
        )

        when (drawer) {
            ReaderDrawer.TOC -> SideDrawer(onClose = { drawer = ReaderDrawer.NONE }) { m ->
                TocPanel(
                    vm,
                    currentPdfPage = currentPage,
                    onJump = { jumpTo(it) },
                    onClose = { drawer = ReaderDrawer.NONE },
                    modifier = m
                )
            }
            ReaderDrawer.BOOKMARKS -> SideDrawer(onClose = { drawer = ReaderDrawer.NONE }) { m ->
                BookmarkPanel(
                    vm,
                    currentPdfPage = currentPage,
                    onJump = { jumpTo(it) },
                    onClose = { drawer = ReaderDrawer.NONE },
                    modifier = m
                )
            }
            ReaderDrawer.NONE -> Unit
        }
    }

    if (showSettings) {
        SettingsSheet(vm, onDismiss = { showSettings = false })
    }
    if (showBrightness) {
        BrightnessSheet(vm, onDismiss = { showBrightness = false })
    }
    noteEditor?.let { st ->
        NoteEditorDialog(
            initial = st.note,
            onSave = { text, color, fontSize, rotation, fontFamily ->
                val existing = st.note
                if (existing == null) {
                    vm.addNote(currentPage, text, color, fontSize, rotation, fontFamily)
                } else {
                    vm.updateNote(
                        existing.copy(
                            text = text,
                            color = color,
                            fontSize = fontSize,
                            rotation = rotation,
                            fontFamily = fontFamily
                        )
                    )
                }
                noteEditor = null
            },
            onDelete = st.note?.let { n ->
                {
                    vm.deleteNote(n)
                    noteEditor = null
                }
            },
            onDismiss = { noteEditor = null }
        )
    }
}

/** 渲染单页 + 该页的笔记叠加层。 */
@Composable
private fun ReaderPage(
    vm: ReaderViewModel,
    pageIndex: Int,
    modifier: Modifier,
    fitHeight: Boolean,
    onEditNote: (Note) -> Unit
) {
    BoxWithConstraints(modifier) {
        val maxW = constraints.maxWidth
        val maxH = if (fitHeight) constraints.maxHeight else Int.MAX_VALUE / 2
        val bitmap by produceState<Bitmap?>(
            initialValue = null,
            key1 = pageIndex,
            key2 = maxW to maxH
        ) {
            var result = vm.renderPage(pageIndex, maxW, maxH)
            if (result == null && isActive) {
                delay(120)
                result = vm.renderPage(pageIndex, maxW, maxH)
            }
            value = result
        }
        val b = bitmap
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = if (fitHeight) Modifier.fillMaxSize()
                else Modifier.fillMaxWidth().aspectRatio(b.width.toFloat() / b.height)
            )
        } else {
            Box(
                modifier = if (fitHeight) Modifier.fillMaxSize()
                else Modifier.fillMaxWidth().aspectRatio(0.75f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.5f),
                    strokeWidth = 2.dp
                )
            }
        }

        // 笔记叠加层
        val pageW = constraints.maxWidth.toFloat()
        val pageH = if (fitHeight) {
            constraints.maxHeight.toFloat()
        } else if (b != null && b.width > 0) {
            pageW * b.height / b.width
        } else {
            pageW * 1.4f
        }
        vm.notesForPage(pageIndex).forEach { note ->
            key(note.id) {
                NoteItem(note, pageW, pageH, vm, onEdit = { onEditNote(note) })
            }
        }
    }
}

@Composable
private fun NoteItem(
    note: Note,
    pageW: Float,
    pageH: Float,
    vm: ReaderViewModel,
    onEdit: () -> Unit
) {
    val latestNote = rememberUpdatedState(note)
    val latestOnEdit = rememberUpdatedState(onEdit)

    var offsetX by remember(note.id) { mutableFloatStateOf(pageW * note.x) }
    var offsetY by remember(note.id) { mutableFloatStateOf(pageH * note.y) }

    LaunchedEffect(note.x, note.y, pageW, pageH) {
        offsetX = pageW * note.x
        offsetY = pageH * note.y
    }

    Box(
        Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .graphicsLayer { rotationZ = note.rotation }
            .pointerInput(note.id) {
                detectTapGestures(
                    onTap = { latestOnEdit.value() }
                )
            }
            .pointerInput(note.id) {
                detectDragGestures(
                    onDrag = { change, amount ->
                        change.consume()
                        offsetX += amount.x
                        offsetY += amount.y
                    },
                    onDragEnd = {
                        val n = latestNote.value
                        val nx = (offsetX / pageW.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        val ny = (offsetY / pageH.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        vm.updateNote(n.copy(x = nx, y = ny))
                    }
                )
            }
            .background(Color(note.color).copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            text = note.text,
            color = Color(note.color),
            fontSize = note.fontSize.sp,
            fontFamily = noteFontFamily(note.fontFamily)
        )
    }
}

private fun noteFontFamily(id: Int): FontFamily = when (id) {
    1 -> FontFamily.Serif
    2 -> FontFamily.Monospace
    3 -> FontFamily.Cursive
    else -> FontFamily.SansSerif
}

@Composable
private fun ReaderTopBar(
    title: String,
    bookPage: Int?,
    pdfPage: Int,
    totalPdf: Int,
    onBack: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(color = Color(0xE6000000)) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (bookPage != null) "第 $bookPage 页 · PDF ${pdfPage + 1}/$totalPdf"
                    else "PDF ${pdfPage + 1}/$totalPdf",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "设置", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    vm: ReaderViewModel,
    currentPage: Int,
    totalPages: Int,
    onSeek: (Int) -> Unit,
    onToc: () -> Unit,
    onBookmarks: () -> Unit,
    onNote: () -> Unit,
    onBrightness: () -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(currentPage.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(currentPage) {
        if (!dragging) sliderValue = currentPage.toFloat()
    }

    val displayPage = if (dragging) {
        sliderValue.toInt().coerceIn(0, (totalPages - 1).coerceAtLeast(0))
    } else {
        currentPage
    }
    val displayChapter = vm.chapterTitleOfPdf(displayPage) ?: "未分章"
    val displayBookPage = vm.pdfToBookPage(displayPage)

    Surface(color = Color(0xE6000000)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    displayChapter,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (displayBookPage != null) "第 $displayBookPage 页"
                    else "${displayPage + 1}/$totalPages",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Slider(
                value = sliderValue,
                onValueChange = {
                    dragging = true
                    sliderValue = it
                },
                onValueChangeFinished = {
                    dragging = false
                    onSeek(sliderValue.toInt())
                },
                enabled = totalPages > 1,
                valueRange = 0f..(totalPages - 1).coerceAtLeast(1).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BottomAction("目录", Icons.Filled.List, onToc)
                BottomAction("书签", Icons.Filled.BookmarkBorder, onBookmarks)
                BottomAction("笔记", Icons.Filled.EditNote, onNote)
                BottomAction("亮度", Icons.Filled.BrightnessHigh, onBrightness)
            }
        }
    }
}

@Composable
private fun BottomAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = Color.White)
            Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}
