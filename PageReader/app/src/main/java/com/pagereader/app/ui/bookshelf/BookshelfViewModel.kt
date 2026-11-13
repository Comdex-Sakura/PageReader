package com.pagereader.app.ui.bookshelf

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pagereader.app.ReaderApplication
import com.pagereader.app.data.Book
import com.pagereader.app.data.BookPage
import com.pagereader.app.data.BookType
import com.pagereader.app.reader.PdfPageSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookshelfViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as ReaderApplication).repository

    val books: StateFlow<List<Book>> =
        repo.books.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var importing by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            importing = true
            val pageCount = runCatching {
                PdfPageSource(getApplication(), uri).use { it.pageCount }
            }.getOrNull()
            if (pageCount == null || pageCount <= 0) {
                errorMessage = "无法读取该 PDF 文件"
                importing = false
                return@launch
            }
            repo.takePersistablePermission(uri)
            val title = queryDisplayName(uri) ?: "未命名 PDF"
            repo.insertBook(
                Book(
                    title = title,
                    type = BookType.PDF,
                    uri = uri.toString(),
                    totalPages = pageCount
                )
            )
            importing = false
        }
    }

    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importing = true
            uris.forEach { repo.takePersistablePermission(it) }
            val title = queryDisplayName(uris.first()) ?: "图片合集"
            val bookId = repo.insertBook(
                Book(
                    title = title,
                    type = BookType.IMAGE,
                    uri = uris.first().toString(),
                    totalPages = uris.size
                )
            )
            repo.insertPages(
                uris.mapIndexed { i, u ->
                    BookPage(bookId = bookId, pageIndex = i, uri = u.toString())
                }
            )
            importing = false
        }
    }

    fun delete(book: Book) {
        viewModelScope.launch { repo.deleteBook(book) }
    }

    fun clearError() {
        errorMessage = null
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        getApplication<Application>().contentResolver
            .query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
    }.getOrNull()
}
