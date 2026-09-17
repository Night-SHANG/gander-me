package com.arjun.gander.library

data class TxtChapter(
    val title: String?,
    val startOffset: Int,
    val contentStartOffset: Int,
    val endOffset: Int,
)

data class ReaderParagraph(
    val text: String,
    val startOffset: Int,
)

object TxtChapterParser {

    fun parse(text: String): List<TxtChapter> {
        if (text.isEmpty()) return listOf(TxtChapter(null, 0, 0, 0))

        val matches = HEADING.findAll(text).toList()
        if (matches.isEmpty()) return listOf(TxtChapter(null, 0, 0, text.length))

        val chapters = mutableListOf<TxtChapter>()
        val firstStart = matches.first().range.first
        if (text.substring(0, firstStart).isNotBlank()) {
            chapters += TxtChapter(
                title = null,
                startOffset = 0,
                contentStartOffset = 0,
                endOffset = firstStart,
            )
        }

        matches.forEachIndexed { index, match ->
            val title = listOf(match.groups[1]?.value, match.groups[2]?.value)
                .firstNotNullOf { it }
                .trim()
            val contentStart = skipLineBreaks(text, match.range.last + 1)
            val end = matches.getOrNull(index + 1)?.range?.first ?: text.length
            chapters += TxtChapter(
                title = title,
                startOffset = match.range.first,
                contentStartOffset = contentStart,
                endOffset = end,
            )
        }
        return chapters
    }

    fun chapterIndexForOffset(chapters: List<TxtChapter>, offset: Int): Int {
        if (chapters.isEmpty()) return 0
        val safeOffset = offset.coerceAtLeast(0)
        return chapters.indexOfLast { safeOffset >= it.startOffset }
            .coerceAtLeast(0)
    }

    fun paragraphs(text: String, chapter: TxtChapter): List<ReaderParagraph> {
        val start = chapter.contentStartOffset.coerceIn(0, text.length)
        val end = chapter.endOffset.coerceIn(start, text.length)
        if (start == end) return emptyList()

        val result = mutableListOf<ReaderParagraph>()
        var cursor = start
        while (cursor < end) {
            val newline = text.indexOf('\n', startIndex = cursor)
            val lineEnd = if (newline == -1 || newline > end) end else newline
            val rawLine = text.substring(cursor, lineEnd)
            val firstContent = rawLine.indexOfFirst { !it.isWhitespace() }
            if (firstContent >= 0) {
                result += ReaderParagraph(
                    text = rawLine.trim(),
                    startOffset = cursor + firstContent,
                )
            }
            cursor = if (lineEnd < end) lineEnd + 1 else end
        }
        return result
    }

    private fun skipLineBreaks(text: String, from: Int): Int {
        var index = from.coerceIn(0, text.length)
        while (index < text.length && (text[index] == '\n' || text[index] == '\r')) {
            index++
        }
        return index
    }

    private val HEADING = Regex(
        pattern = """^[\t ]*(?:(第[零〇一二三四五六七八九十百千万两0-9]+[章节回卷部篇][^\n]{0,60})|(chapter[\t ]+(?:[0-9]+|[ivxlcdm]+)\b[^\n]{0,60}))[\t ]*$""",
        options = setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE),
    )
}