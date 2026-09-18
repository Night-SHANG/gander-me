package com.arjun.gander

import android.content.Context
import android.util.AtomicFile
import java.io.File

/**
 * Position-only metadata for transient Legado reading sessions.
 *
 * The caller supplies an opaque key (content fingerprint for normal files, or a
 * one-way vault identity for encrypted files). No document text or file names live here.
 */
object BookReadingPositions {

    data class Position(
        val chapterIndex: Int,
        val chapterPosition: Int,
    )

    private const val FILE_NAME = "book_reading_positions"
    private const val MAX = 500

    fun get(context: Context, key: String): Position? =
        load(context).firstOrNull { it.key == key }?.let {
            Position(it.chapterIndex, it.chapterPosition)
        }

    fun save(
        context: Context,
        key: String,
        chapterIndex: Int,
        chapterPosition: Int,
    ) {
        val all = load(context)
        val others = all.filter { it.key != key }
        val entry = Entry(
            key = key,
            chapterIndex = chapterIndex.coerceAtLeast(0),
            chapterPosition = chapterPosition.coerceAtLeast(0),
            time = System.currentTimeMillis(),
        )
        write(context, (others + entry).sortedByDescending { it.time }.take(MAX))
    }

    fun clear(context: Context, key: String) {
        val all = load(context)
        val filtered = all.filter { it.key != key }
        if (filtered.size != all.size) write(context, filtered)
    }

    private data class Entry(
        val key: String,
        val chapterIndex: Int,
        val chapterPosition: Int,
        val time: Long,
    )

    private fun file(context: Context) =
        AtomicFile(File(context.noBackupFilesDir, FILE_NAME))

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
            output.write(
                entries.joinToString("") {
                    "${it.key} ${it.chapterIndex} ${it.chapterPosition} ${it.time}\n"
                }.toByteArray(),
            )
            atomic.finishWrite(output)
        }.onFailure {
            atomic.failWrite(output)
        }
    }
}
