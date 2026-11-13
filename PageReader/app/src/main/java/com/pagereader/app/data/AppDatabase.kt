package com.pagereader.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Book::class, Region::class, Chapter::class, Bookmark::class, BookPage::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun regionDao(): RegionDao
    abstract fun chapterDao(): ChapterDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun bookPageDao(): BookPageDao
}

/**
 * 1 → 2：新增 regions 表；chapters 改为「书页起点」；bookmarks 改为 pdfPage。
 * 按约定直接清空旧章节/书签（旧数据是基于 PDF 页的），书籍本身保留。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `chapters`")
        db.execSQL("DROP TABLE IF EXISTS `bookmarks`")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `regions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`bookId` INTEGER NOT NULL, " +
                "`orderIndex` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`pdfStart` INTEGER NOT NULL, " +
                "`bookStart` INTEGER, " +
                "FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_regions_bookId` ON `regions` (`bookId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `chapters` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`bookId` INTEGER NOT NULL, " +
                "`orderIndex` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`bookPage` INTEGER NOT NULL, " +
                "FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_bookId` ON `chapters` (`bookId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bookmarks` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`bookId` INTEGER NOT NULL, " +
                "`pdfPage` INTEGER NOT NULL, " +
                "`label` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_bookId` ON `bookmarks` (`bookId`)")
    }
}
