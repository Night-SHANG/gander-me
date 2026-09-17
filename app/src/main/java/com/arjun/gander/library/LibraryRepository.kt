package com.arjun.gander.library

import android.net.Uri

interface LibraryRepository {
    suspend fun listBooks(): List<LibraryBook>

    suspend fun getBook(id: String): LibraryBook?

    suspend fun importTxt(uri: Uri): LibraryBook

    suspend fun readText(id: String): String

    suspend fun updateProgress(id: String, readingOffset: Int): LibraryBook?

    suspend fun deleteBook(id: String): Boolean
}