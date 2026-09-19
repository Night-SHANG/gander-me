package com.vaultshelf.droidfs

import android.app.Activity
import android.content.Intent
import sushi.hardcore.droidfs.FileShare
import sushi.hardcore.droidfs.VolumeManagerApp
import sushi.hardcore.droidfs.file_viewers.AudioPlayer
import sushi.hardcore.droidfs.file_viewers.VideoPlayer
import sushi.hardcore.droidfs.content_providers.TemporaryFileProvider
import java.io.File
import java.util.UUID

/**
 * Thin integration point between DroidFS' original Explorer and VaultShelf viewers.
 *
 * Encryption/decryption and temporary-file handling stay entirely in DroidFS:
 * [FileShare] and [TemporaryFileProvider] create the same controlled URI used by
 * DroidFS' own external-open path. This class only targets that URI back into the
 * same VaultShelf package for formats handled by Gander or Legado.
 */
object VaultShelfFileRouter {

    const val ACTION_OPEN_VAULT_FILE = "com.arjun.gander.action.OPEN_VAULT_FILE"
    const val EXTRA_VOLUME_ID = "vaultshelf.vault.volume_id"
    const val EXTRA_SESSION_TOKEN = "vaultshelf.vault.session_token"
    const val EXTRA_FILE_KEY = "vaultshelf.vault.file_key"
    const val EXTRA_LEGACY_FILE_KEY = "vaultshelf.vault.legacy_file_key"

    private val extraFormats = setOf(
        // Gander is the single image viewer for both ordinary Files and vault content:
        // tiled large-image zoom plus WebView fallback for animated/vector/new formats.
        "jpg", "jpeg", "png", "webp", "bmp", "heic", "heif",
        "gif", "svg", "avif", "ico",

        // Gander document/Markdown formats not natively handled by DroidFS.
        "docx",
        "xlsx", "xls", "xlsm", "xlsb", "csv", "ods",
        "pptx",
        "pdf",
        "md", "markdown",

        // Legado local ebook formats. TXT is intentionally a reader format here.
        "txt", "epub", "umd", "mobi", "azw3", "azw",
    )

    private val audioFormats = setOf("mp3", "ogg", "m4a", "wav", "flac", "opus")
    private val videoFormats = setOf("mp4", "webm", "mkv", "mov", "m4v")

    fun supports(path: String): Boolean =
        File(path).extension.lowercase() in extraFormats

    fun openAny(
        activity: Activity,
        path: String,
        size: Long,
        volumeId: Int,
    ): Boolean {
        val extension = File(path).extension.lowercase()
        val mediaTarget = when (extension) {
            in audioFormats -> AudioPlayer::class.java
            in videoFormats -> VideoPlayer::class.java
            else -> null
        }
        if (mediaTarget != null) {
            return runCatching {
                activity.startActivity(
                    Intent(activity, mediaTarget)
                        .putExtra("path", path)
                        .putExtra("volumeId", volumeId),
                )
                true
            }.getOrDefault(false)
        }
        return open(activity, path, size, volumeId)
    }

    fun open(
        activity: Activity,
        path: String,
        size: Long,
        volumeId: Int,
    ): Boolean {
        if (!supports(path)) return false

        val volumeUuid = (activity.application as VolumeManagerApp)
            .volumeManager
            .listVolumes()
            .firstOrNull { it.first == volumeId }
            ?.second
            ?.uuid
            ?: return false

        val exportedFile = TemporaryFileProvider.instance.encryptedFileProvider
            .createFile(path, size)
            ?: return false

        val (baseIntent, _) = FileShare(activity).openWith(exportedFile, size, volumeId)
        val uri = baseIntent?.data ?: run {
            exportedFile.free()
            return false
        }

        val target = baseIntent.apply {
            action = ACTION_OPEN_VAULT_FILE
            setClassName(
                activity.packageName,
                "com.arjun.gander.vault.VaultContentActivity",
            )
            putExtra(EXTRA_VOLUME_ID, volumeId)
            putExtra(EXTRA_SESSION_TOKEN, UUID.randomUUID().toString())
            putExtra(EXTRA_FILE_KEY, VaultShelfProgressStore.fileKey(volumeUuid, path))
            putExtra(
                EXTRA_LEGACY_FILE_KEY,
                VaultShelfProgressStore.legacyFileKey(volumeUuid, path),
            )
        }

        return runCatching {
            activity.startActivity(target)
            true
        }.getOrElse {
            runCatching { activity.contentResolver.delete(uri, null, null) }
            false
        }
    }
}
