package com.arjun.gander.vault

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.arjun.gander.BookReadingPositions
import com.arjun.gander.Positions
import com.arjun.gander.library.BookFormat
import com.arjun.gander.library.LibraryBook
import com.arjun.gander.library.LocalLibraryRepository
import com.vaultshelf.droidfs.VaultShelfProgressStore
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
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
    val sourcePath: String? = null,
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

    fun copyWithinVolume(sourcePath: String, destinationPath: String): Boolean =
        copyBetweenVolumes(volume, sourcePath, volume, destinationPath)

    fun copyFromVolume(
        sourceVolume: EncryptedVolume,
        sourcePath: String,
        destinationPath: String,
    ): Boolean = copyBetweenVolumes(sourceVolume, sourcePath, volume, destinationPath)

    fun inputStream(path: String): InputStream = EncryptedVolumeInputStream(volume, path)

    private fun copyBetweenVolumes(
        sourceVolume: EncryptedVolume,
        sourcePath: String,
        destinationVolume: EncryptedVolume,
        destinationPath: String,
    ): Boolean {
        val sourceHandle = sourceVolume.openFileReadMode(sourcePath)
        if (sourceHandle == -1L) return false
        val destinationHandle = destinationVolume.openFileWriteMode(destinationPath)
        if (destinationHandle == -1L) {
            sourceVolume.closeFile(sourceHandle)
            return false
        }
        return try {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var sourceOffset = 0L
            var destinationOffset = 0L
            while (true) {
                val read = sourceVolume.read(
                    sourceHandle,
                    sourceOffset,
                    buffer,
                    0,
                    buffer.size.toLong(),
                )
                if (read < 0) return false
                if (read == 0) break
                val written = destinationVolume.write(
                    destinationHandle,
                    destinationOffset,
                    buffer,
                    0,
                    read.toLong(),
                )
                if (written != read) return false
                sourceOffset += read
                destinationOffset += written
            }
            destinationVolume.truncate(destinationPath, destinationOffset)
        } finally {
            sourceVolume.closeFile(sourceHandle)
            destinationVolume.closeFile(destinationHandle)
        }
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
    private val appContext = context.applicationContext
    private val volume = fileRepository.volume

    @Synchronized
    fun listBooks(): List<VaultLibraryEntry> {
        val entries = migrateLegacyEntries(readEntries())
        return entries
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

        val entries = migrateLegacyEntries(readEntries()).toMutableList()
        entries.firstOrNull { it.sourcePath == path }?.let { existing ->
            if (volume.getAttr(existing.path)?.type == Stat.S_IFREG) return existing
        }

        val id = UUID.randomUUID().toString()
        val privatePath = privateBookPath(id, format)
        fileRepository.ensureDirectory(LIBRARY_FILES_DIRECTORY)
        check(fileRepository.copyWithinVolume(path, privatePath)) {
            "Unable to create private vault-library copy"
        }
        migrateVaultReadingPosition(path, privatePath)

        val fileName = File(path).name
        val title = titleOverride
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
                .ifBlank { fileName }

        val entry = VaultLibraryEntry(
            id = id,
            path = privatePath,
            title = title,
            format = format,
            sizeBytes = stat.size.coerceAtLeast(0L),
            addedAtEpochMillis = System.currentTimeMillis(),
            lastOpenedAtEpochMillis = 0L,
            sourcePath = path,
        )
        entries += entry
        writeEntries(entries)
        return entry
    }

    @Synchronized
    fun remove(id: String, deleteLinkedSource: Boolean = false): Boolean {
        val entries = migrateLegacyEntries(readEntries()).toMutableList()
        val target = entries.firstOrNull { it.id == id } ?: return false
        val libraryDeleted = !volume.pathExists(target.path) || volume.deleteFile(target.path)
        if (!libraryDeleted) return false
        if (deleteLinkedSource) {
            target.sourcePath?.let { linked ->
                if (volume.pathExists(linked)) fileRepository.delete(linked)
            }
        }
        BookReadingPositions.clear(
            appContext,
            VaultShelfProgressStore.fileKey(fileRepository.volumeUuid, target.path),
        )
        val changed = entries.removeAll { it.id == id }
        if (changed) writeEntries(entries)
        return changed
    }

    @Synchronized
    fun removePath(path: String) {
        val prefix = path.trimEnd('/') + "/"
        val entries = migrateLegacyEntries(readEntries())
        var changed = false
        val updated = entries.map { entry ->
            val linked = entry.sourcePath == path ||
                entry.sourcePath?.startsWith(prefix) == true
            if (linked) {
                changed = true
                entry.copy(sourcePath = null)
            } else {
                entry
            }
        }
        if (changed) writeEntries(updated)
    }

    @Synchronized
    fun linkedToSourcePath(path: String): List<VaultLibraryEntry> {
        val prefix = path.trimEnd('/') + "/"
        return migrateLegacyEntries(readEntries()).filter {
            it.sourcePath == path || it.sourcePath?.startsWith(prefix) == true
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
        require(sourceFile.isFile)
        val entries = migrateLegacyEntries(readEntries()).toMutableList()
        entries.firstOrNull {
            it.title == book.title &&
                it.format == book.format &&
                it.sizeBytes == book.sizeBytes &&
                volume.getAttr(it.path)?.type == Stat.S_IFREG
        }?.let { existing -> return existing }

        val id = UUID.randomUUID().toString()
        val privatePath = privateBookPath(id, book.format)
        fileRepository.ensureDirectory(LIBRARY_FILES_DIRECTORY)
        sourceFile.inputStream().buffered().use { input ->
            check(volume.importFile(input, privatePath)) {
                "Unable to import external library book into vault library"
            }
        }
        val entry = VaultLibraryEntry(
            id = id,
            path = privatePath,
            title = book.title,
            format = book.format,
            sizeBytes = sourceFile.length(),
            addedAtEpochMillis = System.currentTimeMillis(),
            lastOpenedAtEpochMillis = 0L,
            sourcePath = null,
        )
        entries += entry
        writeEntries(entries)
        Positions.keyFor(sourceFile)?.let { sourceKey ->
            migrateReadingPosition(
                sourceKey,
                VaultShelfProgressStore.fileKey(fileRepository.volumeUuid, privatePath),
            )
        }
        return entry
    }

    fun importFromVolume(
        sourceVolume: EncryptedVolume,
        sourcePath: String,
        titleOverride: String? = null,
        sourceReadingKey: String? = null,
    ): VaultLibraryEntry {
        val format = requireNotNull(formatForPath(sourcePath)) { "Unsupported library format" }
        val stat = requireNotNull(sourceVolume.getAttr(sourcePath)) { "Source file is missing" }
        require(stat.type == Stat.S_IFREG) { "Source is not a regular file" }

        val id = UUID.randomUUID().toString()
        val privatePath = privateBookPath(id, format)
        fileRepository.ensureDirectory(LIBRARY_FILES_DIRECTORY)
        check(fileRepository.copyFromVolume(sourceVolume, sourcePath, privatePath)) {
            "Unable to import file into vault library"
        }
        val sourceName = File(sourcePath).name
        val entry = VaultLibraryEntry(
            id = id,
            path = privatePath,
            title = titleOverride
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: sourceName.substringBeforeLast('.', missingDelimiterValue = sourceName),
            format = format,
            sizeBytes = stat.size.coerceAtLeast(0L),
            addedAtEpochMillis = System.currentTimeMillis(),
            lastOpenedAtEpochMillis = 0L,
            sourcePath = null,
        )
        val entries = migrateLegacyEntries(readEntries()).toMutableList()
        entries += entry
        writeEntries(entries)
        sourceReadingKey?.let { key ->
            migrateReadingPosition(
                key,
                VaultShelfProgressStore.fileKey(fileRepository.volumeUuid, privatePath),
            )
        }
        return entry
    }

    fun exportToVisibleFile(
        entry: VaultLibraryEntry,
        parentPath: String,
    ): String {
        val requestedName = buildString {
            append(VaultFileRepository.sanitizeFileName(entry.title).ifBlank { "book" })
            append('.')
            append(formatExtension(entry.format))
        }
        val destination = fileRepository.uniquePath(parentPath, requestedName)
        check(fileRepository.copyWithinVolume(entry.path, destination)) {
            "Unable to export vault-library book to vault files"
        }
        migrateVaultReadingPosition(entry.path, destination)
        return destination
    }

    suspend fun exportToExternalLibrary(
        entry: VaultLibraryEntry,
        repository: LocalLibraryRepository,
    ): LibraryBook {
        val imported = repository.importStream(
            displayName = "${entry.title}.${formatExtension(entry.format)}",
            format = entry.format,
            titleOverride = entry.title,
            sourceUri = null,
        ) {
            fileRepository.inputStream(entry.path)
        }
        val targetFile = repository.bookFile(imported.id)
        Positions.keyFor(targetFile)?.let { targetKey ->
            migrateReadingPosition(
                VaultShelfProgressStore.fileKey(fileRepository.volumeUuid, entry.path),
                targetKey,
            )
        }
        return imported
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
                            sourcePath = value.optString("sourcePath")
                                .takeIf { it.isNotBlank() && it != "null" },
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
                    .put("lastOpenedAtEpochMillis", entry.lastOpenedAtEpochMillis)
                    .put("sourcePath", entry.sourcePath ?: JSONObject.NULL),
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

    private fun privateBookPath(id: String, format: BookFormat): String =
        PathUtils.pathJoin(LIBRARY_FILES_DIRECTORY, "$id.${formatExtension(format)}")

    private fun migrateLegacyEntries(entries: List<VaultLibraryEntry>): List<VaultLibraryEntry> {
        var changed = false
        val migrated = entries.map { entry ->
            if (
                entry.path.startsWith("$LIBRARY_FILES_DIRECTORY/") ||
                volume.getAttr(entry.path)?.type != Stat.S_IFREG
            ) {
                entry
            } else {
                val privatePath = privateBookPath(entry.id, entry.format)
                fileRepository.ensureDirectory(LIBRARY_FILES_DIRECTORY)
                if (fileRepository.copyWithinVolume(entry.path, privatePath)) {
                    migrateVaultReadingPosition(entry.path, privatePath)
                    changed = true
                    entry.copy(path = privatePath, sourcePath = entry.sourcePath ?: entry.path)
                } else {
                    entry
                }
            }
        }
        if (changed) writeEntries(migrated)
        return migrated
    }

    private fun migrateVaultReadingPosition(sourcePath: String, destinationPath: String) {
        migrateReadingPosition(
            VaultShelfProgressStore.fileKey(fileRepository.volumeUuid, sourcePath),
            VaultShelfProgressStore.fileKey(fileRepository.volumeUuid, destinationPath),
        )
    }

    private fun migrateReadingPosition(sourceKey: String, destinationKey: String) {
        BookReadingPositions.get(appContext, sourceKey)?.let { position ->
            BookReadingPositions.save(
                appContext,
                destinationKey,
                position.chapterIndex,
                position.chapterPosition,
                position.updatedAtEpochMillis,
            )
        }
    }

    companion object {
        const val LIBRARY_FILES_DIRECTORY = "/.vaultshelf/library-files"
        private const val METADATA_FILE = "/.vaultshelf/library.json"
        private const val METADATA_NEW = "/.vaultshelf/library.json.new"
        private const val METADATA_BACKUP = "/.vaultshelf/library.json.bak"
        private const val MAX_METADATA_BYTES = 2L * 1024L * 1024L

        fun formatForPath(path: String): BookFormat? = BookFormat.fromFileName(path)

        private fun formatExtension(format: BookFormat): String = BookFormat.extension(format)
    }
}

internal class EncryptedVolumeInputStream(
    private val volume: EncryptedVolume,
    path: String,
) : InputStream() {
    private val handle = volume.openFileReadMode(path)
    private var offset = 0L
    private var closed = false

    init {
        require(handle != -1L) { "Unable to open encrypted file" }
    }

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) == 1) one[0].toInt() and 0xff else -1
    }

    override fun read(buffer: ByteArray, off: Int, len: Int): Int {
        if (closed) return -1
        if (len == 0) return 0
        val read = volume.read(handle, offset, buffer, off.toLong(), len.toLong())
        if (read <= 0) return -1
        offset += read
        return read
    }

    override fun close() {
        if (!closed) {
            closed = true
            volume.closeFile(handle)
        }
        super.close()
    }
}
