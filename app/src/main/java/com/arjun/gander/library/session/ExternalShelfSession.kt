package com.arjun.gander.library.session

import android.app.Application
import com.arjun.gander.library.LocalLibraryRepository
import com.arjun.gander.ui.state.RetainedContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext

/** Application-lived metadata shared by shell Activities; never holds an Activity. */
class ExternalShelfSession private constructor(private val application: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val repository = LocalLibraryRepository(application)
    val books = RetainedContent(scope) { repository.listBooks() }
    val folders = RetainedContent(scope) {
        withContext(Dispatchers.IO) { readAuthorizedFolders(application) }
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
        fun get(application: Application): ExternalShelfSession {
            val current = instance
            if (current != null && current.application === application) return current
            current?.close()
            return ExternalShelfSession(application).also { instance = it }
        }
    }
}
