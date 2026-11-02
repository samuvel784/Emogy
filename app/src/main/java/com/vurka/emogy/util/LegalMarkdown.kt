package com.vurka.emogy.util

/** One renderable piece of a legal document. */
sealed interface LegalBlock {
    data class Title(val text: String) : LegalBlock
    data class Heading(val text: String) : LegalBlock
    data class Paragraph(val text: String) : LegalBlock
    data class Bullet(val text: String) : LegalBlock
}

/** A run of text inside a block; [bold] and [code] come from `**x**` and `` `x` `` markers. */
data class LegalSpan(val text: String, val bold: Boolean = false, val code: Boolean = false)

/**
 * Tiny, dependency-free reader for the Markdown subset used by the files in the docs folder:
 * `#` title, `##`/`###` headings, `*`/`-` bullets, paragraphs, `**bold**`, `` `code` `` and
 * `[label](url)` (rendered as just the label, because the in-app copy is plain text).
 *
 * It is pure Kotlin so it can be unit-tested on the JVM without Android.
 */
object LegalMarkdown {

    fun parse(markdown: String): List<LegalBlock> {
        val blocks = mutableListOf<LegalBlock>()
        val paragraph = StringBuilder()

        fun flushParagraph() {
            if (paragraph.isNotBlank()) blocks += LegalBlock.Paragraph(paragraph.toString().trim())
            paragraph.setLength(0)
        }

        markdown.replace("\r\n", "\n").lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> flushParagraph()
                line.startsWith("### ") -> {
                    flushParagraph(); blocks += LegalBlock.Heading(line.removePrefix("### ").trim())
                }
                line.startsWith("## ") -> {
                    flushParagraph(); blocks += LegalBlock.Heading(line.removePrefix("## ").trim())
                }
                line.startsWith("# ") -> {
                    flushParagraph(); blocks += LegalBlock.Title(line.removePrefix("# ").trim())
                }
                line.startsWith("* ") || line.startsWith("- ") -> {
                    flushParagraph(); blocks += LegalBlock.Bullet(line.substring(2).trim())
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line.trim())
                }
            }
        }
        flushParagraph()
        return blocks
    }

    /** Splits inline markers into spans. Unmatched markers are kept as literal text. */
    fun spans(text: String): List<LegalSpan> {
        val clean = stripLinks(text)
        val out = mutableListOf<LegalSpan>()
        val buf = StringBuilder()
        var i = 0

        fun flush() {
            if (buf.isNotEmpty()) out += LegalSpan(buf.toString())
            buf.setLength(0)
        }

        while (i < clean.length) {
            if (clean.startsWith("**", i)) {
                val end = clean.indexOf("**", i + 2)
                if (end > i + 2) {
                    flush()
                    out += LegalSpan(clean.substring(i + 2, end), bold = true)
                    i = end + 2
                    continue
                }
            }
            if (clean[i] == '`') {
                val end = clean.indexOf('`', i + 1)
                if (end > i + 1) {
                    flush()
                    out += LegalSpan(clean.substring(i + 1, end), code = true)
                    i = end + 1
                    continue
                }
            }
            if (clean[i] == '_' && (i == 0 || clean[i - 1] == ' ')) {
                val end = clean.indexOf('_', i + 1)
                if (end > i + 1 && (end + 1 == clean.length || !clean[end + 1].isLetterOrDigit())) {
                    // Italic markers are dropped; the words themselves are kept.
                    buf.append(clean, i + 1, end)
                    i = end + 1
                    continue
                }
            }
            buf.append(clean[i])
            i++
        }
        flush()
        return out
    }

    /** `[label](target)` becomes `label`. */
    internal fun stripLinks(text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (text[i] == '[') {
                val close = text.indexOf("](", i + 1)
                val end = if (close > 0) text.indexOf(')', close + 2) else -1
                if (close > 0 && end > 0) {
                    sb.append(text, i + 1, close)
                    i = end + 1
                    continue
                }
            }
            sb.append(text[i])
            i++
        }
        return sb.toString()
    }
}
