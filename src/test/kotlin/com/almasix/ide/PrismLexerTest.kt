package com.almasix.ide

import com.intellij.psi.tree.IElementType
import org.junit.Assert.assertEquals
import org.junit.Test

class PrismLexerTest {
    @Test
    fun `splits html host text from prism constructs`() {
        assertEquals(
            listOf(
                PrismTokens.TEMPLATE_DATA to "<div class=\"card\">",
                PrismTokens.ECHO to "{{ name }}",
                PrismTokens.TEMPLATE_DATA to "</div>",
            ),
            tokens("<div class=\"card\">{{ name }}</div>"),
        )
    }

    @Test
    fun `directive swallows its argument list`() {
        assertEquals(
            listOf(
                PrismTokens.DIRECTIVE to "@if(count > 1 and label == \")\")",
                PrismTokens.TEMPLATE_DATA to "x",
                PrismTokens.DIRECTIVE to "@endif",
            ),
            tokens("@if(count > 1 and label == \")\")x@endif"),
        )
    }

    @Test
    fun `comments and raw echoes are distinct`() {
        assertEquals(
            listOf(
                PrismTokens.COMMENT to "{{-- hidden --}}",
                PrismTokens.RAW_ECHO to "{!! body !!}",
            ),
            tokens("{{-- hidden --}}{!! body !!}"),
        )
    }

    @Test
    fun `email addresses and escaped at signs stay html`() {
        assertEquals(
            listOf(PrismTokens.TEMPLATE_DATA to "hi@example.com @@notADirective"),
            tokens("hi@example.com @@notADirective"),
        )
    }

    @Test
    fun `unterminated constructs consume the rest without stalling`() {
        assertEquals(listOf(PrismTokens.ECHO to "{{ oops"), tokens("{{ oops"))
        assertEquals(listOf(PrismTokens.COMMENT to "{{-- oops"), tokens("{{-- oops"))
    }

    private fun tokens(text: String): List<Pair<IElementType, String>> {
        val lexer = PrismLexer()
        lexer.start(text, 0, text.length, 0)
        assertEquals(text.length, lexer.bufferEnd)
        val out = mutableListOf<Pair<IElementType, String>>()
        while (true) {
            val type = lexer.tokenType ?: break
            out += type to text.substring(lexer.tokenStart, lexer.tokenEnd)
            check(lexer.tokenEnd > lexer.tokenStart) { "lexer did not advance at ${lexer.tokenStart}" }
            lexer.advance()
        }
        return out
    }

    @Test
    fun `unknown at sequences stay host text`() {
        // Hits directive scanner return -1 paths (not a known directive / email-like).
        assertEquals(
            listOf(PrismTokens.TEMPLATE_DATA to "@notARealDirective"),
            tokens("@notARealDirective"),
        )
    }
}
