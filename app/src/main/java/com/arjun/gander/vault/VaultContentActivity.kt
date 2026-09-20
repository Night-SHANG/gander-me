package com.arjun.gander.vault

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.arjun.gander.BookReadingPositions
import com.arjun.gander.ViewerActivity
import com.vaultshelf.droidfs.VaultShelfFileRouter
import com.vaultshelf.legado.LegadoReaderBridge
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Same-package bridge for a file that DroidFS has already decrypted through its original
 * TemporaryFileProvider. No plaintext is copied into the normal VaultShelf library.
 */
class VaultContentActivity : ComponentActivity() {

    private val cleanupStarted = AtomicBoolean(false)
    // Cleanup must outlive this translucent Activity: auto-lock can finish the bridge
    // at the same instant as its child reader, which cancels lifecycleScope.
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var sourceUri: Uri? = null
    private var sessionToken: String? = null
    private var fileKey: String? = null
    private var legacyFileKey: String? = null
    private var transientBookUrl: String? = null
    private var childActive = false
    private var bridgeResumed = false
    private var pendingChildIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This bridge owns a decrypted vault URI, so use the same user-selected screenshot
        // policy as Explorer and every child reader instead of hard-forcing FLAG_SECURE.
        VaultScreenshotPolicy.apply(this)

        val uri = intent.data
        val token = intent.getStringExtra(VaultShelfFileRouter.EXTRA_SESSION_TOKEN)
        val volumeId = intent.getIntExtra(VaultShelfFileRouter.EXTRA_VOLUME_ID, -1)
        val stableFileKey = intent.getStringExtra(VaultShelfFileRouter.EXTRA_FILE_KEY)
        val oldFileKey = intent.getStringExtra(VaultShelfFileRouter.EXTRA_LEGACY_FILE_KEY)
        if (uri == null || token.isNullOrBlank() || stableFileKey.isNullOrBlank() || volumeId < 0) {
            finish()
            return
        }

        sourceUri = uri
        sessionToken = token
        fileKey = stableFileKey
        legacyFileKey = oldFileKey
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
            "jpg", "jpeg", "png", "webp", "bmp", "heic", "heif",
            "gif", "svg", "avif", "ico",
            "pdf", "docx", "xlsx", "xls", "xlsm", "xlsb", "csv", "ods",
            "pptx", "md", "markdown" -> openWithGander(uri, token)
            else -> cleanupAndFinish()
        }
    }

    override fun onResume() {
        super.onResume()
        VaultScreenshotPolicy.apply(this)
        bridgeResumed = true
        if (childActive && !cleanupStarted.get()) {
            childActive = false
            cleanupAndFinish()
            return
        }
        pendingChildIntent?.let { intent ->
            pendingChildIntent = null
            startChild(intent)
        }
    }

    override fun onPause() {
        bridgeResumed = false
        super.onPause()
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
                val saved = BookReadingPositions.get(applicationContext, key)
                    ?: legacyFileKey?.let { old ->
                        BookReadingPositions.get(applicationContext, old)
                    }
                saved?.let { savedPosition ->
                    withContext(Dispatchers.IO) {
                        LegadoReaderBridge.restoreTransientReadingPosition(
                            applicationContext,
                            session.bookUrl,
                            savedPosition.chapterIndex,
                            savedPosition.chapterPosition,
                        )
                    }
                }
            }

            val reader = LegadoReaderBridge.readerIntent(this@VaultContentActivity, session.bookUrl)
                .putExtra(VaultShelfFileRouter.EXTRA_SESSION_TOKEN, token)
            launchChild(reader)
        }
    }

    private fun openWithGander(uri: Uri, token: String) {
        launchChild(
            Intent(this, ViewerActivity::class.java)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(ViewerActivity.EXTRA_SECURE_VAULT, true)
                .putExtra(VaultShelfFileRouter.EXTRA_SESSION_TOKEN, token),
        )
    }

    private fun launchChild(intent: Intent) {
        if (!bridgeResumed) {
            pendingChildIntent = intent
            return
        }
        startChild(intent)
    }

    private fun startChild(intent: Intent) {
        childActive = true
        runCatching { startActivity(intent) }
            .onFailure {
                childActive = false
                cleanupAndFinish()
            }
    }

    @android.annotation.SuppressLint("Recycle")
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
        startCleanup(finishWhenDone = true)
    }

    private fun startCleanup(finishWhenDone: Boolean) {
        if (!cleanupStarted.compareAndSet(false, true)) return

        val uri = sourceUri
        val token = sessionToken
        val key = fileKey
        val bookUrl = transientBookUrl
        val appContext = applicationContext

        cleanupScope.launch {
            if (bookUrl != null) {
                runCatching {
                    if (key != null) {
                        LegadoReaderBridge.transientReadingPosition(
                            appContext,
                            bookUrl,
                        )?.let { position ->
                            BookReadingPositions.save(
                                appContext,
                                key,
                                position.chapterIndex,
                                position.chapterPosition,
                            )
                        }
                    }
                    LegadoReaderBridge.cleanupTransientBookSession(
                        appContext,
                        bookUrl,
                    )
                }
            }

            if (uri != null) {
                runCatching { appContext.contentResolver.delete(uri, null, null) }
            }

            if (token != null) {
                VaultSessionGuard.unregister(token)
            }

            if (finishWhenDone) {
                withContext(Dispatchers.Main.immediate) {
                    if (!isDestroyed && !isFinishing) finish()
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing && !cleanupStarted.get()) {
            // The independent scope is intentionally not cancelled here.
            startCleanup(finishWhenDone = false)
        }
        super.onDestroy()
    }

    private companion object {
        const val STATE_TRANSIENT_BOOK_URL = "transient_book_url"
    }
}
