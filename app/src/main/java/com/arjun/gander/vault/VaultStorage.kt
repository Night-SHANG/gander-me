package com.arjun.gander.vault

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.arjun.gander.library.BookFormat
import com.arjun.gander.library.LibraryBook
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import sushi.hardcore.droidfs.VolumeManagerApp
import sushi.hardcore.droidfs.explorers.ExplorerElement
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.filesystems.Stat
import sushi.hardcore.droidfs.util.PathUtils

data class VaultFileItem(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val modifiedAtEpochMillis: Long,
    val isDirectory: Boolean,
)

data class VaultLibraryEntry(
    val id: String,
    val path: String,
    val title: String,
    val format: BookFormat,
    val sizeBytes: Long,
    val addedAtEpochMillis: Long,
    val lastOpenedAtEpochMillis: Long,
)

class VaultFileRepository(
    context: Context,
    val volumeId: Int,
) {
    private val appContext = context.applicationContext
    private val volumeManager = (appContext as VolumeManagerApp).volumeManager
    val volume: EncryptedVolume = requireNotNull(volumeManager.getVolume(volumeId)) {
        "Vault volume is not open"
    }
    val volumeUuid: String = requireNotNull(
        volumeManager.listVolumes().firstOrNull { it.first == volumeId }?.second?.uuid,
    ) {
        "Vault volume metadata is unavailable"
    }

    fun list(path: String): List<VaultFileItem> {
        return volume.readDir(path)
            .orEmpty()
            .asSequence()
            .filterNot { it.isParentFolder }
            .filterNot { path.isBlank() && it.name == METADATA_DIRECTORY_NAME }
            .sortedWith(
                compareByDescending<ExplorerElement> { it.isDirectory }
                    .thenBy { it.name.lowercase() },
            )
            .map {
                VaultFileItem(
                    name = it.name,
                    path = it.fullPath,
                    sizeBytes = it.stat.size.coerceAtLeast(0L),
                    modifiedAtEpochMillis = it.stat.mTime.coerceAtLeast(0L),
                    isDirectory = it.isDirectory,
                )
            }
            .toList()
    }

    fun importUri(uri: Uri, parentPath: String): String {
        val displayName = displayName(uri)
        val destination = uniquePath(parentPath, displayName)
        check(volume.importFile(appContext, uri, destination)) {
            "Failed to import into encrypted volume"
        }
        return destination
    }

    fun importFile(file: File, displayName: String, parentPath: String): String {
        require(file.isFile)
        ensureDirectory(parentPath)
        val destination = uniquePath(parentPath, displayName)
        file.inputStream().buffered().use { input ->
            check(volume.importFile(input, destination)) {
                "Failed to import into encrypted volume"
            }
        }
        return destination
    }

    fun createFolder(parentPath: String, name: String): Boolean {
        val safe = sanitizeFileName(name).trim()
        if (safe.isBlank()) return false
        val path = PathUtils.pathJoin(parentPath.ifBlank { "/" }, safe)
        return !volume.pathExists(path) && volume.mkdir(path)
    }

    fun delete(path: String): Boolean {
        val stat = volume.getAttr(path) ?: return true
        return if (stat.type == Stat.S_IFDIR) {
            val children = volume.readDir(path).orEmpty().filterNot { it.isParentFolder }
            children.all { delete(it.fullPath) } && volume.rmdir(path)
        } else {
            volume.deleteFile(path)
        }
    }

    fun ensureDirectory(path: String) {
        if (path.isBlank() || path == "/") return
        if (volume.pathExists(path)) return
        val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
        ensureDirectory(parent)
        check(volume.mkdir(path)) { "Unable to create vault directory: $path" }
    }

    fun uniquePath(parentPath: String, requestedName: String): String {
        val sanitized = sanitizeFileName(requestedName).ifBlank { "file" }
        val base = sanitized.substringBeforeLast('.', missingDelimiterValue = sanitized)
        val extension = sanitized.substringAfterLast('.', missingDelimiterValue = "")
        fun candidate(index: Int): String {
            val fileName = if (index == 1) {
                sanitized
            } else if (extension.isBlank()) {
                "$base ($index)"
            } else {
                "$base ($index).$extension"
            }
            return PathUtils.pathJoin(parentPath.ifBlank { "/" }, fileName)
        }

        var index = 1
        while (volume.pathExists(candidate(index))) index += 1
        return candidate(index)
    }

    private fun displayName(uri: Uri): String {
        return runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "file"
    }

    companion object {
        const val BOOKS_DIRECTORY = "/Books"
        const val METADATA_DIRECTORY = "/.vaultshelf"
        private const val METADATA_DIRECTORY_NAME = ".vaultshelf"

        fun sanitizeFileName(name: String): String =
            name.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }
}

class VaultLibraryStore(
    context: Context,
    private val fileRepository: VaultFileRepository,
) {
    private val volume = fileRepository.volume

    @Synchronized
    fun listBooks(): List<VaultLibraryEntry> {
        return readEntries()
            .filter { entry -> volume.getAttr(entry.path)?.type == Stat.S_IFREG }
            .sortedWith(
                compareByDescending<VaultLibraryEntry> {
                    if (it.lastOpenedAtEpochMillis > 0L) it.lastOpenedAtEpochMillis else it.addedAtEpochMillis
                }.thenBy { it.title.lowercase() },
            )
    }

    fun supportsPath(path: String): Boolean = formatForPath(path) != null

    @Synchronized
    fun addPath(path: String, titleOverride: String? = null): VaultLibraryEntry {
        val format = requireNotNull(formatForPath(path)) { "Unsupported library format" }
        val stat = volume.getAttr(path)
        require(stat?.type == Stat.S_IFREG) { "Vault book is not a regular file" }

        val entries = readEntries().toMutableList()
        entries.firstOrNull { it.path == path }?.let { return it }

        val fileName = File(path).name
        val title = titleOverride
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
                .ifBlank { fileName }

        val entry = VaultLibraryEntry(
            id = UUID.randomUUID().toString(),
            path = path,
            title = title,
            format = format,
            sizeBytes = stat.size.coerceAtLeast(0L),
            addedAtEpochMillis = System.currentTimeMillis(),
            lastOpenedAtEpochMillis = 0L,
        )
        entries += entry
        writeEntries(entries)
        return entry
    }

    @Synchronized
    fun remove(id: String): Boolean {
        val entries = readEntries().toMutableList()
        val changed = entries.removeAll { it.id == id }
        if (changed) writeEntries(entries)
        return changed
    }

    @Synchronized
    fun removePath(path: String) {
        val entries = readEntries().toMutableList()
        if (entries.removeAll { it.path == path || it.path.startsWith(path.trimEnd('/') + "/") }) {
            writeEntries(entries)
        }
    }

    @Synchronized
    fun markOpened(id: String) {
        val entries = readEntries().toMutableList()
        val index = entries.indexOfFirst { it.id == id }
        if (index < 0) return
        entries[index] = entries[index].copy(lastOpenedAtEpochMillis = System.currentTimeMillis())
        writeEntries(entries)
    }

    fun importExternalLibraryBook(book: LibraryBook, sourceFile: File): VaultLibraryEntry {
        fileRepository.ensureDirectory(VaultFileRepository.BOOKS_DIRECTORY)
        val extension = sourceFile.extension.takeIf { it.isNotBlank() } ?: formatExtension(book.format)
        val requestedName = buildString {
            append(VaultFileRepository.sanitizeFileName(book.title).ifBlank { "book" })
            if (extension.isNotBlank()) append('.').append(extension)
        }
        val path = fileRepository.importFile(
            sourceFile,
            requestedName,
            VaultFileRepository.BOOKS_DIRECTORY,
        )
        return addPath(path, book.title)
    }

    private fun readEntries(): List<VaultLibraryEntry> {
        if (!volume.pathExists(METADATA_FILE)) return emptyList()
        val (bytes, errorCode) = volume.loadWholeFile(
            METADATA_FILE,
            maxSize = MAX_METADATA_BYTES,
        )
        if (bytes == null || errorCode != 0) return emptyList()
        return runCatching {
            val array = JSONArray(String(bytes, Charsets.UTF_8))
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.getJSONObject(index)
                    val format = runCatching {
                        BookFormat.valueOf(value.getString("format"))
                    }.getOrNull() ?: continue
                    add(
                        VaultLibraryEntry(
                            id = value.getString("id"),
                            path = value.getString("path"),
                            title = value.getString("title"),
                            format = format,
                            sizeBytes = value.optLong("sizeBytes", 0L),
                            addedAtEpochMillis = value.optLong("addedAtEpochMillis", 0L),
                            lastOpenedAtEpochMillis = value.optLong("lastOpenedAtEpochMillis", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeEntries(entries: List<VaultLibraryEntry>) {
        fileRepository.ensureDirectory(VaultFileRepository.METADATA_DIRECTORY)
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("path", entry.path)
                    .put("title", entry.title)
                    .put("format", entry.format.name)
                    .put("sizeBytes", entry.sizeBytes)
                    .put("addedAtEpochMillis", entry.addedAtEpochMillis)
                    .put("lastOpenedAtEpochMillis", entry.lastOpenedAtEpochMillis),
            )
        }
        val bytes = array.toString().toByteArray(Charsets.UTF_8)

        if (volume.pathExists(METADATA_NEW)) volume.deleteFile(METADATA_NEW)
        check(volume.importFile(ByteArrayInputStream(bytes), METADATA_NEW)) {
            "Unable to write vault library metadata"
        }

        val hadPrevious = volume.pathExists(METADATA_FILE)
        if (volume.pathExists(METADATA_BACKUP)) volume.deleteFile(METADATA_BACKUP)
        if (hadPrevious) {
            check(volume.rename(METADATA_FILE, METADATA_BACKUP)) {
                "Unable to stage vault library metadata"
            }
        }

        if (!volume.rename(METADATA_NEW, METADATA_FILE)) {
            if (hadPrevious && volume.pathExists(METADATA_BACKUP)) {
                volume.rename(METADATA_BACKUP, METADATA_FILE)
            }
            error("Unable to commit vault library metadata")
        }
        if (volume.pathExists(METADATA_BACKUP)) volume.deleteFile(METADATA_BACKUP)
    }

    companion object {
        private const val METADATA_FILE = "/.vaultshelf/library.json"
        private const val METADATA_NEW = "/.vaultshelf/library.json.new"
        private const val METADATA_BACKUP = "/.vaultshelf/library.json.bak"
        private const val MAX_METADATA_BYTES = 2L * 1024L * 1024L

        fun formatForPath(path: String): BookFormat? =
            when (File(path).extension.lowercase()) {
                "txt" -> BookFormat.TXT
                "epub" -> BookFormat.EPUB
                "md", "markdown" -> BookFormat.MARKDOWN
                "pdf" -> BookFormat.PDF
                "umd" -> BookFormat.UMD
                "mobi" -> BookFormat.MOBI
                "azw3" -> BookFormat.AZW3
                "azw" -> BookFormat.AZW
                else -> null
            }

        private fun formatExtension(format: BookFormat): String =
            when (format) {
                BookFormat.TXT -> "txt"
                BookFormat.EPUB -> "epub"
                BookFormat.MARKDOWN -> "md"
                BookFormat.PDF -> "pdf"
                BookFormat.UMD -> "umd"
                BookFormat.MOBI -> "mobi"
                BookFormat.AZW3 -> "azw3"
                BookFormat.AZW -> "azw"
            }
    }
}
