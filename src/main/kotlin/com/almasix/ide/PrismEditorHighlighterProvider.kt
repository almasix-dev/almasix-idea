package com.almasix.ide

import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.util.LayerDescriptor
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.fileTypes.EditorHighlighterProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Paints HTML with the platform HTML highlighter and Prism syntax on top.
 *
 * [LayeredLexerEditorHighlighter] collects every [PrismTokens.TEMPLATE_DATA]
 * span into one HTML stream, so a tag split by `{{ … }}` still highlights as a
 * tag. This is the editor-level composition template plugins use; layering
 * lexers inside a `SyntaxHighlighter` is what produced blank editors before.
 */
class PrismEditorHighlighterProvider : EditorHighlighterProvider {
    override fun getEditorHighlighter(
        project: Project?,
        fileType: FileType,
        virtualFile: VirtualFile?,
        colors: EditorColorsScheme,
    ): EditorHighlighter = PrismEditorHighlighter(project, virtualFile, colors)
}

class PrismEditorHighlighter(
    project: Project?,
    virtualFile: VirtualFile?,
    colors: EditorColorsScheme,
) : LayeredLexerEditorHighlighter(PrismSyntaxHighlighter, colors) {
    init {
        val html = SyntaxHighlighterFactory.getSyntaxHighlighter(
            HTMLLanguage.INSTANCE,
            project,
            virtualFile,
        )
        registerLayer(PrismTokens.TEMPLATE_DATA, LayerDescriptor(html, ""))
    }
}
