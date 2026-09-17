package com.arjun.gander.library

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

object TxtDecoder {

    fun decode(bytes: ByteArray): String {
        val decoded = when {
            bytes.startsWith(UTF8_BOM) -> String(bytes, UTF8_BOM.size, bytes.size - UTF8_BOM.size, StandardCharsets.UTF_8)
            bytes.startsWith(UTF16_LE_BOM) -> String(bytes, UTF16_LE_BOM.size, bytes.size - UTF16_LE_BOM.size, StandardCharsets.UTF_16LE)
            bytes.startsWith(UTF16_BE_BOM) -> String(bytes, UTF16_BE_BOM.size, bytes.size - UTF16_BE_BOM.size, StandardCharsets.UTF_16BE)
            else -> decodeWithoutBom(bytes)
        }
        return normalizeLineEndings(decoded)
    }

    private fun decodeWithoutBom(bytes: ByteArray): String {
        val utf8 = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching { utf8.decode(ByteBuffer.wrap(bytes)).toString() }
            .getOrElse { String(bytes, CharsetHolder.gb18030) }
    }

    private fun normalizeLineEndings(text: String): String = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { this[it] == prefix[it] }
    }

    private object CharsetHolder {
        val gb18030 = java.nio.charset.Charset.forName("GB18030")
    }

    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val UTF16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val UTF16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
}