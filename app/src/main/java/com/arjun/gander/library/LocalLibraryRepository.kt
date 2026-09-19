package com.arjun.gander.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.arjun.gander.R
import com.vaultshelf.legado.LegadoReaderBridge
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LocalLibraryRepository(context: Context) : LibraryRepository {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun listBooks(): List<LibraryBook> = withContext(Dispatchers.IO) {
        storedBooks()
            .asSequence()
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
        attachLegado(
            importFile(uri, BookFormat.TXT, "txt") { storedFile ->
                TxtDecoder.decode(storedFile.readBytes()).length
            },
        )
    }

    override suspend fun importMarkdown(uri: Uri): LibraryBook = withContext(Dispatchers.IO) {
        importFile(uri, BookFormat.MARKDOWN, "md") { storedFile ->
            TxtDecoder.decode(storedFile.readBytes()).length
        }
    }

    override suspend fun importPdf(uri: Uri): LibraryBook = withContext(Dispatchers.IO) {
        importFile(uri, BookFormat.PDF, "pdf") { 0 }
    }

    override suspend fun importUmd(uri: Uri): LibraryBook = withContext(Dispatchers.IO) {
        attachLegado(importFile(uri, BookFormat.UMD, "umd") { 0 })
    }

    override suspend fun importMobi(
        uri: Uri,
        format: BookFormat,
    ): LibraryBook = withContext(Dispatchers.IO) {
        require(format == BookFormat.MOBI || format == BookFormat.AZW3 || format == BookFormat.AZW) {
            "Unsupported MOBI-family format"
        }
        val extension = when (format) {
            BookFormat.MOBI -> "mobi"
            BookFormat.AZW3 -> "azw3"
            BookFormat.AZW -> "azw"
            else -> error("Unsupported MOBI-family format")
        }
        attachLegado(importFile(uri, format, extension) { 0 })
    }

    override suspend fun importEpub(uri: Uri): LibraryBook = withContext(Dispatchers.IO) {
        attachLegado(importFile(uri, BookFormat.EPUB, "epub") { 0 })
    }

    private fun attachLegado(book: LibraryBook): LibraryBook {
        val snapshot = runCatching {
            LegadoReaderBridge.ensureLocalBook(
                appContext,
                bookFileInternal(book),
                book.title,
            )
        }.getOrNull() ?: return book

        val coverFileName = copyLegadoCover(book.id, snapshot.coverPath)
        val updated = book.copy(
            title = snapshot.title.takeIf { it.isNotBlank() } ?: book.title,
            publicationProgression = snapshot.progress,
            coverFileName = coverFileName ?: book.coverFileName,
            legadoBookUrl = snapshot.bookUrl,
        )
        return if (saveBook(updated)) updated else book
    }

    private fun copyLegadoCover(bookId: String, coverPath: String?): String? {
        val source = coverPath
            ?.let(::File)
            ?.takeIf { it.isFile && it.length() > 0L }
            ?: return null
        val name = "$bookId.cover"
        val target = File(libraryDirectory(), name)
        return runCatching {
            source.copyTo(target, overwrite = true)
            name
        }.getOrNull()
    }

    @android.annotation.SuppressLint("Recycle")
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

            val contentSha256 = sha256(temporaryFile)
            findDuplicate(format, temporaryFile.length(), contentSha256)?.let { existing ->
                temporaryFile.delete()
                return existing
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
                contentSha256 = contentSha256,
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

    override suspend fun coverFile(id: String): File? = withContext(Dispatchers.IO) {
        val book = getBook(id) ?: return@withContext null
        val fileName = book.coverFileName ?: return@withContext null
        File(libraryDirectory(), fileName).takeIf { it.isFile }
    }

    private fun bookFileInternal(book: LibraryBook): File {
        val file = File(libraryDirectory(), book.storedFileName)
        if (!file.isFile) throw FileNotFoundException("Imported book file is missing")
        return file
    }

    override suspend fun renameBook(id: String, title: String): LibraryBook? = withContext(Dispatchers.IO) {
        val current = getBook(id) ?: return@withContext null
        val normalized = title.trim()
        if (normalized.isEmpty()) return@withContext current
        val updated = current.copy(title = normalized)
        if (saveBook(updated)) updated else current
    }

    override suspend fun updateProgress(id: String, readingOffset: Int): LibraryBook? = withContext(Dispatchers.IO) {
        val current = getBook(id) ?: return@withContext null
        val updated = current.copy(
            readingOffset = readingOffset.coerceIn(0, current.totalCharacters.coerceAtLeast(0)),
            lastOpenedAtEpochMillis = System.currentTimeMillis(),
        )
        if (saveBook(updated)) updated else current
    }

    override suspend fun updateViewerProgress(
        id: String,
        progressFraction: Float,
    ): LibraryBook? = withContext(Dispatchers.IO) {
        val current = getBook(id) ?: return@withContext null
        val safe = progressFraction.coerceIn(0f, 1f)
        val updated = when (current.format) {
            BookFormat.MARKDOWN -> current.copy(
                readingOffset = (current.totalCharacters * safe).toInt()
                    .coerceIn(0, current.totalCharacters.coerceAtLeast(0)),
                lastOpenedAtEpochMillis = System.currentTimeMillis(),
            )

            BookFormat.PDF -> current.copy(
                publicationProgression = safe,
                lastOpenedAtEpochMillis = System.currentTimeMillis(),
            )

            else -> current
        }
        if (updated == current) current else if (saveBook(updated)) updated else current
    }

    override suspend fun updateLegadoProgress(
        id: String,
        legadoBookUrl: String,
        progressFraction: Float,
    ): LibraryBook? = withContext(Dispatchers.IO) {
        val current = getBook(id) ?: return@withContext null
        val updated = current.copy(
            legadoBookUrl = legadoBookUrl,
            publicationProgression = progressFraction.coerceIn(0f, 1f),
            lastOpenedAtEpochMillis = System.currentTimeMillis(),
        )
        if (saveBook(updated)) updated else current
    }

    override suspend fun deleteBook(id: String): Boolean = withContext(Dispatchers.IO) {
        val current = getBook(id) ?: return@withContext false
        current.legadoBookUrl?.let { bookUrl ->
            runCatching { LegadoReaderBridge.forgetLocalBook(appContext, bookUrl) }
        }
        File(libraryDirectory(), current.storedFileName).delete()
        current.coverFileName?.let { File(libraryDirectory(), it).delete() }
        preferences.edit().remove(bookKey(id)).commit()
    }

    private fun libraryDirectory(): File = File(appContext.filesDir, LIBRARY_DIRECTORY).apply {
        if (!exists() && !mkdirs()) throw IOException("Unable to create library directory")
    }

    @android.annotation.SuppressLint("Recycle")
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

    private fun storedBooks(): List<LibraryBook> = preferences.all
        .asSequence()
        .filter { (key, value) -> key.startsWith(BOOK_KEY_PREFIX) && value is String }
        .mapNotNull { (_, value) -> runCatching { decodeBook(value as String) }.getOrNull() }
        .toList()

    private fun findDuplicate(
        format: BookFormat,
        sizeBytes: Long,
        contentSha256: String,
    ): LibraryBook? {
        storedBooks()
            .asSequence()
            .filter { it.format == format && it.sizeBytes == sizeBytes }
            .forEach { candidate ->
                val file = File(libraryDirectory(), candidate.storedFileName)
                if (!file.isFile) return@forEach
                val candidateHash = candidate.contentSha256
                    ?: runCatching { sha256(file) }.getOrNull()
                    ?: return@forEach
                if (candidateHash == contentSha256) {
                    return if (candidate.contentSha256 == null) {
                        candidate.copy(contentSha256 = candidateHash).also(::saveBook)
                    } else {
                        candidate
                    }
                }
            }
        return null
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }
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
        .put("publicationProgression", book.publicationProgression ?: JSONObject.NULL)
        .put("coverFileName", book.coverFileName ?: JSONObject.NULL)
        .put("legadoBookUrl", book.legadoBookUrl ?: JSONObject.NULL)
        .put("contentSha256", book.contentSha256 ?: JSONObject.NULL)
        .toString()

    private fun decodeBook(json: String): LibraryBook {
        val value = JSONObject(json)
        val publicationProgression = if (
            value.has("publicationProgression") && !value.isNull("publicationProgression")
        ) {
            value.optDouble("publicationProgression").toFloat()
        } else {
            null
        }
        val coverFileName = value.optString("coverFileName")
            .takeIf { it.isNotBlank() && it != "null" }
        val legadoBookUrl = value.optString("legadoBookUrl")
            .takeIf { it.isNotBlank() && it != "null" }
        val contentSha256 = value.optString("contentSha256")
            .takeIf { it.isNotBlank() && it != "null" }
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
            publicationProgression = publicationProgression,
            coverFileName = coverFileName,
            legadoBookUrl = legadoBookUrl,
            contentSha256 = contentSha256,
        )
    }

    private fun bookKey(id: String): String = "$BOOK_KEY_PREFIX$id"

    private companion object {
        const val PREFERENCES_NAME = "vaultshelf_library"
        const val BOOK_KEY_PREFIX = "book:"
        const val LIBRARY_DIRECTORY = "library"
    }
}
