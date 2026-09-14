package com.almasix.ide

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement

/**
 * Ctrl-Q / hover docs for Almasix symbols — shell over [AlmasixHoverDocs].
 */
class AlmasixDocumentationProvider : AbstractDocumentationProvider() {
    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element is AlmasixSymbolPsiElement) {
            val index = AlmasixProjectService.getInstance(element.project).index()
            return AlmasixHoverDocs.forSymbol(index, element.kind, element.symbolName)
                ?.let { toHtml(it) }
        }
        val origin = originalElement ?: element ?: return null
        val file = origin.containingFile ?: return null
        val vFile = file.virtualFile ?: return null
        if (!AlmasixNavigation.isSupportedFile(vFile.name, file.language)) return null
        val document = file.viewProvider.document ?: return null
        val offset = origin.textRange.startOffset + origin.textLength / 2
        val hit = AlmasixSymbolLocator.hitAt(document.text, offset) ?: return null
        val index = AlmasixProjectService.getInstance(origin.project).index()
        return AlmasixHoverDocs.forSymbol(index, hit.kind, hit.name, hit.receiver)?.let { toHtml(it) }
            ?: if (hit.kind == SymbolKind.DIRECTIVE) {
                AlmasixHoverDocs.directive(hit.name)?.let { toHtml(it) }
            } else null
    }

    private fun toHtml(markdownish: String): String {
        // Minimal: backticks → <code>, **x** → <b>, newlines → <br/>
        var html = markdownish
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        html = Regex("""\*\*([^*]+)\*\*""").replace(html) { "<b>${it.groupValues[1]}</b>" }
        html = Regex("""`([^`]+)`""").replace(html) { "<code>${it.groupValues[1]}</code>" }
        return html.replace("\n", "<br/>")
    }
}
