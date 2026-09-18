package com.arjun.gander

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import com.vaultshelf.legado.LegadoReaderBridge
import androidx.webkit.WebViewStartupException
import java.util.concurrent.Executor

/**
 * Keeps Gander's WebView warm-up without replacing DroidFS' original VolumeManagerApp.
 */
class GanderStartupProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: return true
        LegadoReaderBridge.initialize(appContext)

        val executor = Executor { runnable ->
            Thread(runnable, "webview-warmup").apply { isDaemon = true }.start()
        }
        val config = WebViewStartUpConfig.Builder(executor)
            .setShouldRunUiThreadStartUpTasks(true)
            .build()
        runCatching {
            WebViewCompat.startUpWebView(
                appContext,
                config,
                object : WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException> {
                    override fun onResult(result: WebViewStartUpResult) = Unit
                    override fun onError(error: WebViewStartupException) = Unit
                },
            )
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
