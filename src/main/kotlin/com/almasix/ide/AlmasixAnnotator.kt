package com.almasix.ide

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Squiggles unknown Almasix string symbols (routes, views, config, …).
 */
class AlmasixAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element.containingFile ?: return
        val vFile = file.virtualFile ?: return
        val name = vFile.name
        val isPrism = name.endsWith(".prism.html") || file.language === PrismLanguage
        val isPython = name.endsWith(".py")
        if (!isPrism && !isPython) return

        // Annotate only leaf-ish elements that look like string content, or whole file chunks.
        val text = element.text
        if (text.length < 3 || text.length > 200) return
        if (!(text.startsWith("\"") || text.startsWith("'"))) return
        val literal = text.substring(1, text.length - 1)
        if (literal.isEmpty() || literal.length > 120) return

        val project = element.project
        val index = AlmasixProjectService.getInstance(project).index()
        if (!index.ok) return

        val document = file.viewProvider.document ?: return
        val start = element.textRange.startOffset
        if (start <= 0) return
        val before = document.text.substring(0, start)
        val site = CallSiteDetector.detect(before + text.first()) ?: return
        // Re-detect with the string opened so prefix includes empty content end
        val site2 = CallSiteDetector.detect(before + text.first() + literal) ?: site

        val kind = site2.kind
        if (kind !in ANNOTATED) return
        if (kind == SymbolKind.TRANSLATION && index.translationKeys.isEmpty()) return
        if (kind == SymbolKind.GATE && index.gates.isEmpty()) return

        val value = when (kind) {
            SymbolKind.VALIDATION -> literal.substringBefore(":").substringBefore("|")
            else -> literal
        }
        if (index.known(kind, value)) return

        val range = TextRange(element.textRange.startOffset + 1, element.textRange.endOffset - 1)
        holder.newAnnotation(HighlightSeverity.WARNING, "Unknown Almasix ${kind.name.lowercase()}: $value")
            .range(range)
            .create()
    }

    companion object {
        private val ANNOTATED = setOf(
            SymbolKind.ROUTE,
            SymbolKind.VIEW,
            SymbolKind.CONFIG,
            SymbolKind.TRANSLATION,
            SymbolKind.COMPONENT,
            SymbolKind.GATE,
            SymbolKind.MIDDLEWARE,
            SymbolKind.DISK,
            SymbolKind.INERTIA,
        )
    }
}
