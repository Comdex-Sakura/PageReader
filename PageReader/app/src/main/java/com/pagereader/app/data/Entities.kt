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

/** 自定义章节：一个 [startPage, endPage] 闭区间（0 起）。 */
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
    val startPage: Int,
    val endPage: Int
)

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
    val page: Int,
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
