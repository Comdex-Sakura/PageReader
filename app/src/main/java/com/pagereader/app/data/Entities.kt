package com.pagereader.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/** 阅读模式常量，存进数据库用 Int，避免额外转换负担。 */
object ReadingMode {
    const val PAGED = 0   // 左右翻页
    const val SCROLL = 1  // 上下滚动
}

enum class BookType { PDF, IMAGE }

class Converters {
    @TypeConverter
    fun bookTypeToInt(type: BookType): Int = type.ordinal

    @TypeConverter
    fun intToBookType(value: Int): BookType = BookType.entries[value]
}

/** 一本书：一个 PDF 或一组图片。 */
@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: BookType,
    /** 主内容 URI；图片书存第一张图，仅用于权限回收。 */
    val uri: String,
    val totalPages: Int,
    /** 上次阅读到的页（0 起）。 */
    val currentPage: Int = 0,
    val readingMode: Int = ReadingMode.PAGED,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 分区：把 PDF 切成若干区段（封面/目录/正文…）。
 * - [pdfStart] 该区段在 PDF 中的起始页（0 起）。
 * - [bookStart] 该区段第一页对应的「书页码」；为 null 表示该区不参与书页码编号。
 * 映射关系：pdfPage = bookPage - bookStart + pdfStart。
 */
@Entity(
    tableName = "regions",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class Region(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val orderIndex: Int,
    val name: String,
    val pdfStart: Int,
    val bookStart: Int?
)

/** 章节：只记录「起始书页码」，结束页由下一章起始页推出。 */
@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class Chapter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val orderIndex: Int,
    val title: String,
    /** 书页码（1 起）。 */
    val bookPage: Int
)

/** 书签：记录实际 PDF 页（0 起），展示时再换算成书页码。 */
@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val pdfPage: Int,
    val label: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** 图片书的每一页对应一张图片。 */
@Entity(
    tableName = "book_pages",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class BookPage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val pageIndex: Int,
    val uri: String
)

/** 页面上的笔记文本框，位置为相对页面左上角的 0..1 比例。 */
@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val pdfPage: Int,
    val text: String,
    val x: Float = 0.35f,
    val y: Float = 0.4f,
    /** ARGB。 */
    val color: Long = 0xFFE53935,
    val fontSize: Float = 16f,
    val rotation: Float = 0f,
    /** 0=默认 1=宋体 2=等宽 3=手写。 */
    val fontFamily: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
