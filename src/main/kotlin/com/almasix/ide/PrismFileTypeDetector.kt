package com.almasix.ide

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry.FileTypeDetector
import com.intellij.openapi.util.io.ByteSequence
import com.intellij.openapi.vfs.VirtualFile

/**
 * High-priority detector so ``*.prism.html`` is Prism before HTML content sniffing.
 */
class PrismFileTypeDetector : FileTypeDetector {
    override fun detect(
        file: VirtualFile,
        firstBytes: ByteSequence,
        firstCharsIfText: CharSequence?,
    ): FileType? {
        if (PrismFileType.isPrismFileName(file.name)) {
            return PrismFileType.INSTANCE
        }
        return null
    }

    override fun getDesiredContentPrefixLength(): Int = 0
}
