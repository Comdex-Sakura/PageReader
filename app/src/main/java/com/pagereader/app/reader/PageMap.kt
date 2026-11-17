package com.pagereader.app.reader

import com.pagereader.app.data.Region

/**
 * 解析后的分区：PDF 页区间 [pdfStart, pdfEnd]，以及该区间起始对应的书页码。
 * bookStart 为 null 表示该区不编号（封面、目录等）。
 */
data class ResolvedRegion(
    val name: String,
    val pdfStart: Int,
    val pdfEnd: Int,
    val bookStart: Int?
) {
    val pageCount: Int get() = (pdfEnd - pdfStart + 1).coerceAtLeast(0)
    val bookEnd: Int? get() = bookStart?.let { it + pageCount - 1 }
}

object PageMap {

    /**
     * 把数据库里的分区解析成可用的区间。
     * - 没有分区时，默认整本书是一个「正文」区，书页 = PDF页 + 1。
     * - 区间结束 = 下一分区起始页 - 1；最后一个区到书末。
     */
    fun resolve(regions: List<Region>, totalPages: Int): List<ResolvedRegion> {
        if (totalPages <= 0) return emptyList()
        val sorted = regions.sortedBy { it.pdfStart }
        if (sorted.isEmpty()) {
            return listOf(ResolvedRegion("正文", 0, totalPages - 1, 1))
        }
        return sorted.mapIndexed { i, r ->
            val start = r.pdfStart.coerceIn(0, totalPages - 1)
            val end = if (i < sorted.size - 1) {
                (sorted[i + 1].pdfStart - 1).coerceIn(start, totalPages - 1)
            } else {
                totalPages - 1
            }
            ResolvedRegion(r.name, start, end, r.bookStart)
        }
    }

    fun regionByPdf(resolved: List<ResolvedRegion>, pdfPage: Int): ResolvedRegion? =
        resolved.firstOrNull { pdfPage in it.pdfStart..it.pdfEnd }

    fun regionByBook(resolved: List<ResolvedRegion>, bookPage: Int): ResolvedRegion? =
        resolved.firstOrNull { r ->
            val bs = r.bookStart ?: return@firstOrNull false
            val be = r.bookEnd ?: return@firstOrNull false
            bookPage in bs..be
        }

    /** PDF 页 → 书页码；落在不编号分区时返回 null。 */
    fun pdfToBook(resolved: List<ResolvedRegion>, pdfPage: Int): Int? {
        val r = regionByPdf(resolved, pdfPage) ?: return null
        val bs = r.bookStart ?: return null
        return bs + (pdfPage - r.pdfStart)
    }

    /** 书页码 → PDF 页；没有任何编号分区能覆盖时返回 null。 */
    fun bookToPdf(resolved: List<ResolvedRegion>, bookPage: Int): Int? {
        val r = regionByBook(resolved, bookPage) ?: return null
        val bs = r.bookStart ?: return null
        return r.pdfStart + (bookPage - bs)
    }

    /** 章节结束书页：下一章起始页 - 1，且不超过所在分区末页。 */
    fun chapterEnd(
        resolved: List<ResolvedRegion>,
        chapterBookPage: Int,
        nextBookPage: Int?
    ): Int {
        val region = regionByBook(resolved, chapterBookPage)
        val regionEnd = region?.bookEnd ?: chapterBookPage
        return if (nextBookPage != null && nextBookPage > chapterBookPage) {
            minOf(nextBookPage - 1, regionEnd)
        } else {
            regionEnd
        }
    }
}
