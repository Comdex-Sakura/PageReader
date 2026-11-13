package com.pagereader.app.ui.reader

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pagereader.app.data.ReadingMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

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
    var showChapters by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val pageCount = book.totalPages.coerceAtLeast(1)
    val initial = book.currentPage.coerceIn(0, pageCount - 1)
    val pagerState = rememberPagerState(initialPage = initial, pageCount = { pageCount })
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val scope = rememberCoroutineScope()

    val currentPage =
        if (book.readingMode == ReadingMode.PAGED) pagerState.currentPage
        else listState.firstVisibleItemIndex

    fun jumpTo(page: Int) {
        val target = page.coerceIn(0, pageCount - 1)
        uiVisible = false
        scope.launch {
            if (book.readingMode == ReadingMode.PAGED) pagerState.scrollToPage(target)
            else listState.scrollToItem(target)
        }
    }

    // 切换阅读模式时，把当前阅读位置带到新的容器
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

    val chapters = vm.chapters
    val currentBookPage = vm.pdfToBookPage(currentPage)
    val currentChapterIndex = vm.currentChapterIndex(currentPage)
    val currentChapter = chapters.getOrNull(currentChapterIndex)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // 内容区：点按切换 UI 显隐（顶/底栏是兄弟节点，不会误触）
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { uiVisible = !uiVisible } }
        ) {
            if (book.readingMode == ReadingMode.PAGED) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    ReaderPage(vm, page, Modifier.fillMaxSize(), fitHeight = true)
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(pageCount) { page ->
                        ReaderPage(vm, page, Modifier.fillMaxWidth(), fitHeight = false)
                    }
                }
            }

            if (vm.nightMode) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
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
                onChapters = { showChapters = true },
                onBookmarks = { showBookmarks = true },
                onSettings = { showSettings = true }
            )
        }

        AnimatedVisibility(
            visible = uiVisible,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderBottomBar(
                currentPage = currentPage,
                totalPages = book.totalPages,
                currentChapterTitle = currentChapter?.title,
                onPrevChapter = {
                    val idx = currentChapterIndex - 1
                    if (idx >= 0) vm.bookToPdfPage(chapters[idx].bookPage)?.let { jumpTo(it) }
                },
                onNextChapter = {
                    val idx = currentChapterIndex + 1
                    if (idx < chapters.size) vm.bookToPdfPage(chapters[idx].bookPage)?.let { jumpTo(it) }
                },
                onSeek = { jumpTo(it) },
                onToggleMode = {
                    val next = if (book.readingMode == ReadingMode.PAGED)
                        ReadingMode.SCROLL else ReadingMode.PAGED
                    vm.setReadingMode(next)
                },
                isPaged = book.readingMode == ReadingMode.PAGED
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
        )
    }

    if (showChapters) {
        ChapterDialog(vm, onJump = { jumpTo(it) }, onDismiss = { showChapters = false })
    }
    if (showBookmarks) {
        BookmarkSheet(
            vm,
            currentPage = currentPage,
            onJump = { jumpTo(it) },
            onDismiss = { showBookmarks = false }
        )
    }
    if (showSettings) {
        SettingsSheet(vm, onDismiss = { showSettings = false })
    }
}

/** 渲染单页；fitHeight=true 表示翻页模式（适配整屏），否则为滚动模式（按宽度适配）。 */
@Composable
private fun ReaderPage(
    vm: ReaderViewModel,
    pageIndex: Int,
    modifier: Modifier,
    fitHeight: Boolean
) {
    BoxWithConstraints(modifier) {
        val maxW = constraints.maxWidth
        val maxH = if (fitHeight) constraints.maxHeight else Int.MAX_VALUE / 2
        val bitmap by produceState<Bitmap?>(
            initialValue = null,
            key1 = pageIndex,
            key2 = maxW,
            key3 = maxH
        ) {
            value = vm.renderPage(pageIndex, maxW, maxH)
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
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    bookPage: Int?,
    pdfPage: Int,
    totalPdf: Int,
    onBack: () -> Unit,
    onChapters: () -> Unit,
    onBookmarks: () -> Unit,
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
            IconButton(onClick = onChapters) {
                Icon(Icons.Filled.List, contentDescription = "目录", tint = Color.White)
            }
            IconButton(onClick = onBookmarks) {
                Icon(Icons.Filled.BookmarkBorder, contentDescription = "书签", tint = Color.White)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "设置", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    currentPage: Int,
    totalPages: Int,
    currentChapterTitle: String?,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSeek: (Int) -> Unit,
    onToggleMode: () -> Unit,
    isPaged: Boolean
) {
    var sliderValue by remember { mutableFloatStateOf(currentPage.toFloat()) }
    LaunchedEffect(currentPage) { sliderValue = currentPage.toFloat() }

    Surface(color = Color(0xE6000000)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    currentChapterTitle ?: "未分章",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${currentPage + 1} / $totalPages",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onSeek(sliderValue.toInt()) },
                enabled = totalPages > 1,
                valueRange = 0f..(totalPages - 1).coerceAtLeast(1).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onPrevChapter) {
                    Text("上一章", color = Color.White.copy(alpha = 0.9f))
                }
                TextButton(onClick = onToggleMode) {
                    Text(
                        if (isPaged) "切换为滚动" else "切换为翻页",
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                TextButton(onClick = onNextChapter) {
                    Text("下一章", color = Color.White.copy(alpha = 0.9f))
                }
            }
        }
    }
}
