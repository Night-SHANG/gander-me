package com.arjun.gander.vault

import android.content.Context
import android.util.AtomicFile
import java.io.File

/**
 * Position-only metadata for encrypted vault content.
 *
 * Keys are already one-way digests produced at the DroidFS boundary, so neither the
 * plaintext file name nor its vault path is written here. This store never contains
 * decrypted document text, images or media.
 */
object VaultReadingPositions {

    data class BookPosition(
        val chapterIndex: Int,
        val chapterPosition: Int,
    )

    private const val BOOK_FILE = "vault_book_positions"
    private const val MAX = 500

    fun book(context: Context, fileKey: String): BookPosition? =
        load(context).firstOrNull { it.key == fileKey }?.let {
            BookPosition(it.chapterIndex, it.chapterPosition)
        }

    fun saveBook(
        context: Context,
        fileKey: String,
        chapterIndex: Int,
        chapterPosition: Int,
    ) {
        val all = load(context)
        val others = all.filter { it.key != fileKey }
        val entry = Entry(
            key = fileKey,
            chapterIndex = chapterIndex.coerceAtLeast(0),
            chapterPosition = chapterPosition.coerceAtLeast(0),
            time = System.currentTimeMillis(),
        )
        write(
            context,
            (others + entry)
                .sortedByDescending { it.time }
                .take(MAX),
        )
    }

    fun clearBook(context: Context, fileKey: String) {
        val all = load(context)
        val filtered = all.filter { it.key != fileKey }
        if (filtered.size != all.size) write(context, filtered)
    }

    private data class Entry(
        val key: String,
        val chapterIndex: Int,
        val chapterPosition: Int,
        val time: Long,
    )

    private fun file(context: Context) =
        AtomicFile(File(context.noBackupFilesDir, BOOK_FILE))

    private fun load(context: Context): List<Entry> = runCatching {
        String(file(context).readFully())
            .lineSequence()
            .mapNotNull { line ->
                val parts = line.split(' ')
                val chapterIndex = parts.getOrNull(1)?.toIntOrNull()
                val chapterPosition = parts.getOrNull(2)?.toIntOrNull()
                val time = parts.getOrNull(3)?.toLongOrNull()
                if (
                    parts.size == 4 &&
                    chapterIndex != null &&
                    chapterPosition != null &&
                    time != null
                ) {
                    Entry(parts[0], chapterIndex, chapterPosition, time)
                } else {
                    null
                }
            }
            .toList()
    }.getOrDefault(emptyList())

    private fun write(context: Context, entries: List<Entry>) {
        val atomic = file(context)
        val output = runCatching { atomic.startWrite() }.getOrNull() ?: return
        runCatching {
            val text = entries.joinToString("") {
                "${it.key} ${it.chapterIndex} ${it.chapterPosition} ${it.time}\n"
            }
            output.write(text.toByteArray())
            atomic.finishWrite(output)
        }.onFailure {
            atomic.failWrite(output)
        }
    }
}
