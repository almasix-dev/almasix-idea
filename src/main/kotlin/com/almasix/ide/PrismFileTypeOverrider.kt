package com.almasix.ide

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.vfs.VirtualFile

/**
 * Force ``*.prism.html`` onto Prism (EP: ``com.intellij.fileTypeOverrider``).
 *
 * Without this, IntelliJ's ``*.html`` association wins for compound names and
 * the Prism highlighter never runs.
 */
class PrismFileTypeOverrider : FileTypeOverrider {
    override fun getOverriddenFileType(file: VirtualFile): FileType? {
        if (PrismFileType.isPrismFileName(file.name)) {
            return PrismFileType.INSTANCE
        }
        return null
    }
}
