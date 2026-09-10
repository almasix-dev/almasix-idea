package com.almasix.ide

import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.FileViewProviderFactory
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider
import com.intellij.psi.tree.IElementType

/** Prism (base) + HTML (template data) roots, so HTML tooling sees real HTML. */
class PrismFileViewProvider(
    manager: PsiManager,
    file: VirtualFile,
    eventSystemEnabled: Boolean,
) : MultiplePsiFilesPerDocumentFileViewProvider(manager, file, eventSystemEnabled),
    TemplateLanguageFileViewProvider {

    override fun getBaseLanguage(): Language = PrismLanguage

    override fun getTemplateDataLanguage(): Language = HTMLLanguage.INSTANCE

    override fun getLanguages(): Set<Language> = setOf(PrismLanguage, HTMLLanguage.INSTANCE)

    override fun getContentElementType(language: Language): IElementType? =
        if (language === HTMLLanguage.INSTANCE) PRISM_TEMPLATE_DATA else null

    override fun cloneInner(fileCopy: VirtualFile): MultiplePsiFilesPerDocumentFileViewProvider =
        PrismFileViewProvider(manager, fileCopy, false)

    override fun createFile(lang: Language): PsiFile? {
        val definition = LanguageParserDefinitions.INSTANCE.forLanguage(lang) ?: return null
        val file = definition.createFile(this)
        if (lang === HTMLLanguage.INSTANCE && file is PsiFileImpl) {
            file.contentElementType = PRISM_TEMPLATE_DATA
        }
        return file
    }
}

class PrismFileViewProviderFactory : FileViewProviderFactory {
    override fun createFileViewProvider(
        file: VirtualFile,
        language: Language?,
        manager: PsiManager,
        eventSystemEnabled: Boolean,
    ): FileViewProvider = PrismFileViewProvider(manager, file, eventSystemEnabled)
}
