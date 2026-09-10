package com.almasix.ide

import com.intellij.lang.Language
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lexer.LexerBase
import com.intellij.psi.templateLanguages.TemplateDataElementType
import com.intellij.psi.templateLanguages.TemplateLanguage
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.OuterLanguageElementType
import com.intellij.psi.tree.TokenSet

/**
 * Prism is a template language over HTML.
 *
 * Two things must be true for the editor to look right, and they are separate:
 *
 * 1. **PSI** — [PrismFileViewProvider] gives the file an HTML root so HTML
 *    completion / inspections work ([PRISM_TEMPLATE_DATA] strips Prism syntax).
 * 2. **Colors** — [PrismEditorHighlighterProvider] layers the HTML highlighter
 *    over [PrismTokens.TEMPLATE_DATA] spans. A `LayeredLexer` inside the
 *    syntax highlighter is *not* the same thing and blanked editors in 0.1.4.
 */
object PrismLanguage : Language("Prism"), TemplateLanguage {
    private fun readResolve(): Any = PrismLanguage
}

object PrismTokens {
    /** HTML host text; highlighted and parsed as HTML. */
    val TEMPLATE_DATA = IElementType("PRISM_TEMPLATE_DATA", PrismLanguage)

    /** Holes punched into the HTML tree where Prism syntax was removed. */
    val OUTER = OuterLanguageElementType("PRISM_OUTER", PrismLanguage)

    val COMMENT = IElementType("PRISM_COMMENT", PrismLanguage)
    val ECHO = IElementType("PRISM_ECHO", PrismLanguage)
    val RAW_ECHO = IElementType("PRISM_RAW_ECHO", PrismLanguage)
    val DIRECTIVE = IElementType("PRISM_DIRECTIVE", PrismLanguage)

    val PRISM_SYNTAX = TokenSet.create(COMMENT, ECHO, RAW_ECHO, DIRECTIVE)
    val ALL = TokenSet.create(TEMPLATE_DATA, OUTER, COMMENT, ECHO, RAW_ECHO, DIRECTIVE)
}

/** Feeds the HTML parser the buffer with Prism constructs replaced by outer holes. */
val PRISM_TEMPLATE_DATA = TemplateDataElementType(
    "PRISM_HTML",
    HTMLLanguage.INSTANCE,
    PrismTokens.TEMPLATE_DATA,
    PrismTokens.OUTER,
)

/**
 * Splits a template into Prism constructs and HTML host chunks.
 *
 * Every [advance] consumes at least one character, so the lexer can never
 * stall the editor.
 */
class PrismLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var endOffset = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.endOffset = endOffset
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        this.tokenType = null
        if (startOffset < endOffset) advance()
    }

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = endOffset

    override fun advance() {
        tokenStart = tokenEnd
        if (tokenStart >= endOffset) {
            tokenType = null
            return
        }

        val text = buffer
        val i = tokenStart

        if (match(text, i, "{{--")) {
            val close = indexOf(text, i + 4, "--}}")
            tokenEnd = if (close >= 0) close + 4 else endOffset
            tokenType = PrismTokens.COMMENT
            return
        }
        if (match(text, i, "{!!")) {
            val close = indexOf(text, i + 3, "!!}")
            tokenEnd = if (close >= 0) close + 3 else endOffset
            tokenType = PrismTokens.RAW_ECHO
            return
        }
        if (match(text, i, "{{")) {
            val close = indexOf(text, i + 2, "}}")
            tokenEnd = if (close >= 0) close + 2 else endOffset
            tokenType = PrismTokens.ECHO
            return
        }
        val directiveEnd = directiveEndAt(text, i)
        if (directiveEnd > 0) {
            tokenEnd = directiveEnd
            tokenType = PrismTokens.DIRECTIVE
            return
        }

        var j = i + 1
        // `@@if` is an escaped literal: step over both so the second `@` cannot open one.
        if (text[i] == '@' && j < endOffset && text[j] == '@') j++
        while (j < endOffset && !startsPrism(text, j)) j++
        tokenEnd = j
        tokenType = PrismTokens.TEMPLATE_DATA
    }

    private fun startsPrism(text: CharSequence, offset: Int): Boolean =
        match(text, offset, "{{") || match(text, offset, "{!!") || directiveEndAt(text, offset) > 0

    /** End offset of the directive at [offset], or -1 when there is none. */
    private fun directiveEndAt(text: CharSequence, offset: Int): Int {
        if (text[offset] != '@') return -1
        if (offset > 0 && text[offset - 1] == '@') return -1
        var j = offset + 1
        while (j < endOffset && isIdentPart(text[j])) j++
        if (j == offset + 1) return -1
        // Only known directives, so `hi@example.com` stays HTML text.
        if (text.subSequence(offset + 1, j).toString() !in PrismDirectives.NAMES) return -1
        // Directives carry their argument list so `@if(a > b)` cannot break HTML.
        if (j < endOffset && text[j] == '(') {
            val close = matchingParen(text, j)
            j = if (close >= 0) close + 1 else endOffset
        }
        return j
    }

    private fun matchingParen(text: CharSequence, open: Int): Int {
        var depth = 0
        var quote: Char? = null
        var i = open
        while (i < endOffset) {
            val c = text[i]
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == '(' -> depth++
                c == ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    private fun match(text: CharSequence, offset: Int, literal: String): Boolean {
        if (offset + literal.length > endOffset) return false
        for (k in literal.indices) {
            if (text[offset + k] != literal[k]) return false
        }
        return true
    }

    private fun indexOf(text: CharSequence, from: Int, literal: String): Int {
        val last = endOffset - literal.length
        var i = from
        while (i <= last) {
            if (match(text, i, literal)) return i
            i++
        }
        return -1
    }

    private fun isIdentPart(c: Char): Boolean = c == '_' || c.isLetterOrDigit()
}
