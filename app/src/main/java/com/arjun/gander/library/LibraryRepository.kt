package com.arjun.gander.library

import android.net.Uri
import java.io.File

interface LibraryRepository {
    suspend fun listBooks(): List<LibraryBook>

    suspend fun getBook(id: String): LibraryBook?

    suspend fun importTxt(uri: Uri): LibraryBook

    suspend fun importEpub(uri: Uri): LibraryBook

    suspend fun importMarkdown(uri: Uri): LibraryBook

    suspend fun importPdf(uri: Uri): LibraryBook

    suspend fun importUmd(uri: Uri): LibraryBook

    suspend fun importMobi(uri: Uri, format: BookFormat): LibraryBook

    suspend fun readText(id: String): String

    suspend fun bookFile(id: String): File

    suspend fun coverFile(id: String): File?

    suspend fun renameBook(id: String, title: String): LibraryBook?

    suspend fun updateProgress(id: String, readingOffset: Int): LibraryBook?

    suspend fun updateViewerProgress(
        id: String,
        progressFraction: Float,
    ): LibraryBook? = getBook(id)

    suspend fun updateLegadoProgress(
        id: String,
        legadoBookUrl: String,
        progressFraction: Float,
    ): LibraryBook? = getBook(id)

    suspend fun deleteBook(id: String): Boolean

    suspend fun deleteOriginalSource(id: String): Boolean = false
}
