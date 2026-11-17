package com.pagereader.app.reader

import android.content.Context
import android.net.Uri

/** 通过 SAF 读写文本文件（用于目录 JSON 的导入/导出）。 */
object TocFileIo {

    fun readText(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        }
    }.getOrNull()

    fun writeText(context: Context, uri: Uri, text: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri)?.use {
            it.write(text.toByteArray(Charsets.UTF_8))
            it.flush()
        }
        true
    }.getOrDefault(false)
}
