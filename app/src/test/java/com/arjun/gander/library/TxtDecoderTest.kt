package com.arjun.gander.library

import com.google.common.truth.Truth.assertThat
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import org.junit.Test

class TxtDecoderTest {

    @Test
    fun decodesUtf8AndNormalizesLineEndings() {
        val bytes = "第一章\r\n你好\r世界".toByteArray(StandardCharsets.UTF_8)

        assertThat(TxtDecoder.decode(bytes)).isEqualTo("第一章\n你好\n世界")
    }

    @Test
    fun stripsUtf8Bom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "正文".toByteArray(StandardCharsets.UTF_8)

        assertThat(TxtDecoder.decode(bytes)).isEqualTo("正文")
    }

    @Test
    fun decodesUtf16LittleEndianBom() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            "第一章\n正文".toByteArray(StandardCharsets.UTF_16LE)

        assertThat(TxtDecoder.decode(bytes)).isEqualTo("第一章\n正文")
    }

    @Test
    fun decodesUtf16BigEndianBom() {
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
            "Chapter 1\nText".toByteArray(StandardCharsets.UTF_16BE)

        assertThat(TxtDecoder.decode(bytes)).isEqualTo("Chapter 1\nText")
    }

    @Test
    fun fallsBackToGb18030WhenBytesAreNotValidUtf8() {
        val bytes = "第一章 旧编码\n这是正文。".toByteArray(Charset.forName("GB18030"))

        assertThat(TxtDecoder.decode(bytes)).isEqualTo("第一章 旧编码\n这是正文。")
    }
}