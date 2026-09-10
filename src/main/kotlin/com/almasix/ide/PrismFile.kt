package com.almasix.ide

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class PrismFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, PrismLanguage) {
    override fun getFileType(): FileType = PrismFileType.INSTANCE

    override fun toString(): String = "PrismFile"
}
