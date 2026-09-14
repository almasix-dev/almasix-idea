package com.almasix.ide

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * Soft references so Ctrl-hover underlines Almasix symbols and the hand cursor appears.
 */
class AlmasixReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(LeafPsiElement::class.java),
            AlmasixReferenceProvider(),
            PsiReferenceRegistrar.LOWER_PRIORITY,
        )
    }
}

class AlmasixReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext,
    ): Array<PsiReference> {
        val file = element.containingFile ?: return PsiReference.EMPTY_ARRAY
        val vFile = file.virtualFile ?: return PsiReference.EMPTY_ARRAY
        if (!AlmasixNavigation.isSupportedFile(vFile.name, file.language)) {
            return PsiReference.EMPTY_ARRAY
        }
        val text = element.text
        if (text.length > 400) return PsiReference.EMPTY_ARRAY

        val index = AlmasixProjectService.getInstance(element.project).index()
        if (!index.ok) return PsiReference.EMPTY_ARRAY

        val document = file.viewProvider.document ?: return PsiReference.EMPTY_ARRAY
        val fileText = document.text
        val elementStart = element.textRange.startOffset

        val probeOffset = (elementStart + element.textLength / 2).coerceIn(0, fileText.length)
        val hit = AlmasixSymbolLocator.hitAt(fileText, probeOffset)
            ?: AlmasixSymbolLocator.hitAt(fileText, elementStart + 1)
            ?: return PsiReference.EMPTY_ARRAY

        if (hit.range.endOffset <= elementStart || hit.range.startOffset >= element.textRange.endOffset) {
            return PsiReference.EMPTY_ARRAY
        }
        val relStart = (hit.range.startOffset - elementStart).coerceAtLeast(0)
        val relEnd = (hit.range.endOffset - elementStart).coerceAtMost(element.textLength)
        if (relStart >= relEnd) return PsiReference.EMPTY_ARRAY

        val viewName = index.viewNameForPath(vFile.path)
        val target = AlmasixSymbolResolver.resolve(
            index,
            hit.kind,
            hit.name,
            receiver = hit.receiver,
            viewName = viewName,
        ) ?: return PsiReference.EMPTY_ARRAY

        return arrayOf(
            AlmasixSymbolReference(
                element,
                TextRange(relStart, relEnd),
                target,
                hit.kind,
                hit.name,
            ),
        )
    }
}

class AlmasixSymbolReference(
    element: PsiElement,
    rangeInElement: TextRange,
    private val target: AlmasixSymbolResolver.Target,
    val kind: SymbolKind,
    val symbolName: String,
) : PsiReferenceBase<PsiElement>(element, rangeInElement, /* soft = */ true),
    EmptyResolveMessageProvider {
    override fun resolve(): PsiElement =
        AlmasixSymbolPsiElement(element.project, kind, symbolName, target)

    override fun getUnresolvedMessagePattern(): String =
        "Cannot resolve Almasix symbol"

    override fun handleElementRename(newElementName: String): PsiElement {
        AlmasixRenamePlanner.validateNewName(kind, newElementName)?.let { reason ->
            throw IllegalArgumentException(reason)
        }
        return super.handleElementRename(newElementName)
    }
}
