package com.pagereader.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): Book?

    @Insert
    suspend fun insert(book: Book): Long

    @Update
    suspend fun update(book: Book)

    @Delete
    suspend fun delete(book: Book)
}

@Dao
interface RegionDao {
    @Query("SELECT * FROM regions WHERE bookId = :bookId ORDER BY pdfStart ASC")
    fun getByBook(bookId: Long): Flow<List<Region>>

    @Insert
    suspend fun insert(region: Region): Long

    @Update
    suspend fun update(region: Region)

    @Delete
    suspend fun delete(region: Region)

    @Query("DELETE FROM regions WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: Long)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY bookPage ASC, orderIndex ASC")
    fun getByBook(bookId: Long): Flow<List<Chapter>>

    @Insert
    suspend fun insert(chapter: Chapter): Long

    @Update
    suspend fun update(chapter: Chapter)

    @Delete
    suspend fun delete(chapter: Chapter)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY pdfPage ASC, createdAt ASC")
    fun getByBook(bookId: Long): Flow<List<Bookmark>>

    @Insert
    suspend fun insert(bookmark: Bookmark): Long

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: Long)
}

@Dao
interface BookPageDao {
    @Query("SELECT * FROM book_pages WHERE bookId = :bookId ORDER BY pageIndex ASC")
    suspend fun getByBook(bookId: Long): List<BookPage>

    @Insert
    suspend fun insertAll(pages: List<BookPage>)

    @Query("DELETE FROM book_pages WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: Long)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE bookId = :bookId ORDER BY pdfPage ASC, createdAt ASC")
    fun getByBook(bookId: Long): Flow<List<Note>>

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    @Query("DELETE FROM notes WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: Long)
}
