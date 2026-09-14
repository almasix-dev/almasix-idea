package com.almasix.ide

import java.nio.file.Files
import java.nio.file.Path

/**
 * Creates stub Prism files for code actions — pure filesystem helper.
 */
object AlmasixStubFileWriter {
    /**
     * Write [content] to [path] if missing. Creates parent directories.
     * @return true when a new file was written.
     */
    fun writeIfAbsent(path: Path, content: String): Boolean {
        if (Files.exists(path)) return false
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
        return true
    }

    /** Overwrite [path] with [content] (creates parents). */
    fun write(path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }

    fun applyCreateAction(action: AlmasixCodeActionPlanner.Action): Boolean {
        val createPath = action.createPath ?: return false
        val body = AlmasixCodeActionPlanner.stubPrismContent(action.name)
        return writeIfAbsent(Path.of(createPath), body)
    }
}
