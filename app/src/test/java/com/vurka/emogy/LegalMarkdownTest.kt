package com.vurka.emogy

import com.vurka.emogy.util.LegalBlock
import com.vurka.emogy.util.LegalMarkdown
import com.vurka.emogy.util.LegalSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalMarkdownTest {

    @Test
    fun parsesTitlesHeadingsBulletsAndParagraphs() {
        val blocks = LegalMarkdown.parse(
            """
            # Title
            _Last updated: today_

            ## Section
            First line
            continues here.

            * one
            - two
            """.trimIndent()
        )
        assertEquals(
            listOf(
                LegalBlock.Title("Title"),
                LegalBlock.Paragraph("_Last updated: today_"),
                LegalBlock.Heading("Section"),
                LegalBlock.Paragraph("First line continues here."),
                LegalBlock.Bullet("one"),
                LegalBlock.Bullet("two"),
            ),
            blocks
        )
    }

    @Test
    fun handlesWindowsLineEndingsAndEmptyInput() {
        assertTrue(LegalMarkdown.parse("").isEmpty())
        assertEquals(
            listOf(LegalBlock.Heading("A"), LegalBlock.Paragraph("b")),
            LegalMarkdown.parse("## A\r\nb\r\n")
        )
    }

    @Test
    fun boldAndCodeBecomeSpans() {
        assertEquals(
            listOf(
                LegalSpan("Use "),
                LegalSpan("VIBRATE", code = true),
                LegalSpan(" and "),
                LegalSpan("nothing else", bold = true),
                LegalSpan("."),
            ),
            LegalMarkdown.spans("Use `VIBRATE` and **nothing else**.")
        )
    }

    @Test
    fun unmatchedMarkersStayLiteral() {
        assertEquals(listOf(LegalSpan("5 ** 3 and a ` tick")), LegalMarkdown.spans("5 ** 3 and a ` tick"))
    }

    @Test
    fun linksKeepOnlyTheirLabel() {
        assertEquals("See Privacy Policy now", LegalMarkdown.stripLinks("See [Privacy Policy](privacy-policy.md) now"))
        assertEquals("[broken](", LegalMarkdown.stripLinks("[broken]("))
    }

    @Test
    fun italicMarkersAreDroppedButSnakeCaseIsKept() {
        assertEquals(listOf(LegalSpan("Last updated")), LegalMarkdown.spans("_Last updated_"))
        assertEquals(listOf(LegalSpan("my_var_name")), LegalMarkdown.spans("my_var_name"))
    }
}
