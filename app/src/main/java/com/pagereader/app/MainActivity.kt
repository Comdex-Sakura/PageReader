package com.pagereader.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pagereader.app.ui.bookshelf.BookshelfScreen
import com.pagereader.app.ui.reader.ReaderScreen
import com.pagereader.app.ui.theme.PageReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PageReaderTheme {
                var openBookId by remember { mutableStateOf<Long?>(null) }
                val current = openBookId
                if (current == null) {
                    BookshelfScreen(onOpenBook = { openBookId = it })
                } else {
                    ReaderScreen(bookId = current, onBack = { openBookId = null })
                }
            }
        }
    }
}
