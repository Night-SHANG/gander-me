package com.vaultshelf.droidfs

import android.app.Activity
import android.content.Intent
import sushi.hardcore.droidfs.FileShare
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

    private val extraFormats = setOf(
        // Gander document/Markdown formats not natively handled by DroidFS.
        "docx",
        "xlsx", "xls", "xlsm", "xlsb", "csv", "ods",
        "pptx",
        "md", "markdown",

        // Legado local ebook formats not natively handled by DroidFS.
        "epub", "umd", "mobi", "azw3", "azw",
    )

    fun supports(path: String): Boolean =
        File(path).extension.lowercase() in extraFormats

    fun open(
        activity: Activity,
        path: String,
        size: Long,
        volumeId: Int,
    ): Boolean {
        if (!supports(path)) return false

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
