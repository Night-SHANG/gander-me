package com.arjun.gander.navigation

import android.app.Activity

/** Only use after a top-level tab or file-tree handoff, never for reader or settings pages. */
@Suppress("DEPRECATION")
fun Activity.suppressTopLevelTransition() {
    overridePendingTransition(0, 0)
}
