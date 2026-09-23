package com.arjun.gander.library.session

import android.content.Context
import com.arjun.gander.library.LocalLibraryRepository
import com.arjun.gander.ui.state.RetainedContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext

/** Application-lived metadata shared by shell Activities; never holds an Activity. */
class ExternalShelfSession private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val repository = LocalLibraryRepository(context)
    val books = RetainedContent(scope) { repository.listBooks() }
    val folders = RetainedContent(scope) {
        withContext(Dispatchers.IO) { readAuthorizedFolders(context) }
    }

    fun refresh() {
        books.refresh()
        folders.refresh()
    }

    private fun close() {
        books.close()
        folders.close()
        scope.cancel()
    }

    companion object {
        private var instance: ExternalShelfSession? = null

        /** Called from shell Activity lifecycle on the main thread. */
        fun get(context: Context): ExternalShelfSession {
            val application = context.applicationContext
            val current = instance
            if (current != null && current.context === application) return current
            current?.close()
            return ExternalShelfSession(application).also { instance = it }
        }
    }
}
