package com.arjun.gander.vault

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import com.vaultshelf.droidfs.VaultShelfFileRouter
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import sushi.hardcore.droidfs.VolumeData
import sushi.hardcore.droidfs.VolumeManager
import sushi.hardcore.droidfs.VolumeManagerApp

/**
 * Binds VaultShelf reader activities to DroidFS' original volume lifecycle.
 *
 * No lock policy is implemented here. DroidFS decides when a volume is closed.
 * This guard only makes sure plaintext-capable activities already opened for a
 * vault session cannot remain visible after that upstream close event.
 */
object VaultSessionGuard : Application.ActivityLifecycleCallbacks, VolumeManager.Observer {

    private data class Session(
        val volumeId: Int,
        val taskIds: MutableSet<Int> = ConcurrentHashMap.newKeySet(),
        val activities: ConcurrentHashMap<Int, WeakReference<Activity>> = ConcurrentHashMap(),
    )

    private val sessions = ConcurrentHashMap<String, Session>()
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var initialized = false
    private var volumeManager: VolumeManager? = null
    private var application: VolumeManagerApp? = null

    @Synchronized
    private fun initialize(application: VolumeManagerApp) {
        if (initialized) return
        initialized = true
        this.application = application
        volumeManager = application.volumeManager
        application.volumeManager.observe(this)
        application.registerActivityLifecycleCallbacks(this)
    }

    fun register(
        activity: Activity,
        token: String,
        volumeId: Int,
    ) {
        val application = activity.application as VolumeManagerApp
        initialize(application)
        sessions[token] = Session(
            volumeId = volumeId,
        ).also { remember(it, activity) }
        enforce(token)
    }

    fun unregister(token: String) {
        sessions.remove(token)
    }

    override fun onVolumeStateChanged(volume: VolumeData) {
        enforceAll()
    }

    override fun onAllVolumesClosed() {
        enforceAll()
    }

    private fun enforceAll() {
        sessions.keys.toList().forEach(::enforce)
    }

    private fun enforce(token: String) {
        val session = sessions[token] ?: return
        val manager = volumeManager ?: return
        if (manager.getVolume(session.volumeId) != null) return

        stopVaultReadAloudIfNeeded()
        main.post {
            val current = sessions[token] ?: return@post
            current.activities.entries.toList().forEach { (id, reference) ->
                val activity = reference.get()
                if (activity == null || activity.isDestroyed) {
                    current.activities.remove(id)
                } else if (!activity.isFinishing) {
                    activity.finish()
                }
            }
        }
    }

    private fun stopVaultReadAloudIfNeeded() {
        val context = application ?: return
        val authority = ReadBook.book
            ?.bookUrl
            ?.let { runCatching { Uri.parse(it).authority }.getOrNull() }
        if (authority?.endsWith(".temporary_provider") == true) {
            ReadAloud.stop(context)
        }
    }

    private fun remember(session: Session, activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        session.taskIds.add(activity.taskId)
        session.activities[System.identityHashCode(activity)] = WeakReference(activity)
    }

    /**
     * The first Legado ReadBookActivity carries the explicit session token. Activities
     * opened from it (TOC, book info, replacement/highlight editors, link confirmation)
     * are upstream intents and do not forward arbitrary extras. While a vault session is
     * active, inherit the session only for Legado activities in the same Android task.
     *
     * Do not inherit to arbitrary same-task VaultShelf/DroidFS activities: the Explorer
     * sits below VaultContentActivity in that task and must survive locking the volume.
     */
    private fun sessionFor(activity: Activity): Pair<String, Session>? {
        activity.intent
            ?.getStringExtra(VaultShelfFileRouter.EXTRA_SESSION_TOKEN)
            ?.let { token ->
                sessions[token]?.let { return token to it }
            }

        if (!activity.javaClass.name.startsWith(LEGADO_PACKAGE_PREFIX)) return null

        val candidates = sessions.entries.filter { activity.taskId in it.value.taskIds }
        if (candidates.size != 1) return null
        return candidates.single().let { it.key to it.value }
    }

    private fun bindIfSessionActivity(activity: Activity) {
        val (token, session) = sessionFor(activity) ?: return
        remember(session, activity)
        enforce(token)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        bindIfSessionActivity(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        bindIfSessionActivity(activity)
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        val id = System.identityHashCode(activity)
        sessions.values.forEach { it.activities.remove(id) }
    }

    private const val LEGADO_PACKAGE_PREFIX = "io.legado.app."
}
