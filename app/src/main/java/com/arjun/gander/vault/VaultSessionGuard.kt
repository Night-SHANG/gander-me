package com.arjun.gander.vault

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.vaultshelf.droidfs.VaultShelfFileRouter
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import sushi.hardcore.droidfs.VolumeData
import sushi.hardcore.droidfs.VolumeManager
import sushi.hardcore.droidfs.VolumeManagerApp

/**
 * Binds VaultShelf reader activities to DroidFS' original volume lifecycle.
 *
 * No lock policy is implemented here. DroidFS decides when a volume is closed.
 * This guard only makes sure a document already rendered by Gander/Legado does not
 * remain visible after that upstream close event.
 */
object VaultSessionGuard : Application.ActivityLifecycleCallbacks, VolumeManager.Observer {

    private data class Session(
        val volumeId: Int,
        var activity: WeakReference<Activity>,
    )

    private val sessions = ConcurrentHashMap<String, Session>()
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var initialized = false
    private var volumeManager: VolumeManager? = null

    @Synchronized
    private fun initialize(application: VolumeManagerApp) {
        if (initialized) return
        initialized = true
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
        sessions[token] = Session(volumeId, WeakReference(activity))
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
        main.post {
            sessions[token]?.activity?.get()?.takeUnless(Activity::isFinishing)?.finish()
        }
    }

    private fun bindIfSessionActivity(activity: Activity) {
        val token = activity.intent
            ?.getStringExtra(VaultShelfFileRouter.EXTRA_SESSION_TOKEN)
            ?: return
        val session = sessions[token] ?: return
        session.activity = WeakReference(activity)
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
        val token = activity.intent
            ?.getStringExtra(VaultShelfFileRouter.EXTRA_SESSION_TOKEN)
            ?: return
        val session = sessions[token] ?: return
        if (session.activity.get() === activity) {
            session.activity = WeakReference(null)
        }
    }
}
