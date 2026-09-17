package com.arjun.gander.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.arjun.gander.R
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LocalLibraryRepository(context: Context) : LibraryRepository {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun listBooks(): List<LibraryBook> = withContext(Dispatchers.IO) {
        preferences.all
            .asSequence()
            .filter { (key, value) -> key.startsWith(BOOK_KEY_PREFIX) && value is String }
            .mapNotNull { (_, value) -> runCatching { decodeBook(value as String) }.getOrNull() }
            .sortedWith(
                compareByDescending<LibraryBook> {
                    if (it.lastOpenedAtEpochMillis > 0L) it.lastOpenedAtEpochMillis else it.addedAtEpochMillis
                }.thenBy { it.title.lowercase() },
            )
            .toList()
    }

    override suspend fun getBook(id: String): LibraryBook? = withContext(Dispatchers.IO) {
        preferences.getString(bookKey(id), null)?.let { runCatching { decodeBook(it) }.getOrNull() }
    }

    override suspend fun importTxt(uri: Uri): LibraryBook = withContext(Dispatchers.IO) {
        importFile(uri, BookFormat.TXT, "txt") { storedFile ->
            TxtDecoder.decode(storedFile.readBytes()).length
        }
    }

    override suspend fun importEpub(uri: Uri): LibraryBook = withContext(Dispatchers.IO) {
        importFile(uri, BookFormat.EPUB, "epub") { 0 }
    }

    private fun importFile(
        uri: Uri,
        format: BookFormat,
        extension: String,
        characterCounter: (File) -> Int,
    ): LibraryBook {
        val id = UUID.randomUUID().toString()
        val directory = libraryDirectory()
        val temporaryFile = File(directory, ".$id.importing")
        val storedFileName = "$id.$extension"
        val storedFile = File(directory, storedFileName)

        try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw FileNotFoundException("Unable to open imported file")
            input.use { source ->
                temporaryFile.outputStream().buffered().use { destination ->
                    source.copyTo(destination)
                }
            }

            if (!temporaryFile.renameTo(storedFile)) {
                temporaryFile.copyTo(storedFile, overwrite = true)
                temporaryFile.delete()
            }

            val now = System.currentTimeMillis()
            val sourceName = displayName(uri)
            val title = sourceName
                .substringBeforeLast('.', missingDelimiterValue = sourceName)
                .trim()
                .ifEmpty { appContext.getString(R.string.vaultshelf_untitled_book) }
            val book = LibraryBook(
                id = id,
                title = title,
                storedFileName = storedFileName,
                format = format,
                sizeBytes = storedFile.length(),
                totalCharacters = characterCounter(storedFile),
                addedAtEpochMillis = now,
                lastOpenedAtEpochMillis = 0L,
                readingOffset = 0,
            )
            if (!saveBook(book)) throw IOException("Unable to save library metadata")
            return book
        } catch (error: Throwable) {
            temporaryFile.delete()
            storedFile.delete()
            throw error
        }
    }

    override suspend fun readText(id: String): String = withContext(Dispatchers.IO) {
        val book = getBook(id) ?: throw FileNotFoundException("Book metadata is missing")
        if (book.format != BookFormat.TXT) throw IOException("Book is not a text publication")
        TxtDecoder.decode(bookFileInternal(book).readBytes())
    }

    override suspend fun bookFile(id: String): File = withContext(Dispatchers.IO) {
        val book = getBook(id) ?: throw FileNotFoundException("Book metadata is missing")
        bookFileInternal(book)
    }

    private fun bookFileInternal(book: LibraryBook): File {
        val file = File(libraryDirectory(), book.storedFileName)
        if (!file.isFile) throw FileNotFoundException("Imported book file is missing")
        return file
    }

    override suspend fun updateProgress(id: String, readingOffset: Int): LibraryBook? = withContext(Dispatchers.IO) {
        val current = getBook(id) ?: return@withContext null
        val updated = current.copy(
            readingOffset = readingOffset.coerceIn(0, current.totalCharacters.coerceAtLeast(0)),
            lastOpenedAtEpochMillis = System.currentTimeMillis(),
        )
        if (saveBook(updated)) updated else current
    }

    override suspend fun updateEpubProgress(
        id: String,
        locatorJson: String,
        publicationProgression: Float?,
    ): LibraryBook? = withContext(Dispatchers.IO) {
        val current = getBook(id) ?: return@withContext null
        if (current.format != BookFormat.EPUB) return@withContext current
        val updated = current.copy(
            readingLocatorJson = locatorJson,
            publicationProgression = publicationProgression?.coerceIn(0f, 1f),
            lastOpenedAtEpochMillis = System.currentTimeMillis(),
        )
        if (saveBook(updated)) updated else current
    }

    override suspend fun deleteBook(id: String): Boolean = withContext(Dispatchers.IO) {
        val current = getBook(id) ?: return@withContext false
        File(libraryDirectory(), current.storedFileName).delete()
        preferences.edit().remove(bookKey(id)).commit()
    }

    private fun libraryDirectory(): File = File(appContext.filesDir, LIBRARY_DIRECTORY).apply {
        if (!exists() && !mkdirs()) throw IOException("Unable to create library directory")
    }

    private fun displayName(uri: Uri): String {
        runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    cursor.getString(index)?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        }.getOrNull()?.let { return it }

        return uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: appContext.getString(R.string.vaultshelf_untitled_book)
    }

    private fun saveBook(book: LibraryBook): Boolean = preferences.edit()
        .putString(bookKey(book.id), encodeBook(book))
        .commit()

    private fun encodeBook(book: LibraryBook): String = JSONObject()
        .put("id", book.id)
        .put("title", book.title)
        .put("storedFileName", book.storedFileName)
        .put("format", book.format.name)
        .put("sizeBytes", book.sizeBytes)
        .put("totalCharacters", book.totalCharacters)
        .put("addedAtEpochMillis", book.addedAtEpochMillis)
        .put("lastOpenedAtEpochMillis", book.lastOpenedAtEpochMillis)
        .put("readingOffset", book.readingOffset)
        .put("readingLocatorJson", book.readingLocatorJson ?: JSONObject.NULL)
        .put("publicationProgression", book.publicationProgression ?: JSONObject.NULL)
        .toString()

    private fun decodeBook(json: String): LibraryBook {
        val value = JSONObject(json)
        val locatorJson = value.optString("readingLocatorJson")
            .takeIf { it.isNotBlank() && it != "null" }
        val publicationProgression = if (
            value.has("publicationProgression") && !value.isNull("publicationProgression")
        ) {
            value.optDouble("publicationProgression").toFloat()
        } else {
            null
        }
        return LibraryBook(
            id = value.getString("id"),
            title = value.getString("title"),
            storedFileName = value.getString("storedFileName"),
            format = BookFormat.valueOf(value.getString("format")),
            sizeBytes = value.getLong("sizeBytes"),
            totalCharacters = value.optInt("totalCharacters", 0),
            addedAtEpochMillis = value.getLong("addedAtEpochMillis"),
            lastOpenedAtEpochMillis = value.optLong("lastOpenedAtEpochMillis", 0L),
            readingOffset = value.optInt("readingOffset", 0),
            readingLocatorJson = locatorJson,
            publicationProgression = publicationProgression,
        )
    }

    private fun bookKey(id: String): String = "$BOOK_KEY_PREFIX$id"

    private companion object {
        const val PREFERENCES_NAME = "vaultshelf_library"
        const val BOOK_KEY_PREFIX = "book:"
        const val LIBRARY_DIRECTORY = "library"
    }
}