package com.pagereader.app.data

import android.content.Context

/**
 * 全局偏好设置：夜间模式、亮度、默认阅读模式。
 * 亮度 -1f 表示跟随系统。
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var nightMode: Boolean
        get() = prefs.getBoolean("night_mode", false)
        set(value) = prefs.edit().putBoolean("night_mode", value).apply()

    var brightness: Float
        get() = prefs.getFloat("brightness", -1f)
        set(value) = prefs.edit().putFloat("brightness", value).apply()

    var defaultReadingMode: Int
        get() = prefs.getInt("reading_mode", ReadingMode.PAGED)
        set(value) = prefs.edit().putInt("reading_mode", value).apply()
}
