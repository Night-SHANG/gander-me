package com.arjun.gander.reader

import android.content.Context
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import java.util.Locale

object ReaderChromePreferences {
    const val KEY_PRIMARY = "vaultshelf_reader_chrome_primary"
    const val DEFAULT_PRIMARY = "#F3F3F3"

    private fun prefs(context: Context) =
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)

    fun normalizeHex(value: String): String? {
        val body = value.trim().removePrefix("#")
        if (!body.matches(Regex("[0-9A-Fa-f]{6}"))) return null
        return "#" + body.uppercase(Locale.ROOT)
    }

    fun isValid(value: String): Boolean =
        normalizeHex(value)?.let { runCatching { it.toColorInt() }.isSuccess } == true

    fun load(context: Context): String =
        normalizeHex(prefs(context).getString(KEY_PRIMARY, null).orEmpty())
            ?: DEFAULT_PRIMARY

    fun save(context: Context, color: String) {
        prefs(context).edit {
            putString(KEY_PRIMARY, normalizeHex(color) ?: DEFAULT_PRIMARY)
        }
    }

    fun reset(context: Context) {
        prefs(context).edit {
            remove(KEY_PRIMARY)
        }
    }
}
