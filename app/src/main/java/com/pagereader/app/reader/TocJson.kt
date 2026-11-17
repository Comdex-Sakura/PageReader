package com.pagereader.app.reader

import com.pagereader.app.data.Chapter
import com.pagereader.app.data.Region
import org.json.JSONArray
import org.json.JSONObject

/** 解析出来的目录数据（id / bookId 由仓库层在写入时补全）。 */
data class TocData(val regions: List<Region>, val chapters: List<Chapter>)

/**
 * 目录 JSON 格式（便于手动编辑）：
 * {
 *   "version": 1,
 *   "regions": [ { "name": "正文", "pdfStart": 15, "bookStart": 1 } ],
 *   "chapters": [ { "title": "第一章", "bookPage": 1 } ]
 * }
 * 注意：pdfStart / bookStart / bookPage 在 JSON 里是人类可读的「1 起」页码。
 */
object TocJson {

    fun export(regions: List<Region>, chapters: List<Chapter>): String {
        val root = JSONObject()
        root.put("version", 1)

        val rArr = JSONArray()
        regions.sortedBy { it.pdfStart }.forEach { r ->
            val o = JSONObject()
            o.put("name", r.name)
            o.put("pdfStart", r.pdfStart + 1)
            o.put("bookStart", r.bookStart ?: JSONObject.NULL)
            rArr.put(o)
        }
        root.put("regions", rArr)

        val cArr = JSONArray()
        chapters.sortedBy { it.bookPage }.forEach { c ->
            val o = JSONObject()
            o.put("title", c.title)
            o.put("bookPage", c.bookPage)
            cArr.put(o)
        }
        root.put("chapters", cArr)

        return root.toString(2)
    }

    fun parse(text: String): Result<TocData> = runCatching {
        val root = JSONObject(text)
        val regions = mutableListOf<Region>()
        val rArr = root.optJSONArray("regions") ?: JSONArray()
        for (i in 0 until rArr.length()) {
            val o = rArr.getJSONObject(i)
            val bookStart = if (o.isNull("bookStart")) {
                null
            } else {
                o.optInt("bookStart", 0).takeIf { it > 0 }
            }
            regions.add(
                Region(
                    bookId = 0,
                    orderIndex = i,
                    name = o.optString("name", "未命名分区"),
                    pdfStart = (o.optInt("pdfStart", 1) - 1).coerceAtLeast(0),
                    bookStart = bookStart
                )
            )
        }

        val chapters = mutableListOf<Chapter>()
        val cArr = root.optJSONArray("chapters") ?: JSONArray()
        for (i in 0 until cArr.length()) {
            val o = cArr.getJSONObject(i)
            val bp = o.optInt("bookPage", 0)
            if (bp >= 1) {
                chapters.add(
                    Chapter(
                        bookId = 0,
                        orderIndex = i,
                        title = o.optString("title", "未命名章节"),
                        bookPage = bp
                    )
                )
            }
        }
        TocData(regions, chapters)
    }
}
