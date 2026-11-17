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
import com.pagereader.app.reader.ImagePageSource
import com.pagereader.app.reader.OutlineExtractor
import com.pagereader.app.reader.PageSize
import com.pagereader.app.reader.PageSource
import com.pagereader.app.reader.PdfPageSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as ReaderApplication).repository
    private val settings = (app as ReaderApplication).settings

    private var bookId: Long = -1L
    private var source: PageSource? = null
    private var lastSavedPage: Int = -1
    private var chapterJob: Job? = null
    private var bookmarkJob: Job? = null

    var book by mutableStateOf<Book?>(null); private set
    var chapters by mutableStateOf<List<Chapter>>(emptyList()); private set
    var bookmarks by mutableStateOf<List<Bookmark>>(emptyList()); private set
    var nightMode by mutableStateOf(false); private set
    var brightness by mutableStateOf(-1f); private set
    var loading by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set

    val pageCount: Int get() = book?.totalPages ?: 0

    fun load(id: Long) {
        if (bookId == id) return
        bookId = id
        book = null
        chapters = emptyList()
        bookmarks = emptyList()
        source?.close()
        source = null
        chapterJob?.cancel()
        bookmarkJob?.cancel()
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
        chapterJob = viewModelScope.launch { repo.chapters(id).collect { chapters = it } }
        bookmarkJob = viewModelScope.launch { repo.bookmarks(id).collect { bookmarks = it } }
    }

    suspend fun renderPage(index: Int, maxWidth: Int, maxHeight: Int) =
        source?.renderPage(index, maxWidth, maxHeight)

    fun pageSize(index: Int): PageSize? = source?.pageSize(index)

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

    fun addChapter(title: String, startPage: Int, endPage: Int) {
        val b = book ?: return
        viewModelScope.launch {
            val order = (chapters.maxOfOrNull { it.orderIndex } ?: -1) + 1
            repo.insertChapter(
                Chapter(
                    bookId = b.id,
                    orderIndex = order,
                    title = title,
                    startPage = startPage,
                    endPage = endPage
                )
            )
        }
    }

    fun updateChapter(chapter: Chapter, title: String, startPage: Int, endPage: Int) {
        viewModelScope.launch {
            repo.updateChapter(
                chapter.copy(title = title, startPage = startPage, endPage = endPage)
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
                repo.deleteChaptersByBook(b.id)
                entries.forEachIndexed { i, e ->
                    repo.insertChapter(
                        Chapter(
                            bookId = b.id,
                            orderIndex = i,
                            title = e.title,
                            startPage = e.pageIndex,
                            endPage = e.pageIndex
                        )
                    )
                }
                message = "已导入 ${entries.size} 个章节"
            }
            loading = false
        }
    }

    fun addBookmark(page: Int, label: String) {
        val b = book ?: return
        viewModelScope.launch {
            repo.insertBookmark(Bookmark(bookId = b.id, page = page, label = label))
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { repo.deleteBookmark(bookmark) }
    }

    fun clearMessage() {
        message = null
    }

    override fun onCleared() {
        super.onCleared()
        source?.close()
        source = null
    }
}
