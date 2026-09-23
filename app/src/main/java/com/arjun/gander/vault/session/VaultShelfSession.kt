package com.arjun.gander.vault.session

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.arjun.gander.ui.state.RetainedContent
import com.arjun.gander.vault.VaultFileRepository
import com.arjun.gander.vault.VaultLibraryStore
import java.util.IdentityHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import sushi.hardcore.droidfs.VolumeManagerApp
import sushi.hardcore.droidfs.filesystems.EncryptedVolume

/** One owner per open volume instance; clears book snapshots when the volume closes. */
class VaultShelfSession private constructor(context: Context, volumeId: Int) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val fileRepository = VaultFileRepository(context, volumeId)
    val libraryStore = VaultLibraryStore(context, fileRepository)
    val books = RetainedContent(scope) {
        withContext(Dispatchers.IO) { libraryStore.listBooks() }
    }

    private fun close() {
        books.close()
        scope.cancel()
    }

    companion object {
        private val sessions = IdentityHashMap<EncryptedVolume, VaultShelfSession>()
        private val main = Handler(Looper.getMainLooper())

        /** Called on the main thread; object identity prevents reuse after re-unlock. */
        fun get(context: Context, volumeId: Int): VaultShelfSession {
            val application = context.applicationContext as VolumeManagerApp
            val volume = requireNotNull(application.volumeManager.getVolume(volumeId))
            return sessions.getOrPut(volume) {
                VaultShelfSession(application, volumeId).also { session ->
                    volume.observe(object : EncryptedVolume.Observer {
                        override fun onClose() {
                            val clear = {
                                session.close()
                                sessions.remove(volume)
                                Unit
                            }
                            if (Looper.myLooper() == Looper.getMainLooper()) clear()
                            else main.post { clear() }
                        }
                    })
                }
            }
        }
    }
}
