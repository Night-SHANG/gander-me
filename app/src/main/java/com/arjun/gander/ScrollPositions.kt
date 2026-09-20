package com.arjun.gander

import android.content.Context
import android.util.AtomicFile
import java.io.File

/**
 * Last scroll position for long WebView-backed documents such as Markdown.
 *
 * PDF keeps using [Positions] because its renderer has real page numbers.
 * This store only keeps a normalized vertical scroll fraction, keyed with the
 * same content-derived key as [Positions], so moving or renaming a file does
 * not lose its place.
 */
object ScrollPositions {

    private const val FILE_NAME = "scroll_positions"
    private const val MAX = 100

    private class Entry(
        val key: String,
        val fraction: Float,
        val time: Long,
    )

    fun fraction(context: Context, key: String): Float =
        load(context).firstOrNull { it.key == key }?.fraction ?: 0f

    fun save(context: Context, key: String, fraction: Float) {
        val safe = fraction.coerceIn(0f, 1f)
        val all = load(context)
        val others = all.filter { it.key != key }

        // Top and effectively-finished documents reopen from the top, matching
        // the existing PDF position behavior.
        val keep = safe > 0.01f && safe < 0.99f
        if (!keep && others.size == all.size) return

        val entries = if (keep) {
            others + Entry(key, safe, System.currentTimeMillis())
        } else {
            others
        }

        val text = entries
            .sortedByDescending { it.time }
            .take(MAX)
            .joinToString("") { "${it.key} ${it.fraction} ${it.time}\n" }

        val file = file(context)
        val out = runCatching { file.startWrite() }.getOrNull() ?: return
        runCatching {
            out.write(text.toByteArray())
            file.finishWrite(out)
        }.onFailure {
            file.failWrite(out)
        }
    }

    private fun file(context: Context) =
        AtomicFile(File(context.noBackupFilesDir, FILE_NAME))

    private fun load(context: Context): List<Entry> = runCatching {
        String(file(context).readFully())
            .lines()
            .mapNotNull { line ->
                val parts = line.split(' ')
                val fraction = parts.getOrNull(1)?.toFloatOrNull()
                val time = parts.getOrNull(2)?.toLongOrNull()
                if (
                    parts.size == 3 &&
                    fraction != null &&
                    fraction in 0f..1f &&
                    time != null
                ) {
                    Entry(parts[0], fraction, time)
                } else {
                    null
                }
            }
    }.getOrDefault(emptyList())
}
