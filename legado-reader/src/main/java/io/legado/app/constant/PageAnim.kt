/*
 * Adapted from Legado / 阅读 3.0.
 * Source: https://github.com/LegadoTeam/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.constant

import androidx.annotation.IntDef

@Suppress("ConstPropertyName")
object PageAnim {

    const val coverPageAnim = 0
    const val slidePageAnim = 1
    const val simulationPageAnim = 2
    const val scrollPageAnim = 3
    const val noAnim = 4

    @Target(AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(coverPageAnim, slidePageAnim, simulationPageAnim, scrollPageAnim, noAnim)
    annotation class Anim
}
