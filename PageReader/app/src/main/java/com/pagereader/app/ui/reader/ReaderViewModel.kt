package com.pagereader.app.ui.reader

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pagereader.app.ReaderApplication
import com.pagereader.app.data.Book
import com.pagereader.app.data.BookType
import com.pagereader.app.data.Bookmark
import com.pagereader.app.data.Chapter
import com.pagereader.app.data.Note
import com.pagereader.app.data.Region
import com.pagereader.app.reader.ImagePageSource
import com.pagereader.app.reader.OutlineExtractor
import com.pagereader.app.reader.PageMap
import com.pagereader.app.reader.PageSize
import com.pagereader.app.reader.PageSource
import com.pagereader.app.reader.PdfPageSource
import com.pagereader.app.reader.ResolvedRegion
import com.pagereader.app.reader.TocJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as ReaderApplication).repository
    private val settings = (app as ReaderApplication).settings

    private var bookId: Long = -1L
    private var source: PageSource? = null
    private var lastSavedPage: Int = -1
    private var regionJob: Job? = null
    private var chapterJob: Job? = null
    private var bookmarkJob: Job? = null
    private var noteJob: Job? = null
    private var prefetchJob: Job? = null

    var book by mutableStateOf<Book?>(null); private set
    var regions by mutableStateOf<List<Region>>(emptyList()); private set
    var chapters by mutableStateOf<List<Chapter>>(emptyList()); private set
    var bookmarks by mutableStateOf<List<Bookmark>>(emptyList()); private set
    var notes by mutableStateOf<List<Note>>(emptyList()); private set
    var nightMode by mutableStateOf(false); private set
    var brightness by mutableStateOf(-1f); private set
    var loading by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set

    val pageCount: Int get() = book?.totalPages ?: 0

    /** 解析后的分区（含默认「正文」）。 */
    val resolvedRegions: List<ResolvedRegion>
        get() = PageMap.resolve(regions, pageCount)

    fun load(id: Long) {
        if (bookId == id) return
        bookId = id
        book = null
        regions = emptyList()
        chapters = emptyList()
        bookmarks = emptyList()
        notes = emptyList()
        source?.close()
        source = null
        regionJob?.cancel()
        chapterJob?.cancel()
        bookmarkJob?.cancel()
        noteJob?.cancel()
        prefetchJob?.cancel()
        nightMode = settings.nightMode
        brightness = settings.brightness
        viewModelScope.launch {
            val b = repo.getBook(id) ?: return@launch
            source = when (b.type) {
                BookType.PDF -> PdfPageSource(getApplication(), Uri.parse(b.uri))
                BookType.IMAGE -> {
                    val pages = repo.getPages(id)
                    ImagePageSource(getApplication(), pages)
                }
            }
            book = b
            lastSavedPage = b.currentPage
        }
        regionJob = viewModelScope.launch { repo.regions(id).collect { regions = it } }
        chapterJob = viewModelScope.launch { repo.chapters(id).collect { chapters = it } }
        bookmarkJob = viewModelScope.launch { repo.bookmarks(id).collect { bookmarks = it } }
        noteJob = viewModelScope.launch { repo.notes(id).collect { notes = it } }
    }

    suspend fun renderPage(index: Int, maxWidth: Int, maxHeight: Int) =
        source?.renderPage(index, maxWidth, maxHeight)

    fun pageSize(index: Int): PageSize? = source?.pageSize(index)

    /**
     * 在 viewModelScope 中预渲染当前页左右各 2 页。
     * 不依赖页面组合，避免快速翻页时渲染被取消导致“翻回来才加载”。
     */
    fun prefetchAround(pdfPage: Int, maxWidth: Int, maxHeight: Int) {
        val src = source ?: return
        if (maxWidth <= 0 || maxHeight <= 0) return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            val order = listOf(pdfPage, pdfPage + 1, pdfPage - 1, pdfPage + 2, pdfPage - 2)
            for (p in order) {
                if (p !in 0 until pageCount) continue
                if (!isActive) break
                src.renderPage(p, maxWidth, maxHeight)
            }
        }
    }

    fun setPage(page: Int) {
        val b = book ?: return
        val clamped = page.coerceIn(0, (b.totalPages - 1).coerceAtLeast(0))
        if (clamped == lastSavedPage) return
        lastSavedPage = clamped
        val updated = b.copy(currentPage = clamped)
        book = updated
        viewModelScope.launch { repo.updateBook(updated) }
    }

    fun setReadingMode(mode: Int) {
        val b = book ?: return
        val updated = b.copy(readingMode = mode)
        book = updated
        viewModelScope.launch { repo.updateBook(updated) }
    }

    fun updateNightMode(v: Boolean) {
        nightMode = v
        settings.nightMode = v
    }

    fun updateBrightness(v: Float) {
        brightness = v
        settings.brightness = v
    }

    // ---------- 页码映射 ----------

    fun pdfToBookPage(pdfPage: Int): Int? = PageMap.pdfToBook(resolvedRegions, pdfPage)

    fun bookToPdfPage(bookPage: Int): Int? = PageMap.bookToPdf(resolvedRegions, bookPage)

    fun regionNameOfPdf(pdfPage: Int): String? =
        PageMap.regionByPdf(resolvedRegions, pdfPage)?.name

    /** 章节结束的书页码（由下一章推出，或截断到分区末页）。 */
    fun chapterEndBookPage(chapter: Chapter): Int {
        val sorted = chapters.sortedBy { it.bookPage }
        val idx = sorted.indexOfFirst { it.id == chapter.id }
        val next = sorted.getOrNull(idx + 1)?.bookPage
        return PageMap.chapterEnd(resolvedRegions, chapter.bookPage, next)
    }

    /** 某 PDF 页所属章节标题（书签显示用）。chapters 已按 bookPage 排序。 */
    fun chapterTitleOfPdf(pdfPage: Int): String? {
        val bookPage = pdfToBookPage(pdfPage) ?: return null
        val idx = chapters.indexOfLast { it.bookPage <= bookPage }
        return chapters.getOrNull(idx)?.title
    }

    /** 当前 PDF 页所在章节下标（chapters 已按书页排序），-1 表示未命中。 */
    fun currentChapterIndex(pdfPage: Int): Int {
        val bookPage = pdfToBookPage(pdfPage) ?: return -1
        return chapters.indexOfLast { it.bookPage <= bookPage }
    }

    // ---------- 章节 ----------

    fun addChapter(title: String, bookPage: Int) {
        val b = book ?: return
        viewModelScope.launch {
            val order = (chapters.maxOfOrNull { it.orderIndex } ?: -1) + 1
            repo.insertChapter(
                Chapter(
                    bookId = b.id,
                    orderIndex = order,
                    title = title,
                    bookPage = bookPage.coerceAtLeast(1)
                )
            )
        }
    }

    fun updateChapter(chapter: Chapter, title: String, bookPage: Int) {
        viewModelScope.launch {
            repo.updateChapter(
                chapter.copy(title = title, bookPage = bookPage.coerceAtLeast(1))
            )
        }
    }

    fun deleteChapter(chapter: Chapter) {
        viewModelScope.launch { repo.deleteChapter(chapter) }
    }

    fun importOutline() {
        val b = book ?: return
        if (b.type != BookType.PDF) return
        viewModelScope.launch {
            loading = true
            val entries = OutlineExtractor.extract(getApplication(), Uri.parse(b.uri))
            if (entries.isEmpty()) {
                message = "未能从该 PDF 中读取到目录，请手动新建章节"
            } else {
                val resolved = PageMap.resolve(regions, b.totalPages)
                repo.deleteChaptersByBook(b.id)
                entries.forEachIndexed { i, e ->
                    val bookPage = PageMap.pdfToBook(resolved, e.pageIndex) ?: (e.pageIndex + 1)
                    repo.insertChapter(
                        Chapter(bookId = b.id, orderIndex = i, title = e.title, bookPage = bookPage)
                    )
                }
                message = "已导入 ${entries.size} 个章节"
            }
            loading = false
        }
    }

    // ---------- 分区 ----------

    fun addRegion(name: String, pdfStart: Int, bookStart: Int?) {
        val b = book ?: return
        viewModelScope.launch {
            val order = (regions.maxOfOrNull { it.orderIndex } ?: -1) + 1
            repo.insertRegion(
                Region(
                    bookId = b.id,
                    orderIndex = order,
                    name = name,
                    pdfStart = pdfStart.coerceAtLeast(0),
                    bookStart = bookStart
                )
            )
        }
    }

    fun updateRegion(region: Region, name: String, pdfStart: Int, bookStart: Int?) {
        viewModelScope.launch {
            repo.updateRegion(
                region.copy(
                    name = name,
                    pdfStart = pdfStart.coerceAtLeast(0),
                    bookStart = bookStart
                )
            )
        }
    }

    fun deleteRegion(region: Region) {
        viewModelScope.launch { repo.deleteRegion(region) }
    }

    // ---------- 书签 ----------

    fun addBookmark(pdfPage: Int, label: String) {
        val b = book ?: return
        viewModelScope.launch {
            repo.insertBookmark(Bookmark(bookId = b.id, pdfPage = pdfPage, label = label))
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { repo.deleteBookmark(bookmark) }
    }

    fun isBookmarked(pdfPage: Int): Boolean = bookmarks.any { it.pdfPage == pdfPage }

    /** 切换当前页书签：已有则删除，没有则添加。 */
    fun toggleBookmark(pdfPage: Int) {
        val b = book ?: return
        val existing = bookmarks.firstOrNull { it.pdfPage == pdfPage }
        if (existing != null) {
            viewModelScope.launch { repo.deleteBookmark(existing) }
            message = "已取消书签"
        } else {
            viewModelScope.launch {
                repo.insertBookmark(Bookmark(bookId = b.id, pdfPage = pdfPage, label = ""))
            }
            message = "已添加书签"
        }
    }

    // ---------- 笔记 ----------

    fun notesForPage(pdfPage: Int): List<Note> = notes.filter { it.pdfPage == pdfPage }

    fun addNote(
        pdfPage: Int,
        text: String,
        color: Long,
        fontSize: Float,
        rotation: Float,
        fontFamily: Int
    ) {
        val b = book ?: return
        viewModelScope.launch {
            repo.insertNote(
                Note(
                    bookId = b.id,
                    pdfPage = pdfPage,
                    text = text,
                    color = color,
                    fontSize = fontSize,
                    rotation = rotation,
                    fontFamily = fontFamily
                )
            )
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch { repo.updateNote(note) }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch { repo.deleteNote(note) }
    }

    // ---------- 目录 JSON ----------

    fun exportTocJson(): String = TocJson.export(regions, chapters)

    fun importTocJson(text: String) {
        val b = book ?: return
        TocJson.parse(text).onSuccess { data ->
            viewModelScope.launch {
                loading = true
                repo.replaceToc(b.id, data.regions, data.chapters)
                message = "已导入 ${data.regions.size} 个分区、${data.chapters.size} 个章节"
                loading = false
            }
        }.onFailure {
            message = "JSON 解析失败：${it.message}"
        }
    }

    fun showMessage(msg: String) {
        message = msg
    }

    fun clearMessage() {
        message = null
    }

    override fun onCleared() {
        super.onCleared()
        prefetchJob?.cancel()
        source?.close()
        source = null
    }
}
