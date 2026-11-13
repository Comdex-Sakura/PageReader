package com.pagereader.app.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.pagereader.app.data.BookPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

data class PageSize(val width: Int, val height: Int)

/** 统一页面源：PDF 或图片，供翻页/滚动两种模式共用。 */
interface PageSource : AutoCloseable {
    val pageCount: Int
    fun pageSize(index: Int): PageSize

    /** 后台渲染一页。maxHeight 传超大值表示“只按宽度适配”（滚动模式）。 */
    suspend fun renderPage(index: Int, maxWidth: Int, maxHeight: Int): Bitmap?

    override fun close()
}

private fun cacheSize(): Int {
    val mb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    return (mb / 8).coerceAtLeast(8 * 1024) * 1024
}

/** 基于系统内置 PdfRenderer 的 PDF 页面源，带位图缓存。 */
class PdfPageSource(context: Context, uri: Uri) : PageSource {

    private val resolver = context.contentResolver
    private val pfd: ParcelFileDescriptor =
        resolver.openFileDescriptor(uri, "r") ?: error("无法打开 PDF 文件")
    private val renderer = PdfRenderer(pfd)
    private val sizes = mutableMapOf<Int, PageSize>()
    private val cache = LruCache<String, Bitmap>(cacheSize())

    override val pageCount: Int get() = renderer.pageCount

    override fun pageSize(index: Int): PageSize = sizes.getOrPut(index) {
        val page = renderer.openPage(index)
        PageSize(page.width, page.height).also { page.close() }
    }

    override suspend fun renderPage(index: Int, maxWidth: Int, maxHeight: Int): Bitmap? {
        val key = "$index@${maxWidth}x$maxHeight"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            cache.get(key) ?: runCatching {
                val s = pageSize(index)
                val scale = min(
                    maxWidth.toFloat() / s.width.coerceAtLeast(1),
                    maxHeight.toFloat() / s.height.coerceAtLeast(1)
                )
                val outW = (s.width * scale).toInt().coerceAtLeast(1)
                val outH = (s.height * scale).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                val page = renderer.openPage(index)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                cache.put(key, bmp)
                bmp
            }.getOrNull()
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}

/** 图片页面源：每页对应一张图，按需降采样解码并缓存。 */
class ImagePageSource(context: Context, pages: List<BookPage>) : PageSource {

    private val resolver = context.contentResolver
    private val uris = pages.sortedBy { it.pageIndex }.map { Uri.parse(it.uri) }
    private val sizes = mutableMapOf<Int, PageSize>()
    private val cache = LruCache<String, Bitmap>(cacheSize())

    override val pageCount: Int get() = uris.size

    override fun pageSize(index: Int): PageSize = sizes.getOrPut(index) {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(uris[index])?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }
        PageSize(opts.outWidth.coerceAtLeast(0), opts.outHeight.coerceAtLeast(0))
    }

    override suspend fun renderPage(index: Int, maxWidth: Int, maxHeight: Int): Bitmap? {
        val key = "$index@${maxWidth}"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            cache.get(key) ?: decode(index, maxWidth)?.also { cache.put(key, it) }
        }
    }

    private fun decode(index: Int, maxWidth: Int): Bitmap? = runCatching {
        val uri = uris[index]
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return@runCatching null

        var sample = 1
        val mw = maxWidth.coerceAtLeast(1)
        while (w / (sample * 2) >= mw) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()

    override fun close() {
        // 无需释放系统资源
    }
}
