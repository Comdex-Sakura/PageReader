package com.pagereader.app.reader

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OutlineEntry(val title: String, val pageIndex: Int)

/**
 * 提取 PDF 内置书签大纲。任何异常都会被吞掉并返回空列表，
 * 由上层决定是否降级为“纯手动章节”。
 */
object OutlineExtractor {

    suspend fun extract(context: Context, uri: Uri): List<OutlineEntry> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<OutlineEntry>()
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    PDDocument.load(stream).use { doc ->
                        val outline = doc.documentCatalog.documentOutline
                        if (outline == null) return@use
                        val pages = doc.documentCatalog.pages

                        fun pageIndexOf(page: PDPage?): Int {
                            if (page == null) return -1
                            var i = 0
                            for (p in pages) {
                                if (p === page) return i
                                i++
                            }
                            return -1
                        }

                        fun walk(items: Iterable<PDOutlineItem>) {
                            for (item in items) {
                                val title = item.title?.takeIf { it.isNotBlank() } ?: "未命名"
                                val page = runCatching { item.findDestinationPage(doc) }.getOrNull()
                                val idx = pageIndexOf(page)
                                if (idx >= 0) result.add(OutlineEntry(title, idx))
                                if (item.hasChildren()) walk(item.children())
                            }
                        }

                        walk(outline.children())
                    }
                }
            }
            result
        }
}
