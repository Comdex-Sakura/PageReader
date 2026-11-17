package com.pagereader.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/**
 * 统一数据访问层，并负责 SAF（存储访问框架）持久化 URI 权限的申请与回收。
 */
class Repository(private val context: Context, private val db: AppDatabase) {

    val books: Flow<List<Book>> = db.bookDao().getAll()

    fun chapters(bookId: Long): Flow<List<Chapter>> = db.chapterDao().getByBook(bookId)

    fun bookmarks(bookId: Long): Flow<List<Bookmark>> = db.bookmarkDao().getByBook(bookId)

    suspend fun getBook(id: Long): Book? = db.bookDao().getById(id)

    suspend fun getPages(bookId: Long): List<BookPage> = db.bookPageDao().getByBook(bookId)

    suspend fun insertBook(book: Book): Long = db.bookDao().insert(book)

    suspend fun updateBook(book: Book) = db.bookDao().update(book)

    suspend fun insertPages(pages: List<BookPage>) = db.bookPageDao().insertAll(pages)

    suspend fun insertChapter(chapter: Chapter): Long = db.chapterDao().insert(chapter)

    suspend fun updateChapter(chapter: Chapter) = db.chapterDao().update(chapter)

    suspend fun deleteChapter(chapter: Chapter) = db.chapterDao().delete(chapter)

    suspend fun deleteChaptersByBook(bookId: Long) = db.chapterDao().deleteByBook(bookId)

    suspend fun insertBookmark(bookmark: Bookmark): Long = db.bookmarkDao().insert(bookmark)

    suspend fun deleteBookmark(bookmark: Bookmark) = db.bookmarkDao().delete(bookmark)

    suspend fun deleteBook(book: Book) = db.withTransaction {
        db.chapterDao().deleteByBook(book.id)
        db.bookmarkDao().deleteByBook(book.id)
        val pages = db.bookPageDao().getByBook(book.id)
        db.bookPageDao().deleteByBook(book.id)
        db.bookDao().delete(book)

        // 回收已持久化的读权限
        val resolver = context.contentResolver
        (listOf(book.uri) + pages.map { it.uri })
            .filter { it.isNotBlank() }
            .forEach { s ->
                runCatching {
                    resolver.releasePersistableUriPermission(
                        Uri.parse(s),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
    }

    /** 尝试持久化某个 SAF URI 的读权限，失败不影响主流程。 */
    fun takePersistablePermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }
}
