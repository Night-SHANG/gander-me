package com.arjun.gander.vault

import android.content.Context
import android.net.Uri
import com.arjun.gander.BookReadingPositions
import com.vaultshelf.droidfs.VaultShelfProgressStore
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import sushi.hardcore.droidfs.VolumeData
import sushi.hardcore.droidfs.VolumeDatabase
import sushi.hardcore.droidfs.VolumeManagerApp
import sushi.hardcore.droidfs.filesystems.EncryptedVolume

object VaultBackupManager {

    enum class Failure {
        NOT_HIDDEN,
        VOLUME_OPEN,
        SOURCE_MISSING,
        INVALID_BACKUP,
        UNSUPPORTED_VOLUME,
        ALREADY_EXISTS,
        IO,
    }

    class BackupException(
        val failure: Failure,
        cause: Throwable? = null,
    ) : Exception(failure.name, cause)

    data class RestoredVolume(
        val volume: VolumeData,
    )

    fun hiddenVolumes(context: Context): List<VolumeData> =
        VolumeDatabase(context.applicationContext)
            .getVolumes()
            .filter { it.isHidden }
            .sortedBy { it.shortName.lowercase() }

    fun isOpen(context: Context, volume: VolumeData): Boolean =
        (context.applicationContext as? VolumeManagerApp)
            ?.volumeManager
            ?.isOpen(volume)
            ?: false

    fun export(
        context: Context,
        volume: VolumeData,
        destination: Uri,
    ) {
        val appContext = context.applicationContext
        if (!volume.isHidden) throw BackupException(Failure.NOT_HIDDEN)
        if (isOpen(appContext, volume)) throw BackupException(Failure.VOLUME_OPEN)
        if (volume.type != EncryptedVolume.GOCRYPTFS_VOLUME_TYPE) {
            throw BackupException(Failure.UNSUPPORTED_VOLUME)
        }

        val source = File(volume.getFullPath(appContext.filesDir.path))
        if (!source.isDirectory || EncryptedVolume.getVolumeType(source.path) != volume.type) {
            throw BackupException(Failure.SOURCE_MISSING)
        }

        val output = appContext.contentResolver.openOutputStream(destination, "w")
            ?: throw BackupException(Failure.IO)

        try {
            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                // Ciphertext is already effectively incompressible; avoid wasting CPU.
                zip.setLevel(Deflater.NO_COMPRESSION)
                writeBytes(zip, MANIFEST_ENTRY, manifest(volume).toString().toByteArray(StandardCharsets.UTF_8))

                val prefix = VaultShelfProgressStore.volumePrefix(volume.uuid)
                val bookPositions = BookReadingPositions.exportOpaque(appContext, prefix)
                if (bookPositions.isNotEmpty()) {
                    writeBytes(zip, BOOK_POSITIONS_ENTRY, bookPositions)
                }

                val mediaPositions = VaultShelfProgressStore.exportOpaque(appContext, volume.uuid)
                if (mediaPositions.isNotEmpty()) {
                    writeBytes(zip, MEDIA_POSITIONS_ENTRY, mediaPositions)
                }

                val root = source.canonicalFile
                source.walkTopDown().forEach { file ->
                    if (file == source) return@forEach
                    val canonical = file.canonicalFile
                    if (!isWithin(root, canonical)) {
                        throw BackupException(Failure.IO)
                    }

                    val relative = file.relativeTo(source).invariantSeparatorsPath
                    if (relative.isBlank()) return@forEach
                    val entryName = "$VOLUME_PREFIX$relative" + if (file.isDirectory) "/" else ""
                    zip.putNextEntry(ZipEntry(entryName))
                    if (file.isFile) {
                        file.inputStream().buffered().use { input -> input.copyTo(zip) }
                    }
                    zip.closeEntry()
                }
            }
        } catch (error: BackupException) {
            runCatching { appContext.contentResolver.delete(destination, null, null) }
            throw error
        } catch (error: Throwable) {
            runCatching { appContext.contentResolver.delete(destination, null, null) }
            throw BackupException(Failure.IO, error)
        }
    }

    fun restore(
        context: Context,
        source: Uri,
    ): RestoredVolume {
        val appContext = context.applicationContext
        val database = VolumeDatabase(appContext)

        var temporary: File? = null
        var finalDirectory: File? = null
        var registered = false

        try {
            val input = appContext.contentResolver.openInputStream(source)
                ?: throw BackupException(Failure.IO)

            val result = ZipInputStream(BufferedInputStream(input)).use { zip ->
                val manifestEntry = zip.nextEntry ?: throw BackupException(Failure.INVALID_BACKUP)
                if (manifestEntry.isDirectory || manifestEntry.name != MANIFEST_ENTRY) {
                    throw BackupException(Failure.INVALID_BACKUP)
                }
                val manifestBytes = readSmallEntry(zip, MAX_MANIFEST_BYTES)
                zip.closeEntry()

                val manifest = parseManifest(manifestBytes)
                val identity = runCatching {
                    Triple(
                        manifest.getString("uuid"),
                        manifest.getString("name"),
                        manifest.getInt("type").toByte(),
                    )
                }.getOrElse {
                    throw BackupException(Failure.INVALID_BACKUP, it)
                }
                val (uuid, name, type) = identity

                validateIdentity(uuid, name)
                if (type != EncryptedVolume.GOCRYPTFS_VOLUME_TYPE) {
                    throw BackupException(Failure.UNSUPPORTED_VOLUME)
                }

                if (
                    database.getVolumes().any { it.uuid == uuid } ||
                    database.isVolumeSaved(name, true)
                ) {
                    throw BackupException(Failure.ALREADY_EXISTS)
                }

                val volumesRoot = File(appContext.filesDir, VolumeData.VOLUMES_DIRECTORY).apply {
                    if (!exists() && !mkdirs()) throw BackupException(Failure.IO)
                }
                val temp = File(volumesRoot, ".restore-$uuid").canonicalFile
                val target = File(
                    VolumeData.getHiddenVolumeFullPath(appContext.filesDir.path, name),
                ).canonicalFile
                if (!isWithin(volumesRoot.canonicalFile, temp) || !isWithin(volumesRoot.canonicalFile, target)) {
                    throw BackupException(Failure.INVALID_BACKUP)
                }
                if (temp.exists()) temp.deleteRecursively()
                if (!temp.mkdirs()) throw BackupException(Failure.IO)
                if (target.exists()) throw BackupException(Failure.ALREADY_EXISTS)
                temporary = temp
                finalDirectory = target

                var bookMetadata: ByteArray? = null
                var mediaMetadata: ByteArray? = null
                var entryCount = 0
                var restoredBytes = 0L
                val maxRestoreBytes = (volumesRoot.usableSpace - RESTORE_SPACE_RESERVE)
                    .coerceAtLeast(0L)

                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > MAX_ENTRY_COUNT || entry.name.length > MAX_ENTRY_NAME) {
                        throw BackupException(Failure.INVALID_BACKUP)
                    }

                    when {
                        entry.name == BOOK_POSITIONS_ENTRY -> {
                            bookMetadata = readSmallEntry(zip, MAX_METADATA_BYTES)
                        }

                        entry.name == MEDIA_POSITIONS_ENTRY -> {
                            mediaMetadata = readSmallEntry(zip, MAX_METADATA_BYTES)
                        }

                        entry.name.startsWith(VOLUME_PREFIX) -> {
                            val relative = entry.name.removePrefix(VOLUME_PREFIX).trimEnd('/')
                            if (relative.isNotBlank()) {
                                val destination = File(temp, relative).canonicalFile
                                if (!isWithin(temp, destination)) {
                                    throw BackupException(Failure.INVALID_BACKUP)
                                }
                                if (entry.isDirectory) {
                                    if (!destination.exists() && !destination.mkdirs()) {
                                        throw BackupException(Failure.IO)
                                    }
                                } else {
                                    destination.parentFile?.let {
                                        if (!it.exists() && !it.mkdirs()) throw BackupException(Failure.IO)
                                    }
                                    destination.outputStream().buffered().use { output ->
                                        restoredBytes = copyVolumeEntry(
                                            zip = zip,
                                            output = output,
                                            bytesSoFar = restoredBytes,
                                            maxBytes = maxRestoreBytes,
                                        )
                                    }
                                }
                            }
                        }

                        else -> throw BackupException(Failure.INVALID_BACKUP)
                    }
                    zip.closeEntry()
                }

                if (EncryptedVolume.getVolumeType(temp.path) != type) {
                    throw BackupException(Failure.INVALID_BACKUP)
                }

                if (!temp.renameTo(target)) {
                    throw BackupException(Failure.IO)
                }
                temporary = null

                val volume = VolumeData(
                    uuid = uuid,
                    name = name,
                    isHidden = true,
                    type = type,
                    encryptedHash = null,
                    iv = null,
                )
                if (!database.saveVolume(volume)) {
                    throw BackupException(Failure.ALREADY_EXISTS)
                }
                registered = true

                // Opaque keys were derived from this preserved volume UUID, so they become
                // valid again without ever putting plaintext vault paths in the backup.
                runCatching {
                    bookMetadata?.let { BookReadingPositions.mergeOpaque(appContext, it) }
                    mediaMetadata?.let { VaultShelfProgressStore.mergeOpaque(appContext, it) }
                }

                RestoredVolume(volume)
            }
            return result
        } catch (error: BackupException) {
            if (!registered) {
                temporary?.deleteRecursively()
                finalDirectory?.takeIf { it.exists() }?.deleteRecursively()
            }
            throw error
        } catch (error: Throwable) {
            if (!registered) {
                temporary?.deleteRecursively()
                finalDirectory?.takeIf { it.exists() }?.deleteRecursively()
            }
            throw BackupException(Failure.IO, error)
        }
    }

    private fun manifest(volume: VolumeData) = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("uuid", volume.uuid)
        .put("name", volume.shortName)
        .put("type", volume.type.toInt())

    private fun parseManifest(bytes: ByteArray): JSONObject {
        val value = runCatching {
            JSONObject(String(bytes, StandardCharsets.UTF_8))
        }.getOrElse { throw BackupException(Failure.INVALID_BACKUP, it) }

        if (value.optString("format") != FORMAT || value.optInt("version", -1) != VERSION) {
            throw BackupException(Failure.INVALID_BACKUP)
        }
        return value
    }

    private fun validateIdentity(uuid: String, name: String) {
        runCatching { UUID.fromString(uuid) }
            .getOrElse { throw BackupException(Failure.INVALID_BACKUP, it) }

        if (
            name.isBlank() ||
            name == "." ||
            name == ".." ||
            name.contains('/') ||
            name.contains('\\') ||
            name.length > MAX_VOLUME_NAME
        ) {
            throw BackupException(Failure.INVALID_BACKUP)
        }
    }

    private fun copyVolumeEntry(
        zip: ZipInputStream,
        output: java.io.OutputStream,
        bytesSoFar: Long,
        maxBytes: Long,
    ): Long {
        var total = bytesSoFar
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = zip.read(buffer)
            if (read <= 0) break
            total += read
            if (total > maxBytes) throw BackupException(Failure.IO)
            output.write(buffer, 0, read)
        }
        return total
    }

    private fun writeBytes(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun readSmallEntry(zip: ZipInputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = java.io.ByteArrayOutputStream()
        while (true) {
            val read = zip.read(buffer)
            if (read <= 0) break
            if (output.size() + read > maxBytes) {
                throw BackupException(Failure.INVALID_BACKUP)
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun isWithin(root: File, child: File): Boolean {
        val rootPath = root.path.trimEnd(File.separatorChar) + File.separator
        return child.path == root.path || child.path.startsWith(rootPath)
    }

    private const val FORMAT = "VaultShelfEncryptedVault"
    private const val VERSION = 1
    private const val MANIFEST_ENTRY = "manifest.json"
    private const val BOOK_POSITIONS_ENTRY = "metadata/book_positions.txt"
    private const val MEDIA_POSITIONS_ENTRY = "metadata/media_positions.txt"
    private const val VOLUME_PREFIX = "volume/"
    private const val MAX_MANIFEST_BYTES = 64 * 1024
    private const val MAX_METADATA_BYTES = 2 * 1024 * 1024
    private const val MAX_ENTRY_COUNT = 200_000
    private const val MAX_ENTRY_NAME = 4096
    private const val MAX_VOLUME_NAME = 255
    private const val RESTORE_SPACE_RESERVE = 64L * 1024L * 1024L
}
