package com.vaultshelf.droidfs

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest
import sushi.hardcore.droidfs.VolumeManagerApp

/**
 * VaultShelf metadata layered beside DroidFS. No plaintext names or paths are persisted.
 * DroidFS still owns encryption, decryption, locking and the media player itself.
 */
object VaultShelfProgressStore {

    private const val MEDIA_FILE = "vault_media_positions"
    private const val MAX = 500

    fun volumePrefix(volumeUuid: String): String =
        "v1-" + digest(volumeUuid.toByteArray()).take(8).toHex()

    fun fileKey(volumeUuid: String, path: String): String =
        volumePrefix(volumeUuid) + ":" + legacyFileKey(volumeUuid, path)

    fun legacyFileKey(volumeUuid: String, path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(volumeUuid.toByteArray())
        digest.update(0)
        digest.update(path.toByteArray())
        return digest.digest().take(16).toHex()
    }

    fun plainMediaPosition(context: Context, opaqueContentKey: String): Long =
        load(context)
            .firstOrNull { it.key == plainMediaKey(opaqueContentKey) }
            ?.positionMs
            ?: 0L

    fun savePlainMediaPosition(
        context: Context,
        opaqueContentKey: String,
        positionMs: Long,
        durationMs: Long,
    ) {
        val key = plainMediaKey(opaqueContentKey)
        savePosition(context, key, emptySet(), positionMs, durationMs)
    }

    fun mediaPosition(
        context: Context,
        volumeId: Int,
        path: String,
    ): Long {
        val uuid = volumeUuid(context, volumeId) ?: return 0L
        return mediaPosition(context, uuid, path)
    }

    fun mediaPosition(
        context: Context,
        volumeUuid: String,
        path: String,
    ): Long {
        val current = fileKey(volumeUuid, path)
        val legacy = legacyFileKey(volumeUuid, path)
        return load(context)
            .firstOrNull { it.key == current || it.key == legacy }
            ?.positionMs
            ?: 0L
    }

    fun saveMediaPosition(
        context: Context,
        volumeId: Int,
        path: String,
        positionMs: Long,
        durationMs: Long,
    ) {
        val uuid = volumeUuid(context, volumeId) ?: return
        saveMediaPosition(context, uuid, path, positionMs, durationMs)
    }

    fun saveMediaPosition(
        context: Context,
        volumeUuid: String,
        path: String,
        positionMs: Long,
        durationMs: Long,
    ) {
        val key = fileKey(volumeUuid, path)
        val legacy = legacyFileKey(volumeUuid, path)
        savePosition(context, key, setOf(legacy), positionMs, durationMs)
    }

    fun exportOpaque(context: Context, volumeUuid: String? = null): ByteArray {
        val entries = if (volumeUuid == null) {
            load(context)
        } else {
            val prefix = volumePrefix(volumeUuid) + ":"
            load(context).filter { it.key.startsWith(prefix) }
        }
        return encode(entries)
    }

    fun mergeOpaque(context: Context, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val merged = (load(context) + parse(bytes))
            .groupBy { it.key }
            .mapNotNull { (_, values) -> values.maxByOrNull { it.time } }
            .sortedByDescending { it.time }
            .take(MAX)
        write(context, merged)
    }

    private fun plainMediaKey(opaqueContentKey: String) = "plain:$opaqueContentKey"

    private fun savePosition(
        context: Context,
        key: String,
        aliases: Set<String>,
        positionMs: Long,
        durationMs: Long,
    ) {
        val all = load(context)
        val others = all.filter { it.key != key && it.key !in aliases }

        // Near the start and near the end both reopen from the beginning.
        val safe = positionMs.coerceAtLeast(0L)
        val keep = safe >= 5_000L &&
            (durationMs <= 0L || safe < (durationMs - 5_000L).coerceAtLeast(0L))

        if (!keep && others.size == all.size) return

        val entries = if (keep) {
            others + Entry(key, safe, System.currentTimeMillis())
        } else {
            others
        }
        write(context, entries.sortedByDescending { it.time }.take(MAX))
    }

    private fun volumeUuid(context: Context, volumeId: Int): String? =
        (context.applicationContext as? VolumeManagerApp)
            ?.volumeManager
            ?.listVolumes()
            ?.firstOrNull { it.first == volumeId }
            ?.second
            ?.uuid

    private data class Entry(
        val key: String,
        val positionMs: Long,
        val time: Long,
    )

    private fun file(context: Context) =
        AtomicFile(File(context.noBackupFilesDir, MEDIA_FILE))

    private fun load(context: Context): List<Entry> = runCatching {
        parse(file(context).readFully())
    }.getOrDefault(emptyList())

    private fun parse(bytes: ByteArray): List<Entry> =
        String(bytes)
            .lineSequence()
            .mapNotNull { line ->
                val parts = line.split(' ')
                val position = parts.getOrNull(1)?.toLongOrNull()
                val time = parts.getOrNull(2)?.toLongOrNull()
                if (parts.size == 3 && position != null && time != null) {
                    Entry(parts[0], position, time)
                } else {
                    null
                }
            }
            .toList()

    private fun digest(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun encode(entries: List<Entry>): ByteArray =
        entries.joinToString("") {
            "${it.key} ${it.positionMs} ${it.time}\n"
        }.toByteArray()

    private fun write(context: Context, entries: List<Entry>) {
        val atomic = file(context)
        val output = runCatching { atomic.startWrite() }.getOrNull() ?: return
        runCatching {
            output.write(encode(entries))
            atomic.finishWrite(output)
        }.onFailure {
            atomic.failWrite(output)
        }
    }
}
