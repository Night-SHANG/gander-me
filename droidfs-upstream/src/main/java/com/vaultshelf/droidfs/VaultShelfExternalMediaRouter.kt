package com.vaultshelf.droidfs

import android.content.Context
import android.content.Intent
import android.net.Uri
import sushi.hardcore.droidfs.file_viewers.AudioPlayer
import sushi.hardcore.droidfs.file_viewers.VideoPlayer
import java.io.File

/**
 * Reuses DroidFS' original media Activities for ordinary, unencrypted Android URIs.
 * Only the data source differs; the player UI and interaction stay upstream.
 */
object VaultShelfExternalMediaRouter {

    const val EXTRA_PLAIN_MEDIA = "vaultshelf.media.plain"
    const val EXTRA_DISPLAY_NAME = "vaultshelf.media.display_name"

    private val audioExt = setOf("mp3", "ogg", "m4a", "wav", "flac", "opus")
    private val videoExt = setOf("mp4", "webm", "mkv", "mov", "m4v")

    fun supports(name: String, mime: String?): Boolean {
        val ext = File(name).extension.lowercase()
        return ext in audioExt || ext in videoExt ||
            mime?.startsWith("audio/") == true ||
            mime?.startsWith("video/") == true
    }

    fun intent(
        context: Context,
        uri: Uri,
        name: String,
        mime: String?,
    ): Intent {
        val ext = File(name).extension.lowercase()
        val audio = ext in audioExt || mime?.startsWith("audio/") == true
        val target = if (audio) AudioPlayer::class.java else VideoPlayer::class.java

        return Intent(context, target)
            .setData(uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(EXTRA_PLAIN_MEDIA, true)
            .putExtra(EXTRA_DISPLAY_NAME, name)
            .putExtra("path", name)
    }
}
