package com.almasix.ide

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor

/**
 * Alt-F7 / Find Usages for Almasix routes, views, config keys, components, and env keys.
 */
class AlmasixFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
    override fun canFindUsages(element: PsiElement): Boolean =
        resolveSymbol(element) != null

    override fun createFindUsagesHandler(
        element: PsiElement,
        forHighlightUsages: Boolean,
    ): FindUsagesHandler? {
        val symbol = resolveSymbol(element) ?: return null
        return AlmasixFindUsagesHandler(symbol)
    }

    companion object {
        fun resolveSymbol(element: PsiElement): AlmasixSymbolPsiElement? {
            if (element is AlmasixSymbolPsiElement) {
                if (element.kind in AlmasixCallSiteSearcher.SUPPORTED) return element
                return null
            }
            // Soft reference resolve target
            for (ref in element.references) {
                if (ref is AlmasixSymbolReference) {
                    val resolved = ref.resolve()
                    if (resolved is AlmasixSymbolPsiElement &&
                        resolved.kind in AlmasixCallSiteSearcher.SUPPORTED
                    ) {
                        return resolved
                    }
                }
            }
            // Caret on a string / tag without going through the reference first
            val file = element.containingFile ?: return null
            val vFile = file.virtualFile ?: return null
            if (!AlmasixNavigation.isSupportedFile(vFile.name, file.language)) return null
            val document = file.viewProvider.document ?: return null
            val offset = element.textRange.startOffset + element.textLength / 2
            val hit = AlmasixSymbolLocator.hitAt(document.text, offset)
                ?: AlmasixSymbolLocator.hitAt(document.text, element.textRange.startOffset + 1)
                ?: return null
            if (hit.kind !in AlmasixCallSiteSearcher.SUPPORTED) return null
            val index = AlmasixProjectService.getInstance(element.project).index()
            if (!index.ok) return null
            val viewName = index.viewNameForPath(vFile.path)
            val target = AlmasixSymbolResolver.resolve(
                index,
                hit.kind,
                hit.name,
                receiver = hit.receiver,
                viewName = viewName,
            )
            return AlmasixSymbolPsiElement(element.project, hit.kind, hit.name, target)
        }
    }
}

class AlmasixFindUsagesHandler(
    private val symbol: AlmasixSymbolPsiElement,
) : FindUsagesHandler(symbol) {
    override fun getPrimaryElements(): Array<PsiElement> = arrayOf(symbol)

    override fun processElementUsages(
        element: PsiElement,
        processor: Processor<in UsageInfo>,
        options: FindUsagesOptions,
    ): Boolean {
        val project = element.project
        val root = AlmasixProjectService.getInstance(project).appRoot() ?: return true
        val occurrences = AlmasixCallSiteSearcher.findUsages(root, symbol.kind, symbol.symbolName)
        for (info in AlmasixUsageInfoFactory.usageInfos(project, occurrences)) {
            if (!processor.process(info)) return false
        }
        return true
    }

    override fun getFindUsagesOptions(dataContext: DataContext?): FindUsagesOptions {
        val options = super.getFindUsagesOptions(dataContext)
        options.isSearchForTextOccurrences = false
        return options
    }
}
