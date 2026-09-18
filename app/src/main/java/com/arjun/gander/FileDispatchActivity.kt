package com.arjun.gander

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.vaultshelf.droidfs.VaultShelfExternalMediaRouter
import com.vaultshelf.legado.LegadoReaderBridge
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps Gander's mature file browser unchanged while delegating book formats to
 * the original Legado reader.
 */
class FileDispatchActivity : ComponentActivity() {

    private val cleanupStarted = AtomicBoolean(false)

    private var transientBookUrl: String? = null
    private var positionKey: String? = null

    private val readerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        cleanupAndFinish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent.data ?: run {
            finish()
            return
        }

        transientBookUrl = savedInstanceState?.getString(STATE_TRANSIENT_BOOK_URL)
        positionKey = savedInstanceState?.getString(STATE_POSITION_KEY)

        if (savedInstanceState != null) {
            // LegadoReaderBridge startup cleanup removes a transient row left by process death.
            cleanupAndFinish()
            return
        }

        val meta = metadata(uri)
        val extension = meta.name.substringAfterLast('.', "").lowercase()

        when {
            extension in EBOOK_EXTENSIONS -> openWithLegado(uri, meta.size)

            VaultShelfExternalMediaRouter.supports(meta.name, meta.mime) -> {
                startActivity(
                    VaultShelfExternalMediaRouter.intent(
                        this,
                        uri,
                        meta.name,
                        meta.mime,
                    ),
                )
                finish()
            }

            else -> {
                startActivity(
                    Intent(this, ViewerActivity::class.java)
                        .setData(uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
                finish()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        transientBookUrl?.let { outState.putString(STATE_TRANSIENT_BOOK_URL, it) }
        positionKey?.let { outState.putString(STATE_POSITION_KEY, it) }
        super.onSaveInstanceState(outState)
    }

    private fun openWithLegado(uri: Uri, size: Long) {
        lifecycleScope.launch {
            val key = withContext(Dispatchers.IO) {
                Positions.keyFor(contentResolver, uri, size)
            }
            positionKey = key

            val session = runCatching {
                withContext(Dispatchers.IO) {
                    LegadoReaderBridge.createTransientBookSession(
                        applicationContext,
                        uri,
                    )
                }
            }.getOrElse {
                // If a provider cannot support Legado's local-book reader, preserve Gander's
                // old behaviour instead of turning a previously openable file into a dead end.
                startActivity(
                    Intent(this@FileDispatchActivity, ViewerActivity::class.java)
                        .setData(uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
                finish()
                return@launch
            }

            transientBookUrl = session.bookUrl

            key?.let { stableKey ->
                BookReadingPositions.get(applicationContext, stableKey)?.let { saved ->
                    withContext(Dispatchers.IO) {
                        LegadoReaderBridge.restoreTransientReadingPosition(
                            applicationContext,
                            session.bookUrl,
                            saved.chapterIndex,
                            saved.chapterPosition,
                        )
                    }
                }
            }

            readerLauncher.launch(
                LegadoReaderBridge.readerIntent(
                    this@FileDispatchActivity,
                    session.bookUrl,
                ),
            )
        }
    }

    private fun cleanupAndFinish() {
        if (!cleanupStarted.compareAndSet(false, true)) return

        val bookUrl = transientBookUrl
        val key = positionKey

        lifecycleScope.launch {
            if (bookUrl != null) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        if (key != null) {
                            LegadoReaderBridge.transientReadingPosition(
                                applicationContext,
                                bookUrl,
                            )?.let { position ->
                                BookReadingPositions.save(
                                    applicationContext,
                                    key,
                                    position.chapterIndex,
                                    position.chapterPosition,
                                )
                            }
                        }

                        LegadoReaderBridge.cleanupTransientBookSession(
                            applicationContext,
                            bookUrl,
                        )
                    }
                }
            }
            finish()
        }
    }

    override fun onDestroy() {
        if (isFinishing && !cleanupStarted.get()) {
            val bookUrl = transientBookUrl
            val key = positionKey
            Thread {
                if (bookUrl != null) {
                    runCatching {
                        if (key != null) {
                            LegadoReaderBridge.transientReadingPosition(
                                applicationContext,
                                bookUrl,
                            )?.let { position ->
                                BookReadingPositions.save(
                                    applicationContext,
                                    key,
                                    position.chapterIndex,
                                    position.chapterPosition,
                                )
                            }
                        }
                        LegadoReaderBridge.cleanupTransientBookSession(
                            applicationContext,
                            bookUrl,
                        )
                    }
                }
            }.apply {
                name = "file-reader-cleanup"
                isDaemon = true
            }.start()
        }
        super.onDestroy()
    }

    private data class Metadata(
        val name: String,
        val size: Long,
        val mime: String?,
    )

    private fun metadata(uri: Uri): Metadata {
        var name = uri.lastPathSegment.orEmpty()
        var size = -1L
        runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.takeIf { it.isNotBlank() }?.let { name = it }
                    if (!cursor.isNull(1)) size = cursor.getLong(1)
                }
            }
        }
        return Metadata(name, size, contentResolver.getType(uri))
    }

    private companion object {
        val EBOOK_EXTENSIONS = setOf("txt", "epub", "umd", "mobi", "azw3", "azw")
        const val STATE_TRANSIENT_BOOK_URL = "transient_book_url"
        const val STATE_POSITION_KEY = "position_key"
    }
}
