package com.arjun.gander.library.session

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.arjun.gander.R

internal fun readAuthorizedFolders(context: Context): List<Pair<Uri, String>> =
    context.contentResolver.persistedUriPermissions
        .asSequence()
        .filter { it.isReadPermission && isTreeUri(it.uri) }
        .map { it.uri to readTreeLabel(context, it.uri) }
        .sortedBy { (_, label) -> label.lowercase() }
        .toList()

private fun isTreeUri(uri: Uri): Boolean =
    runCatching { DocumentsContract.getTreeDocumentId(uri) }.isSuccess &&
        uri.pathSegments.firstOrNull() == "tree"

private fun readTreeLabel(context: Context, uri: Uri): String {
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        ?: return context.getString(R.string.vaultshelf_files_folder_fallback)
    return runCatching {
        context.contentResolver.query(
            DocumentsContract.buildDocumentUriUsingTree(uri, documentId),
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: documentId.substringAfterLast(':').ifBlank {
            context.getString(R.string.vaultshelf_files_folder_fallback)
        }
}
