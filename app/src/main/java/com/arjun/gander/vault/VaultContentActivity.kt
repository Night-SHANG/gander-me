package com.arjun.gander.vault

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.arjun.gander.BookReadingPositions
import com.arjun.gander.ViewerActivity
import com.vaultshelf.droidfs.VaultShelfFileRouter
import com.vaultshelf.legado.LegadoReaderBridge
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Same-package bridge for a file that DroidFS has already decrypted through its original
 * TemporaryFileProvider. No plaintext is copied into the normal VaultShelf library.
 */
class VaultContentActivity : ComponentActivity() {

    private val cleanupStarted = AtomicBoolean(false)

    private var sourceUri: Uri? = null
    private var sessionToken: String? = null
    private var fileKey: String? = null
    private var transientBookUrl: String? = null

    private val childLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        cleanupAndFinish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent.data
        val token = intent.getStringExtra(VaultShelfFileRouter.EXTRA_SESSION_TOKEN)
        val volumeId = intent.getIntExtra(VaultShelfFileRouter.EXTRA_VOLUME_ID, -1)
        val stableFileKey = intent.getStringExtra(VaultShelfFileRouter.EXTRA_FILE_KEY)
        if (uri == null || token.isNullOrBlank() || stableFileKey.isNullOrBlank() || volumeId < 0) {
            finish()
            return
        }

        sourceUri = uri
        sessionToken = token
        fileKey = stableFileKey
        transientBookUrl = savedInstanceState?.getString(STATE_TRANSIENT_BOOK_URL)

        VaultSessionGuard.register(this, token, volumeId)

        if (savedInstanceState != null) {
            // A DroidFS TemporaryFileProvider URI is process-local. If Android had to
            // recreate this bridge after process death, the provider mapping is gone and
            // Legado's startup orphan cleanup removes the old temporary book.
            cleanupAndFinish()
            return
        }

        val extension = displayName(uri)
            .substringAfterLast('.', "")
            .lowercase()

        when (extension) {
            "txt", "epub", "umd", "mobi", "azw3", "azw" -> openWithLegado(uri, token)
            "pdf", "docx", "xlsx", "xls", "xlsm", "xlsb", "csv", "ods",
            "pptx", "md", "markdown" -> openWithGander(uri, token)
            else -> cleanupAndFinish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        transientBookUrl?.let { outState.putString(STATE_TRANSIENT_BOOK_URL, it) }
        super.onSaveInstanceState(outState)
    }

    private fun openWithLegado(uri: Uri, token: String) {
        lifecycleScope.launch {
            val session = runCatching {
                withContext(Dispatchers.IO) {
                    LegadoReaderBridge.createTransientBookSession(
                        applicationContext,
                        uri,
                    )
                }
            }.getOrElse {
                cleanupAndFinish()
                return@launch
            }

            transientBookUrl = session.bookUrl
            fileKey?.let { key ->
                BookReadingPositions.get(applicationContext, key)?.let { saved ->
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

            val reader = LegadoReaderBridge.readerIntent(this@VaultContentActivity, session.bookUrl)
                .putExtra(VaultShelfFileRouter.EXTRA_SESSION_TOKEN, token)
            childLauncher.launch(reader)
        }
    }

    private fun openWithGander(uri: Uri, token: String) {
        childLauncher.launch(
            Intent(this, ViewerActivity::class.java)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(ViewerActivity.EXTRA_SECURE_VAULT, true)
                .putExtra(VaultShelfFileRouter.EXTRA_SESSION_TOKEN, token),
        )
    }

    private fun displayName(uri: Uri): String {
        return runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull().orEmpty()
    }

    private fun cleanupAndFinish() {
        if (!cleanupStarted.compareAndSet(false, true)) return

        val uri = sourceUri
        val token = sessionToken
        val key = fileKey
        val bookUrl = transientBookUrl

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

            if (uri != null) {
                withContext(Dispatchers.IO) {
                    runCatching { contentResolver.delete(uri, null, null) }
                }
            }

            if (token != null) {
                VaultSessionGuard.unregister(token)
            }
            finish()
        }
    }

    override fun onDestroy() {
        if (isFinishing && !cleanupStarted.get()) {
            val uri = sourceUri
            val token = sessionToken
            val key = fileKey
            val bookUrl = transientBookUrl
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
                if (uri != null) {
                    runCatching { contentResolver.delete(uri, null, null) }
                }
                if (token != null) {
                    VaultSessionGuard.unregister(token)
                }
            }.apply {
                name = "vault-session-cleanup"
                isDaemon = true
            }.start()
        }
        super.onDestroy()
    }

    private companion object {
        const val STATE_TRANSIENT_BOOK_URL = "transient_book_url"
    }
}
