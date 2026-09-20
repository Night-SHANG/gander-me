package com.vaultshelf.droidfs

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import sushi.hardcore.droidfs.explorers.ExplorerElement
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.filesystems.Stat
import sushi.hardcore.droidfs.util.PathUtils
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * Presents one persisted SAF tree through DroidFS' EncryptedVolume contract.
 *
 * This is intentionally not encryption. It is an adapter that lets VaultShelf reuse the
 * mature DroidFS Explorer implementation for ordinary external folders as well as vaults.
 * The UI and file-operation engine can therefore stay identical while the backing storage
 * remains either SAF (plain files) or a real DroidFS encrypted volume.
 */
class SafVolume(
    context: Context,
    private val treeUri: Uri,
) : EncryptedVolume() {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
    private val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId)
    private val nodes = HashMap<String, Node>()
    private val handles = HashMap<Long, OpenHandle>()
    private val nextHandle = AtomicLong(1L)

    @Volatile
    private var closed = false

    init {
        nodes[ROOT] = Node(
            uri = rootUri,
            documentId = rootDocumentId,
            mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
            size = 0L,
            modified = 0L,
        )
    }

    private data class Node(
        val uri: Uri,
        val documentId: String,
        val mimeType: String,
        val size: Long,
        val modified: Long,
    ) {
        val isDirectory: Boolean
            get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
    }

    private sealed class OpenHandle {
        abstract fun close()

        class Reader(
            val descriptor: ParcelFileDescriptor,
            val stream: FileInputStream,
        ) : OpenHandle() {
            override fun close() {
                runCatching { stream.close() }
                .onFailure { runCatching { descriptor.close() } }
            }
        }

        class Writer(
            val descriptor: ParcelFileDescriptor,
            val stream: FileOutputStream,
        ) : OpenHandle() {
            override fun close() {
                runCatching { stream.close() }
                .onFailure { runCatching { descriptor.close() } }
            }
        }
    }

    override fun openFileReadMode(path: String): Long {
        val node = resolve(path) ?: return -1L
        if (node.isDirectory) return -1L
        val descriptor = runCatching {
            resolver.openFileDescriptor(node.uri, "r")
        }.getOrNull() ?: return -1L
        val stream = runCatching { FileInputStream(descriptor.fileDescriptor) }.getOrElse {
            descriptor.close()
            return -1L
        }
        val id = nextHandle.getAndIncrement()
        handles[id] = OpenHandle.Reader(descriptor, stream)
        return id
    }

    override fun openFileWriteMode(path: String): Long {
        val node = resolve(path) ?: createRegularFile(path) ?: return -1L
        if (node.isDirectory) return -1L
        val descriptor = runCatching {
            resolver.openFileDescriptor(node.uri, "rw")
        }.getOrNull() ?: return -1L
        val stream = runCatching { FileOutputStream(descriptor.fileDescriptor) }.getOrElse {
            descriptor.close()
            return -1L
        }
        val id = nextHandle.getAndIncrement()
        handles[id] = OpenHandle.Writer(descriptor, stream)
        return id
    }

    override fun read(
        fileHandle: Long,
        fileOffset: Long,
        buffer: ByteArray,
        dstOffset: Long,
        length: Long,
    ): Int {
        val handle = handles[fileHandle] as? OpenHandle.Reader ?: return -1
        if (dstOffset < 0L || length < 0L || dstOffset + length > buffer.size.toLong()) return -1
        return runCatching {
            handle.stream.channel.position(fileOffset)
            handle.stream.channel.read(
                ByteBuffer.wrap(buffer, dstOffset.toInt(), length.toInt()),
            )
        }.getOrDefault(-1)
    }

    override fun write(
        fileHandle: Long,
        fileOffset: Long,
        buffer: ByteArray,
        srcOffset: Long,
        length: Long,
    ): Int {
        val handle = handles[fileHandle] as? OpenHandle.Writer ?: return -1
        if (srcOffset < 0L || length < 0L || srcOffset + length > buffer.size.toLong()) return -1
        return runCatching {
            handle.stream.channel.position(fileOffset)
            handle.stream.channel.write(
                ByteBuffer.wrap(buffer, srcOffset.toInt(), length.toInt()),
            )
        }.getOrDefault(-1)
    }

    override fun closeFile(fileHandle: Long): Boolean {
        val handle = handles.remove(fileHandle) ?: return false
        return runCatching {
            handle.close()
            true
        }.getOrDefault(false)
    }

    override fun truncate(path: String, size: Long): Boolean {
        val node = resolve(path) ?: return false
        if (node.isDirectory) return false
        return runCatching {
            resolver.openFileDescriptor(node.uri, "rw")?.use { descriptor ->
                FileOutputStream(descriptor.fileDescriptor).use { stream ->
                    stream.channel.truncate(size)
                }
            } != null
        }.getOrDefault(false)
    }

    override fun deleteFile(path: String): Boolean = deleteDocument(path)

    override fun readDir(path: String): MutableList<ExplorerElement>? {
        val directory = resolve(path) ?: return null
        if (!directory.isDirectory) return null
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            directory.documentId,
        )
        val result = mutableListOf<ExplorerElement>()
        return runCatching {
            resolver.query(
                childrenUri,
                PROJECTION,
                null,
                null,
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                )
                val nameIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                )
                val mimeIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                )
                val sizeIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_SIZE,
                )
                val modifiedIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                )
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex) ?: continue
                    val documentId = cursor.getString(idIndex) ?: continue
                    val mimeType = cursor.getString(mimeIndex) ?: "application/octet-stream"
                    val size = if (cursor.isNull(sizeIndex)) 0L else cursor.getLong(sizeIndex)
                    val modified = if (cursor.isNull(modifiedIndex)) 0L else cursor.getLong(modifiedIndex)
                    val childPath = PathUtils.pathJoin(path.ifBlank { ROOT }, name)
                    val node = Node(
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                        documentId = documentId,
                        mimeType = mimeType,
                        size = size,
                        modified = modified,
                    )
                    nodes[childPath] = node
                    result += ExplorerElement(
                        name,
                        Stat(
                            if (node.isDirectory) Stat.S_IFDIR else Stat.S_IFREG,
                            size,
                            modified,
                        ),
                        path.ifBlank { ROOT },
                    )
                }
            }
            result
        }.getOrNull()
    }

    override fun mkdir(path: String): Boolean {
        if (path == ROOT || resolve(path) != null) return false
        val parent = resolve(parentPath(path)) ?: return false
        if (!parent.isDirectory) return false
        val name = fileName(path)
        val uri = runCatching {
            DocumentsContract.createDocument(
                resolver,
                parent.uri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                name,
            )
        }.getOrNull() ?: return false
        cache(path, uri)
        return true
    }

    override fun rmdir(path: String): Boolean = deleteDocument(path)

    override fun getAttr(path: String): Stat? {
        if (path == ROOT || path.isBlank()) return Stat(Stat.S_IFDIR, 0L, 0L)
        val node = resolve(path) ?: return null
        val refreshed = queryNode(node.uri) ?: node
        nodes[normalize(path)] = refreshed
        return Stat(
            if (refreshed.isDirectory) Stat.S_IFDIR else Stat.S_IFREG,
            refreshed.size,
            refreshed.modified,
        )
    }

    override fun rename(srcPath: String, dstPath: String): Boolean {
        val sourcePath = normalize(srcPath)
        val targetPath = normalize(dstPath)
        val source = resolve(sourcePath) ?: return false
        val sourceParent = resolve(parentPath(sourcePath)) ?: return false
        val targetParent = resolve(parentPath(targetPath)) ?: return false
        if (!targetParent.isDirectory) return false

        val existing = resolve(targetPath)
        if (existing != null && existing.uri != source.uri) {
            if (!runCatching {
                    DocumentsContract.deleteDocument(resolver, existing.uri)
                }.getOrDefault(false)
            ) {
                return false
            }
            nodes.remove(targetPath)
        }

        var currentUri = source.uri
        if (sourceParent.uri != targetParent.uri) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
            currentUri = runCatching {
                DocumentsContract.moveDocument(
                    resolver,
                    currentUri,
                    sourceParent.uri,
                    targetParent.uri,
                )
            }.getOrNull() ?: return false
        }

        val targetName = fileName(targetPath)
        val currentName = displayName(currentUri)
        if (currentName != targetName) {
            currentUri = runCatching {
                DocumentsContract.renameDocument(resolver, currentUri, targetName)
            }.getOrNull() ?: return false
        }

        removeCachedTree(sourcePath)
        cache(targetPath, currentUri)
        return true
    }

    override fun close() {
        if (closed) return
        closed = true
        handles.values.toList().forEach { handle -> runCatching { handle.close() } }
        handles.clear()
        nodes.clear()
    }

    override fun isClosed(): Boolean = closed

    private fun createRegularFile(path: String): Node? {
        val normalized = normalize(path)
        val parent = resolve(parentPath(normalized)) ?: return null
        if (!parent.isDirectory) return null
        val name = fileName(normalized)
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        val uri = runCatching {
            DocumentsContract.createDocument(resolver, parent.uri, mime, name)
        }.getOrNull() ?: return null
        return cache(normalized, uri)
    }

    private fun deleteDocument(path: String): Boolean {
        val normalized = normalize(path)
        if (normalized == ROOT) return false
        val node = resolve(normalized) ?: return false
        val deleted = runCatching {
            DocumentsContract.deleteDocument(resolver, node.uri)
        }.getOrDefault(false)
        if (deleted) removeCachedTree(normalized)
        return deleted
    }

    fun uriForPath(path: String): Uri? = resolve(path)?.uri

    private fun resolve(path: String): Node? {
        val normalized = normalize(path)
        nodes[normalized]?.let { return it }
        if (normalized == ROOT) return nodes[ROOT]

        val parentPath = parentPath(normalized)
        val parent = resolve(parentPath) ?: return null
        if (!parent.isDirectory) return null
        val wanted = fileName(normalized)
        val child = findChild(parent, wanted) ?: return null
        nodes[normalized] = child
        return child
    }

    private fun findChild(parent: Node, displayName: String): Node? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            parent.documentId,
        )
        return runCatching {
            resolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                )
                val nameIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                )
                val mimeIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                )
                val sizeIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_SIZE,
                )
                val modifiedIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                )
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) != displayName) continue
                    val documentId = cursor.getString(idIndex) ?: return@use null
                    val mime = cursor.getString(mimeIndex) ?: "application/octet-stream"
                    return@use Node(
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                        documentId = documentId,
                        mimeType = mime,
                        size = if (cursor.isNull(sizeIndex)) 0L else cursor.getLong(sizeIndex),
                        modified = if (cursor.isNull(modifiedIndex)) 0L else cursor.getLong(modifiedIndex),
                    )
                }
                null
            }
        }.getOrNull()
    }

    private fun cache(path: String, uri: Uri): Node? {
        val node = queryNode(uri) ?: return null
        nodes[normalize(path)] = node
        return node
    }

    private fun queryNode(uri: Uri): Node? = runCatching {
        resolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val documentId = cursor.getString(
                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            ) ?: return@use null
            Node(
                uri = uri,
                documentId = documentId,
                mimeType = cursor.getString(
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE),
                ) ?: "application/octet-stream",
                size = cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_SIZE,
                ).let { index -> if (index < 0 || cursor.isNull(index)) 0L else cursor.getLong(index) },
                modified = cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ).let { index -> if (index < 0 || cursor.isNull(index)) 0L else cursor.getLong(index) },
            )
        }
    }.getOrNull()

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun removeCachedTree(path: String) {
        val normalized = normalize(path)
        val prefix = if (normalized == ROOT) ROOT else "$normalized/"
        nodes.keys.filter { it == normalized || it.startsWith(prefix) }
            .toList()
            .forEach(nodes::remove)
    }

    private fun normalize(path: String): String =
        PathUtils.normalizePath(path.ifBlank { ROOT }).let {
            if (it.isBlank()) ROOT else it
        }

    private fun parentPath(path: String): String =
        PathUtils.getParentPath(normalize(path))

    private fun fileName(path: String): String =
        normalize(path).substringAfterLast('/')

    companion object {
        private const val ROOT = "/"
        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
