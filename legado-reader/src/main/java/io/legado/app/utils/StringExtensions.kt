/*
 * Adapted from Legado / 阅读 3.0.
 * Source: https://github.com/TsaiYongChuan/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.utils

/** Splits a string by Unicode code point so surrogate-pair emoji stay intact. */
fun String.toStringArray(): Array<String> {
    var codePointIndex = 0
    return try {
        Array(codePointCount(0, length)) {
            val start = codePointIndex
            codePointIndex = offsetByCodePoints(start, 1)
            substring(start, codePointIndex)
        }
    } catch (_: Exception) {
        map { it.toString() }.toTypedArray()
    }
}
