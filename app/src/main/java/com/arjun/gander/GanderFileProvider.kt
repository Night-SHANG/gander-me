package com.arjun.gander

import androidx.core.content.FileProvider

/**
 * Keeps VaultShelf/Gander's cache-sharing provider distinct from Legado's
 * FileProvider so manifest merging cannot combine their authorities or paths.
 */
class GanderFileProvider : FileProvider()
