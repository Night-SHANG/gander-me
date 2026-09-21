package com.arjun.gander.vault

import android.content.Context
import androidx.core.content.edit
import sushi.hardcore.droidfs.Constants
import sushi.hardcore.droidfs.VolumeData
import sushi.hardcore.droidfs.VolumeDatabase

/**
 * VaultShelf's single source of truth for the default vault.
 *
 * DroidFS historically stored the volume name in DEFAULT_VOLUME_KEY. VaultShelf stores the
 * immutable volume UUID instead, but transparently migrates an old name the first time it is read.
 * The original DroidFS password-dialog checkbox is patched to use the same UUID-backed key, so
 * this setting, the volume chooser action and that checkbox always stay synchronized.
 */
object VaultDefaultVolumePreference {
    private fun prefs(context: Context) =
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)

    fun resolve(context: Context): VolumeData? =
        VolumeDatabase(context).use { database ->
            resolve(context, database.getVolumes())
        }

    fun resolve(context: Context, volumes: List<VolumeData>): VolumeData? {
        val stored = prefs(context).getString(Constants.DEFAULT_VOLUME_KEY, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        volumes.firstOrNull { it.uuid == stored }?.let { return it }

        // One-time migration from DroidFS' old name-based value.
        volumes.firstOrNull { it.name == stored }?.let { legacy ->
            set(context, legacy)
            return legacy
        }

        // Do not leave a stale default pointing at a removed volume.
        clear(context)
        return null
    }

    fun set(context: Context, volume: VolumeData) {
        prefs(context).edit {
            putString(Constants.DEFAULT_VOLUME_KEY, volume.uuid)
        }
    }

    fun clear(context: Context) {
        prefs(context).edit {
            remove(Constants.DEFAULT_VOLUME_KEY)
        }
    }

    fun isDefault(context: Context, volume: VolumeData): Boolean =
        resolve(context)?.uuid == volume.uuid
}
