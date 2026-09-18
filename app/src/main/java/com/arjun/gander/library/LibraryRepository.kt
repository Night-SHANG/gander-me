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

    suspend fun readText(id: String): String

    suspend fun bookFile(id: String): File

    suspend fun coverFile(id: String): File?

    suspend fun renameBook(id: String, title: String): LibraryBook?

    suspend fun updateProgress(id: String, readingOffset: Int): LibraryBook?

    suspend fun updateEpubProgress(
        id: String,
        locatorJson: String,
        publicationProgression: Float?,
    ): LibraryBook?

    suspend fun updateUmdProgress(
        id: String,
        locatorJson: String,
        publicationProgression: Float?,
    ): LibraryBook?

    suspend fun deleteBook(id: String): Boolean
}
