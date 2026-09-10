package com.almasix.ide

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType

/**
 * Colors Prism syntax only — HTML is layered on top of
 * [PrismTokens.TEMPLATE_DATA] by [PrismEditorHighlighterProvider].
 */
class PrismSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(
        project: Project?,
        virtualFile: VirtualFile?,
    ): SyntaxHighlighter = PrismSyntaxHighlighter
}

object PrismSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = PrismLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        when (tokenType) {
            PrismTokens.COMMENT -> COMMENT_KEYS
            PrismTokens.ECHO, PrismTokens.RAW_ECHO -> ECHO_KEYS
            PrismTokens.DIRECTIVE -> DIRECTIVE_KEYS
            else -> EMPTY
        }

    private val COMMENT_KEYS = arrayOf(PrismColors.COMMENT)
    private val ECHO_KEYS = arrayOf(PrismColors.ECHO)
    private val DIRECTIVE_KEYS = arrayOf(PrismColors.DIRECTIVE)
    private val EMPTY = emptyArray<TextAttributesKey>()
}

object PrismColors {
    val COMMENT: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "PRISM_COMMENT",
        DefaultLanguageHighlighterColors.BLOCK_COMMENT,
    )
    val ECHO: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "PRISM_ECHO",
        DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR,
    )
    val DIRECTIVE: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "PRISM_DIRECTIVE",
        DefaultLanguageHighlighterColors.KEYWORD,
    )
}
