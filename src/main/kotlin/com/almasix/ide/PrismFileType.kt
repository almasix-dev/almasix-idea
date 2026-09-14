package com.almasix.ide

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * Prism templates (``.prism.html``).
 *
 * The compound extension is not something IntelliJ matches on its own, so the
 * type also identifies itself by virtual file and is backed by an overrider and
 * a detector — otherwise HTML claims the file and Prism syntax never binds.
 */
class PrismFileType private constructor() :
    LanguageFileType(PrismLanguage),
    FileTypeIdentifiableByVirtualFile {
    override fun getName(): String = "Prism"

    override fun getDescription(): String = "Almasix Prism template (.prism.html)"

    override fun getDefaultExtension(): String = "prism.html"

    override fun getIcon(): Icon =
        IconLoader.getIcon("/icons/prism.svg", PrismFileType::class.java)

    override fun isMyFileType(file: VirtualFile): Boolean =
        !file.isDirectory && isPrismFileName(file.name)

    companion object {
        @JvmField
        val INSTANCE = PrismFileType()

        fun isPrismFileName(name: String): Boolean =
            name.endsWith(".prism.html", ignoreCase = true)
    }
}
