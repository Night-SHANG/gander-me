package com.arjun.gander.vault

import android.app.Activity
import android.content.Context
import android.view.WindowManager

/**
 * One screenshot policy for every plaintext-capable vault surface.
 *
 * DroidFS owns the preference key. VaultShelf mirrors it here so its Compose shell,
 * Gander viewer and Legado session activities cannot drift from DroidFS Explorer.
 */
object VaultScreenshotPolicy {
    const val PREF_ALLOW_SCREENSHOTS = "usf_screenshot"

    fun preferences(context: Context) =
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)

    fun screenshotsAllowed(context: Context): Boolean =
        preferences(context).getBoolean(PREF_ALLOW_SCREENSHOTS, false)

    fun apply(activity: Activity) {
        if (screenshotsAllowed(activity)) {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
