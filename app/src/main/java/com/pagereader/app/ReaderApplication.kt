package com.pagereader.app

import android.app.Application
import androidx.room.Room
import com.pagereader.app.data.AppDatabase
import com.pagereader.app.data.Repository
import com.pagereader.app.data.SettingsStore

class ReaderApplication : Application() {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "pagereader.db").build()
    }

    val repository: Repository by lazy { Repository(this, database) }

    val settings: SettingsStore by lazy { SettingsStore(this) }
}
